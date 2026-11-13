// 같이 가기 (여러 차가 같은 목적지로 함께 갈 때 서로 위치 나누기). 메모리에만 둔다 (디스크 · 로그에 남기지 않는다).
// - 모임: 6자리 숫자 코드(차 화면 키보드로 넣기 쉽게) + id. 12시간 지나면 사라진다.
// - 차: 마지막 위치 하나만 둔다. 60초 넘게 소식이 없으면 stale, 10분이면 뺀다.
// 계산(앞 · 뒤, 도착 차이)은 앱 안 Rust(core-rs/src/convoy.rs)가 한다. 서버는 나르기만.

export const GROUP_TTL_MS = 12 * 3600 * 1000;
export const STALE_MS = 60 * 1000;
export const DROP_MS = 10 * 60 * 1000;
export const MAX_MEMBERS = 8;
export const MAX_GROUPS = 2000;

const ALPHA = 'abcdefghijkmnpqrstuvwxyz23456789';
const rid = (n = 12) => {
  const a = new Uint8Array(n);
  crypto.getRandomValues(a);
  return [...a].map((b) => ALPHA[b % 32]).join('');
};
const num = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : null);
const cleanName = (s, fallback) => (typeof s === 'string' && s.trim() ? s.trim().slice(0, 16) : fallback);
const cleanDest = (d) => {
  if (!d || num(d.lat) == null || num(d.lng) == null) return null;
  if (Math.abs(d.lat) > 90 || Math.abs(d.lng) > 180) return null;
  return { name: cleanName(d.name, '목적지'), lat: d.lat, lng: d.lng };
};

export class ConvoyStore {
  constructor(now = () => Date.now()) {
    this.now = now;
    this.groups = new Map(); // id -> group
    this.codes = new Map(); // code -> id
  }

  sweep() {
    const t = this.now();
    for (const [id, g] of this.groups) {
      if (t > g.expires_at) {
        this.groups.delete(id);
        this.codes.delete(g.code);
        continue;
      }
      for (const [mid, m] of g.members) if (t - m.ts > DROP_MS) g.members.delete(mid);
    }
  }

  newCode() {
    for (let i = 0; i < 50; i++) {
      const a = new Uint32Array(1);
      crypto.getRandomValues(a);
      const c = String(100000 + (a[0] % 900000));
      if (!this.codes.has(c)) return c;
    }
    throw new Error('no code');
  }

  addMember(g, name) {
    const id = rid();
    g.members.set(id, { id, name: cleanName(name, `차 ${g.members.size + 1}`), lat: null, lng: null, heading: 0, speed: 0, eta_s: -1, ts: this.now() });
    return id;
  }

  create({ name, dest } = {}) {
    this.sweep();
    if (this.groups.size >= MAX_GROUPS) return { status: 503, body: { error: 'busy' } };
    const t = this.now();
    const g = { id: rid(16), code: this.newCode(), dest: cleanDest(dest), members: new Map(), expires_at: t + GROUP_TTL_MS };
    this.groups.set(g.id, g);
    this.codes.set(g.code, g.id);
    const member_id = this.addMember(g, name);
    return { status: 200, body: { id: g.id, code: g.code, member_id, dest: g.dest, expires_at: g.expires_at } };
  }

  join({ code, name } = {}) {
    this.sweep();
    const g = this.groups.get(this.codes.get(String(code || '').trim()));
    if (!g) return { status: 404, body: { error: 'no such code' } };
    if (g.members.size >= MAX_MEMBERS) return { status: 409, body: { error: 'full' } };
    const member_id = this.addMember(g, name);
    return { status: 200, body: { id: g.id, code: g.code, member_id, dest: g.dest, expires_at: g.expires_at } };
  }

  member(id, mid) {
    const g = this.groups.get(id);
    if (!g || this.now() > g.expires_at) return [null, null];
    return [g, g.members.get(mid) || null];
  }

  position(id, b = {}) {
    const [g, m] = this.member(id, b.member_id);
    if (!g) return { status: 404, body: { error: 'gone' } };
    if (!m) return { status: 403, body: { error: 'not a member' } };
    const lat = num(b.lat);
    const lng = num(b.lng);
    if (lat == null || lng == null || Math.abs(lat) > 90 || Math.abs(lng) > 180) return { status: 400, body: { error: 'bad position' } };
    Object.assign(m, { lat, lng, heading: num(b.heading) ?? 0, speed: num(b.speed) ?? 0, eta_s: num(b.eta_s) ?? -1, ts: this.now() });
    if (typeof b.name === 'string') m.name = cleanName(b.name, m.name);
    return { status: 200, body: this.state(g) };
  }

  setDest(id, b = {}) {
    const [g, m] = this.member(id, b.member_id);
    if (!g) return { status: 404, body: { error: 'gone' } };
    if (!m) return { status: 403, body: { error: 'not a member' } };
    g.dest = cleanDest(b.dest);
    return { status: 200, body: this.state(g) };
  }

  leave(id, b = {}) {
    const g = this.groups.get(id);
    if (g) g.members.delete(b.member_id);
    if (g && g.members.size === 0) {
      this.groups.delete(id);
      this.codes.delete(g.code);
    }
    return { status: 200, body: { ok: true } };
  }

  get(id, mid) {
    this.sweep();
    const [g, m] = this.member(id, mid);
    if (!g) return { status: 404, body: { error: 'gone' } };
    if (!m) return { status: 403, body: { error: 'not a member' } };
    return { status: 200, body: this.state(g) };
  }

  state(g) {
    const t = this.now();
    const members = [...g.members.values()]
      .filter((m) => m.lat != null)
      .map((m) => ({ id: m.id, name: m.name, lat: m.lat, lng: m.lng, heading: m.heading, speed: m.speed, eta_s: m.eta_s, ts: m.ts, stale: t - m.ts > STALE_MS }));
    return { id: g.id, code: g.code, dest: g.dest, members, expires_at: g.expires_at, now: t };
  }

  // 요청 하나: POST /convoy, POST /convoy/join, GET /convoy/:id?member=, POST /convoy/:id/{pos,dest,leave}
  async handle(request) {
    const url = new URL(request.url);
    const parts = url.pathname.split('/').filter(Boolean);
    let body = {};
    if (request.method === 'POST') {
      try {
        body = await request.json();
      } catch {
        return { status: 400, body: { error: 'bad json' } };
      }
    }
    if (parts.length === 1 && request.method === 'POST') return this.create(body);
    if (parts.length === 2 && parts[1] === 'join' && request.method === 'POST') return this.join(body);
    if (parts.length === 2 && request.method === 'GET') return this.get(parts[1], url.searchParams.get('member'));
    if (parts.length === 3 && request.method === 'POST') {
      if (parts[2] === 'pos') return this.position(parts[1], body);
      if (parts[2] === 'dest') return this.setDest(parts[1], body);
      if (parts[2] === 'leave') return this.leave(parts[1], body);
    }
    return { status: 404, body: { error: 'not found' } };
  }
}

const respond = (r) => new Response(JSON.stringify(r.body), { status: r.status, headers: { 'Content-Type': 'application/json' } });

// Durable Object: 모든 모임을 한 곳(메모리)에 → 어느 Worker 인스턴스로 와도 같은 상태를 본다.
export class ConvoyHub {
  constructor() {
    this.store = new ConvoyStore();
  }
  async fetch(request) {
    return respond(await this.store.handle(request));
  }
}

// Durable Object 연결이 없을 때(wrangler dev 등) 쓰는 이 인스턴스 메모리
const local = new ConvoyStore();

export async function convoy(request, env) {
  if (env.CONVOY) return env.CONVOY.get(env.CONVOY.idFromName('hub')).fetch(request);
  return respond(await local.handle(request));
}
