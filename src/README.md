# src/

앱 코드는 아래 위치에 있다 (`CLAUDE.md`, `docs/60_앱_아키텍처.md` 기준).

| 위치 | 내용 |
|---|---|
| `core-rs/` | Rust 네이티브 코어 (eodi-core) |
| `app/` | Android 앱 (Kotlin + Compose) — 뼈대 단계에서 생성 예정 |
| `server/` | LLM 요약 · 코스 공유 서버 — 2차 단계 |

이 폴더는 위에 속하지 않는 보조 스크립트(시연 데이터 생성 등)를 둘 때 쓴다.
