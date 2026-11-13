"""태블릿에 깐 차 판의 시연용 인포 화면에 꾸밈 그림을 넣는다.

차 판은 차가 아닌 기기(태블릿)에서 인포 화면을 직접 그린다 (app/src/car/.../ui/shell/FakeCarShell.kt).
기기의 `files/shell/panel.png`(주행 정보 화면의 차 그림 · 음악 줄)와 `bar.png`(하단 바)가 있으면 직접 그린 것 대신 그 그림을 쓴다.
이 스크립트는 Pleos 에뮬레이터 화면(2560x1440)을 찍어 그 두 부분만 오려 넣는다.
오린 그림은 에뮬레이터 화면이라 저장소에는 두지 않는다.

쓰는 법 (Pillow 필요: pip install pillow):
    python scripts/demo-shell/push_shell.py --to <태블릿 adb 주소>                 # 켜져 있는 에뮬레이터를 찍어서
    python scripts/demo-shell/push_shell.py --to <태블릿 adb 주소> --shot 화면.png  # 찍어 둔 화면으로
    python scripts/demo-shell/push_shell.py --to <태블릿 adb 주소> --remove         # 그림 빼기 (직접 그린 화면으로)
넣은 뒤 태블릿의 앱을 다시 켠다. 에뮬레이터에 어디였지가 떠 있을 때 찍어야 하단 바에 앱 아이콘이 켜진 모습으로 나온다.
"""
import argparse
import io
import subprocess
import sys

from PIL import Image

PACKAGE = "app.eodiyeotji"
SIZE = (2560, 1440)
# 오릴 자리 (왼쪽, 위, 오른쪽, 아래): 주행 정보 화면의 윗줄(기어 · 속도) 아래 전부, 하단 바 전체
PARTS = {"panel.png": (0, 90, 854, 1300), "bar.png": (0, 1300, 2560, 1440)}


def adb(serial, *args, data=None):
    return subprocess.run(["adb", "-s", serial, *args], input=data, capture_output=True, check=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--emulator", default="emulator-5554", help="화면을 찍을 Pleos 에뮬레이터")
    ap.add_argument("--shot", help="찍어 둔 에뮬레이터 화면 (2560x1440 PNG). 비우면 지금 찍는다")
    ap.add_argument("--to", required=True, help="그림을 넣을 기기 (adb 주소)")
    ap.add_argument("--remove", action="store_true")
    a = ap.parse_args()

    if a.remove:
        for name in PARTS:
            adb(a.to, "shell", "run-as", PACKAGE, "rm", "-f", f"files/shell/{name}")
        print("그림을 뺐다. 앱을 다시 켜면 직접 그린 화면이 나온다.")
        return
    if a.shot:
        shot = Image.open(a.shot)
    else:
        raw = adb(a.emulator, "exec-out", "screencap", "-p").stdout
        if not raw.startswith(b"\x89PNG"):
            sys.exit("에뮬레이터 화면을 찍지 못했다")
        shot = Image.open(io.BytesIO(raw))
    if shot.size != SIZE:
        sys.exit(f"화면 크기가 {shot.size}다. {SIZE} 화면이어야 한다.")
    adb(a.to, "shell", "run-as", PACKAGE, "mkdir", "-p", "files/shell")
    for name, box in PARTS.items():
        buf = io.BytesIO()
        shot.convert("RGB").crop(box).save(buf, "PNG")
        r = adb(a.to, "exec-in", "run-as", PACKAGE, "sh", "-c", f"cat > files/shell/{name}", data=buf.getvalue())
        if r.returncode != 0:
            sys.exit(r.stderr.decode(errors="replace"))
        print(f"{name} {len(buf.getvalue()) // 1024}KB")
    print("넣었다. 앱을 다시 켜면 보인다.")


if __name__ == "__main__":
    main()
