# 중계 서버 (Cloudflare Worker)

앱이 카카오 · 구글 키 없이 쓰도록 키를 들고 대신 묻는 작은 서버. 키는 이 서버의 비밀값에만 있고 저장소 · APK에는 없다.

| 길 | 대신 묻는 곳 |
|---|---|
| `/kakao/v2/local/...` | 카카오 로컬 (장소 이름) |
| `/kakaonavi/v1/...` | 카카오모빌리티 길찾기 (코스 도로 경로 · 폰 길 안내) |
| `/google/v1/places:searchText`, `/google/v1/places/*/photos/*/media` | 구글 Places (사진, 30일 캐시) |
| `/llm/v1/chat/completions`, `/llm/v1/models` | Google Gemini (OpenAI 호환, `src/gemini.js`). `GEMINI_API_KEY`가 없을 때만 개발 PC의 Qwen 터널(`LLM_ORIGIN`) |

## LLM: Gemini (개발 PC 필요 없음)

- 앱은 OpenAI 형식으로 `/llm/v1/chat/completions`에 묻고, 중계가 키(`GEMINI_API_KEY`, 무료 등급)를 붙여 `generativelanguage.googleapis.com/v1beta/openai/`로 넘긴다. 도구 호출(tools) 그대로 쓴다.
- llama.cpp 전용 값(`cache_prompt`, `chat_template_kwargs` 등)은 빼고 표준 값만 넘긴다.
- 모델: 앱이 보낸 Gemini 모델(기본 `gemini-flash-lite-latest`) → 503(붐빔)·429(한도)면 `gemini-3.1-flash-lite` → `gemini-3.5-flash-lite` 순으로 다시 묻는다. 목록은 비밀값 `GEMINI_MODELS`(쉼표)로 바꿀 수 있다.
- 키 넣기 (값을 화면에 찍지 않고): `cd server/relay; (Get-Content ..\..\local.properties | sls '^GEMINI_API_KEY=') -replace '^GEMINI_API_KEY=','' | npx wrangler secret put GEMINI_API_KEY`
- 개발 PC의 Qwen(`scripts/llm/start-llm.ps1`, `expose-llm.ps1`)은 로컬 개발용 선택 사항이다. 중계에 `GEMINI_API_KEY`가 있으면 쓰이지 않는다.

정해 둔 길만 통과하고, 앱의 `X-Eodi-App` 값(또는 사진 주소의 `app=`)이 맞아야 한다. 진짜 비용 한도는 각 API 콘솔의 하루 한도로 건다.

## 띄우기

```powershell
cd server/relay
npm install
npx wrangler login                     # 처음 한 번 (브라우저에서 허용)
npx wrangler deploy                    # → https://eodi-relay.memo2025.workers.dev (지금 열려 있는 주소)
npx wrangler secret put KAKAO_REST_API_KEY
npx wrangler secret put GOOGLE_PLACES_API_KEY
npx wrangler secret put APP_TOKEN      # local.properties의 RELAY_TOKEN과 같은 값
```

앱 쪽: `local.properties`에 `RELAY_URL=https://eodi-relay.<계정>.workers.dev`, `RELAY_TOKEN=...`을 넣고 빌드하면 키 없이 중계로 묻는다 (키는 APK에 들어가지 않는다).

로컬 시험: `.dev.vars`(git 제외)에 세 값을 넣고 `npx wrangler dev`.
