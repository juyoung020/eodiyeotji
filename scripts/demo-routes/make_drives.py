"""경치 드라이브 길 모양 만들기 (데이터 준비용, 앱 코드 아님).

OSRM 공개 서버에 출발 → (길을 붙잡는 점) → 도착을 물어 실제 도로 모양을 받고, 약 25m 오차로 줄여 JSON으로 쓴다.
"붙잡는 점"은 길 모양을 그 도로로 맞추려는 것일 뿐 앱의 경유지가 아니다 (코스에 경유지 없음).
출처: © OpenStreetMap contributors (ODbL), OSRM (router.project-osrm.org).
사용: python -I scripts/demo-routes/make_drives.py core-rs/src/community/drive_routes.json
"""
import json
import math
import sys
import time
import urllib.request

OSRM = "https://router.project-osrm.org/route/v1/driving/"

# id, 제목, 지역, 시작 이름, 끝 이름, [(lat, lon), ...] (처음=출발, 끝=도착, 가운데=길 붙잡는 점)
DRIVES = [
    ("d-gangbyeon-night", "강변북로 한강 야경 드라이브", "서울", "월드컵대교 북단", "광진교 북단",
     [(37.5560, 126.8930), (37.5405, 126.9470), (37.5290, 127.0010), (37.5390, 127.0950)]),
    ("d-olympic-sunset", "올림픽대로 한강 노을 드라이브", "서울", "잠실", "가양대교 남단",
     [(37.5170, 127.0880), (37.5115, 127.0050), (37.5265, 126.9290), (37.5640, 126.8500)]),
    ("d-incheon-bridge", "인천대교 바다 위 드라이브", "인천", "송도", "영종도",
     [(37.4060, 126.6090), (37.4290, 126.5750), (37.4630, 126.5050)]),
    ("d-yeongjong-eurwang", "영종대교 건너 을왕리 해안길", "인천", "청라", "을왕리해수욕장",
     [(37.5480, 126.6400), (37.5430, 126.5800), (37.4470, 126.3730)]),
    ("d-jayuro-imjin", "자유로 따라 임진각 드라이브", "경기", "가양대교 북단", "임진각",
     [(37.5800, 126.8600), (37.6700, 126.7400), (37.8890, 126.7400)]),
    ("d-sihwa", "시화방조제 바닷길", "경기", "오이도", "대부도 방아머리",
     [(37.3440, 126.6900), (37.3200, 126.6400), (37.3080, 126.6150)]),
    ("d-route7", "7번 국도 동해안 드라이브", "강원", "경포해변", "하조대",
     [(37.8040, 128.9080), (37.8930, 128.8300), (38.0280, 128.7180)]),
    ("d-heonhwa", "헌화로 바닷길", "강원", "정동진", "금진항",
     [(37.6910, 129.0340), (37.6440, 129.0500)]),
    ("d-daegwallyeong", "대관령 옛길 드라이브", "강원", "대관령마을휴게소", "강릉 성산면",
     [(37.6880, 128.7540), (37.7120, 128.8000), (37.7280, 128.8370)]),
    ("d-bukhangang-391", "북한강 391번 지방도", "경기", "두물머리", "청평",
     [(37.5450, 127.3180), (37.6000, 127.3500), (37.7300, 127.4250)]),
]


def osrm(points):
    coords = ";".join(f"{lon:.5f},{lat:.5f}" for lat, lon in points)
    url = f"{OSRM}{coords}?overview=full&geometries=geojson&continue_straight=true"
    req = urllib.request.Request(url, headers={"User-Agent": "eodiyeotji-demo-data"})
    d = json.load(urllib.request.urlopen(req, timeout=30))
    r = d["routes"][0]
    return [(lat, lon) for lon, lat in r["geometry"]["coordinates"]], r["distance"], r["duration"]


def meters(a, b):
    lat = math.radians((a[0] + b[0]) / 2)
    return math.hypot((b[0] - a[0]) * 111_320, (b[1] - a[1]) * 111_320 * math.cos(lat))


def seg_dist(p, a, b):
    lat = math.radians(p[0])
    ax, ay = a[1] * 111_320 * math.cos(lat), a[0] * 111_320
    bx, by = b[1] * 111_320 * math.cos(lat), b[0] * 111_320
    px, py = p[1] * 111_320 * math.cos(lat), p[0] * 111_320
    dx, dy = bx - ax, by - ay
    t = 0 if dx == dy == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def rdp(pts, eps):
    if len(pts) < 3:
        return pts
    i, dmax = 0, 0.0
    for k in range(1, len(pts) - 1):
        d = seg_dist(pts[k], pts[0], pts[-1])
        if d > dmax:
            i, dmax = k, d
    if dmax <= eps:
        return [pts[0], pts[-1]]
    return rdp(pts[: i + 1], eps)[:-1] + rdp(pts[i:], eps)


def cut_loops(pts, near=60.0, away=800.0):
    """지나온 자리(near m 안)로 다시 돌아오는 고리(그사이 away m 넘게 다녀옴)를 잘라 낸다 (반대 차로에서 돌아 나오는 길)."""
    out = list(pts)
    changed = True
    while changed:
        changed = False
        for i in range(len(out)):
            far = 0.0
            for j in range(i + 1, len(out)):
                far += meters(out[j - 1], out[j])
                if far > away and meters(out[i], out[j]) < near:
                    out = out[: i + 1] + out[j + 1 :]
                    changed = True
                    break
            if changed:
                break
    return out


def main():
    out = []
    for did, title, region, start, end, pts in DRIVES:
        line, dist, dur = osrm(pts)
        full = sum(meters(line[i], line[i + 1]) for i in range(len(line) - 1))
        line = cut_loops(line)
        dist = sum(meters(line[i], line[i + 1]) for i in range(len(line) - 1))
        dur = dur * dist / max(full, 1)  # 잘라 낸 고리만큼 시간도 줄인다
        simple = rdp(line, 25.0)
        straight = sum(meters(pts[i], pts[i + 1]) for i in range(len(pts) - 1))
        print(f"{did}: {dist/1000:.1f}km (직선 합 {straight/1000:.1f}km), {dur/60:.0f}분, 점 {len(line)}→{len(simple)}")
        out.append({
            "id": did, "title": title, "region": region, "start": start, "end": end,
            "distance_m": round(dist),
            # OSRM 시간이 너무 느리면(방조제 등 속도 정보가 이상) 평균 40km/h로 잡는다
            "duration_s": round(dur if dist / max(dur, 1) * 3.6 >= 15 else dist / (40 / 3.6)),
            "line": [[round(a, 5), round(b, 5)] for a, b in simple],
        })
        time.sleep(1.1)
    json.dump(out, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
