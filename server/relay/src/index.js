// 어디였지 중계 서버 (Cloudflare Worker).
// 앱은 카카오 · 구글 키를 갖지 않고 이 서버에 묻는다. 서버가 키를 붙여 대신 묻고 결과만 돌려준다.
// - 정해 둔 길만 통과시킨다 (남이 아무 요청이나 대신 보내는 데 쓰지 못하게).
// - 앱이 보내는 X-Eodi-App 값이 맞아야 한다 (APK 안에 있는 값이라 막는 힘은 약하다 → 진짜 한도는 각 API 콘솔의 하루 한도).
// - 구글 사진은 Cloudflare 캐시에 30일 둔다 → 같은 사진은 구글에 다시 묻지 않는다 (하루 한도 절약).
// - /llm/ 은 개발 PC의 Qwen(llama-server)으로 Cloudflare 터널을 거쳐 넘긴다 (터널 주소는 LLM_ORIGIN, 바뀌면 scripts/llm/expose-llm.ps1이 고친다).
// 비밀값(wrangler secret): KAKAO_REST_API_KEY, GOOGLE_PLACES_API_KEY, APP_TOKEN, LLM_ORIGIN, LLM_ORIGIN_KEY

import { convoy } from './convoy.js';

export { ConvoyHub } from './convoy.js';

const ROUTES = [
  // 카카오 로컬: 장소 이름 (카테고리 · 키워드 · 좌표→주소)
  { prefix: '/kakao/', upstream: 'https://dapi.kakao.com/', allow: /^v2\/local\/(search\/category|search\/keyword|geo\/coord2address)\.json$/, auth: 'kakao' },
  // 카카오모빌리티 길찾기: 코스 곳 사이 도로 경로
  { prefix: '/kakaonavi/', upstream: 'https://apis-navi.kakaomobility.com/', allow: /^v1\/(directions|waypoints\/directions)$/, auth: 'kakao' },
  // 구글 Places (New): 장소 찾기 · 사진
  { prefix: '/google/', upstream: 'https://places.googleapis.com/', allow: /^v1\/(places:searchText|places\/[^/]+\/photos\/[^/]+\/media)$/, auth: 'google' },
];

const PHOTO_CACHE_SECONDS = 30 * 24 * 3600;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === '/health') return json({ ok: true });
    const bearer = (request.headers.get('Authorization') || '').replace(/^Bearer\s+/i, '');
    if (env.APP_TOKEN && request.headers.get('X-Eodi-App') !== env.APP_TOKEN && url.searchParams.get('app') !== env.APP_TOKEN && bearer !== env.APP_TOKEN) {
      return json({ error: 'forbidden' }, 403);
    }
    if (url.pathname.startsWith('/llm/')) return llm(request, env, url);
    // 같이 가기: 차들 위치 나누기 (src/convoy.js)
    if (url.pathname === '/convoy' || url.pathname.startsWith('/convoy/')) return convoy(request, env);
    const route = ROUTES.find((r) => url.pathname.startsWith(r.prefix));
    if (!route) return json({ error: 'not found' }, 404);
    const path = url.pathname.slice(route.prefix.length);
    if (!route.allow.test(path)) return json({ error: 'not allowed' }, 404);

    const query = new URLSearchParams(url.searchParams);
    query.delete('app');
    const headers = new Headers();
    if (route.auth === 'kakao') {
      headers.set('Authorization', `KakaoAK ${env.KAKAO_REST_API_KEY}`);
    } else {
      headers.set('X-Goog-Api-Key', env.GOOGLE_PLACES_API_KEY);
      const mask = request.headers.get('X-Goog-FieldMask');
      if (mask) headers.set('X-Goog-FieldMask', mask);
    }
    const isPhoto = route.auth === 'google' && path.endsWith('/media');
    if (isPhoto) query.set('key', env.GOOGLE_PLACES_API_KEY);
    const target = `${route.upstream}${path}${query.toString() ? `?${query}` : ''}`;

    if (request.method === 'POST') {
      headers.set('Content-Type', request.headers.get('Content-Type') || 'application/json');
      const res = await fetch(target, { method: 'POST', headers, body: await request.text() });
      return pass(res);
    }
    if (request.method !== 'GET') return json({ error: 'method' }, 405);

    if (!isPhoto) return pass(await fetch(target, { headers }));

    // 사진: 캐시 키는 키를 뺀 우리 주소 (같은 사진 · 같은 크기면 같은 키)
    const cacheKey = new Request(`${url.origin}${url.pathname}?${new URLSearchParams([...query].filter(([k]) => k !== 'key'))}`);
    const cache = caches.default;
    const hit = await cache.match(cacheKey);
    if (hit) return hit;
    const res = await fetch(target, { headers, redirect: 'follow' });
    if (!res.ok) return pass(res);
    const out = new Response(res.body, res);
    out.headers.set('Cache-Control', `public, max-age=${PHOTO_CACHE_SECONDS}`);
    out.headers.delete('Set-Cookie');
    ctx.waitUntil(cache.put(cacheKey, out.clone()));
    return out;
  },
};

// Qwen (OpenAI 호환): 대화 · 모델 목록만. 응답(스트리밍 포함)은 그대로 흘려보낸다.
async function llm(request, env, url) {
  const path = url.pathname.slice('/llm/'.length);
  if (!/^v1\/(chat\/completions|models)$/.test(path)) return json({ error: 'not allowed' }, 404);
  if (!env.LLM_ORIGIN) return json({ error: 'llm offline' }, 503);
  const headers = new Headers({ 'Content-Type': request.headers.get('Content-Type') || 'application/json' });
  if (env.LLM_ORIGIN_KEY) headers.set('Authorization', `Bearer ${env.LLM_ORIGIN_KEY}`);
  const init = { method: request.method, headers };
  if (request.method === 'POST') init.body = request.body;
  let res;
  try {
    res = await fetch(`${env.LLM_ORIGIN.trim().replace(/\/$/, '')}/${path}`, init);
  } catch (e) {
    return json({ error: 'llm unreachable' }, 502);
  }
  return pass(res);
}

function pass(res) {
  const out = new Response(res.body, res);
  out.headers.delete('Set-Cookie');
  return out;
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
