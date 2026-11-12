# 중계 서버 (Cloudflare Worker)

앱이 카카오 · 구글 키 없이 쓰도록 키를 들고 대신 묻는 작은 서버. 키는 이 서버의 비밀값에만 있고 저장소 · APK에는 없다.

| 길 | 대신 묻는 곳 |
|---|---|
| `/kakao/v2/local/...` | 카카오 로컬 (장소 이름) |
| `/kakaonavi/v1/...` | 카카오모빌리티 길찾기 (코스 도로 경로 · 폰 길 안내) |
| `/google/v1/places:searchText`, `/google/v1/places/*/photos/*/media` | 구글 Places (사진, 30일 캐시) |

정해 둔 길만 통과하고, 앱의 `X-Eodi-App` 값(또는 사진 주소의 `app=`)이 맞아야 한다. 진짜 비용 한도는 각 API 콘솔의 하루 한도로 건다.

## 띄우기

```powershell
cd server/relay
npm install
npx wrangler login                     # 처음 한 번 (브라우저에서 허용)
npx wrangler deploy                    # → https://eodi-relay.<계정>.workers.dev
npx wrangler secret put KAKAO_REST_API_KEY
npx wrangler secret put GOOGLE_PLACES_API_KEY
npx wrangler secret put APP_TOKEN      # local.properties의 RELAY_TOKEN과 같은 값
```

앱 쪽: `local.properties`에 `RELAY_URL=https://eodi-relay.<계정>.workers.dev`, `RELAY_TOKEN=...`을 넣고 빌드하면 키 없이 중계로 묻는다 (키는 APK에 들어가지 않는다).

로컬 시험: `.dev.vars`(git 제외)에 세 값을 넣고 `npx wrangler dev`.
