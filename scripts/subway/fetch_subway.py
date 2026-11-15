"""수도권 지하철 · 전철 역과 이웃 역 연결 표를 만든다 → core-rs/src/subway_data.rs

- 자료: OpenStreetMap의 노선 관계(route = subway / light_rail / train)와 그 정차역(stop) 순서, Overpass API로 받는다.
  자료 © OpenStreetMap 기여자 (ODbL). 서버에는 범위(수도권 네모)만 보낸다 (사용자 기록은 쓰지 않는다).
- 고속 · 일반 열차(KTX · ITX · 무궁화 · 새마을 등)와 정차역이 5개 미만인 관계는 뺀다.
- 같은 이름의 정차 지점이 300m 안에 있으면 한 역으로 묶고(상 · 하행 승강장), 노선 순서에서 이웃한 역끼리 잇는다.
  갈아타는 역(이름이 같고 600m 안)도 서로 잇는다.
- 급행이 역을 건너뛰어 생긴 연결은 뺀다 (길을 역마다 따라 그리려고).

실행: python scripts/subway/fetch_subway.py   (역이 새로 생기면 다시 실행)
"""
import io
import json
import math
import os
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "core-rs", "src", "subway_data.rs")
BOX = "37.20,126.55,37.95,127.35"
QUERY = f"""[out:json][timeout:180];
relation["route"~"^(subway|light_rail|train)$"]["type"="route"]({BOX});
out body;
node(r);
out body;
"""
SKIP = ("KTX", "ITX", "무궁화", "새마을", "누리로", "SRT", "관광", "직통")
MERGE_M = 300.0
TRANSFER_M = 600.0
# 각 역 정차로 돌아가는 길이 이 배수 안이면 급행 연결로 본다
EXPRESS_DETOUR = 1.3


def dist(a, b):
    k = math.cos(math.radians(a[0]))
    return math.hypot((a[0] - b[0]) * 111_195.0, (a[1] - b[1]) * 111_195.0 * k)


def clean(name):
    name = name.split("(")[0].strip()
    return name if name.endswith("역") else name + "역"


def prune_express(stations, edges):
    """급행이 건너뛰어 생긴 연결(가 → 다)은 각 역 정차 연결(가 → 나 → 다)이 있으면 뺀다 — 길을 역마다 따라 그리려고."""
    import heapq

    length = {e: dist(stations[e[0]][1:], stations[e[1]][1:]) for e in edges}
    keep = set(edges)
    for e in sorted(edges, key=lambda e: -length[e]):
        if stations[e[0]][0] == stations[e[1]][0]:
            continue  # 갈아타는 연결
        adj = {}
        for a, b in keep:
            if (a, b) != e:
                adj.setdefault(a, []).append((b, length[(a, b)]))
                adj.setdefault(b, []).append((a, length[(a, b)]))
        limit = length[e] * EXPRESS_DETOUR
        best, heap = {e[0]: 0.0}, [(0.0, e[0])]
        while heap:
            d, u = heapq.heappop(heap)
            if u == e[1]:
                keep.discard(e)
                break
            if d > best.get(u, 1e18):
                continue
            for v, w in adj.get(u, ()):
                nd = d + w
                if nd <= limit and nd < best.get(v, 1e18):
                    best[v] = nd
                    heapq.heappush(heap, (nd, v))
    return keep


def main():
    req = urllib.request.Request(
        "https://overpass-api.de/api/interpreter",
        data=urllib.parse.urlencode({"data": QUERY}).encode(),
        headers={"User-Agent": "eodiyeotji-subway/1.0"},
    )
    data = json.load(urllib.request.urlopen(req, timeout=240))
    nodes = {e["id"]: e for e in data["elements"] if e["type"] == "node"}
    stations = []  # (name, lat, lon)

    def station(node_id):
        n = nodes.get(node_id)
        name = (n or {}).get("tags", {}).get("name")
        if not n or not name:
            return None
        name, at = clean(name), (n["lat"], n["lon"])
        for i, (s, lat, lon) in enumerate(stations):
            if s == name and dist(at, (lat, lon)) <= MERGE_M:
                return i
        stations.append((name, at[0], at[1]))
        return len(stations) - 1

    edges = set()
    lines = 0
    for rel in (e for e in data["elements"] if e["type"] == "relation"):
        tags = rel.get("tags", {})
        if any(w in tags.get("name", "") for w in SKIP):
            continue
        stops = [station(m["ref"]) for m in rel["members"] if m["type"] == "node" and m["role"].startswith("stop")]
        stops = [s for s in stops if s is not None]
        if len(stops) < 5:
            continue
        lines += 1
        for a, b in zip(stops, stops[1:]):
            # 순서가 어긋난 자료로 먼 역끼리 이어지는 것을 막는다 (이웃 역은 길어야 십몇 km)
            if a != b and dist(stations[a][1:], stations[b][1:]) < 15_000:
                edges.add((min(a, b), max(a, b)))
    for i, (s, lat, lon) in enumerate(stations):
        for j in range(i + 1, len(stations)):
            if stations[j][0] == s and dist((lat, lon), stations[j][1:]) <= TRANSFER_M:
                edges.add((i, j))
    edges = prune_express(stations, edges)
    used = sorted({i for e in edges for i in e})
    index = {old: new for new, old in enumerate(used)}
    with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("//! 수도권 지하철 · 전철 역과 이웃 역 연결 표 (`subway.rs`가 쓴다).\n")
        f.write("//!\n")
        f.write("//! 만든 것: `scripts/subway/fetch_subway.py` (손으로 고치지 않는다 — 역이 새로 생기면 스크립트를 다시 실행).\n")
        f.write("//! 출처: OpenStreetMap 노선 관계의 정차역 순서, 자료 © OpenStreetMap 기여자 (ODbL).\n\n")
        f.write("/// (역 이름, 위도, 경도)\n#[rustfmt::skip]\n")
        f.write("pub(super) const STATIONS: &[(&str, f64, f64)] = &[\n")
        for old in used:
            s, lat, lon = stations[old]
            f.write(f'    ("{s}", {lat:.5f}, {lon:.5f}),\n')
        f.write("];\n\n/// 이웃한 두 역 ([STATIONS] 번호)\n#[rustfmt::skip]\n")
        f.write("pub(super) const LINKS: &[(u16, u16)] = &[\n")
        for a, b in sorted((index[a], index[b]) for a, b in edges):
            f.write(f"    ({a}, {b}),\n")
        f.write("];\n")
    print(f"lines {lines}, stations {len(used)}, links {len(edges)} -> {OUT} ({os.path.getsize(OUT)} bytes)")


if __name__ == "__main__":
    main()
