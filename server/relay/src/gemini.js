// Gemini (OpenAI 호환 엔드포인트)로 LLM 요청을 넘긴다. 키는 Worker 비밀값 GEMINI_API_KEY에만 있다.
// - llama.cpp 전용 값(cache_prompt, chat_template_kwargs 등)은 빼고 OpenAI 표준 값만 넘긴다.
// - 모델이 503(붐빔)·429(한도)면 다음 모델로 한 번씩 더 묻는다 (본문을 받기 전에만).

export const GEMINI_BASE = 'https://generativelanguage.googleapis.com/v1beta/openai/';
export const GEMINI_MODELS = ['gemini-flash-lite-latest', 'gemini-3.1-flash-lite', 'gemini-3.5-flash-lite'];

const ALLOWED = new Set([
  'model', 'messages', 'tools', 'tool_choice', 'temperature', 'top_p', 'max_tokens', 'max_completion_tokens',
  'stream', 'stream_options', 'n', 'stop', 'response_format', 'seed', 'reasoning_effort',
  'presence_penalty', 'frequency_penalty',
]);

/** 넘길 본문만 남긴다. */
export function cleanBody(body) {
  const out = {};
  for (const [k, v] of Object.entries(body || {})) if (ALLOWED.has(k) && v !== null && v !== undefined) out[k] = v;
  // 빈 tools 배열은 Gemini가 거부한다
  if (Array.isArray(out.tools) && out.tools.length === 0) {
    delete out.tools;
    delete out.tool_choice;
  }
  // Gemini 3은 앞서 받은 도구 호출에 thought_signature를 다시 붙여 보내야 받아 준다.
  // 앱은 그 값을 들고 있지 않으므로, 없으면 Google이 정해 둔 "검사 건너뛰기" 값을 붙인다.
  if (Array.isArray(out.messages)) {
    out.messages = out.messages.map((m) => {
      if (m?.role !== 'assistant' || !Array.isArray(m.tool_calls)) return m;
      const tool_calls = m.tool_calls.map((c) =>
        c?.extra_content?.google?.thought_signature ? c : { ...c, extra_content: { google: { thought_signature: SKIP_SIGNATURE } } });
      return { ...m, tool_calls };
    });
  }
  return out;
}

export const SKIP_SIGNATURE = 'skip_thought_signature_validator';

/** 물어볼 모델 순서. 요청 모델이 Gemini면 맨 앞, 아니면(qwen 등 옛 설정) 기본 목록. */
export function modelChain(requested, configured) {
  const base = (configured || '').split(',').map((s) => s.trim()).filter(Boolean);
  const list = base.length ? base : GEMINI_MODELS;
  const first = typeof requested === 'string' && requested.startsWith('gemini') ? [requested] : [];
  return [...new Set([...first, ...list])];
}

export async function gemini(request, env, path, fetchImpl = fetch) {
  const headers = { Authorization: `Bearer ${env.GEMINI_API_KEY}` };
  if (path === 'v1/models') {
    if (request.method !== 'GET') return json({ error: 'method' }, 405);
    const models = modelChain(null, env.GEMINI_MODELS);
    return json({ object: 'list', data: models.map((id) => ({ id, object: 'model', owned_by: 'google' })) });
  }
  if (request.method !== 'POST') return json({ error: 'method' }, 405);
  let body;
  try {
    body = cleanBody(await request.json());
  } catch {
    return json({ error: 'bad json' }, 400);
  }
  const chain = modelChain(body.model, env.GEMINI_MODELS);
  let res;
  for (const model of chain) {
    try {
      res = await fetchImpl(`${GEMINI_BASE}chat/completions`, {
        method: 'POST',
        headers: { ...headers, 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...body, model }),
      });
    } catch {
      res = null;
      continue;
    }
    if (res.status !== 503 && res.status !== 429) break;
  }
  if (!res) return json({ error: 'llm unreachable' }, 502);
  const out = new Response(res.body, res);
  out.headers.delete('Set-Cookie');
  return out;
}

function json(b, status = 200) {
  return new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });
}

// Gemini는 일부 지역(예: 홍콩)에서 오는 요청을 막는다 ("User location is not supported").
// Worker는 사용자와 가까운 곳(한국 사용자는 홍콩일 때가 있다)에서 돌므로, Gemini 호출만
// 미국 서부에 둔 Durable Object에서 보낸다. 다른 길(카카오 · 구글 · 같이 가기)은 그대로 가까운 곳에서.
export class GeminiEgress {
  async fetch(request) {
    if (!request.url.startsWith(GEMINI_BASE)) return json({ error: 'not allowed' }, 404);
    return fetch(request);
  }
}

/** Durable Object를 거쳐 보내는 fetch. 바인딩이 없으면 그냥 fetch. */
export function egressFetch(env) {
  if (!env.GEMINI_EGRESS) return fetch;
  const stub = env.GEMINI_EGRESS.get(env.GEMINI_EGRESS.idFromName('us-west'), { locationHint: 'wnam' });
  return (url, init) => stub.fetch(new Request(url, init));
}
