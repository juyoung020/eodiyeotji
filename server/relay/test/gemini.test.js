import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cleanBody, modelChain, gemini, GEMINI_MODELS, SKIP_SIGNATURE } from '../src/gemini.js';

const post = (body) => new Request('https://x/llm/v1/chat/completions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

test('llama.cpp 전용 값은 빼고 표준 값만 넘긴다', () => {
  const b = cleanBody({ model: 'qwen3.5-9b', messages: [], tools: [], tool_choice: 'auto', cache_prompt: true, chat_template_kwargs: { enable_thinking: false }, temperature: 0, max_tokens: 64 });
  assert.deepEqual(b, { model: 'qwen3.5-9b', messages: [], temperature: 0, max_tokens: 64 });
});

test('모델 순서: Gemini 요청 모델 먼저, 옛 qwen 이름은 기본 목록', () => {
  assert.deepEqual(modelChain('qwen3.5-9b'), GEMINI_MODELS);
  assert.deepEqual(modelChain('gemini-3.5-flash'), ['gemini-3.5-flash', ...GEMINI_MODELS]);
  assert.deepEqual(modelChain('gemini-flash-lite-latest', 'a, b'), ['gemini-flash-lite-latest', 'a', 'b']);
});

test('503 · 429면 다음 모델, 키는 Bearer로 붙인다', async () => {
  const seen = [];
  const fake = async (url, init) => {
    seen.push({ url, auth: init.headers.Authorization, body: JSON.parse(init.body) });
    return new Response('{}', { status: seen.length < 3 ? [503, 429][seen.length - 1] : 200 });
  };
  const res = await gemini(post({ model: 'gemini-flash-lite-latest', messages: [{ role: 'user', content: 'hi' }], cache_prompt: true }), { GEMINI_API_KEY: 'k' }, 'v1/chat/completions', fake);
  assert.equal(res.status, 200);
  assert.deepEqual(seen.map((s) => s.body.model), GEMINI_MODELS);
  assert.ok(seen[0].url.endsWith('/v1beta/openai/chat/completions'));
  assert.equal(seen[0].auth, 'Bearer k');
  assert.equal(seen[0].body.cache_prompt, undefined);
});

test('400 같은 오류는 다시 묻지 않고 그대로 돌려준다', async () => {
  let n = 0;
  const res = await gemini(post({ messages: [] }), { GEMINI_API_KEY: 'k' }, 'v1/chat/completions', async () => (n++, new Response('{}', { status: 400 })));
  assert.equal(res.status, 400);
  assert.equal(n, 1);
});

test('모델 목록은 고정 목록', async () => {
  const res = await gemini(new Request('https://x/llm/v1/models'), { GEMINI_API_KEY: 'k' }, 'v1/models', async () => assert.fail());
  assert.deepEqual((await res.json()).data.map((m) => m.id), GEMINI_MODELS);
});

test('앞선 도구 호출에 thought_signature가 없으면 건너뛰기 값을 붙인다', () => {
  const keep = { id: 'b', type: 'function', function: { name: 'g', arguments: '{}' }, extra_content: { google: { thought_signature: 'real' } } };
  const b = cleanBody({ messages: [{ role: 'user', content: 'x' }, { role: 'assistant', content: '', tool_calls: [{ id: 'a', type: 'function', function: { name: 'f', arguments: '{}' } }, keep] }] });
  assert.equal(b.messages[1].tool_calls[0].extra_content.google.thought_signature, SKIP_SIGNATURE);
  assert.equal(b.messages[1].tool_calls[1].extra_content.google.thought_signature, 'real');
  assert.deepEqual(b.messages[0], { role: 'user', content: 'x' });
});
