# 사용 가능한 API 한눈에 보기

> 출처: Pleos Playground 개발 문서 https://document.pleos.ai (2026-11-05 수집), 공모전 FAQ·개발환경 페이지
> 메서드 전체 목록(시그니처·권한 포함)은 [30_API_전체목록.md](30_API_전체목록.md), 메서드별 상세 문서와 예제 코드는 `refs/pleos-docs/connect-sdk/`에 있습니다.

## 0. 먼저 알아둘 것

| 항목 | 내용 | 출처 |
|---|---|---|
| 공모전 공식 SDK | **Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation** (5종) | 공모전 FAQ "어떤 SDK와 API를 쓸 수 있나요?" |
| 개발 환경 | Android API 28+, Gradle 8.0+, Java 또는 Kotlin | 공모전 개발환경 페이지 |
| 표준 차량 데이터 | 속도, 기어, 외기 온도, 연료 레벨 같은 표준 데이터는 Pleos SDK가 아니라 **AAOS 표준 Car API**(`CarPropertyManager`, `VehiclePropertyIds`)로 읽음. Pleos SDK는 AAOS에 없는 특화 기능만 제공 | Pleos "SDK API 정책" |
| 차량별 지원 차이 | Vehicle·FusedLocation은 차종에 따라 다름 → `checkXxxCapabilities()` / `isSupported()`로 확인. NaviHelper는 내비 탑재 차량만. TTS·STT는 전체 호환 | Pleos "API 호환성 및 가용성" |
| LLM SDK | 문서는 있지만 **공모전 공식 5종에 없음**. 2026-10-19 SDK 릴리즈 노트에도 없음 → 쓰려면 운영사무국(appdevchallenge@hyundai-partners.com)에 사용 가능 여부 문의 필요 | Pleos 문서 / 릴리즈 노트 |
| 테스트 | Pleos Connect 에뮬레이터 + Vehicle 에뮬레이터로 차량 값 시뮬레이션 가능 | `refs/pleos-docs/guides/connect/getting-started/` |

### SDK 버전 (2026-10-19 릴리즈 노트)
Vehicle v2.3.2 · NaviHelper v3.0.5 · TextToSpeech v3.3.1 · SpeechToText v3.3.1 · FusedLocation v1.1.3

### 규모
| SDK | 패키지 | 클래스/인터페이스 | 멤버 |
|---|---|---|---|
| Vehicle | 13 | 137 | 1,018 |
| NaviHelper | 4 | 48 | 434 |
| SpeechToText | 4 | 7 | 52 |
| TextToSpeech | 3 | 6 | 45 |
| FusedLocation | 3 | 7 | 35 |
| LLM (공식 목록 외) | 4 | 8 | 32 |

---

## 1. Vehicle SDK — 차량 상태 읽기·제어

사용 흐름: `Vehicle(context)` → `initialize()` → `vehicle.getXxx()`로 도메인 객체 → `checkXxxCapabilities()`(지원 여부) / `getXxx()`(현재 값) / `setXxx()`(제어) / `registerXxx()`(값 변경 구독) → `release()`

권한은 모두 `pleos.car.permission.*` (protection level: normal). 읽기는 `CAR_*`, 제어는 `CONTROL_CAR_*`.

| 도메인 (`getXxx()`) | 읽을 수 있는 것 | 제어(set) | 권한 |
|---|---|---|---|
| **Brake** | 브레이크 페달 상태 | – | CAR_DRIVE |
| **CarInfo** | VIN, CRN(차량 식별 정보) | – | CAR_INFO |
| **Display** | 온도 표시 단위 | – | CAR_INFO |
| **Door** | 도어 개폐 위치, 도어 잠금, 글러브박스 잠금 | 도어 잠금, 도어 위치, 글러브박스 잠금 | CAR_DOORS / CONTROL_CAR_DOORS |
| **DrivingMode** | 주행 모드, 크루즈 컨트롤 상태·목표 속도 | – | CAR_DRIVE |
| **EvBattery** | 배터리 충전량(SoC), 전비(순간·현재·누적·충전 후), 충전 상태 | 충전 전류 제한, 충전 % 제한, 충전구 열기, 충전 스위치 | CAR_ENERGY / CONTROL_CAR_ENERGY |
| **Fuel** | 연료 %, 연비(순간·현재·누적·주유 후) | – | CAR_ENERGY |
| **HVAC** | 에어컨, 공기청정, 오토, 내/외기 순환, 제상, 듀얼, 풍향, 풍량, 맥스 AC/제상, 전원, 설정 온도, 실내 온도 | 13개 항목 제어 | CAR_CLIMATE / CONTROL_CAR_CLIMATE |
| **Light** | 앰비언트 라이트(밝기·색상·주행모드 연동), 실내등, 독서등, 상향·하향등, 전·후방 안개등, 미등 | 11개 항목 제어 (앰비언트 색상 등) | CAR_LIGHTS / CONTROL_CAR_LIGHTS |
| **Odometer** | 총 주행거리, 주행가능거리, 트립 거리·시간(현재·누적·주유 후) | 1개 | CAR_DRIVE |
| **Safety** | 브레이크액·엔진오일·변속기오일 레벨, 경고등(Telltale), 에어백, 안전벨트 미착용 경고, 워셔액 부족 경고 | – | CAR_SAFETY |
| **Seat** | 시트 위치(등받이·높이·전후·쿠션·헤드레스트·요추), **탑승 여부**, **안전벨트 착용**, 열선·통풍 | 열선, 통풍 | CAR_SEATS / CONTROL_CAR_SEATS |
| **SideMirror** | 사이드미러 열선 | 열선 | CAR_MIRRORS / CONTROL_CAR_MIRRORS |
| **SteeringWheel** | 조향각, 핸들 열선 | 열선 | CAR_DRIVE, CAR_CLIMATE |
| **Tire** | 타이어 공기압 저하 | – | CAR_SAFETY |
| **Toll** | 하이패스(ETCS) 음성 안내 볼륨 | 볼륨 | CAR_TOLL / CONTROL_CAR_TOLL |
| **TurnSignal** | 방향지시등 상태 | – | CAR_LIGHTS |
| **Window** | 창문 위치·잠금, 블라인드, 스마트 글래스(투명도) | 창문 위치·잠금, 스마트 글래스 | CAR_WINDOWS / CONTROL_CAR_WINDOWS |
| **Wiper** | 와이퍼 상태·스위치 | 와이퍼 스위치 | CAR_WINDOWS / CONTROL_CAR_WINDOWS |

- 관련 가이드: `refs/pleos-docs/connect-sdk/Vehicle/` (project-setup, get-property, set-property, callback, check-capability, vehicle-permission, vehicle-error-code)
- 튜토리얼: `refs/pleos-docs/guides/pleos-only/tutorial/vehicle-sdk-sample-app.md`
- 제어(set) API는 앱마켓 심사에서 권한 사용을 따로 검토함: `refs/pleos-docs/guides/pleos-only/distribute/app-review-guide/review-functionality-performance-stability.md` → "Vehicle SDK 퍼미션 심사 기준"

## 2. NaviHelper SDK — 내비게이션 연동

내비 앱이 탑재된 차량에서 경로·목적지·주행 정보를 주고받습니다.

| 분류 | API |
|---|---|
| 경로 요청 | `requestRoute`, `requestRouteWithWaypoints`, `requestReRoute`, `cancelRoute`, `changeRouteOption`(추천/최단시간/최단거리/고속도로/무료/편한길/어린이보호구역) |
| 경유지 | `addWaypoint`, `removeWaypoint`, `getWaypointInfo` |
| 정보 조회 | `getCurrentLocationInfo`, `getDestinationInfo`, `getRouteStateInfo`, `getTBTInfo`(턴바이턴), `getBookmarkInfo`(집·회사·EV충전소), `getRecentDestinationInfo`, `getChargerOperatorInfo` |
| 트립 계산 | `requestTrip` → `TripInfo`(거리, ETA, 에너지 소비, **도착 시 예상 배터리 %**, 교통 상황) |
| 화면 전환 | `requestNavigationScreen`(경로 개요, 주변 충전소, POI 상세, 즐겨찾기 등) |
| 이벤트 (`NaviHelperEventListener`) | 경로 시작·완료·취소·재탐색, 목적지·경유지 도착, 현재 위치, **도로 제한속도**(매초), **과속 카메라 알림**, **주의 구간**(사고다발·낙석·안개·과속방지턱 등), 교통 정체·사고 정보, TBT 안내, 주행 정보 |

- 가이드: `refs/pleos-docs/connect-sdk/NaviHelper/` (Routes, Destinations, Waypoints, Event listeners 등)

## 3. FusedLocation SDK — 위치

| API | 설명 |
|---|---|
| `isSupported` | 차량에서 지원하는지 확인 |
| `getLastKnownLocation` | 마지막 위치 |
| `registerFusedLocationCallback` / `unregister…` | 위치 업데이트 구독 (`LocationData`: 위도, 경도, 고도, heading, 정확도, 시각) |
| `addProximityAlert` / `removeProximityAlert` | **지오펜스**: 특정 지점 반경 진입·이탈(ENTER/EXIT) 알림 |
| `showLocationAccessBlockedPopup` | 위치 권한 차단 시 팝업 |

## 4. SpeechToText SDK — 음성 인식

| API | 설명 |
|---|---|
| `initialize`, `registerApp`, `release` | 초기화·앱 등록(서버 모드) |
| `request` / `stop` | 인식 시작·중지 |
| `sendAudio`, `completeAudioSend` | 직접 오디오 데이터 전달 |
| `setLocale` | 언어 설정 |
| 모드 | `ON_DEVICE` / `SERVER` / `HYBRID` |
| `ResultListener` | onReady, onStartedRecognition, onUpdated(중간 결과), onEndedRecognition, onUpdatedRms(음량), onError |

## 5. TextToSpeech SDK — 음성 출력

| API | 설명 |
|---|---|
| `speak` / `stop` | 문장 읽기·중지 |
| `setLocale`, `setSpeakerType` | 언어, 화자(목소리) |
| `setAudioAttributes`, `setAudioZoneId` | 오디오 속성, **좌석별 출력 구역** |
| 모드 | `ON_DEVICE` / `SERVER` / `HYBRID` |
| `EventListener` | onReady, onStart, onDone, onStop, onError, onUpdatedRms |

## 6. (참고) LLM SDK — 공모전 공식 목록 외

| API | 설명 |
|---|---|
| `startChat`, `sendMessage` | 멀티턴 대화 |
| `generateContent` | 단발 생성 |
| `setModelParameter` | MODEL_NAME, TEMPERATURE, MAX_TOKENS |
| 모드 | `ON_DEVICE` / `SERVER` |

> 공모전 FAQ의 공식 SDK 목록과 최신 SDK 릴리즈 노트에 없으므로 기획서의 핵심 기능으로 쓰기 전에 사용 가능 여부를 먼저 문의하세요.

---

## 7. 개발할 때 같이 봐야 하는 가이드 (`refs/pleos-docs/guides/`)

| 주제 | 파일 |
|---|---|
| 개발 시작·에뮬레이터 | `connect/getting-started/application-development/` (setup-connect-sdk-emulator, configure-pleos-connect-emulator, use-vehicle-emulator) |
| 프로젝트 생성 | `pleos-only/create-my-project.md`, `pleos-only/getting-started.md` |
| 주행 중 제한(Driving Restriction) | `pleos-only/vehicle-app-planning-guide/driving-restiction-policy.md`, `pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide.md` |
| 운전자 주의 분산 | `pleos-only/vehicle-app-planning-guide/driver-distraction-guideline.md` |
| 앱 카테고리·화면 구조 | `pleos-only/vehicle-app-planning-guide/app-category.md`, `app-structure-screen-model.md` |
| 오디오 포커스 / 음악 앱 / 내비 앱 | `pleos-only/vehicle-app-developer-guide/` |
| 로터리 컨트롤러(CCP) | `pleos-only/vehicle-app-developer-guide/ccp-rotary-controller-development.md` |
| UI 디자인 가이드 | `connect/docs-design/` (컴포넌트, 컬러, 타이포, 주행 화면) |
| 앱마켓 심사 기준 | `pleos-only/distribute/app-review-guide/` |
