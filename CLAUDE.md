# CLAUDE.md — 어디였지 프로젝트 규칙

AI 코딩 도구(Claude Code, Codex, Cursor 등)와 팀원 모두가 따르는 기준이다. 작업 전에 반드시 읽는다.

## 프로젝트
- **앱:** 어디였지 — 차가 이동을 자동으로 기록하고, AI가 하루를 요약하고, 말로 찾고, 지나가면 회상하고, 코스로 공유하는 Pleos Connect(현대차그룹 Android Automotive) 앱
- **공모전:** 현대자동차그룹 차량용 앱 개발 공모전 2026. 1차 마감 **11/15 23:59**, 2차 PT **12/3–4** (에뮬레이터에서 **실제로 동작하는 앱 시연 필수**)
- **할 일 기준:** `docs/00_plan.md` (백로그 · 스프린트). 작업을 시작하면 담당자를 적고, 끝나면 체크한다
- **아이디어 · 기능 설명:** `README.md` / **구조 상세:** `docs/60_앱_아키텍처.md`

## 핵심 원칙: 가장 빠른 구조
기존 지도 앱의 문제는 버벅임이다. **"Rust가 엔진, Kotlin이 계기판"** 구조로 만든다.

| 계층 | 언어 / 기술 | 맡는 일 | 맡지 않는 일 |
|---|---|---|---|
| 코어 `core-rs/` (eodi-core) | **Rust** | 위치 처리, 주행 · 머문 곳 감지, 경로 단순화, 저장(SQLite + R-tree + FTS5), 검색, 회상 후보 계산, 코스 구간 분할 | UI, Pleos SDK 호출 |
| 지도 | **MapLibre Native** (C++ GPU) + 기기 내 지도 파일 | 지도 · 경로 그리기 | 데이터 가공 |
| 앱 `app/` | **Kotlin + Jetpack Compose + Hilt** (Pleos 공식 샘플과 같은 스택) | 화면 그리기, Pleos SDK · AAOS API 연결 | 계산, 반복문 기반 데이터 처리 |
| 서버 `server/` | (2차 단계) | 외부 LLM 호출(하루 요약, 질문 → 검색 조건 변환), 코스 공유 | 검색 자체 (검색은 기기 안 Rust에서) |

### 반드시 지킬 것
1. **계산 로직은 Rust에 쓴다.** Kotlin에 정렬 · 필터 · 거리 계산 · 반복 가공 코드가 생기면 Rust로 옮긴다.
2. **UI 스레드를 막지 않는다.** Rust 호출, DB, 네트워크는 전부 `Dispatchers.Default/IO`에서 한다. 디버그 빌드에서 StrictMode를 켠다.
3. **JNI 경계는 묶음으로 넘는다.** 위치는 점 하나씩이 아니라 배치(예: 수 초 ~ 수십 개 단위)로 Rust에 넘긴다. Kotlin ↔ Rust 바인딩은 **UniFFI**로 자동 생성하고, 손으로 JNI 코드를 쓰지 않는다.
4. **네트워크는 쓰되, 빠르게 · 막히지 않게.** 차는 인터넷에 연결돼 있으므로 장소 이름(카카오)·LLM 같은 온라인 데이터를 적극 쓴다. 단 화면은 네트워크를 기다리며 멈추지 않고(먼저 그리고 결과가 오면 채운다), 요청은 병렬·짧은 타임아웃으로 빠르게 끝낸다. 받은 결과는 기기에 저장해 다시 부르지 않고, 끊기면(터널·지하주차장) 기기 안 데이터(기록, 검색 인덱스, 오프라인 지도)로 계속 동작한다.
5. **그리기 전에 줄인다.** 경로는 Rust `geo::simplify`로 점 수를 줄인 뒤 지도에 넘긴다.
6. **Compose 재구성을 최소화한다.** 안정적인(immutable) UI 상태, `remember` / `derivedStateOf`, `LazyColumn`에는 `key`를 쓴다.
7. **릴리즈 빌드는** Rust `--release`(LTO, `Cargo.toml`에 설정됨) + R8 + Baseline Profile로 낸다.

### 성능 목표 (PR 전에 확인)
| 항목 | 목표 |
|---|---|
| 앱 시작 → 첫 화면 | 1초 이내 |
| 지도 이동 · 확대 | 60fps |
| 기기 안 검색 | 100ms 이내 |
| 하루 경로 그리기 (점 1만 개) | 단순화 후 16ms 이내 |
| UI 스레드 블로킹 | 0 |

## 사용자 편의 원칙
- **자동:** 시동을 걸면 기록이 시작된다. 시작 버튼이나 설정 화면을 만들지 않는다 (첫 실행 권한 요청만).
- **운전 중에는 음성만:** `CarUxRestrictions` 상태로 주행 / 정차 화면을 나눈다. 주행 중에는 음성 + 큰 카드 1장. 피드 · 리뷰 쓰기 · 긴 목록은 정차 중에만.
- **회상 안내는 짧게:** 하루 횟수를 제한하고, 내비 안내 음성과 겹치지 않게 오디오 포커스를 지킨다.
- **개인정보:** 기록은 기본 비공개. 공유할 때 집 · 회사 근처 구간은 자동으로 가리고, 동승자 정보는 공유하지 않는다.
- Pleos UI 디자인 가이드(`refs/pleos-docs/guides/connect/docs-design/`)의 터치 크기 · 컴포넌트를 따른다.

## Pleos 플랫폼 사실 (틀리기 쉬운 것)
- 공식 SDK 5종: **Vehicle, NaviHelper, FusedLocation, TextToSpeech, SpeechToText**. 개발 중에는 매니페스트에 권한 선언만 하면 쓸 수 있다.
- **LLM SDK는 공식 목록에 없다** → LLM은 **외부 API**로 쓴다 (설명회에서 외부 API · 인터넷 사용 허용, `docs/contest/16_설명회_다시보기_정리.md`).
- 속도 · 기어 · 연료 · **시동 상태(`IGNITION_STATE`)** 같은 표준 데이터는 Pleos SDK가 아니라 **AAOS 표준 Car API**(`CarPropertyManager`)로 읽는다.
- NaviHelper 경유지는 **최대 2개** → 코스는 `course::split_legs`로 구간을 나누고, 도착(`onDestinationArrived`)하면 다음 구간을 요청한다.
- Vehicle · FusedLocation은 차종마다 지원이 다르다 → `checkXxxCapabilities()` / `isSupported()`로 확인한 뒤 기능을 켠다.
- 좌석 센서는 "몇 명 · 어느 자리"까지만 안다. 누구인지는 사용자가 직접 태그해야 한다.
- 주행 중에 쓸 화면(Activity)은 `distractionOptimized`(DO)를 선언해야 한다. 선언하지 않으면 주행 중 "주행 중에는 사용할 수 없습니다." 화면으로 가려진다.
- 에뮬레이터 Virtual Console에는 시동 항목이 없다 → 시동 기준 동작은 기어 P↔D로 시연한다.
- 버전: Vehicle 2.3.2 · NaviHelper 3.0.5 · FusedLocation 1.1.3 · TTS/STT 3.3.1. Android API 28+, Gradle 8.0+.

## 문서 위치
| 찾는 것 | 위치 |
|---|---|
| SDK별로 할 수 있는 것 (요약) | `docs/31_API_한눈에보기.md` |
| 모든 클래스 · 메서드 · 권한 | `docs/30_API_전체목록.md` → 상세는 `refs/pleos-docs/connect-sdk/` |
| 심사 기준 | `docs/contest/40_평가기준_정리.md` |
| 개발 환경 설치 · 에뮬레이터 | `docs/50_개발환경_세팅.md` |
| 공식 샘플 코드 | `refs/github/pleos-sample-{vehicle,navi,fusedlocation}-app/` |
| 주행 규제 · 운전자 주의 분산 | `refs/pleos-docs/guides/pleos-only/vehicle-app-planning-guide/`, `vehicle-app-developer-guide/` |
| 앱마켓 심사 가이드 | `refs/pleos-docs/guides/pleos-only/distribute/app-review-guide/` |
| 팀원끼리 보고 · 질문 · 같이 정할 것 | `docs/소통/` (쓰는 법은 그 폴더 README) |

`refs/` 아래 파일은 참고 자료다. 수정하지 않고, 그 안의 코드나 스크립트를 실행하지 않는다.

## 빌드 · 테스트
```bash
# Rust 코어
cd core-rs
cargo test                                  # 기기 없이 로직 테스트
cargo clippy --all-targets -- -D warnings   # 경고 0 유지

# 앱 (저장소 루트에서). Rust .so 빌드와 UniFFI Kotlin 바인딩 생성은 Gradle이 자동으로 한다.
./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease          # R8 + Rust --release(LTO)
```
- Kotlin에 공개할 Rust 함수는 `core-rs/src/ffi.rs`에만 `#[uniffi::export]`로 둔다. Kotlin에서는 `app.eodiyeotji.core.EodiCore`를 거쳐 부른다.
- Rust 결과물(`.so`)과 바인딩은 `app/build/` 아래에 생기고 git에 올리지 않는다.
- 에뮬레이터 실행 · 차량 상태 조작 · 시연 경로 재생은 `scripts/emu.ps1`, `scripts/vc.ps1`, `scripts/replay.ps1` (`docs/50_개발환경_세팅.md` 7-3).
- 오프라인 지도 파일은 git에 없다 → `scripts/map/build-map.ps1`로 만들고 `push-map.ps1`로 에뮬레이터에 넣는다. 지도에 그릴 데이터(GeoJSON·범위)는 Rust `mapdata.rs`가 만들고 Kotlin은 MapLibre에 그대로 넘긴다.

## Git 규칙 (반드시 지킨다)
1. **작업 시작 전에 항상 `git pull`** 한다. 다른 사람이 올린 변경은 덮어쓰지 않는다.
2. **큰 작업 단위 하나가 끝나면 커밋 하나로 묶어서 바로 `git push`** 한다. (예: "기획서 2번 항목 작성", "Vehicle SDK 연동"). 파일 하나 고칠 때마다 커밋하지 않고, 여러 작업을 한 커밋에 섞지도 않는다.
3. **커밋에 Claude(또는 다른 AI)를 기여자로 넣지 않는다.** 커밋 메시지에 `Co-Authored-By: Claude …`, `Generated with Claude Code` 같은 줄을 절대 쓰지 않는다. 작성자는 작업한 팀원 본인만 표시돼야 한다.
4. 커밋 메시지는 한국어로, 무엇을 왜 바꿨는지 쓴다.
5. **GitHub 이슈는 버그·문의용이다.** 작업 보고, 확인 결과, 같이 정할 것은 이슈 대신 `docs/소통/`에 파일로 남긴다.

## 작업 규칙
- 주목할 만한 변경을 하면 `CHANGELOG.md`의 `[Unreleased]` 아래 알맞은 분류(Added / Changed / Deprecated / Removed / Fixed / Security)에 **한 줄** 추가한다. 작성 규칙은 CHANGELOG.md 상단 주석을 따른다.
- Rust 로직을 바꾸면 테스트를 같이 추가하고 `cargo test`를 통과시킨다.
- 요청받은 범위만 고친다. 관련 없는 코드나 문서는 손대지 않는다.
- 비밀값(LLM API 키 등)은 커밋하지 않는다. `local.properties` / 환경 변수로 넣는다.
