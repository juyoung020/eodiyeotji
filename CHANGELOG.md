# Changelog

이 프로젝트(어디였지)의 주목할 만한 변경 사항을 모두 이 파일에 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르고,
버전은 [유의적 버전(Semantic Versioning)](https://semver.org/lang/ko/)을 따릅니다.

<!--
작성 규칙 (사람 · AI 공통)
- 변경 하나당 한 줄. 커밋 로그를 그대로 붙이지 말고, 사용자·팀원 입장에서 "무엇이 달라졌나"를 쓴다.
- 작업할 때마다 [Unreleased] 아래 알맞은 분류에 한 줄 추가한다. 분류 순서: Added, Changed, Deprecated, Removed, Fixed, Security. 비어 있는 분류는 쓰지 않는다.
- 릴리즈할 때 [Unreleased] 내용을 새 버전 제목 `## [x.y.z] - YYYY-MM-DD` 아래로 옮기고, git 태그 `vx.y.z`를 만들고, 맨 아래 비교 링크를 갱신한다.
- 날짜는 ISO 8601 (YYYY-MM-DD). 최신 버전이 맨 위.
- 버전 올리는 기준 (1.0.0 전까지): 새 기능 = MINOR(0.x.0), 수정 = PATCH(0.0.x). 공모전 1차 제출본 = 0.2.0, 2차 PT 시연본 = 0.3.0 예정.
- 내렸던 버전은 지우지 말고 제목 끝에 [YANKED]를 붙인다.
-->

## [Unreleased]

### Added
- 기억 에이전트 연결: 타임라인 머리의 "어디였지? 물어보세요" → 질문·답·추천 카드(지도·코스) → "이 코스로 안내"로 차량 내비 경유지 안내까지. 주행 중(운전자 주의 분산 제약)에는 타임라인을 감추고 에이전트 주행 화면만 보임. 디버그용 시연 데이터 넣기 수신기 (`app/.../ui/agent/MemoryAgentPort.kt`, `CarDrivingStatePort.kt`, `ui/home/HomeScreen.kt`)
- 기억 에이전트(Rust): 날짜·검색어 규칙으로 기기 안에서 바로 답하는 빠른 길 + 필요할 때만 Qwen3.5-9B 도구 호출(도구 4개, 잘못된 호출 되돌려 주기, 3번 실패하면 멈춤, 안내는 운전자 확인 뒤에만) + 하루 제목 라벨링 (`core-rs/src/agent/`, `app/.../agent/`)
- 기억 코어: 주행·머문 곳을 출근길·퇴근길·볼일·나들이·여행 에피소드로 묶고, 집·회사 추정, AI 하루 라벨링 대기열, 한국어 부분 일치 검색(0.2ms), 코스 최적 순서, 1년치 시연 데이터 (`core-rs/src/memory/`, `core-rs/src/course.rs`)
- 화면 디자인: Pleos 색·글자 크기 토큰, 세로 타임라인 상세, 날짜 목록에 하루 제목·들른 곳, 빈 화면·오류 화면, 새 앱 아이콘 (`app/.../ui/theme/` 외)
- 기억 에이전트용 LLM 서버: Qwen3.5-9B를 llama.cpp로 띄우는 스크립트, 도구 호출 0.5~0.9초 (`scripts/llm/start-llm.ps1`, `docs/70_기억_검색_설계.md`)
- 장소 이름: 머문 곳·출발/도착을 좌표 대신 실제 장소 이름으로 표시. 출처 우선순위 차량 내비 > 카카오 로컬 > OSM, 가까운 지점은 한 장소로 묶어 한 번만 조회 (`core-rs/src/places.rs`, `app/.../place/`)
- 차량 내비 연동(NaviHelper): 목적지·경유지를 기록의 장소 이름으로, 차량 내비 장소 검색 화면 열기(`SearchPlace`)·장소 상세 팝업 (`app/.../pleos/PleosNavi.kt`)
- 지도 핀에 머문 곳 순서 번호·장소 이름, 날짜별 들른 곳 요약 데이터 (`stop_names`)
- 세팅 문서에 Pleos 에뮬레이터를 Android Studio 없이 명령줄(sdkmanager·avdmanager)로 설치하는 방법 추가 (`docs/50_개발환경_세팅.md` 7-2-1)
- 지도: 하루 상세 위에 그날 주행 경로·머문 곳 핀을 MapLibre로 그림. 항목을 누르면 지도에서 강조·이동, 지도를 누르면 항목 선택 (`app/.../ui/map/`)
- 오프라인 지도: 서울·서울→강릉 고속도로·강릉 구간을 기기 안 지도 파일(PMTiles 60MB)과 한글 지명 스타일로 만들고 에뮬레이터에 넣는 스크립트 (`scripts/map/`, `app/src/main/assets/map/`)
- Rust가 하루치 지도 데이터(GeoJSON·화면 범위·항목별 범위)를 만들어 줌 (`core-rs/src/mapdata.rs`)
- 자동 기록: 기어가 P를 벗어나면(또는 시동 ON) 주행을 시작해 Pleos 위치를 10초마다 묶어 저장하고, P로 돌아오면 마감. 앱을 안 열어도 부팅 때부터 동작 (`app/.../record/`, `pleos/`)
- 기록 저장소: Rust SQLite에 위치를 저장하고 주행·머문 곳·날짜별 요약을 증분 계산 (1년치 날짜 목록 0.1ms) (`core-rs/src/store.rs`)
- 타임라인 화면: 날짜별 목록(최신순) + 하루 상세(주행·머문 곳 시간순), 넓은 창은 나란히·좁은 창은 한 장씩, 기록이 바뀌면 자동 갱신 (`app/.../ui/timeline/`)
- 시연 주행 재생: 실제 도로 경로(강릉 하루 코스, 서울→강릉 여행, 서울 짧은 코스)를 에뮬레이터에 재생 (`scripts/replay.ps1`, `scripts/demo-routes/`)
- Android 앱 뼈대: Compose · Hilt · Pleos Maven, minSdk 28. 첫 화면에서 Rust 코어 연결과 하루 경로(점 1만 개) 단순화 시간을 보여 줌 (`app/`)
- Rust 코어를 UniFFI로 Kotlin에 연결: Gradle 빌드가 에뮬레이터·실차용 `.so`와 Kotlin 바인딩을 자동으로 만듦 (`core-rs/src/ffi.rs`)
- 개발 환경 세팅 문서: 설치 구성·버전, 환경 변수, Pleos Maven 설정, 빌드 방법, 검증 결과, 수동 단계 (`docs/50_개발환경_세팅.md`)
- 팀원용 한 번에 설치 스크립트: JDK·Android SDK·NDK·Rust·cargo-ndk 설치·검증, 이미 있으면 건너뜀 (`scripts/setup-dev.ps1`)
- 에뮬레이터 실행 스크립트: cold boot + RAM 파일 안 만들기 + Virtual Console 포트 연결 (`scripts/emu.ps1`)
- 차량 상태 조작 스크립트: 기어·속도·위치를 명령으로 바꿔 주행/정차 전환 재현 (`scripts/vc.ps1`)
- 세팅 문서에 에뮬레이터 실측 동작(앱 영역 크기, 주행 상태, 주행 중 일반 앱 차단, 시동 항목 없음)과 데스크톱 설치 기록 추가 (`docs/50_개발환경_세팅.md`)
- 접수양식 02장 ①~⑥ 기획서 초안 v0 (`docs/00_plan.md`)
- 개발 순서(MVP): 단계별 시연 목표, 자르는 순서, 위험 요소, 데일리 칸 (`docs/00_plan.md`)

### Changed
- 앱 아이콘: Gemini로 그린 "지나온 점선 길 → 위치 핀 + 반짝임" 그림으로 교체(Pleos 둥근 사각형 틀에 맞춤), 알림 아이콘은 따로 분리 (`res/mipmap-*/ic_launcher_foreground.png`, `res/drawable/ic_notification.xml`)
- 장소 이름 찾기를 빠르게: 주행이 끝나면 바로, 요청을 동시에 보내 한 지점 약 50ms. 출발·도착도 가게·명소 이름으로 (예: 광장시장, 경복궁)
- 앱 화면이 두 개 뜨지 않게 MainActivity를 한 개로 유지 (`launchMode=singleTask`)
- 공모전 자료를 `docs/contest/`로 모으고, 백로그를 `docs/00_plan.md`로 이름 바꿔 docs 맨 위에 둠
- CLAUDE.md에 Git 규칙 명시: 작업 전 pull, 큰 작업 단위로 커밋·푸시, AI 기여자 표시 금지
- 계획에서 일정(날짜·스프린트 기간·간트 차트)을 빼고 순서만 남김 (`docs/00_plan.md`, `README.md`)
- GitHub 저장소 이름을 `hmg-app-contest` → `eodiyeotji`로 바꾸고 설명·토픽 정리
- 빈 폴더(`src/`, `presentation/`, `submission/`)에 용도 안내 README를 넣어 저장소에 올라가게 함

### Fixed
- 세팅 문서의 Rust 빌드 안내 수정: `cargo ndk -o app/src/main/jniLibs`로 직접 넣으면 Gradle 결과물과 겹쳐 빌드가 실패하므로, `gradlew assembleDebug` 하나로 빌드하도록 바꿈 (`docs/50_개발환경_세팅.md`)

## [0.1.0] - 2026-11-05

### Added
- 공모전 공식 사이트 전체 내용을 Markdown으로 정리 (`docs/00~15`, 접수양식 원문 `docs/20`)
- Pleos 개발 문서 2,007페이지 미러 (`refs/pleos-docs/`)
- Pleos Connect SDK 전체 API 목록 — 클래스·메서드·권한 (`docs/30_API_전체목록.md`)
- SDK별로 할 수 있는 것 한국어 요약 (`docs/31_API_한눈에보기.md`)
- 평가기준 정리 — 심사 단계·항목·가산점·양식과 평가 항목 대응 (`docs/40_평가기준_정리.md`)
- 10/28 온라인 설명회 다시보기 정리와 자막 (`docs/16_설명회_다시보기_정리.md`)
- 참고자료: Pleos 관련 YouTube 영상 목록·자막, 공식 샘플 저장소 3종, GitHub 저장소 목록 (`refs/youtube/`, `refs/github/`)
- 앱 아이디어 소개 — 기록·기억·공유 구조, 평가 항목별 강점, 로드맵 (`README.md`)
- 앱 이름 확정: 어디였지
- 백로그·스프린트 계획 (`docs/plan.md`)
- 앱 아키텍처 설계 — Rust 코어 + MapLibre + 얇은 Kotlin UI (`docs/60_앱_아키텍처.md`)
- Rust 네이티브 코어 `eodi-core`: 경로 단순화(Douglas-Peucker) (`core-rs/`)
- Rust 코어: 시동 꺼짐 기준 주행 나누기, 10분 이상 머문 곳 감지
- Rust 코어: 공유 코스를 경유지 2개 제한에 맞춰 구간으로 나누기
- AI·팀원 공통 작업 규칙 (`CLAUDE.md`)
- 변경 이력 파일 (`CHANGELOG.md`)

[unreleased]: https://github.com/juyoung020/eodiyeotji/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/juyoung020/eodiyeotji/releases/tag/v0.1.0
