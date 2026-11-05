// 어디였지 오프라인 지도 스타일 · 글꼴 · 아이콘 생성기
//
//   cd scripts/map/style && npm ci
//   node generate.mjs                  # 시연용 가벼운 스타일(demo)로 app/src/main/assets/map/ 을 다시 만든다 (인터넷 필요)
//   node generate.mjs --flavor full    # Protomaps light 레이어 전부(71개)로 다시 만든다
//   node generate.mjs --check          # 인터넷 없이 확인만: 앱에 넣은 style.json 이 지도 파일 라벨을 그릴 글꼴 범위를 다 갖췄는가
//   node generate.mjs --flavor full --style-only --out <파일>   # 스타일 JSON 만 다른 곳에 쓴다 (비교 · 측정용, assets 는 안 건드림)
//
// 스타일은 Protomaps 기본 지도(@protomaps/basemaps, light, 라벨 한국어)에서 시작해 앱과의 약속에 맞춰 바꾼다.
//   - 지도 소스 이름 "protomaps", url 은 자리표시자 "{{PMTILES_URL}}" (앱이 실행 중에 pmtiles://file://<절대 경로> 로 바꾼다)
//   - glyphs / sprite 는 APK 안 assets (asset://map/...) — 네트워크를 쓰지 않는다
//   - 글꼴 이름에서 공백을 뺀다 (URL 인코딩 문제를 아예 없앤다). 데바나가리 글꼴은 한국 지도에 필요 없어 기본 글꼴로 돌린다
//   - 한글 라벨은 글자 간격을 HANGUL_LETTER_SPACING 으로 둔다 (원본 0.1em 은 한글에 넓다)
//   - demo: 차 화면에서 60fps 를 내도록 무거운 레이어를 빼고 합친다 (DEMO_* 설정 참고), 라벨은 한 줄
// 한글 · 한자는 앱이 MapLibre localIdeographFontFamily(기기 글꼴)로 그린다. 글꼴 범위 파일이 하나라도 없으면 MapLibre Native 는
// 그 범위 글자가 든 타일의 라벨을 끝내 그리지 못하므로(GlyphManager::processResponse), 지도 파일의 모든 지물에 스타일 식을
// 직접 계산해 실제로 그려질 글자의 범위를 찾아 넣는다. 아랍 문자는 MapLibre 가 표현형(U+FB50–FEFF)으로 바꿔 그리므로 그 범위도 넣는다.
// 글자가 없는(수십 바이트짜리) 범위 파일은 전부 넣어 둔다.

import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";
import { layers, namedFlavor } from "@protomaps/basemaps";
import { validateStyleMin, featureFilter, normalizePropertyExpression, latest } from "@maplibre/maplibre-gl-style-spec";

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
const BASE_FLAVOR = "light";
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
const ARABIC_PRESENTATION_RANGES = [64256, 64512, 64768, 65024]; // U+FB00–FEFF: MapLibre 가 아랍 문자를 바꿔 그리는 표현형
const TINY_BYTES = 8192; // 이보다 작은 범위 파일(글자가 거의 없음)은 전부 넣는다
// 길 따라 놓는 라벨이 세로로 서면 MapLibre 가 문장부호를 세로쓰기 글자로 바꿔 그린다 (maplibre-native src/mln/util/i18n.cpp verticalPunctuation)
const VERTICAL_PUNCT = new Map(
  [..."!︕#＃$＄%％&＆(︵)︶*＊+＋,︐-︲.・/／:︓;︔<︿=＝>﹀?︖@＠[﹇\\＼]﹈^＾_︳`｀{︷|―}︸~～¢￠£￡¥￥¦￤¬￢¯￣–︲—︱‘﹃’﹄“﹁”﹂…︙‧・₩￦、︑。︒〈︿〉﹀《︽》︾「﹁」﹂『﹃』﹄【︻】︼〔︹〕︺〖︗〗︘！︕（︵）︶，︐－︲．・：︓；︔＜︿＞﹀？︖［﹇］﹈＿︳｛︷｜―｝︸｟︵｠︶｡︒｢﹁｣﹂"]
    .reduce((pairs, ch, i, all) => (i % 2 ? pairs : [...pairs, [ch, all[i + 1]]]), []),
);

// 한글 라벨의 글자 간격(em). MapLibre Native(Android)는 기기 글꼴로 그린 한글 한 자의 폭을 글꼴과 상관없이
// 1em(24px 중 24)으로 고정한다(LocalGlyphRasterizer advance=24). Pleos 에뮬레이터 화면에서 재 보니 글자 잉크 폭이
// 약 0.78em 이라 간격 0 이어도 글자 사이가 0.22em 로 넓다(원본 0.1em 을 더하면 0.32em). -0.08 로 0.14em 쯤에 맞춘다.
const HANGUL_LETTER_SPACING = -0.08;
const IS_KOREAN_LABEL = ["any", ["has", "name:ko"], ["==", ["get", "script"], "Hangul"]];

// ---- demo(시연용 가벼운 스타일) 설정 ----------------------------------------------------------------
// 빼는 레이어: 건물, 자잘한 땅 쓰임새, 보행로·서비스 도로, 터널·다리 테두리, 일방통행 화살표, 번지
const DEMO_DROP = new Set([
  "landuse_urban_green", "landuse_hospital", "landuse_industrial", "landuse_school", "landuse_zoo",
  "landuse_pedestrian", "landuse_pier", "roads_taxiway", "water_stream", "buildings", "roads_pier",
  "roads_other", "roads_minor_service", "roads_minor_service_casing", "roads_minor_casing", "roads_link_casing",
  "roads_oneway", "address_label",
]);
// 터널 · 다리 레이어(19개)는 빼고, 일반 도로 레이어가 터널 · 다리 구간도 그리게 합친다 (영동고속도로 터널 구간이 끊기지 않게)
const DEMO_MERGE_TUNNELS_BRIDGES = /^roads_(tunnels|bridges)_/;
// 공원류 녹지: 공원 · 국립공원 · 보호구역 · 골프장 · 묘지만 남긴다 (산림 · 풀밭 다각형은 산마다 커서 무겁다. 넓은 녹지는 landcover 가 낮은 줌에서 보여 준다)
const DEMO_PARK_KINDS = ["national_park", "park", "protected_area", "nature_reserve", "golf_course", "cemetery"];
// 이 줌부터만 그린다
const DEMO_MINZOOM = { roads_minor: 14, roads_labels_minor: 16, water_waterway_label: 14 };
// 남길 장소 아이콘(pois) 종류: 역 · 공항 · 항구 · 해변 · 국립공원 · 산 · 경기장 · 대학
const DEMO_POI_KINDS = ["station", "aerodrome", "ferry_terminal", "beach", "national_park", "peak", "stadium", "university"];
// 한 줄 라벨: name:ko → (한글 이름이면) name → name:en → name
const DEMO_LABEL = [
  "coalesce",
  ["get", "name:ko"],
  ["case", ["==", ["get", "script"], "Hangul"], ["get", "name"], ["coalesce", ["get", "name:en"], ["get", "name"]]],
];

// ---------------------------------------------------------------- 인자
const args = process.argv.slice(2);
const argValue = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : undefined; };
const CHECK_ONLY = args.includes("--check");
const STYLE_ONLY = args.includes("--style-only");
const OUT = argValue("--out");
let FLAVOR = argValue("--flavor");
if (CHECK_ONLY && !FLAVOR) FLAVOR = JSON.parse(fs.readFileSync(path.join(ASSETS, "style.json"), "utf8")).metadata?.["eodiyeotji:flavor"];
FLAVOR ??= "demo";
if (!["demo", "full"].includes(FLAVOR)) throw new Error(`--flavor 는 demo 또는 full: ${FLAVOR}`);

// ---------------------------------------------------------------- 스타일
const isEqual = (a, b) => JSON.stringify(a) === JSON.stringify(b);
// 레거시 필터 ["all", ..., ["!has","is_tunnel"], ...] 에서 터널 · 다리 제외 조건을 지운다
function allowTunnelsBridges(filter) {
  if (!Array.isArray(filter) || filter[0] !== "all") return filter;
  return filter.filter((f) => !isEqual(f, ["!has", "is_tunnel"]) && !isEqual(f, ["!has", "is_bridge"]));
}

function demoLayers(all) {
  const out = [];
  for (const src of all) {
    const l = structuredClone(src);
    if (DEMO_DROP.has(l.id) || DEMO_MERGE_TUNNELS_BRIDGES.test(l.id)) continue;
    if (l.type === "line" && l["source-layer"] === "roads") l.filter = allowTunnelsBridges(l.filter);
    if (l.id === "landuse_park") {
      l.filter = ["in", "kind", ...DEMO_PARK_KINDS];
      l.paint["fill-color"] = "#9cd3b4";
    }
    if (l.id === "pois") l.filter = ["all", ["in", ["get", "kind"], ["literal", DEMO_POI_KINDS]], l.filter[2]];
    if (DEMO_MINZOOM[l.id] !== undefined) l.minzoom = Math.max(l.minzoom ?? 0, DEMO_MINZOOM[l.id]);
    if (l.layout?.["text-field"] && JSON.stringify(l.layout["text-field"]).includes('"name')) l.layout["text-field"] = DEMO_LABEL;
    out.push(l);
  }
  return out;
}

function buildStyle(flavor) {
  let json = JSON.stringify(layers(SOURCE, namedFlavor(BASE_FLAVOR), { lang: LANG }));
  for (const [from, to] of Object.entries(FONT_RENAME)) json = json.split(JSON.stringify(from)).join(JSON.stringify(to));
  let styleLayers = JSON.parse(json);
  if (flavor === "demo") styleLayers = demoLayers(styleLayers);
  for (const l of styleLayers) {
    if (l.type !== "symbol" || !JSON.stringify(l.layout?.["text-field"] ?? "").includes('"name')) continue; // 이름 라벨만 (도로 번호 방패는 그대로)
    const orig = l.layout["text-letter-spacing"] ?? 0;
    if (typeof orig !== "number") continue;
    if (orig === HANGUL_LETTER_SPACING) continue;
    l.layout["text-letter-spacing"] = orig === 0 && HANGUL_LETTER_SPACING === 0 ? 0 : ["case", IS_KOREAN_LABEL, HANGUL_LETTER_SPACING, orig];
  }
  return {
    version: 8,
    name: `어디였지 라이트 (Protomaps light, 한국어, ${flavor})`,
    metadata: {
      "eodiyeotji:generator": "scripts/map/style/generate.mjs",
      "eodiyeotji:flavor": flavor,
      "eodiyeotji:basemaps": `@protomaps/basemaps 5.7.2 flavor=${BASE_FLAVOR} lang=${LANG}`,
      "eodiyeotji:assets": `protomaps/basemaps-assets@${BASEMAPS_ASSETS_SHA}`,
    },
    sources: { [SOURCE]: { type: "vector", url: PMTILES_PLACEHOLDER, attribution: ATTRIBUTION } },
    glyphs: GLYPHS,
    sprite: SPRITE,
    layers: styleLayers,
  };
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
function zoomOfTileId(id) {
  let acc = 0;
  for (let z = 0; z < 32; z++) { const n = 4 ** z; if (id < acc + n) return z; acc += n; }
  throw new Error(`tile id 범위 밖: ${id}`);
}
// 타일 내용(중복 제거)과 그 내용이 쓰이는 줌들
function* pmtilesTiles(file) {
  const b = fs.readFileSync(file);
  if (b.toString("latin1", 0, 7) !== "PMTiles" || b[7] !== 3) throw new Error(`PMTiles v3 파일이 아닙니다: ${file}`);
  const u64 = (o) => Number(b.readBigUInt64LE(o));
  const rootOff = u64(8), rootLen = u64(16), leafOff = u64(40), dataOff = u64(56);
  const internal = b[97], tileComp = b[98], maxZoom = b[101];
  const blobs = new Map(); // offset → { len, zooms }
  const stack = [[rootOff, rootLen]];
  while (stack.length) {
    const [off, len] = stack.pop();
    const d = decompress(b.subarray(off, off + len), internal);
    let p = 0, n;
    [n, p] = varint(d, p);
    const e = Array.from({ length: n }, () => ({}));
    let id = 0;
    for (const x of e) { let v; [v, p] = varint(d, p); id += v; x.id = id; }
    for (const x of e) [x.run, p] = varint(d, p);
    for (const x of e) [x.len, p] = varint(d, p);
    e.forEach((x, i) => {
      let v;
      [v, p] = varint(d, p);
      x.off = v === 0 && i > 0 ? e[i - 1].off + e[i - 1].len : v - 1;
    });
    for (const x of e) {
      if (x.run === 0) { stack.push([leafOff + x.off, x.len]); continue; }
      if (!blobs.has(x.off)) blobs.set(x.off, { len: x.len, zooms: new Set() });
      const zs = blobs.get(x.off).zooms;
      zs.add(zoomOfTileId(x.id));
      zs.add(zoomOfTileId(x.id + x.run - 1));
    }
  }
  for (const [off, { len, zooms }] of blobs) {
    yield { maxZoom, zooms, data: decompress(b.subarray(dataOff + off, dataOff + off + len), tileComp) };
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
    else if (wire === 5) { yield [field, wire, p]; p += 4; }
    else if (wire === 1) { yield [field, wire, p]; p += 8; }
    else throw new Error(`알 수 없는 protobuf wire type ${wire}`);
  }
}
function mvtValue(t, s, e) {
  for (const [f, w, a, b] of pbfFields(t, s, e)) {
    if (f === 1 && w === 2) return t.toString("utf8", a, b);
    if (f === 2 && w === 5) return t.readFloatLE(a);
    if (f === 3 && w === 1) return t.readDoubleLE(a);
    if ((f === 4 || f === 5) && w === 0) return a;
    if (f === 6 && w === 0) return a % 2 ? -(a + 1) / 2 : a / 2;
    if (f === 7 && w === 0) return a !== 0;
  }
  return null;
}
// 타일의 레이어별 지물: { name, features: [{ type, properties }] }
function* mvtLayers(t) {
  for (const [f, w, s, e] of pbfFields(t, 0, t.length)) {
    if (f !== 3 || w !== 2) continue;
    let name = "";
    const keys = [], values = [], feats = [];
    for (const [lf, lw, ls, le] of pbfFields(t, s, e)) {
      if (lf === 1 && lw === 2) name = t.toString("utf8", ls, le);
      else if (lf === 3 && lw === 2) keys.push(t.toString("utf8", ls, le));
      else if (lf === 4 && lw === 2) values.push(mvtValue(t, ls, le));
      else if (lf === 2 && lw === 2) feats.push([ls, le]);
    }
    yield {
      name,
      *features() {
        for (const [fs_, fe] of feats) {
          let type = 0, tags = null;
          for (const [ff, fw, a, b] of pbfFields(t, fs_, fe)) {
            if (ff === 3 && fw === 0) type = a;
            else if (ff === 2 && fw === 2) tags = [a, b];
          }
          const properties = {};
          if (tags) for (let p = tags[0]; p < tags[1];) {
            let k, v;
            [k, p] = varint(t, p);
            [v, p] = varint(t, p);
            properties[keys[k]] = values[v];
          }
          yield { type, properties };
        }
      },
    };
  }
}

// ---------------------------------------------------------------- 실제로 그려질 글자 → 글꼴별 범위
function compileLabelLayers(style) {
  const spec = latest.layout_symbol;
  const compile = (value, key) => normalizePropertyExpression(value, spec[key]); // 상수 · 식 모두 evaluate(globals, feature)
  return style.layers
    .filter((l) => l.type === "symbol" && l.layout?.["text-field"])
    .map((l) => ({
      id: l.id,
      sourceLayer: l["source-layer"],
      minzoom: l.minzoom ?? 0,
      maxzoom: l.maxzoom ?? 24,
      filter: featureFilter(l.filter).filter,
      text: compile(l.layout["text-field"], "text-field"),
      font: compile(l.layout["text-font"] ?? spec["text-font"].default, "text-font"),
      transform: l.layout["text-transform"] ? compile(l.layout["text-transform"], "text-transform") : null,
      alongLine: (l.layout["symbol-placement"] ?? "point") !== "point",
    }));
}
function neededRanges(style, file) {
  const compiled = compileLabelLayers(style);
  const bySource = new Map();
  for (const c of compiled) { if (!bySource.has(c.sourceLayer)) bySource.set(c.sourceLayer, []); bySource.get(c.sourceLayer).push(c); }
  const need = new Map(); // 글꼴 → Map(범위 시작값 → 예시 글자)
  const add = (stack, r, ex) => { if (!need.has(stack)) need.set(stack, new Map()); const m = need.get(stack); if (!m.has(r)) m.set(r, ex); };
  for (const s of fontStacks(style)) for (const r of ALWAYS_RANGES) add(s, r, "(기본)");
  let tiles = 0, labels = 0;
  if (!file) return { need, tiles, labels };
  for (const { maxZoom, zooms, data } of pmtilesTiles(file)) {
    tiles++;
    const zs = new Set();
    for (const z of zooms) {
      zs.add(z); zs.add(z + 0.5); zs.add(z + 0.999);
      if (z === maxZoom) for (let oz = z + 1; oz <= 22; oz++) zs.add(oz); // 최대 줌 타일은 더 확대해서도 쓰인다
    }
    for (const layer of mvtLayers(data)) {
      const cs = bySource.get(layer.name);
      if (!cs) continue;
      for (const feature of layer.features()) {
        for (const c of cs) {
          for (const zoom of zs) {
            if (zoom < c.minzoom || zoom >= c.maxzoom) continue;
            const g = { zoom };
            if (!c.filter(g, feature)) continue;
            const text = c.text.evaluate(g, feature);
            if (!text) continue;
            const fontDefault = c.font.evaluate(g, feature).join(",");
            const transform = c.transform?.evaluate(g, feature);
            const sections = typeof text === "string" ? [{ text, fontStack: null }] : text.sections;
            for (const sec of sections) {
              let str = sec.text ?? "";
              if (transform === "uppercase") str = str.toLocaleUpperCase();
              else if (transform === "lowercase") str = str.toLocaleLowerCase();
              if (!str) continue;
              labels++;
              const stack = sec.fontStack || fontDefault;
              let arabic = false;
              for (let i = 0; i < str.length; i++) {
                const cu = str.charCodeAt(i);
                if ((cu >= 0x600 && cu <= 0x6ff) || (cu >= 0x750 && cu <= 0x77f) || (cu >= 0x8a0 && cu <= 0x8ff)) arabic = true;
                add(stack, Math.floor(cu / 256) * 256, str);
                const vertical = c.alongLine && VERTICAL_PUNCT.get(str[i]);
                if (vertical) add(stack, Math.floor(vertical.charCodeAt(0) / 256) * 256, `${str} (세로 문장부호)`);
              }
              if (arabic) for (const r of ARABIC_PRESENTATION_RANGES) add(stack, r, `${str} (아랍 표현형)`);
            }
          }
        }
      }
    }
  }
  return { need, tiles, labels };
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
const style = CHECK_ONLY ? JSON.parse(fs.readFileSync(path.join(ASSETS, "style.json"), "utf8")) : buildStyle(FLAVOR);
const errors = validateStyleMin(style);
if (errors.length) {
  for (const e of errors) console.error("스타일 오류:", e.message);
  process.exit(1);
}
const stacks = fontStacks(style);
console.log(`스타일(${FLAVOR}): 레이어 ${style.layers.length}개, 글꼴 ${stacks.join(", ")}`);

if (STYLE_ONLY) {
  const out = path.resolve(OUT ?? path.join(ASSETS, "style.json"));
  fs.writeFileSync(out, JSON.stringify(style, null, 2) + "\n");
  console.log(`스타일만 썼습니다: ${out}`);
  process.exit(0);
}

const haveTiles = fs.existsSync(PMTILES);
if (!haveTiles) console.warn(`지도 파일이 없어 기본 범위만 넣습니다: ${PMTILES}`);
const t0 = Date.now();
const { need, tiles, labels } = neededRanges(style, haveTiles ? PMTILES : null);
if (haveTiles) console.log(`지도 파일 라벨 계산: 타일 ${tiles}개, 라벨 ${labels}건, ${((Date.now() - t0) / 1000).toFixed(1)}초`);
for (const s of stacks) console.log(`  ${s}: 글자가 쓰는 범위 ${need.get(s)?.size ?? 0}개`);
for (const s of need.keys()) if (!stacks.includes(s)) throw new Error(`스타일에 없는 글꼴이 라벨에 쓰였습니다: ${s}`);

if (CHECK_ONLY) {
  const missing = [];
  for (const [s, m] of need) for (const [r, ex] of m) {
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
    if (e.size <= TINY_BYTES || need.get(stack)?.has(start)) jobs.push([e.path, path.join(ASSETS, "fonts", stack, rangeName(start))]);
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
for (const s of stacks) {
  const d = dirSize(path.join(ASSETS, "fonts", s));
  console.log(`  ${s}: 범위 파일 ${d.files}개, ${(d.total / 1024).toFixed(0)} KB`);
}
console.log(`완료: ${ASSETS}  파일 ${files}개, ${(total / 1024).toFixed(0)} KB`);
