import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ConvoyStore, GROUP_TTL_MS, STALE_MS } from '../src/convoy.js';

const req = (method, path, body) =>
  new Request(`https://x${path}`, { method, headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });

test('만들기 · 코드로 참여 · 위치 · 상태', async () => {
  let t = 1_000;
  const s = new ConvoyStore(() => t);
  const a = await s.handle(req('POST', '/convoy', { name: '나', dest: { name: '카페', lat: 37.5, lng: 127 } }));
  assert.equal(a.status, 200);
  assert.match(a.body.code, /^\d{6}$/);
  const b = await s.handle(req('POST', '/convoy/join', { code: a.body.code, name: '민수' }));
  assert.equal(b.status, 200);
  assert.equal(b.body.dest.name, '카페');
  const p = await s.handle(req('POST', `/convoy/${a.body.id}/pos`, { member_id: b.body.member_id, lat: 37.6, lng: 127.1, speed: 12 }));
  assert.equal(p.status, 200);
  const g = await s.handle(req('GET', `/convoy/${a.body.id}?member=${a.body.member_id}`));
  assert.equal(g.body.members.length, 1); // 위치를 보낸 차만
  assert.equal(g.body.members[0].name, '민수');
  t += STALE_MS + 1;
  assert.equal((await s.handle(req('GET', `/convoy/${a.body.id}?member=${a.body.member_id}`))).body.members[0].stale, true);
});

test('남은 못 본다 · 잘못된 값 · 12시간 뒤 만료', async () => {
  let t = 0;
  const s = new ConvoyStore(() => t);
  const a = (await s.handle(req('POST', '/convoy', { name: '나' }))).body;
  assert.equal((await s.handle(req('GET', `/convoy/${a.id}?member=nobody`))).status, 403);
  assert.equal((await s.handle(req('POST', `/convoy/${a.id}/pos`, { member_id: a.member_id, lat: 999, lng: 0 }))).status, 400);
  assert.equal((await s.handle(req('POST', '/convoy/join', { code: '000000' }))).status, 404);
  t += GROUP_TTL_MS + 1;
  assert.equal((await s.handle(req('POST', '/convoy/join', { code: a.code }))).status, 404);
});

test('모두 나가면 모임이 사라진다', async () => {
  const s = new ConvoyStore();
  const a = (await s.handle(req('POST', '/convoy', {}))).body;
  await s.handle(req('POST', `/convoy/${a.id}/leave`, { member_id: a.member_id }));
  assert.equal((await s.handle(req('POST', '/convoy/join', { code: a.code }))).status, 404);
});
