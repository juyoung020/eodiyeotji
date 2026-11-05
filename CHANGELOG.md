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

### Changed
- 공모전 자료를 `docs/contest/`로 모으고, 백로그를 `docs/00_plan.md`로 이름 바꿔 docs 맨 위에 둠

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

[unreleased]: https://github.com/juyoung020/hmg-app-contest/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/juyoung020/hmg-app-contest/releases/tag/v0.1.0
