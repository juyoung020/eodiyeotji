# 오프라인 지도 (`scripts/map`)

앱은 지도를 인터넷 없이 그린다 (CLAUDE.md 4번: 네트워크를 기다리지 않는다). 지도는 두 부분이다.

| 부분 | 위치 | git | 크기 |
|---|---|---|---|
| 지도 타일 `korea-demo.pmtiles` (시연 지역만) | 만들기 `scripts/map/out/` → 기기 앱 폴더 | 올리지 않음 | 60.5 MB (최대 줌 15) |
| 스타일 · 글꼴 · 아이콘 | `app/src/main/assets/map/` (APK 안) | 올림 | 1.1 MB (파일 670개, 글꼴 1.0 MB) |

## 바로 쓰기

```powershell
powershell -ExecutionPolicy Bypass -File scripts\map\build-map.ps1    # 타일 만들기 (인터넷 필요, 약 1분)
powershell -ExecutionPolicy Bypass -File scripts\map\push-map.ps1     # 실행 중인 에뮬레이터의 앱 폴더로 보내기
```

- `build-map.ps1`: pmtiles 도구(go-pmtiles 1.31.2, SHA-256 확인)를 `scripts/map/tools/`에 받고, 시연 경로로 영역을 만들고, Protomaps 일일 빌드에서 그 영역만 잘라 `scripts/map/out/korea-demo.pmtiles`를 만든 뒤 `pmtiles verify` · `show`로 확인한다. 다시 돌리면 덮어쓴다.
  - `-MaxZoom 14` (작게), `-BufferKm 2` (경로 띠 좁게), `-Date 20261105` (빌드 날짜 고정) 옵션은 스크립트 맨 위 도움말 참고.
  - 끝에 `style/generate.mjs --check`로 지도 안 라벨 글자가 앱 글꼴 범위에 다 들어 있는지 확인한다. 모자라면 아래 "스타일 다시 만들기".
- `push-map.ps1`: 지금 화면 사용자(Pleos 에뮬레이터는 **사용자 10**)의 앱 폴더로 보내고 SHA-256을 비교한다. 앱을 다시 설치할 필요 없다.

## 시연 지역

`build-map.ps1`이 `scripts/demo-routes/legs/*.csv`(시연 주행 경로)에서 영역을 만든다 → 경로를 바꾸면 다시 돌리기만 하면 된다.

- 경로 띠: 모든 시연 경로를 따라 2 km마다 한 변 8 km 정사각형 (경로 양옆 4 km). 잠실 → 영동고속도로 → 횡성휴게소 → 대관령 → 강릉 구간 전체
- 서울 사각형: 경도 126.68–127.25, 위도 37.38–37.75 (서울 전역 + 김포공항 · 과천 · 성남 · 하남 · 구리)
- 강릉 사각형: 경도 128.78–129.00, 위도 37.68–37.87 (강릉역 · 경포 · 초당 · 안목 · 강릉 시내)
- 줌 0–15 모두 들어 있다. 낮은 줌(넓게 본 지도)은 한반도 · 세계가 보이고, 높은 줌은 위 영역 밖이 비어 있다.

| 영역 | 최대 줌 | 타일 수 | 크기 |
|---|---|---|---|
| 서울 시내 + 경로 양옆 3 km + 강릉 시내 | 14 | 1,285 | 23.8 MB |
| 같은 영역 | 15 | 4,536 | 48.4 MB |
| **지금 기본값** (위 표의 넓은 사각형 + 경로 양옆 4 km) | **15** | **6,199** | **60.5 MB** |

Protomaps 원본의 최대 줌이 15다. 앱은 15보다 더 확대하면 15 타일을 키워 그린다.

## 기기 안 위치 (앱과의 약속)

앱은 아래 순서로 찾는다.

1. `getExternalFilesDir(null)/map/korea-demo.pmtiles` = `/storage/emulated/<사용자>/Android/data/app.eodiyeotji/files/map/`
2. `filesDir/map/korea-demo.pmtiles` = `/data/user/<사용자>/app.eodiyeotji/files/map/`

`push-map.ps1`이 보내는 방법:

| 화면 사용자 | 방법 | 들어가는 곳 | 조건 |
|---|---|---|---|
| 0 | `adb push`로 바로 | 1번 (외부 앱 폴더) | 없음 |
| 10 (Pleos 에뮬레이터) | `/data/local/tmp`에 올린 뒤 `run-as app.eodiyeotji --user 10`으로 복사 | 2번 (앱 내부 폴더) | **디버그 빌드(debuggable)** 가 설치돼 있어야 함 |

- `adb shell`은 사용자 0으로 돌아서 `/storage/emulated/10/...`이 보이지 않는다 (Permission denied). Pleos 에뮬레이터는 user 빌드(`ro.debuggable=0`)라 `adb root`도 안 된다. 그래서 사용자 10에는 `run-as`가 유일한 길이다.
- 앱을 지우면(uninstall) 2번 폴더도 지워진다 → 다시 `push-map.ps1`. 덮어쓰기 설치(`installDebug`, `install -r`)는 파일이 남는다.
- 지우기: `push-map.ps1 -Remove`

## 스타일 약속 (`app/src/main/assets/map/style.json`)

| 항목 | 값 |
|---|---|
| 지도 소스 이름 | `protomaps` (type `vector`) |
| 소스 url | 자리표시자 문자열 `"{{PMTILES_URL}}"` → 앱이 실행 중에 `pmtiles://file://<절대 경로>`로 바꾼다 |
| `glyphs` | `asset://map/fonts/{fontstack}/{range}.pbf` |
| `sprite` | `asset://map/sprites/light` (`light.json` · `light.png` · `light@2x.json` · `light@2x.png`) |
| 글꼴 이름 (`text-font`) | `NotoSans-Regular`, `NotoSans-Medium`, `NotoSans-Italic` (공백 없음) |
| 라벨 언어 | 한국어 한 줄: `name:ko` → (한글 이름이면) `name` → `name:en` → `name` |
| 한글 글자 간격 | 이름 라벨은 한글이면 `text-letter-spacing` -0.08em (아래 설명) |
| 레이어 | 시연용(demo) 33개: `background`, `earth`, `landcover`, `landuse_park`, `water`, `roads_highway` · `roads_major` · `roads_link` · `roads_minor`, `roads_rail`, `boundaries`, `roads_labels_*`, `roads_shields`, `pois`, `places_*` … 앱 데이터 레이어는 없다 → 앱이 경로 · 머문 곳을 맨 위에 더한다 |
| 출처 표시 | 소스 `attribution`에 들어 있다 (MapLibre 출처 표시 버튼에 나온다) |

### 시연용(demo) 스타일이 빼고 바꾼 것

Protomaps light 전체(71개 레이어)는 Pleos 에뮬레이터에서 지도를 끌 때 약 20fps였다 (렌더 스레드 CPU 한계). 차 화면에 필요한 것만 남겼다.

- 뺀 것: 건물, 자잘한 땅 쓰임새(병원 · 학교 · 공업 · 놀이터 등), 산림 · 풀밭 다각형(공원 · 국립공원 · 보호구역 · 골프장 · 묘지만 남김), 보행로 · 서비스 도로, 테두리(차선 테두리는 고속도로 · 큰길만), 일방통행 화살표, 번지, 작은 개울
- 합친 것: 터널 · 다리 레이어 19개를 빼고 일반 도로 레이어가 그 구간도 그린다 (영동고속도로 터널 구간이 끊기지 않는다)
- 줄인 것: 골목길(`roads_minor`)은 줌 14부터, 골목 이름은 16부터, 장소 아이콘(`pois`)은 역 · 공항 · 항구 · 해변 · 국립공원 · 산 · 경기장 · 대학만
- 브라우저 MapLibre GL JS로 같은 화면을 그려 비교한 값 (앱 fps가 아니라 대리 지표):

| 위치 · 줌 | 전체(71개) 그리기 호출 / 정점 색인 | 시연용(33개) |
|---|---|---|
| 서울 시청 z14.5 | 262 / 571k | 124 / 121k |
| 잠실 z15 | 442 / 211k | 188 / 43k |
| 강릉 z14 | 383 / 244k | 206 / 68k |
| 영동고속도로 z13 | 124 / 49k | 71 / 9k |

전체 스타일이 필요하면 `node generate.mjs --flavor full` (레이어 · 글꼴 범위를 모두 그에 맞게 다시 만든다). 덜어낼 목록은 `generate.mjs` 위쪽 `DEMO_*` 설정에 있다.

### 글자

- 한글 · 한자 · 가나는 앱이 MapLibre `localIdeographFontFamily`(기기 글꼴)로 그린다. 그래서 글꼴 파일에는 그 글자가 없다.
- MapLibre Native(Android)는 기기 글꼴로 그린 한 자의 폭을 글꼴과 상관없이 1em으로 고정한다 (`LocalGlyphRasterizer` advance 24). 에뮬레이터 화면에서 재 보니 한글 잉크 폭이 약 0.78em이라 간격 0이어도 넓어 보였다 (원본 동 이름은 여기에 0.1em을 더 벌렸다). 그래서 한글 이름 라벨은 -0.08em으로 둔다. 더 좁히거나 넓히려면 `generate.mjs`의 `HANGUL_LETTER_SPACING` 하나만 바꾼다.
- 글꼴 범위 파일이 하나라도 없으면 MapLibre Native는 그 범위 글자가 든 타일을 끝내 그리지 못한다 (`GlyphManager::processResponse`가 오류일 때 기다리는 타일을 풀어 주지 않음. 브라우저 GL JS에서도 타일 한 칸이 통째로 비는 것을 확인함). 그래서 `generate.mjs`는
  - 글자가 거의 없는 작은 범위 파일(8 KB 이하, 한글 · 한자 범위 포함)은 전부 넣고,
  - 지도 파일(`korea-demo.pmtiles`)의 모든 지물에 스타일의 필터 · 라벨 식을 직접 계산해 실제로 그려질 글자의 범위를 넣는다 (최대 줌 타일은 줌 22까지 확대해 쓰이는 것까지),
  - MapLibre가 바꿔 그리는 글자도 넣는다: 길 따라 놓인 라벨이 세로로 서면 문장부호를 세로쓰기 글자로 바꾸고(예: `램프-A`의 `-` → U+FE32, 범위 65024-65279), 아랍 문자는 표현형(U+FB50–FEFF)으로 바꾼다.
- 레이어 id는 Protomaps 생성기가 정하며, 같은 패키지 버전(`@protomaps/basemaps` 5.7.2 고정)이면 바뀌지 않는다.

## 스타일 다시 만들기

```powershell
cd scripts\map\style
npm ci                              # @protomaps/basemaps 5.7.2, @maplibre/maplibre-gl-style-spec 24.4.1 (스타일 검사 · 식 계산)
node generate.mjs                   # 시연용 스타일로 app/src/main/assets/map/ 을 다시 만든다 (인터넷 필요, 지도 파일이 있으면 그 글자 범위를 반영)
node generate.mjs --flavor full     # Protomaps light 전체(71개 레이어)로 다시 만든다
node generate.mjs --check           # 인터넷 없이 확인만: 지금 assets 의 style.json 이 쓸 글꼴 범위가 다 있는가
node generate.mjs --flavor full --style-only --out 다른곳.json   # 스타일 JSON 만 따로 (비교용, assets 는 그대로)
```

글꼴 · 아이콘은 `protomaps/basemaps-assets` 커밋 `028c18f7`에서 받는다 (`generate.mjs` 위쪽에 고정). 지도 파일을 넓히거나 새 빌드로 바꾸면 `--check`가 모자란 범위를 알려 준다.

## 데이터 출처 · 라이선스

| 무엇 | 출처 | 라이선스 |
|---|---|---|
| 지도 데이터 | © OpenStreetMap contributors (Protomaps 일일 빌드 https://build.protomaps.com/ 경유, 2026-11-05 빌드) | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/) — 앱 화면에 출처 표시 필수 |
| 타일 구조 · 스타일 | [Protomaps basemaps](https://github.com/protomaps/basemaps) | 코드 BSD-3-Clause, 디자인 CC0 |
| 글꼴 | Noto Sans (protomaps/basemaps-assets, © The Noto Project Authors) | SIL Open Font License 1.1 |
| 아이콘 | protomaps/basemaps-assets 스프라이트 (Mapzen tangrams/icons 파생) | MIT |
| pmtiles 도구 | [protomaps/go-pmtiles](https://github.com/protomaps/go-pmtiles) (만들 때만 씀, 배포 안 함) | BSD-3-Clause |

라이선스 원문은 APK 안에 같이 들어간다: `assets/map/NOTICE.txt`, `LICENSE-protomaps-basemaps.md`, `fonts/OFL.txt`, `sprites/LICENSE-tangrams-icons.md`.

**앱 화면에 표시할 문구:** `© OpenStreetMap contributors · Protomaps` (스타일 소스 `attribution`과 같은 내용. MapLibre 출처 표시를 켜 두면 자동으로 나온다)
