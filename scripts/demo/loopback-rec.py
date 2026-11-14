"""PC 스피커로 나가는 소리(에뮬레이터 TTS 포함)를 WAV로 녹음한다 (Windows WASAPI 루프백).

record-demo.ps1이 녹화와 같이 띄운다. adb screenrecord에는 소리가 없어서 따로 받는다.
  python scripts/demo/loopback-rec.py <out.wav> <stop-file>
<stop-file>이 생기면 끝낸다. 필요: pip install --user soundcard (없으면 바로 끝내고 알린다).
주의: PC의 다른 소리(알림 등)도 같이 들어간다. 에뮬레이터 소리가 기본 출력 장치로 나가야 한다.
"""
import os
import sys
import time
import wave

RATE = 48000
CHUNK = RATE // 10  # 0.1초


def main() -> int:
    out, stop = sys.argv[1], sys.argv[2]
    try:
        import numpy as np
        import soundcard as sc
    except ImportError as e:  # 선택 기능: 없으면 영상만 만든다
        print(f"loopback: soundcard 모듈이 없어 소리는 녹음하지 않습니다 ({e})", file=sys.stderr)
        return 0
    spk = sc.default_speaker()
    mic = sc.get_microphone(id=str(spk.name), include_loopback=True)
    with wave.open(out, "wb") as w, mic.recorder(samplerate=RATE, channels=2) as rec:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        start = time.time()
        written = 0
        while not os.path.exists(stop) and time.time() - start < 600:
            data = rec.record(numframes=CHUNK)
            # 조용할 때 WASAPI 루프백은 실제 시간보다 빨리 빈 소리를 돌려준다 → 벽시계에 맞춰 영상과 길이를 맞춘다
            ahead = written / RATE - (time.time() - start)
            if ahead > 0:
                time.sleep(ahead)
            w.writeframes((np.clip(data, -1, 1) * 32767).astype("<i2").tobytes())
            written += len(data)
    return 0


if __name__ == "__main__":
    sys.exit(main())
