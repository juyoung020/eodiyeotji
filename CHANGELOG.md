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
- 개발 환경 세팅 문서: 설치 구성·버전, 환경 변수, Pleos Maven 설정, 빌드 방법, 검증 결과, 수동 단계 (`docs/50_개발환경_세팅.md`)
- 팀원용 한 번에 설치 스크립트: JDK·Android SDK·NDK·Rust·cargo-ndk 설치·검증, 이미 있으면 건너뜀 (`scripts/setup-dev.ps1`)
- 에뮬레이터 실행 스크립트: cold boot + RAM 파일 안 만들기 + Virtual Console 포트 연결 (`scripts/emu.ps1`)
- 차량 상태 조작 스크립트: 기어·속도·위치를 명령으로 바꿔 주행/정차 전환 재현 (`scripts/vc.ps1`)
- 세팅 문서에 에뮬레이터 실측 동작(앱 영역 크기, 주행 상태, 주행 중 일반 앱 차단, 시동 항목 없음)과 데스크톱 설치 기록 추가 (`docs/50_개발환경_세팅.md`)
- 접수양식 02장 ①~⑥ 기획서 초안 v0 (`docs/00_plan.md`)
- 개발 순서(MVP): 단계별 시연 목표, 자르는 순서, 위험 요소, 데일리 칸 (`docs/00_plan.md`)

### Changed
- 공모전 자료를 `docs/contest/`로 모으고, 백로그를 `docs/00_plan.md`로 이름 바꿔 docs 맨 위에 둠
- CLAUDE.md에 Git 규칙 명시: 작업 전 pull, 큰 작업 단위로 커밋·푸시, AI 기여자 표시 금지
- 계획에서 일정(날짜·스프린트 기간·간트 차트)을 빼고 순서만 남김 (`docs/00_plan.md`, `README.md`)
- GitHub 저장소 이름을 `hmg-app-contest` → `eodiyeotji`로 바꾸고 설명·토픽 정리
- 빈 폴더(`src/`, `presentation/`, `submission/`)에 용도 안내 README를 넣어 저장소에 올라가게 함

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
