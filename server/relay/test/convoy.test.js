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
  const p = await s.handle(req('POST', `/convoy/${a.body.id}/pos`, { member_id: b.body.member_id, lat: 37.6, lng: 127.1, speed: 12, ev: true, energy_pct: 12, range_km: 32 }));
  assert.equal(p.status, 200);
  const g = await s.handle(req('GET', `/convoy/${a.body.id}?member=${a.body.member_id}`));
  assert.equal(g.body.members.length, 1); // 위치를 보낸 차만
  assert.equal(g.body.members[0].name, '민수');
  assert.equal(g.body.members[0].range_km, 32);
  assert.equal(g.body.members[0].ev, true);
  const w = await s.handle(req('POST', `/convoy/${a.body.id}/dest`, { member_id: b.body.member_id, waypoint: { name: '충전소', lat: 37.55, lng: 127.05 } }));
  assert.equal(w.body.waypoint.name, '충전소');
  assert.equal(w.body.dest.name, '카페'); // 경유지만 바꿔도 목적지는 그대로
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

test('객체가 아닌 JSON 본문은 500이 아니라 400', async () => {
  const s = new ConvoyStore(() => 1_000);
  for (const raw of ['null', '3', '[]', '"x"']) {
    const r = await s.handle(new Request('https://x/convoy', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: raw }));
    assert.equal(r.status, 400, raw);
  }
});
