"""태블릿에 깐 차 판에 가짜 인포 틀 그림을 넣는다.

차 판은 차가 아닌 기기(태블릿)에서 `files/shell/frame.png`가 있으면 그 그림을 바탕에 깔고,
앱을 차에서와 같은 자리 · 같은 크기로 그 안에 그린다 (app/src/car/.../ui/shell/FakeCarShell.kt).
틀 그림은 Pleos 에뮬레이터 화면을 찍은 것이라 저장소에는 두지 않고, 이 스크립트로 그때그때 찍어 넣는다.

쓰는 법 (에뮬레이터가 켜져 있고 어디였지가 떠 있을 때):
    python scripts/demo-shell/push_shell.py --to <태블릿 adb 주소>
    python scripts/demo-shell/push_shell.py --to <태블릿 adb 주소> --remove   # 틀 빼기
넣은 뒤 태블릿의 앱을 다시 켠다.
"""
import argparse
import subprocess
import sys

PACKAGE = "app.eodiyeotji"


def adb(serial, *args, data=None):
    return subprocess.run(["adb", "-s", serial, *args], input=data, capture_output=True, check=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--emulator", default="emulator-5554", help="틀을 찍을 Pleos 에뮬레이터")
    ap.add_argument("--to", required=True, help="틀을 넣을 기기 (adb 주소)")
    ap.add_argument("--remove", action="store_true")
    a = ap.parse_args()

    if a.remove:
        r = adb(a.to, "shell", "run-as", PACKAGE, "rm", "-f", "files/shell/frame.png")
        print("틀을 뺐다" if r.returncode == 0 else r.stderr.decode(errors="replace"))
        return
    shot = adb(a.emulator, "exec-out", "screencap", "-p").stdout
    if not shot.startswith(b"\x89PNG"):
        sys.exit("에뮬레이터 화면을 찍지 못했다")
    adb(a.to, "shell", "run-as", PACKAGE, "mkdir", "-p", "files/shell")
    r = adb(a.to, "exec-in", "run-as", PACKAGE, "sh", "-c", "cat > files/shell/frame.png", data=shot)
    if r.returncode != 0:
        sys.exit(r.stderr.decode(errors="replace"))
    print(f"틀 그림 {len(shot) // 1024}KB를 넣었다. 앱을 다시 켜면 보인다.")


if __name__ == "__main__":
    main()
