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
- 자동 기록: 기어가 P를 벗어나면(또는 시동 ON) 주행을 시작해 Pleos 위치를 10초마다 묶어 저장하고, P로 돌아오면 마감. 앱을 열지 않아도 부팅 때부터 동작 (`app/.../record/`)
- 기록 저장소: Rust SQLite에 위치를 저장하고 주행·머문 곳·날짜별 요약을 증분 계산, 1년치 날짜 목록 0.1ms (`core-rs/src/store.rs`)
- 타임라인: 날짜별 목록(하루 제목·들른 곳) + 하루 상세(주행·머문 곳 시간순). 넓은 창은 나란히, 좁은 창은 한 장씩, 기록이 바뀌면 자동 갱신 (`app/.../ui/timeline/`)
- 지도: 하루 상세 위에 그날 경로와 머문 곳 핀(순서 번호·이름)을 MapLibre로 그림. 항목을 누르면 지도가 그곳으로, 지도를 누르면 항목 선택. 그릴 데이터는 Rust가 만듦 (`app/.../ui/map/`, `core-rs/src/mapdata.rs`)
- 오프라인 지도: 서울·서울→강릉 고속도로·강릉 구간 지도 파일(PMTiles 60MB)과 한글 지명 스타일, 에뮬레이터에 넣는 스크립트 (`scripts/map/`)
- 장소 이름: 머문 곳·출발·도착을 좌표 대신 실제 가게·명소 이름으로(예: 광장시장, 경복궁). 출처 우선순위 차량 내비 > 카카오 로컬 > OSM, 한 지점 약 50ms, 가까운 지점은 한 장소로 묶어 한 번만 조회 (`core-rs/src/places.rs`, `app/.../place/`)
- 차량 내비 연동(NaviHelper): 목적지·경유지 이름을 기록에 반영, 코스 안내(경유지 2개씩 구간), 내비 장소 검색 화면·장소 상세 열기 (`app/.../pleos/PleosNavi.kt`)
- 기억: 주행·머문 곳을 출근길·퇴근길·볼일·나들이·여행으로 묶고 집·회사를 추정, 한국어 부분 일치 검색(0.2ms), 코스를 지금 위치에서 가장 짧은 순서로 (`core-rs/src/memory/`, `core-rs/src/course.rs`)
- 기억 에이전트: "1년 전에 간 바다 어디였지?"처럼 물으면 날짜·검색어 규칙으로 기기 안에서 바로 답하고, 필요할 때만 로컬 LLM(Qwen3.5-9B)이 도구를 골라 씀. 안내는 운전자가 "응"이라고 한 뒤에만. 하루 제목은 LLM이 하루 한 번 붙임 (`core-rs/src/agent/`)
- 기억 에이전트 화면: 타임라인 위 "어디였지? 물어보세요" → 답 + 추천 카드(그날 지도·들른 곳) → "이 코스로 안내"로 차량 내비 경유지 안내. 주행 중에는 타임라인을 감추고 큰 카드 한 장만 보임 (`app/.../ui/agent/`, `ui/home/`)
- 정차한 곳 대표 사진(구글 지도 사진): 지도 위 핀마다 네모 사진 상자, 타임라인 머문 곳과 추천 카드의 들른 곳마다 작은 사진, 누르면 그곳 사진(최대 6장)을 넘겨 봄, 올린 사람 표시. 결과는 기기에 30일 저장하고, 구글 키는 Places API에만·하루 한도 안(무료 범위)으로 묶음 (`core-rs/src/photos.rs`, `app/.../place/`, `ui/place/`, `ui/map/MapPhotoPins.kt`)
- 화면 디자인: Pleos 색·글자 크기·간격 토큰, 빈 화면·오류 화면, 앱 아이콘(지나온 점선 길 → 위치 핀 + 반짝임)과 알림 아이콘 (`app/.../ui/theme/`, `res/`)
- Android 앱 뼈대: Compose · Hilt · Pleos Maven, minSdk 28. Rust 코어를 UniFFI로 연결하고 Gradle이 에뮬레이터·실차용 `.so`와 Kotlin 바인딩을 자동으로 만듦 (`app/`, `core-rs/src/ffi.rs`)
- 개발 도구: 한 번에 설치(`scripts/setup-dev.ps1`), 에뮬레이터 실행(`emu.ps1`), 기어·속도·위치 조작(`vc.ps1`), 실제 도로 경로 재생(`replay.ps1`, `demo-routes/`), LLM 서버 실행(`scripts/llm/start-llm.ps1`), 디버그 빌드용 1년치 시연 기록 넣기
- 개발 환경 세팅 문서: 설치 구성·버전, Android Studio 없이 명령줄로 에뮬레이터 설치, 빌드 방법, 에뮬레이터 실측 동작(주행 중 일반 앱 차단, 시동 항목 없음 등) (`docs/50_개발환경_세팅.md`)
- 기억·검색·에이전트 설계 문서 (`docs/70_기억_검색_설계.md`)
- 기획서 v1(업무 모드, 실제 방문 기준 장소 순위)과 공식 접수양식(docx) 옮김, 개발 순서(MVP)와 위험 요소 (`docs/00_plan.md`, `submission/`)
- README 첫머리에 에뮬레이터 시연 GIF: 질문 → 그날 코스 카드 → 들른 곳 사진 → 차량 내비 경유지 안내 (`docs/images/demo.gif`)
- 팀원 소통 폴더: 작업 보고·질문·같이 정할 것을 이슈 대신 파일로 남김 (`docs/소통/`)

### Changed
- README: 팀명 Car-pe Diem(카-르페디엠) 확정, 검토 항목 R1~R7 반영
- 공모전 자료를 `docs/contest/`로 모으고, 백로그를 `docs/00_plan.md`로 이름 바꿔 docs 맨 위에 둠
- 계획에서 일정(날짜·스프린트 기간·간트 차트)을 빼고 순서만 남김 (`docs/00_plan.md`, `README.md`)
- CLAUDE.md 작업 규칙: 작업 전 pull, 큰 작업 단위로 커밋·푸시, AI 기여자 표시 금지, GitHub 이슈는 버그·문의용, 네트워크는 쓰되 빠르게·막히지 않게
- GitHub 저장소 이름을 `hmg-app-contest` → `eodiyeotji`로 바꾸고 설명·토픽 정리
- 빈 폴더(`src/`, `presentation/`, `submission/`)에 용도 안내 README를 넣어 저장소에 올라가게 함

### Fixed
- 세팅 문서의 Rust 빌드 안내: `cargo ndk -o app/src/main/jniLibs`로 직접 넣으면 Gradle 결과물과 겹쳐 빌드가 실패하므로 `gradlew assembleDebug` 하나로 빌드하도록 바꿈 (`docs/50_개발환경_세팅.md`)

### Security
- 개인정보가 들어간 제출용 양식(`submission/*_개인정보*`)과 Office 임시 잠금 파일(`~$*`)이 저장소에 올라가지 않게 함 (`.gitignore`)

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

[Unreleased]: https://github.com/juyoung020/eodiyeotji/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/juyoung020/eodiyeotji/releases/tag/v0.1.0
