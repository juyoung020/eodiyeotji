# 중계 서버 (Cloudflare Worker)

앱이 카카오 · 구글 키 없이 쓰도록 키를 들고 대신 묻는 작은 서버. 키는 이 서버의 비밀값에만 있고 저장소 · APK에는 없다.

| 길 | 대신 묻는 곳 |
|---|---|
| `/kakao/v2/local/...` | 카카오 로컬 (장소 이름) |
| `/kakaonavi/v1/...` | 카카오모빌리티 길찾기 (코스 도로 경로 · 폰 길 안내) |
| `/google/v1/places:searchText`, `/google/v1/places/*/photos/*/media` | 구글 Places (사진, 30일 캐시) |
| `/llm/v1/chat/completions`, `/llm/v1/models` | 개발 PC의 Qwen (Cloudflare 터널, 비밀번호 `LLM_ORIGIN_KEY`) |

## Qwen 켜 두기 (심사 기간 · PT 동안 개발 PC를 켜 둔다)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\llm\start-llm.ps1     # 창 1: Qwen (local.properties의 LLM_ORIGIN_KEY로 비밀번호)
powershell -ExecutionPolicy Bypass -File scripts\llm\expose-llm.ps1    # 창 2: 터널 + 중계 서버에 새 주소 알림
```
두 창을 닫지 않는다. 터널 주소는 켤 때마다 바뀌지만 창 2가 중계 서버 비밀값 `LLM_ORIGIN`을 고치므로 앱은 그대로다.

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
