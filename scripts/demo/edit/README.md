# 시연영상 편집 (사양: docs/97_시연영상_스토리보드.md v5)

| 파일 | 하는 일 |
|---|---|
| `lines.tsv` | 내레이션 대본 (장면 키 → 문장) |
| `make-vo.sh` | Gemini TTS(Aoede)로 내레이션 → `build/demo/edit/vo/t_*.wav` (지금 들어 있는 것은 임시 음성 edge-tts SunHi) |
| `build.py` | 장면표대로 이어 붙여 1920×1080 115초 영상 (자막 · 칩 · 정직 표시 · 실사 화면 합성 · 내레이션 · 음악) |

재료 (git에 없음, 이 PC의 `build/demo/`):
- `final-1115/scenes/` 최종 녹화 14장면 (`startrec` · `replay` · `biz`는 `rehearsal-v3b` 녹화로 바꿔 넣음)
- `ai/vid_A.mp4` · `vid_G.mp4` · `vid_H2.mp4` · `vid_F.mp4` Google Flow 실사 장면 (Omni 1.2 Flash, 60크레딧 사용)

```bash
bash scripts/demo/edit/make-vo.sh                          # Gemini 음성으로 바꾸기 (한도 걸리면 다시 실행)
python -X utf8 scripts/demo/edit/build.py build/demo/edit/out.mp4
```
