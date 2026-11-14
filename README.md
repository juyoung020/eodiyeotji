<div align="center">

# 🚗 어디였지

### "그때 거기, 어디였지?" — 차가 기억하는 나의 모든 길

시동만 걸면 차가 들른 곳과 지나온 길을 저절로 기록하고,<br>
말로 물으면 그날을 찾아 그 길 그대로 다시 안내하는 **Pleos Connect 차량용 앱**

<br>

![Contest](https://img.shields.io/badge/현대자동차그룹-차량용_앱_개발_공모전_2026-002C5F?style=for-the-badge)
![Platform](https://img.shields.io/badge/Pleos_Connect-Android_Automotive-00AAD2?style=for-the-badge)
![SDK](https://img.shields.io/badge/Pleos_SDK-5종_전부_활용-2EA44F?style=for-the-badge)

<br>

<img src="docs/images/cover-2026-11-14.gif" width="760" alt="Pleos 에뮬레이터 시연: 말로 찾기 → 그날로 돌아가기 → 같이 가기 휴게소 제안">

<sub>Pleos Connect 에뮬레이터 실제 화면 · 시연 영상은 [`video/`](video/)</sub>

</div>

---

## 📌 한눈에 보기

| | |
|---|---|
| **앱 이름** | 어디였지 (팀 Car-pe Diem) · 패키지 `app.eodiyeotji` · 버전 0.7.0 |
| **한 줄 소개** | 차가 알아서 기록한 길을, 말 한마디로 다시 찾고 그대로 안내받는 드라이브 기억 앱 |
| **APK** | [GitHub Releases `submission-1`](https://github.com/juyoung020/eodiyeotji/releases/tag/submission-1) → [`Car-pe-Diem_eodiyeotji_release.apk`](https://github.com/juyoung020/eodiyeotji/releases/download/submission-1/Car-pe-Diem_eodiyeotji_release.apk) (61MB라 저장소 대신 Releases에 올림, 안내: [`apk/`](apk/)) |
| **테스트 환경** | Pleos Connect 에뮬레이터 v2.1.0 (Android API 34, x86_64, 2560 × 1440) |
| **필요한 설정** | **없음.** APK만 설치하면 AI · 장소 검색 · 같이 가기까지 모두 동작합니다 (클라우드 중계 서버 사용) |
| **시연 영상** | [`video/`](video/) |

<br>

## ▶️ 3단계로 실행하기

```powershell
# 1. Pleos Connect 에뮬레이터를 켭니다
# 2. APK를 내려받아 설치합니다
adb install -r Car-pe-Diem_eodiyeotji_release.apk
# 3. 앱을 엽니다 (앱 목록의 "어디였지"를 눌러도 됩니다)
adb shell monkey -p app.eodiyeotji -c android.intent.category.LAUNCHER 1
```

처음 켜면 위치 · 마이크 · 사진 권한을 묻습니다. 모두 허용하면 모든 기능을 볼 수 있습니다.

<br>

## 🧪 심사 때 이렇게 둘러보세요 (약 5분)

새로 설치한 앱은 기록이 비어 있습니다. 혼자서도 전체 흐름을 볼 수 있도록 시연용 데이터를 준비해 두었습니다.

| 순서 | 해 볼 것 | 보이는 것 |
|:-:|---|---|
| 1 | 첫 화면의 **[시연 기록 넣기]** | 1년치 주행 기록이 바로 채워집니다 (여행 · 출퇴근 · 출장) |
| 2 | 날짜 하나를 누르기 | 그날 지도 · 경로 · 들른 곳 · 사진 · AI가 붙인 하루 제목 |
| 3 | 검색창에 **"작년 강릉 바다 어디였지"** (마이크로 말해도 됩니다) | 답 카드와 그날의 코스 → [이 코스로 안내]를 누르면 Pleos 내비로 바로 안내 |
| 4 | 이어서 **"거기 몇 명이서 갔어?"**, **"순두부집 빼고 안내해 줘"** | 앞 대화를 기억해서 답하고, 뺄 곳만 빼고 코스를 다시 짭니다 |
| 5 | 날짜 화면의 **[그날로 돌아가기]** | 그날의 길을 3D로 다시 달리는 회상 화면 |
| 6 | 장소 창의 **[같이 가기] → [새로 만들기]** | 시연 차 2대가 자동으로 참여하고, 일행의 위치와 배터리 · 연료가 실시간으로 보입니다 |
| 7 | Virtual Console에서 **기어를 D**로 | 주행 화면으로 바뀝니다 (음성 + 큰 카드 1장). 같이 가기 중에는 일행이 뒤처지면 앞쪽 휴게소에서 기다리자고 제안합니다 |
| 8 | 왼쪽 메뉴의 **모아보기 · 커뮤니티** | 출장 관리 · 운행기록부, 다른 사람이 달린 드라이브 코스 · 리뷰 |

- 에뮬레이터에는 시동 항목이 없어서, **기어 P → D**를 시동으로 씁니다. 기어를 D로 바꾸면 기록이 저절로 시작되고, P로 돌아오면 그 주행이 정리됩니다.
- 주행 중에는 안전을 위해 목록 · 글쓰기 화면이 닫히고 음성으로만 씁니다. 다시 P로 두면 열립니다.
- 커뮤니티 글과 같이 가기의 시연 차는 시연용 예시입니다.

<br>

## 🧭 무엇을 하나

```mermaid
flowchart LR
    A["① 기록<br/>시동을 걸면 저절로"] --> B["② 기억<br/>말로 찾고 · 지나가면 떠올리고"]
    B --> C["③ 함께<br/>코스 공유 · 같이 가기"]
    C -.->|따라간 길도 다시 기록| A
```

| | 기능 | 운전자에게 주는 것 |
|:-:|---|---|
| ① | **자동 기록** | 앱을 켤 필요 없이, 다닌 길 · 머문 곳 · 탄 사람 수 · 비 온 날 · 주행거리가 저절로 남습니다 |
| ② | **말로 찾기** | 이름이나 날짜가 기억 안 나도 "비 오던 날 넷이서 간 바다"처럼 상황으로 찾습니다 |
| ② | **한 문장 코스** | "작년 강릉 여행 그대로, 순두부집만 빼고" → 바로 다시 짠 코스로 안내 |
| ② | **회상** | 예전에 들른 곳을 지나가면 짧게 알려 주고, 정차 중에는 그날로 돌아가 3D로 다시 달립니다 (무드등도 그날 하늘빛으로) |
| ③ | **같이 가기** | 여러 차가 함께 갈 때 일행 위치 · 배터리 · 연료를 공유하고, 부족하면 같이 들를 충전소를, 뒤처지면 기다릴 휴게소를 먼저 제안합니다 |
| ③ | **커뮤니티** | 실제로 달린 기록으로만 올라오는 드라이브 코스(3D 미리 달리기) · 여행 리뷰. 집 · 회사 근처는 자동으로 가립니다 |
| ➕ | **업무** | 출장 자동 정리 · 정산서, 주행거리계 기반 운행기록부 CSV |
| ➕ | **길 위 도우미** | "화장실 급해" 한마디에 경로 앞쪽의 가장 가까운 휴게소 · 졸음쉼터 안내 (공공데이터) |
| ➕ | **스마트폰 앱** | 차 화면 QR로 연결, 내리면 주차 위치로 걸어서 내 차 찾기 |

<br>

## 🛠️ Pleos SDK 5종 활용

| SDK | 어디에 쓰나 |
|---|---|
| **Vehicle** | 좌석 센서로 탑승 인원, 와이퍼로 비 온 날, 주행거리계로 운행 거리, 배터리 · 연료로 충전 필요 여부, 회상 때 무드등 |
| **NaviHelper** | 찾은 장소 · 코스를 순정 내비로 바로 안내. 경유지 2개 제한은 구간으로 나눠 도착하면 다음 구간을 자동으로 이어서 요청 |
| **FusedLocation** | 터널 · 지하주차장에서도 끊기지 않는 차량 위치로 경로와 머문 곳 기록 |
| **SpeechToText** | 주행 중 말로 묻기 (기기 안 음성 인식) |
| **TextToSpeech** | 답 · 회상 · 같이 가기 알림을 음성으로 (내비 안내 음성과 겹치지 않게) |

기어 · 시동 같은 표준 차량 데이터는 AAOS `CarPropertyManager`로, 주행 중 화면 제한은 `CarUxRestrictions`로 처리합니다.

<br>

## ⚡ 빠르고 끊기지 않게

- **"Rust가 엔진, Kotlin이 계기판"**: 위치 처리 · 주행 감지 · 저장(SQLite + FTS5) · 검색 · 코스 계산은 Rust 코어에서, 화면과 SDK 연결은 Kotlin(Jetpack Compose)이 맡습니다.
- 앱 첫 화면 약 0.4초, 기기 안 기억 검색은 1ms 안쪽입니다 (release 빌드).
- 기록과 검색은 기기 안에서 하므로 통신이 끊긴 곳에서도 동작합니다. AI · 장소 이름 · 같이 가기는 클라우드 중계 서버([`server/relay`](server/relay/))를 거치며, API 키는 서버에만 있고 APK에는 없습니다.

<br>

## 🔒 개인정보 · 안전

- 기록은 기기 안에만 저장되고 기본 비공개입니다. 계정 · 로그인이 없고, 차량 번호 · 차대번호 · 기기 ID를 읽거나 보내지 않습니다.
- 공유할 때는 집 · 회사 1km 안 구간을 자동으로 가리고, 같이 가기도 그 안에서는 내 위치를 보내지 않습니다.
- **"기록 모두 지우기"** 한 번으로 모든 기록을 지우고 동의를 철회할 수 있습니다.
- 주행 중에는 음성과 큰 카드 한 장만 보이고, 회상 음성은 하루 횟수를 제한해 내비 안내와 겹치지 않게 합니다.
- 분석 · 광고 SDK가 없고, 주행 기록을 광고나 제3자 제공에 쓰지 않습니다.

<br>

<details>
<summary><b>🔧 개발자용: 소스에서 빌드하기</b></summary>

```powershell
git clone https://github.com/juyoung020/eodiyeotji.git
cd eodiyeotji
./gradlew installCarRelease      # 시연용 (R8 + Rust 릴리즈 빌드)
./gradlew installCarDebug        # 개발용 (디버그 도구 포함)
./gradlew assemblePhoneDebug     # 스마트폰 판
```

- 필요한 것: JDK 17+, Android SDK 35 · NDK 27.3.13750724, Rust stable + `cargo-ndk`. 한 번에 설치: `scripts/setup-dev.ps1`, 자세히: [`docs/50_개발환경_세팅.md`](docs/50_개발환경_세팅.md)
- Rust 코어(`core-rs/`) 빌드와 Kotlin 바인딩 생성은 Gradle이 자동으로 합니다. 로직 시험: `cd core-rs && cargo test`
- 소스 빌드에서 AI · 장소 검색을 쓰려면 `local.properties`에 `RELAY_TOKEN`을 넣습니다 (중계 서버 주소는 기본값으로 들어 있음). 없으면 기기 안 검색만으로 동작합니다.
- 고음질 서버 TTS(Pleos HYBRID)는 `-PPREMIUM_TTS=true`로 빌드할 때만 켜집니다. 기본은 기기 안 음성입니다.
- 앱마켓 제출판(시연 데이터 제외): `./gradlew assembleCarRelease -PCONTEST_DEMO=false`
- 오프라인 지도 파일은 저장소 밖에서 만듭니다: `scripts/map/build-map.ps1` → `push-map.ps1`. 없으면 온라인 지도로 그립니다.
- 시연 영상 녹화: [`docs/96_시연영상_녹화.md`](docs/96_시연영상_녹화.md)

| 폴더 | 내용 |
|---|---|
| `core-rs/` | Rust 코어 (기록 · 기억 · 검색 · 비서 · 지도 데이터) |
| `app/` | Android 앱. `src/main` 공통, `src/car` Pleos 차량 판, `src/phone` 스마트폰 판 |
| `server/relay/` | 클라우드 중계 서버 (Cloudflare Worker) |
| `docs/` | 계획, 아키텍처, API 정리, 공모전 자료 |
| `video/` | 시연 · 홍보 영상 |
| `scripts/` | 에뮬레이터 실행 · 차량 조작 · 시연 경로 재생 · 지도 · 시연 녹화 |

변경 이력: [`CHANGELOG.md`](CHANGELOG.md) · 팀 작업 규칙: [`CLAUDE.md`](CLAUDE.md)

</details>

<details>
<summary><b>📋 앱마켓 제출 시 사전 협의가 필요한 권한</b></summary>

모두 실제로 쓰고, 없거나 승인되지 않아도 그 기능만 빠지고 앱은 동작합니다.

| 권한 | 쓰는 곳 | 없을 때 |
|---|---|---|
| `pleos.car.permission.CONTROL_CAR_LIGHTS` · `CAR_LIGHTS` | 회상 중 무드등을 그날 하늘빛으로, 닫으면 원래 색 | 무드등을 건드리지 않음 |
| `pleos.car.permission.CAR_SEATS` | 탑승 인원 수 | 인원 없이 기록 |
| `pleos.car.permission.CAR_ENERGY` · `CAR_DRIVE` | 배터리 · 연료, 주행거리계 | 그 칸이 빈 채로 |
| `pleos.car.permission.CAR_WINDOWS` | 와이퍼 (비 온 날) | 비 표시 없이 |

마켓 등록 자료 초안: [`docs/market/`](docs/market/)

</details>

<details>
<summary><b>📚 데이터 출처</b></summary>

| 데이터 | 출처 | 쓰는 곳 |
|---|---|---|
| 고속도로 휴게소 · 졸음쉼터 | 공공데이터포털 [전국휴게소정보표준데이터](https://www.data.go.kr/data/15025446/standard.do) · [전국졸음쉼터표준데이터](https://www.data.go.kr/data/15028203/standard.do), 제공 한국도로공사 ([`data/public/`](data/public/README.md)) | 길 위 도우미 · 같이 가기 휴게소 제안 |
| 지도 | OpenStreetMap 기여자 | 오프라인 지도 |
| 날씨 | Open-Meteo | 공유 코스의 그날 날씨 |

</details>

---

<div align="center">
<sub>현대자동차그룹 차량용 앱 개발 공모전 2026 출품작 · <a href="https://appdevchallenge.auto-hmg.io/">공모전 사이트</a></sub>
</div>
