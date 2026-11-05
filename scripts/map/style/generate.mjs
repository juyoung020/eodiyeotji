// 어디였지 오프라인 지도 스타일 · 글꼴 · 아이콘 생성기
//
//   cd scripts/map/style && npm ci
//   node generate.mjs           # style.json + 글꼴(.pbf) + 아이콘(sprite) + 라이선스를 app/src/main/assets/map/ 에 다시 만든다 (인터넷 필요)
//   node generate.mjs --check   # 인터넷 없이 확인만: scripts/map/out/korea-demo.pmtiles 의 글자가 앱에 넣은 글꼴 범위로 다 그려지는가
//
// 스타일은 Protomaps 기본 지도(@protomaps/basemaps, light, 라벨 한국어)를 그대로 쓰고, 앱과의 약속에 맞춰 아래만 바꾼다.
//   - 지도 소스 이름 "protomaps", url 은 자리표시자 "{{PMTILES_URL}}" (앱이 실행 중에 pmtiles://file://<절대 경로> 로 바꾼다)
//   - glyphs / sprite 는 APK 안 assets (asset://map/...) — 네트워크를 쓰지 않는다
//   - 글꼴 이름에서 공백을 뺀다 (URL 인코딩 문제를 아예 없앤다). 데바나가리 글꼴은 한국 지도에 필요 없어 기본 글꼴로 돌린다
// 한글 · 한자는 앱이 MapLibre localIdeographFontFamily(기기 글꼴)로 그린다. 그래서 글꼴은 라틴 등 실제로 쓰이는 범위만 넣고,
// 글자가 없는(수십 바이트짜리) 범위 파일은 전부 넣어 둔다 — 범위 파일이 없으면 MapLibre Native 는 그 타일의 라벨을 끝내 못 그리기 때문이다.

import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";
import { layers, namedFlavor } from "@protomaps/basemaps";
import { validateStyleMin } from "@maplibre/maplibre-gl-style-spec";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "../../..");
const ASSETS = path.join(REPO, "app/src/main/assets/map");
const PMTILES = path.resolve(HERE, "../out/korea-demo.pmtiles");

// 받을 곳은 커밋으로 고정한다 (같은 입력 → 같은 결과)
const BASEMAPS_ASSETS_SHA = "028c18f713baecad011301ff7a69acc39bcc2ae7"; // protomaps/basemaps-assets
const BASEMAPS_SHA = "42ffaaa4a85a41bfcb23e43cc0f5b492a5eca123"; // protomaps/basemaps (LICENSE.md)
const TANGRAMS_ICONS_SHA = "92510779634f4a006c61ea70e50cb8c52c765a81"; // tangrams/icons (아이콘 원본 LICENSE.md)
const RAW = (repo, sha, p) => `https://raw.githubusercontent.com/${repo}/${sha}/${p.split("/").map(encodeURIComponent).join("/")}`;

// 앱과의 약속 (scripts/map/README.md "스타일 약속")
const SOURCE = "protomaps";
const PMTILES_PLACEHOLDER = "{{PMTILES_URL}}";
const GLYPHS = "asset://map/fonts/{fontstack}/{range}.pbf";
const SPRITE = "asset://map/sprites/light";
const FLAVOR = "light";
const LANG = "ko";
const ATTRIBUTION =
  '<a href="https://github.com/protomaps/basemaps">Protomaps</a> © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

// 원본 글꼴 이름 → 앱 안 글꼴 폴더 이름
const FONT_RENAME = {
  "Noto Sans Regular": "NotoSans-Regular",
  "Noto Sans Medium": "NotoSans-Medium",
  "Noto Sans Italic": "NotoSans-Italic",
  "Noto Sans Devanagari Regular v1": "NotoSans-Regular",
};
const ALWAYS_RANGES = [0, 256, 8192]; // 기본 라틴 · 라틴 확장 · 일반 문장부호(· – ’ 등)
const TINY_BYTES = 8192; // 이보다 작은 범위 파일(글자가 거의 없음)은 전부 넣는다

const CHECK_ONLY = process.argv.includes("--check");

// ---------------------------------------------------------------- 스타일
function buildStyle() {
  let json = JSON.stringify(layers(SOURCE, namedFlavor(FLAVOR), { lang: LANG }));
  for (const [from, to] of Object.entries(FONT_RENAME)) json = json.split(JSON.stringify(from)).join(JSON.stringify(to));
  const styleLayers = JSON.parse(json);
  return {
    version: 8,
    name: "어디였지 라이트 (Protomaps light, 한국어)",
    metadata: {
      "eodiyeotji:generator": "scripts/map/style/generate.mjs",
      "eodiyeotji:basemaps": `@protomaps/basemaps 5.7.2 flavor=${FLAVOR} lang=${LANG}`,
      "eodiyeotji:assets": `protomaps/basemaps-assets@${BASEMAPS_ASSETS_SHA}`,
    },
    sources: { [SOURCE]: { type: "vector", url: PMTILES_PLACEHOLDER, attribution: ATTRIBUTION } },
    glyphs: GLYPHS,
    sprite: SPRITE,
    layers: styleLayers,
  };
}

// 스타일 안에서 ["get", "키"] 로 읽는 속성 이름 (text-field 안) · 쓰는 글꼴 이름 (text-font 안)
function collect(node, pred, out) {
  if (Array.isArray(node)) {
    if (pred(node)) out.add(node[1]);
    for (const x of node) collect(x, pred, out);
  } else if (node && typeof node === "object") {
    for (const v of Object.values(node)) collect(v, pred, out);
  }
  return out;
}
function textKeys(style) {
  const keys = new Set();
  for (const l of style.layers) {
    if (l.layout?.["text-field"]) collect(l.layout["text-field"], (a) => a[0] === "get" && typeof a[1] === "string", keys);
  }
  return keys;
}
function fontStacks(style) {
  const stacks = new Set();
  const add = (v) => {
    if (typeof v === "string" && Object.values(FONT_RENAME).includes(v)) stacks.add(v);
    else if (Array.isArray(v)) v.forEach(add);
    else if (v && typeof v === "object") Object.values(v).forEach(add);
  };
  for (const l of style.layers) add(l.layout);
  return [...stacks].sort();
}

// ---------------------------------------------------------------- pmtiles / MVT 읽기 (의존성 없이 최소한만)
function varint(b, p) {
  let v = 0, m = 1, c;
  do { c = b[p++]; v += (c & 0x7f) * m; m *= 128; } while (c & 0x80);
  return [v, p];
}
function decompress(buf, kind) {
  if (kind === 1) return buf; // none
  if (kind === 2) return zlib.gunzipSync(buf); // gzip
  throw new Error(`지원하지 않는 압축 방식 ${kind}`);
}
function* pmtilesTiles(file) {
  const b = fs.readFileSync(file);
  if (b.toString("latin1", 0, 7) !== "PMTiles" || b[7] !== 3) throw new Error(`PMTiles v3 파일이 아닙니다: ${file}`);
  const u64 = (o) => Number(b.readBigUInt64LE(o));
  const rootOff = u64(8), rootLen = u64(16), leafOff = u64(40), dataOff = u64(56);
  const internal = b[97], tileComp = b[98];
  const seen = new Set();
  const stack = [[rootOff, rootLen]];
  while (stack.length) {
    const [off, len] = stack.pop();
    const d = decompress(b.subarray(off, off + len), internal);
    let p = 0, n;
    [n, p] = varint(d, p);
    const e = Array.from({ length: n }, () => ({}));
    for (const x of e) [, p] = varint(d, p); // tile id 차이 (여기선 안 씀)
    for (const x of e) [x.run, p] = varint(d, p);
    for (const x of e) [x.len, p] = varint(d, p);
    e.forEach((x, i) => {
      let v;
      [v, p] = varint(d, p);
      x.off = v === 0 && i > 0 ? e[i - 1].off + e[i - 1].len : v - 1;
    });
    for (const x of e) {
      if (x.run === 0) stack.push([leafOff + x.off, x.len]);
      else if (!seen.has(x.off)) { seen.add(x.off); yield decompress(b.subarray(dataOff + x.off, dataOff + x.off + x.len), tileComp); }
    }
  }
}
function* pbfFields(b, start, end) {
  let p = start;
  while (p < end) {
    let k;
    [k, p] = varint(b, p);
    const field = Math.floor(k / 8), wire = k & 7;
    if (wire === 0) { let v; [v, p] = varint(b, p); yield [field, wire, v]; }
    else if (wire === 2) { let n; [n, p] = varint(b, p); yield [field, wire, p, p + n]; p += n; }
    else if (wire === 5) p += 4;
    else if (wire === 1) p += 8;
    else throw new Error(`알 수 없는 protobuf wire type ${wire}`);
  }
}
// 타일 안에서 style 이 글자로 그리는 속성 값의 UTF-16 코드 단위 → 범위 시작값(256 단위)
// 결과: "타일레이어\0속성" → Map(범위 시작값 → 예시 글자)
function scanRanges(file, keysWanted) {
  const byKey = new Map();
  let tiles = 0;
  for (const t of pmtilesTiles(file)) {
    tiles++;
    for (const [f, w, s, e] of pbfFields(t, 0, t.length)) {
      if (f !== 3 || w !== 2) continue; // layer
      const keys = [], values = [], tagLists = [];
      let layerName = "";
      for (const [lf, lw, ls, le] of pbfFields(t, s, e)) {
        if (lf === 1 && lw === 2) layerName = t.toString("utf8", ls, le);
        else if (lf === 3 && lw === 2) keys.push(t.toString("utf8", ls, le));
        else if (lf === 4 && lw === 2) {
          let str = null;
          for (const [vf, vw, vs, ve] of pbfFields(t, ls, le)) if (vf === 1 && vw === 2) str = t.toString("utf8", vs, ve);
          values.push(str);
        } else if (lf === 2 && lw === 2) {
          for (const [ff, fw, fs_, fe] of pbfFields(t, ls, le)) if (ff === 2 && fw === 2) tagLists.push([fs_, fe]);
        }
      }
      for (const [ts, te] of tagLists) {
        let p = ts;
        while (p < te) {
          let k, v;
          [k, p] = varint(t, p);
          [v, p] = varint(t, p);
          const str = values[v];
          if (str && keysWanted.has(keys[k])) {
            const id = `${layerName}\0${keys[k]}`;
            if (!byKey.has(id)) byKey.set(id, new Map());
            const ranges = byKey.get(id);
            for (let i = 0; i < str.length; i++) {
              const r = Math.floor(str.charCodeAt(i) / 256) * 256;
              if (!ranges.has(r)) ranges.set(r, str);
            }
          }
        }
      }
    }
  }
  return { byKey, tiles };
}
// 글꼴별로 필요한 범위: 그 글꼴을 쓰는 스타일 레이어가 읽는 (타일레이어, 속성)의 글자만 센다
function neededByStack(style, byKey) {
  const out = new Map();
  for (const l of style.layers) {
    if (!l.layout?.["text-field"]) continue;
    const keys = collect(l.layout["text-field"], (a) => a[0] === "get" && typeof a[1] === "string", new Set());
    for (const stack of fontStacks({ layers: [l] })) {
      if (!out.has(stack)) out.set(stack, new Map(ALWAYS_RANGES.map((r) => [r, "(기본)"])));
      const need = out.get(stack);
      for (const k of keys) for (const [r, ex] of byKey.get(`${l["source-layer"]}\0${k}`) ?? []) if (!need.has(r)) need.set(r, ex);
    }
  }
  return out;
}
const rangeName = (start) => `${start}-${start + 255}.pbf`;

// ---------------------------------------------------------------- 받기 · 쓰기
async function get(url) {
  for (let attempt = 1; ; attempt++) {
    const res = await fetch(url);
    if (res.ok) return Buffer.from(await res.arrayBuffer());
    if (attempt >= 3) throw new Error(`${res.status} ${url}`);
    await new Promise((r) => setTimeout(r, 1000 * attempt));
  }
}
async function pool(items, n, fn) {
  let i = 0;
  await Promise.all(Array.from({ length: n }, async () => { while (i < items.length) await fn(items[i++]); }));
}
function dirSize(d) {
  let total = 0, files = 0;
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) { const s = dirSize(p); total += s.total; files += s.files; }
    else { total += fs.statSync(p).size; files++; }
  }
  return { total, files };
}

// ---------------------------------------------------------------- 실행
const style = buildStyle();
const errors = validateStyleMin(style);
if (errors.length) {
  for (const e of errors) console.error("스타일 오류:", e.message);
  process.exit(1);
}
const keys = textKeys(style);
const stacks = fontStacks(style);
console.log(`스타일: 레이어 ${style.layers.length}개, 글꼴 ${stacks.join(", ")}`);
console.log(`라벨에 쓰는 속성: ${[...keys].sort().join(", ")}`);

let byKey = new Map();
if (fs.existsSync(PMTILES)) {
  const scan = scanRanges(PMTILES, keys);
  byKey = scan.byKey;
  console.log(`지도 파일 글자 검사: 타일 ${scan.tiles}개 (${PMTILES})`);
} else {
  console.warn(`지도 파일이 없어 기본 범위만 넣습니다: ${PMTILES}`);
}
const needed = neededByStack(style, byKey); // 글꼴 → Map(범위 시작값 → 예시 글자)
for (const s of stacks) {
  const rs = [...(needed.get(s)?.keys() ?? [])].sort((a, b) => a - b);
  console.log(`  ${s}: 글자가 쓰는 범위 ${rs.length}개`);
}

if (CHECK_ONLY) {
  const missing = [];
  for (const [s, need] of needed) for (const [r, ex] of need) {
    if (!fs.existsSync(path.join(ASSETS, "fonts", s, rangeName(r)))) missing.push(`${s}/${rangeName(r)}  예: ${JSON.stringify(ex).slice(0, 40)}`);
  }
  if (missing.length) {
    console.error(`앱에 없는 글꼴 범위 ${missing.length}개 → node generate.mjs 로 다시 만드세요:\n  ${missing.join("\n  ")}`);
    process.exit(1);
  }
  console.log("확인 완료: 지도 파일의 라벨 글자가 모두 앱 안 글꼴 범위 파일로 그려집니다.");
  process.exit(0);
}

// 원본 파일 목록(크기 포함)
const treeRes = await fetch(`https://api.github.com/repos/protomaps/basemaps-assets/git/trees/${BASEMAPS_ASSETS_SHA}?recursive=1`, {
  headers: { "User-Agent": "eodiyeotji-map-style" },
});
if (!treeRes.ok) throw new Error(`basemaps-assets 목록을 못 받았습니다: ${treeRes.status}`);
const tree = (await treeRes.json()).tree.filter((e) => e.type === "blob");

fs.rmSync(path.join(ASSETS, "fonts"), { recursive: true, force: true });
fs.rmSync(path.join(ASSETS, "sprites"), { recursive: true, force: true });
fs.mkdirSync(ASSETS, { recursive: true });

const jobs = [];
for (const [orig, stack] of Object.entries(FONT_RENAME)) {
  if (!stacks.includes(stack) || orig.includes("Devanagari")) continue;
  for (const e of tree) {
    const m = e.path.match(/^fonts\/([^/]+)\/(\d+)-(\d+)\.pbf$/);
    if (!m || m[1] !== orig) continue;
    const start = Number(m[2]);
    if (e.size <= TINY_BYTES || needed.get(stack)?.has(start)) jobs.push([e.path, path.join(ASSETS, "fonts", stack, rangeName(start))]);
  }
}
for (const f of ["light.json", "light.png", "light@2x.json", "light@2x.png"]) jobs.push([`sprites/v4/${f}`, path.join(ASSETS, "sprites", f)]);
jobs.push(["fonts/OFL.txt", path.join(ASSETS, "fonts", "OFL.txt")]);

await pool(jobs, 16, async ([src, dst]) => {
  fs.mkdirSync(path.dirname(dst), { recursive: true });
  fs.writeFileSync(dst, await get(RAW("protomaps/basemaps-assets", BASEMAPS_ASSETS_SHA, src)));
});
fs.writeFileSync(path.join(ASSETS, "sprites", "LICENSE-tangrams-icons.md"), await get(RAW("tangrams/icons", TANGRAMS_ICONS_SHA, "LICENSE.md")));
fs.writeFileSync(path.join(ASSETS, "LICENSE-protomaps-basemaps.md"), await get(RAW("protomaps/basemaps", BASEMAPS_SHA, "LICENSE.md")));
fs.writeFileSync(
  path.join(ASSETS, "NOTICE.txt"),
  [
    "어디였지 오프라인 지도 — 출처와 라이선스",
    "",
    "지도 데이터: © OpenStreetMap contributors, Open Database License (ODbL) 1.0 — https://www.openstreetmap.org/copyright",
    "지도 타일 · 스타일: Protomaps basemap (https://github.com/protomaps/basemaps) — 코드 BSD-3-Clause, 디자인 CC0 (LICENSE-protomaps-basemaps.md)",
    "글꼴: Noto Sans © The Noto Project Authors — SIL Open Font License 1.1 (fonts/OFL.txt)",
    "아이콘: protomaps/basemaps-assets 스프라이트, Mapzen tangrams/icons 파생 — MIT (sprites/LICENSE-tangrams-icons.md)",
    "",
    "앱 화면에 표시할 문구: © OpenStreetMap contributors · Protomaps",
    "",
  ].join("\n"),
);
fs.writeFileSync(path.join(ASSETS, "style.json"), JSON.stringify(style, null, 2) + "\n");

const { total, files } = dirSize(ASSETS);
const fontSize = dirSize(path.join(ASSETS, "fonts"));
for (const s of stacks) {
  const d = dirSize(path.join(ASSETS, "fonts", s));
  console.log(`  ${s}: 범위 파일 ${d.files}개, ${(d.total / 1024).toFixed(0)} KB`);
}
console.log(`완료: ${ASSETS}  파일 ${files}개, ${(total / 1024).toFixed(0)} KB (글꼴 ${(fontSize.total / 1024).toFixed(0)} KB)`);
