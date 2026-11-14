#!/usr/bin/env bash
# 시연영상 내레이션 만들기 — Gemini TTS(Aoede). 무료 한도가 1분당 몇 번뿐이라 한 줄씩 쉬어 가며 만든다.
#   bash scripts/demo/edit/make-vo.sh            # 없는 줄만 만든다 (다시 만들려면 build/demo/edit/vo/g_*.wav 지우기)
# 키: local.properties 의 GEMINI_API_KEY (커밋 금지). 만든 뒤: python -X utf8 scripts/demo/edit/build.py build/demo/edit/out.mp4
set -u
cd "$(dirname "$0")/../../.."
VO=build/demo/edit/vo; mkdir -p "$VO"
GT="../eodiyeotji-promo/tools/gtts.py"
while IFS=$'\t' read -r k t; do
  [ -f "$VO/g_$k.wav" ] && continue
  for try in 1 2 3 4 5 6 7 8; do
    python -X utf8 "$GT" Aoede "" "$t" "$VO/g_$k.wav" >/dev/null 2>&1 && break
    rm -f "$VO/g_$k.wav"; sleep 30
  done
  if [ -f "$VO/g_$k.wav" ]; then
    ffmpeg -v error -y -i "$VO/g_$k.wav" -af "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.1,areverse,loudnorm=I=-15:TP=-1.5" -ar 48000 -ac 2 "$VO/t_$k.wav"
    echo "ok $k"
  else echo "FAIL $k (한도 — 조금 뒤 다시 실행)"; fi
  sleep 10
done < scripts/demo/edit/lines.tsv
