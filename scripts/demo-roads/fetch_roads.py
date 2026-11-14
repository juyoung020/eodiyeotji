"""시연 기록의 주행을 실제 길 모양으로 그리기 위한 길 모양 표를 만든다 → core-rs/src/memory/demo_roads.rs

- 장소 좌표는 core-rs/src/memory/demo.rs 의 POIS 표에서 읽는다 (이름 상수 순서 = 표 순서).
- 길 모양은 OSRM 공개 서버(router.project-osrm.org, 자료 © OpenStreetMap 기여자, ODbL)의 차 길찾기에서 받는다.
  보내는 것은 시연 장소 좌표뿐이다 (사용자 기록은 쓰지 않는다). 서버 사용 정책에 맞게 1초에 한 번만 묻는다.
- 받은 길은 10m 오차로 줄이고(Douglas–Peucker) 구글 폴리라인(소수 5자리) 글로 적는다.

실행: python scripts/demo-roads/fetch_roads.py   (구간을 더하려면 아래 PAIRS에 더하고 다시 실행)
"""
import io
import json
import math
import os
import re
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEMO = os.path.join(ROOT, "core-rs", "src", "memory", "demo.rs")
OUT = os.path.join(ROOT, "core-rs", "src", "memory", "demo_roads.rs")

# (출발, 도착) — 반대 방향은 같은 길을 뒤집어 쓴다
PAIRS = [
    # 1년 전 강릉 당일 여행
    ("HOME", "HOENGSEONG"), ("HOENGSEONG", "GYEONGPO"), ("GYEONGPO", "CHODANG"), ("CHODANG", "ANMOK"),
    ("ANMOK", "SEAFOOD"), ("SEAFOOD", "HOME"),
    # 크리스마스
    ("HOME", "SUNDUBU"),
    # 평일 출퇴근 · 볼일
    ("HOME", "OFFICE"), ("HOME", "STARBUCKS"), ("STARBUCKS", "OFFICE"), ("HOME", "GAS"), ("GAS", "OFFICE"),
    ("OFFICE", "HOSPITAL"), ("HOSPITAL", "HOME"), ("OFFICE", "EMART"), ("EMART", "HOME"),
    # 출장
    ("OFFICE", "NAMYANG"), ("OFFICE", "PANGYO"), ("OFFICE", "DCC"),
]
TOLERANCE_M = 10.0


def read_pois():
    s = io.open(DEMO, encoding="utf-8").read()
    table = s[s.index("const POIS: &[Poi] = &["):]
    table = table[: table.index("];")]
    coords = [(float(a), float(b)) for a, b in re.findall(r'",\s*(-?\d+\.\d+),\s*(-?\d+\.\d+)\),', table)]
    names = dict((m.group(1), int(m.group(2))) for m in re.finditer(r"^const ([A-Z_]+): usize = (\d+);", s, re.M))
    return coords, names


def decode(text):
    pts, i, lat, lon = [], 0, 0, 0
    while i < len(text):
        for k in range(2):
            shift = result = 0
            while True:
                b = ord(text[i]) - 63
                i += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            d = ~(result >> 1) if result & 1 else result >> 1
            if k == 0:
                lat += d
            else:
                lon += d
        pts.append((lat / 1e5, lon / 1e5))
    return pts


def encode(pts):
    out, plat, plon = [], 0, 0
    for lat, lon in pts:
        ilat, ilon = round(lat * 1e5), round(lon * 1e5)
        for v in (ilat - plat, ilon - plon):
            v = ~(v << 1) if v < 0 else v << 1
            while v >= 0x20:
                out.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            out.append(chr(v + 63))
        plat, plon = ilat, ilon
    return "".join(out)


def simplify(pts, tol_m):
    if len(pts) < 3:
        return pts
    k = math.cos(math.radians(pts[0][0]))
    xy = [(p[1] * k * 111_195.0, p[0] * 111_195.0) for p in pts]
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = xy[a], xy[b]
        dx, dy = bx - ax, by - ay
        n = math.hypot(dx, dy) or 1e-9
        best, at = 0.0, -1
        for i in range(a + 1, b):
            d = abs((xy[i][0] - ax) * dy - (xy[i][1] - ay) * dx) / n
            if d > best:
                best, at = d, i
        if best > tol_m:
            keep[at] = True
            stack += [(a, at), (at, b)]
    return [p for p, k_ in zip(pts, keep) if k_]


def main():
    coords, names = read_pois()
    rows = []
    for a, b in PAIRS:
        (alat, alon), (blat, blon) = coords[names[a]], coords[names[b]]
        url = f"https://router.project-osrm.org/route/v1/driving/{alon},{alat};{blon},{blat}?overview=full&geometries=polyline"
        req = urllib.request.Request(url, headers={"User-Agent": "eodiyeotji-demo-roads/1.0"})
        data = json.load(urllib.request.urlopen(req, timeout=30))
        route = data["routes"][0]
        pts = simplify(decode(route["geometry"]), TOLERANCE_M)
        rows.append((a, b, encode(pts), len(pts), route["distance"] / 1000))
        print(f"{a:>11} -> {b:<11} {route['distance'] / 1000:7.1f} km  {len(pts):5d} points")
        time.sleep(1.1)
    with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("//! 시연 기록의 주행을 실제 길 모양으로 그리기 위한 길 모양 표 (`demo.rs`가 쓴다).\n")
        f.write("//!\n")
        f.write("//! 만든 것: `scripts/demo-roads/fetch_roads.py` (손으로 고치지 않는다 — 구간을 더하려면 스크립트의 PAIRS에 더하고 다시 실행).\n")
        f.write("//! 출처: OSRM 공개 서버의 차 길찾기, 자료 © OpenStreetMap 기여자 (ODbL). 10m 오차로 줄인 구글 폴리라인(소수 5자리) 글.\n")
        f.write("//! 한 줄: (출발 장소, 도착 장소, 길 모양). 반대 방향은 같은 길을 뒤집어 쓴다.\n\n")
        f.write("use super::*;\n\n")
        f.write("#[rustfmt::skip]\n")
        f.write("pub(super) const ROADS: &[(usize, usize, &str)] = &[\n")
        for a, b, enc, n, km in rows:
            f.write(f"    // {km:.1f}km · 점 {n}개\n")
            f.write(f'    ({a}, {b}, r#"{enc}"#),\n')
        f.write("];\n")
    print("wrote", OUT, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
