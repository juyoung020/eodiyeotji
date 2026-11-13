"""공공데이터포털 휴게소 · 졸음쉼터 표준데이터(CSV, CP949) → core-rs/src/roadside_places.json (UTF-8, 한 줄에 한 곳).

쓰는 법 (저장소 루트에서, 표준 라이브러리만 씀):
    python -I scripts/data/build_roadside.py data/public/전국휴게소정보표준데이터.csv data/public/전국졸음쉼터표준데이터.csv

출처 · 이용허락: data/public/README.md
"""
import csv
import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "core-rs" / "src" / "roadside_places.json"

# 우리나라 범위 (제주 · 울릉 포함)
LAT = (33.0, 38.7)
LNG = (124.5, 131.0)
# "○○기점 + ××종점"의 오타 · 긴 이름 정리
CITY_FIX = {"양앙": "양양", "광주광역시": "광주"}


def rows(path):
    with open(path, encoding="cp949", newline="") as f:
        return list(csv.DictReader(f))


def yn(v):
    return (v or "").strip().upper() == "Y"


def coord(r, kind, name, dropped):
    try:
        lat, lng = float(r["위도"]), float(r["경도"])
    except (TypeError, ValueError):
        dropped.append(f"{kind} {name}: 좌표 없음")
        return None
    if not (LAT[0] <= lat <= LAT[1] and LNG[0] <= lng <= LNG[1]):
        dropped.append(f"{kind} {name}: 범위 밖 {lat},{lng}")
        return None
    return round(lat, 6), round(lng, 6)


def road(r):
    n = (r["도로노선명"] or "").strip()
    return n[:-1] + "고속도로" if n.endswith("선") else n


def rest_areas(path, dropped):
    out = []
    for r in rows(path):
        raw = r["휴게소명"].strip()
        m = re.match(r"^(.*?)\((.+)\)$", raw)
        base, toward = (m.group(1), m.group(2)) if m else (raw, "")
        dir_text = (r["도로노선방향"] or "").strip()
        if dir_text == "양방향":
            toward = ""
        name = base + ("" if base.endswith("휴게소") else "휴게소") + (f" {toward}방향" if toward else "")
        c = coord(r, "휴게소", raw, dropped)
        if not c:
            continue
        out.append({
            "name": name,
            "kind": "rest_area",
            "type": r["휴게소종류"].strip(),
            "road": road(r),
            "road_no": r["도로노선번호"].strip(),
            "dir": dir_text,
            "toward": toward,
            "lat": c[0],
            "lng": c[1],
            "toilet": yn(r["화장실유무"]),
            "gas": yn(r["주유소유무"]),
            "lpg": yn(r["LPG충전소유무"]),
            "ev_charger": yn(r["전기차충전소유무"]),
            "restaurant": yn(r["음식점유무"]),
            "store": yn(r["매점유무"]),
            "pharmacy": yn(r["약국유무"]),
            "nursing_room": yn(r["수유실유무"]),
            "hours": f'{r["휴게소운영시작시각"].strip()}-{r["휴게소운영종료시각"].strip()}'.strip("-"),
            "food": (r["휴게소대표음식명"] or "").strip(),
            "date": r["데이터기준일자"].strip(),
            "src": "data.go.kr/15025446",
        })
    return out


def shelters(path, dropped):
    out = []
    for r in rows(path):
        raw = r["졸음쉼터명"].strip()
        dir_text = (r["도로노선방향"] or "").strip()
        m = re.search(r"\+\s*(.+?)종점", dir_text)
        toward = CITY_FIX.get(m.group(1).strip(), m.group(1).strip()) if m else ""
        base = raw if raw.endswith("졸음쉼터") else raw + "졸음쉼터"
        name = base + (f" {toward}방향" if toward else "")
        c = coord(r, "졸음쉼터", raw, dropped)
        if not c:
            continue
        out.append({
            "name": name,
            "kind": "drowsy_shelter",
            "road": road(r),
            "road_no": r["도로노선번호"].strip(),
            "dir": dir_text,
            "toward": toward,
            "lat": c[0],
            "lng": c[1],
            "toilet": yn(r["화장실유무"]),
            "date": r["데이터기준일자"].strip(),
            "src": "data.go.kr/15028203",
        })
    return out


def dist_m(a, b):
    k = math.pi / 180
    x = (b["lng"] - a["lng"]) * k * math.cos((a["lat"] + b["lat"]) / 2 * k)
    y = (b["lat"] - a["lat"]) * k
    return 6_371_000 * math.hypot(x, y)


def dedup(places, dropped):
    out = []
    for p in places:
        if any(q["kind"] == p["kind"] and q["name"] == p["name"] and dist_m(p, q) < 100 for q in out):
            dropped.append(f"중복 {p['name']}")
            continue
        out.append(p)
    return out


def main():
    rest_csv, shelter_csv = sys.argv[1], sys.argv[2]
    dropped = []
    rest = rest_areas(rest_csv, dropped)
    shel = shelters(shelter_csv, dropped)
    places = dedup(rest + shel, dropped)
    doc = {
        "source": "공공데이터포털 전국휴게소정보표준데이터(15025446) · 전국졸음쉼터표준데이터(15028203), 제공 한국도로공사. "
                  "이용허락범위: 공공데이터법에 따른 자유 이용(표준데이터, data/public/README.md). 내려받은 날 2026-11-13. 만드는 법: scripts/data/build_roadside.py",
        "places": places,
    }
    lines = ["{", f' "source": {json.dumps(doc["source"], ensure_ascii=False)},', ' "places": [']
    lines += ["  " + json.dumps(p, ensure_ascii=False, separators=(",", ":")) + ("," if i < len(places) - 1 else "") for i, p in enumerate(places)]
    lines += [" ]", "}", ""]
    OUT.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    n_rest = sum(p["kind"] == "rest_area" for p in places)
    print(f"휴게소 {n_rest} · 졸음쉼터 {len(places) - n_rest} → {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB)")
    for d in dropped:
        print("  뺌:", d)


if __name__ == "__main__":
    main()
