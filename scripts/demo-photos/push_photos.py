"""시연용 "내 사진"을 차 앱에 넣는다: 1년 전 강릉 여행 날의 들른 곳 · 달리던 길에 사진이 붙어 보이게.

    python scripts/demo-photos/push_photos.py                 # 오늘 기준 1년 전 날짜
    python scripts/demo-photos/push_photos.py --day 2025-11-12 --serial emulator-5554 --user 10
    python scripts/demo-photos/push_photos.py --remove        # 넣은 사진 지우기

- 사진은 위키미디어 공용의 자유 이용 사진이다 (아래 PHOTOS, 출처 · 라이선스는 README.md). 저장소에는 넣지 않고
  처음 실행할 때 `~/Documents/어디였지_시연사진/`에 1920px로 받아 둔다.
- 넣는 곳은 폰이 보내 준 사진을 두는 자리(`files/phone_photos`, `gallery/PhonePhotoStore.kt`)다. 파일 이름이
  `p_<찍은 시각 ms>_<위도×1e6>_<경도×1e6>.jpg`라서 갤러리 권한이나 API 키 없이도 하루 지도 · 들른 곳에 뜬다.
- 디버그 판에서만 된다 (`run-as`). 넣은 뒤 release 판을 덮어 깔아도 사진은 남는다.
- 시각은 시연 기록의 강릉 여행 일정(`core-rs/src/memory/demo.rs gangneung`)에 맞춘 한국 시각이다.
"""
import argparse
import datetime as dt
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request

# (받아 둘 이름, 공용 파일 이름, 시, 분, 위도, 경도)
PHOTOS = [
    ("8_tunnel.jpg", "Yeongdong_Expressway_Daegwanryeong_1_tunnel.jpg", 9, 35, 37.6900, 128.7400),     # 달리는 중 (대관령)
    ("2_gyeongpo_a.jpg", "Gyeongpo_Beach_20220502_023.jpg", 10, 30, 37.8057, 128.9085),                 # 경포해변
    ("3_gyeongpo_b.jpg", "Gyeongpo_Beach_20220502_016.jpg", 11, 10, 37.8052, 128.9080),
    ("4_sundubu.jpg", "Chodang_Sundubu_04_(33422779322).jpg", 12, 20, 37.7915, 128.9142),               # 초당할머니순두부
    ("5_anmok.jpg", "Anmok_beach.jpg", 13, 20, 37.7723, 128.9476),                                       # 안목해변 카페거리
    ("6_coffee.jpg", "A_person_holds_a_cup_of_cappuccino_featuring_intricate_latte_art,_sitting_at_a_café_table.jpg", 13, 50, 37.7721, 128.9472),
    ("7_hoe.jpg", "Korean_cuisine-Saengseon_hoe-02.jpg", 14, 50, 37.7690, 128.9520),                     # 안목항 바다횟집
]
CACHE = os.path.expanduser("~/Documents/어디였지_시연사진")
KST = dt.timezone(dt.timedelta(hours=9))


def fetch(name, commons):
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        os.makedirs(CACHE, exist_ok=True)
        url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(commons) + "?width=1920"
        req = urllib.request.Request(url, headers={"User-Agent": "eodiyeotji-demo-photos/0.1 (contest demo)"})
        with urllib.request.urlopen(req, timeout=60) as r, open(path, "wb") as f:
            f.write(r.read())
        time.sleep(3)  # 공용 서버 요청 한도
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--day", help="강릉 여행 날 YYYY-MM-DD (기본: 오늘 기준 1년 전)")
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--user", default="10")
    ap.add_argument("--package", default="app.eodiyeotji")
    ap.add_argument("--remove", action="store_true")
    a = ap.parse_args()
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    run_as = ["adb", "-s", a.serial, "shell", f"run-as {a.package} --user {a.user} sh -c "]

    def sh(cmd, data=None):
        return subprocess.run(run_as[:-1] + [run_as[-1] + "'" + cmd + "'"], input=data, env=env, capture_output=True)

    if a.remove:
        sh("rm -rf files/phone_photos")
        print("지움")
        return
    if a.day:
        y, m, d = map(int, a.day.split("-"))
    else:
        t = dt.datetime.now(KST)
        y, m, d = t.year - 1, t.month, t.day
    sh("mkdir -p files/phone_photos")
    for name, commons, hh, mm, lat, lon in PHOTOS:
        ms = int(dt.datetime(y, m, d, hh, mm, tzinfo=KST).timestamp() * 1000)
        target = f"files/phone_photos/p_{ms}_{round(lat * 1e6)}_{round(lon * 1e6)}.jpg"
        r = subprocess.run(["adb", "-s", a.serial, "exec-in", "run-as", a.package, "--user", a.user, "sh", "-c", f"cat > {target}"],
                           stdin=open(fetch(name, commons), "rb"), env=env, capture_output=True)
        print(("넣음 " if r.returncode == 0 else "실패 ") + f"{hh:02d}:{mm:02d} {name}", r.stderr.decode("utf-8", "replace")[:120])
    print(sh("ls -la files/phone_photos").stdout.decode("utf-8", "replace"))
    print("앱을 다시 열면 그날 기록에 사진이 붙는다.")


if __name__ == "__main__":
    sys.exit(main())
