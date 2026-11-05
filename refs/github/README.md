# GitHub 참고 저장소 — 현대자동차그룹 차량용 앱 개발 공모전 (Pleos Connect)

수집일: 2026-11-05. `gh search repos`(pleos, Pleos Connect, Pleos Playground, 차량용 앱 공모전 등)와 `gh search code`(`ai.pleos.playground`, `document.pleos.ai`, `appdevchallenge.auto-hmg.io` 등)로 찾고, 모든 저장소는 `gh api`로 존재·메타데이터를 확인했습니다. 별(★)·최근 푸시는 수집 시점 값입니다.

- **SDK 열**: GitHub 코드 검색에서 `ai.pleos.playground.{vehicle,navi,tts,stt,location.fused,llm}` import 또는 `ai.pleos.playground:<모듈>` 의존성이 실제로 검색된 경우만 표시. 비어 있으면 "코드 검색으로 확인 안 됨"이지 "안 씀"은 아닙니다.
- **📁 표시**: `refs/github/<repo-name>/`에 얕은 복제(--depth 1, .git 삭제)해 둔 저장소. 다운로드한 코드는 실행하지 않았습니다(신뢰할 수 없는 외부 코드).
- 공식 Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` — 좌표 예: `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`, `ai.pleos.playground:FusedLocation:1.1.3` (공식 샘플 README 기준).

## 바로 볼 것 (추천 순)

1. **pleos-sample-vehicle-app / -navi-app / -fusedlocation-app** — 공식 샘플 3종. 의존성·권한 선언·초기화/해제 패턴의 정답지.
2. **pleos-playground-api Discussions** — 공식 FAQ(심사 반려 사유, DO, 운전 중 제한, 가로모드 등). `pleos-playground-api/DISCUSSIONS_snapshot.md`에 전문 저장.
3. **WhoisHOO/pleos-app-challenge (Roadio)**, **ian939/ev-fit** — SDK 5종(Vehicle·NaviHelper·FusedLocation·TTS·STT)을 모두 연동한 완성도 높은 출품작 + 제출 요건에 맞춘 README 예시.
4. **panicgit/poopilot** — LLM SDK까지 쓰는 유일한 공개 예제(Hilt로 SDK별 모듈 분리).
5. **leetae9yu/ulw-research** — API별 권한·제공 범위 조사표(단, 10/23 기준 정보 주의).

## A. 공식 저장소 (Pleos-Playground 조직) — 5개

| 저장소 | 소유자 | ★ | 최근 푸시 | SDK(코드검색) | URL | 요약 |
|---|---|---|---|---|---|---|
| pleos-sample-vehicle-app 📁 | Pleos-Playground | 0 | 2026-10-17 | Vehicle | https://github.com/Pleos-Playground/pleos-sample-vehicle-app | [공식] Vehicle SDK 2.3.2 샘플. Door Position을 기준 예제로 capability 확인→읽기→쓰기→구독/해제 패턴을 보여 주고, 19개 도메인(브레이크·EV배터리·HVAC·창문·시트 등) 442개 공개 메서드를 검색하는 카탈로그 화면 포함. Vehicle API 이름·파라미터 찾을 때 1순위. |
| pleos-playground-api 📁 | Pleos-Playground | 6 | 2025-11-25 |  | https://github.com/Pleos-Playground/pleos-playground-api | [공식] Pleos 개발자 지원 채널. 코드는 없고 docs(용어집)와 GitHub Discussions(FAQ·기술 Q&A)가 핵심. 심사 반려 사유, DO(Distraction Optimized), 가로모드, 주행가능거리 조회 등 FAQ가 공모전에 바로 쓸모 있음. 스냅샷을 DISCUSSIONS_snapshot.md로 저장. |
| pleos-sample-navi-app 📁 | Pleos-Playground | 0 | 2026-10-16 |  | https://github.com/Pleos-Playground/pleos-sample-navi-app | [공식] NaviHelper SDK 3.0.5 샘플(NaviDevApp). 목적지+경유지 경로 요청, get/request API 호출, NaviHelperEventListener 콜백 로그 확인. Maven 저장소 nexus-playground.pleos.ai 설정법 포함. |
| pleos-sample-fusedlocation-app 📁 | Pleos-Playground | 0 | 2026-10-16 |  | https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app | [공식] FusedLocation SDK 1.1.3 샘플. isSupported() 확인→권한(pleos.car.permission.FUSED_LOCATION)→최근 위치/연속 업데이트→근접 알림(ENTER/EXIT). adb로 mock location 허용하는 테스트 방법까지 설명. |
| pleos-guide | Pleos-Playground | 0 | 2026-06-12 |  | https://github.com/Pleos-Playground/pleos-guide | [공식] 앱 사용·개발환경 가이드용 저장소로 만들어졌지만 현재 비어 있음(내용 없음). |

## B. 2026 공모전 출품작·준비 저장소 — 104개

| 저장소 | 소유자 | ★ | 최근 푸시 | SDK(코드검색) | URL | 요약 |
|---|---|---|---|---|---|---|
| poopilot 📁 | panicgit | 0 | 2026-04-11 | LLM, NaviHelper, STT, TTS, Vehicle | https://github.com/panicgit/poopilot | 급똥모드: 음성 한마디로 가장 가까운 화장실까지 경로 안내. 카카오·네이버·공공데이터 검색 + NaviHelper 경유지, 도착 시 Vehicle SDK로 운전석 도어 언락, TTS 안내, Gleo AI 브로드캐스트 연동. 유일하게 Pleos LLM SDK까지 쓰는 예제(Hilt DI로 SDK별 모듈 분리). 4월 제작(공모전 이전). |
| ev-fit 📁 | ian939 | 0 | 2026-11-02 | FusedLocation, NaviHelper, STT, TTS, Vehicle | https://github.com/ian939/ev-fit | EV FIT: 결제수단별 실제 충전요금·경로·충전시간을 종합해 충전소 추천. Vehicle(EV 배터리/충전 제어)·NaviHelper(경로·충전 경유지)·FusedLocation·STT/TTS를 Gateway 클래스로 감싼 구조. README가 공모전 제출 요건(한 줄 소개·실행방법·테스트환경·APK 위치) 예시로 좋음. |
| pleos-app-challenge 📁 | WhoisHOO | 0 | 2026-11-05 | FusedLocation, NaviHelper, STT, TTS, Vehicle | https://github.com/WhoisHOO/pleos-app-challenge | Roadio(차창 밖 이야기): 경로를 따라 지나가는 장소 이야기를 자동 재생하는 오디오 도슨트. TTS(차량 온디바이스 엔진)·STT·FusedLocation·NaviHelper·Vehicle(Odometer) 연동, Media3 미디어세션, CarUxRestrictions/DO 처리, 오디오 포커스 처리까지 README·docs에 정리. 제출 APK 포함. |
| autolog-hyundai-2026 | chabinhwang | 0 | 2026-11-04 | FusedLocation, NaviHelper, STT, TTS, Vehicle | https://github.com/chabinhwang/autolog-hyundai-2026 | 차가 쓰는 업무용 승용차 운행일지. Vehicle·NaviHelper(현재 위치 지역명)·FusedLocation·STT/TTS 모두 사용. |
| StoryDrive | soojeongmin | 0 | 2026-10-27 | NaviHelper, STT, TTS, Vehicle | https://github.com/soojeongmin/StoryDrive | 스토리 드라이브: 차량 신호와 내비 경로로 진행되는 오디오 동화, 뒷좌석 아이가 벨트 매고 음성으로 참여. NaviHelper(정체·주의지점)·TTS·STT·Vehicle 사용. |
| running-tale | xxcordeau | 0 | 2026-10-29 | NaviHelper, STT, TTS, Vehicle | https://github.com/xxcordeau/running-tale | 달리는 동화: 실제 주행 신호(회전·도착 등)로 진행되는 뒷좌석 동화. NaviHelper 이벤트·TTS·STT·Vehicle 사용. 저장소 용량 큼(약 140MB). |
| cornernote | tsetse012 | 0 | 2026-10-22 | FusedLocation, TTS, Vehicle | https://github.com/tsetse012/cornernote | 코너노트: 속도(45.6Hz)·조향각·브레이크 신호로 코너별 거친 운전 원인을 짚는 운전 습관 코치. Vehicle 리스너(Brake/Steering/Gear/Energy)·FusedLocation·TTS 사용, 신호처리 코드(가속도 추정)가 상세. |
| gilnun | tsetse012 | 0 | 2026-10-25 | NaviHelper, TTS, Vehicle | https://github.com/tsetse012/gilnun | 길눈: 경치 좋은 코스의 경유지 8곳을 NaviHelper로 한 번에 넣고 도착 시 TTS·창문 연출. 경유지 개수 검증, 네트워크 끊김 시 경로 요청 실패 등 실측 기반 주의사항이 코드 주석에 정리됨. |
| precheck | hyuntaek2002 | 0 | 2026-10-30 | NaviHelper, TTS, Vehicle | https://github.com/hyuntaek2002/precheck | Precheck: 출발 전 차량 점검(경고등 Telltale·엔진오일 등)과 내비 연동 차량 관리 앱. Vehicle·NaviHelper·TTS. |
| dawasseo | ShakeJ | 0 | 2026-10-31 | NaviHelper, STT, Vehicle | https://github.com/ShakeJ/dawasseo | 다 왔어?: 도착 시간에 맞춰 끝나는 차 안 이야기. Vehicle·NaviHelper·STT(안드로이드 기본 인식기 대체 포함). |
| cabincue-pleos | Fiddich-Dev | 0 | 2026-11-04 | STT, TTS, Vehicle | https://github.com/Fiddich-Dev/cabincue-pleos | CabinCue: 정차 중에만 쓰는 발표·말하기 리허설 앱. STT/TTS·Vehicle 모니터링, androidTest로 STT 동작 검증 코드 포함. |
| Android | HanGilLab | 0 | 2026-11-05 | FusedLocation, NaviHelper, Vehicle | https://github.com/HanGilLab/Android | 공모전 출품 Android 프로젝트. FusedLocation·NaviHelper·Vehicle 의존성, settings.gradle에 Pleos Maven 저장소 설정 예. |
| rearguard | tsetse012 | 0 | 2026-10-24 | TTS, Vehicle | https://github.com/tsetse012/rearguard | 리어가드: 뒷문이 열리면 뒷창문 잠금·열선/실내등 끄기 등 뒷자리 케어. Vehicle 제어 + TTS. 숏폼 영상도 있음. |
| feels-good | QuantumNovice | 0 | 2026-11-02 | NaviHelper, Vehicle | https://github.com/QuantumNovice/feels-good | 느좋: 공중화장실·주차·데이트·관광·팝업 장소 찾기. Vehicle(기어·도어)·NaviHelper. |
| buckleair | youngjin9145 | 0 | 2026-11-03 | NaviHelper, Vehicle | https://github.com/youngjin9145/buckleair | 철컥항공: 뒷좌석 아이가 벨트를 매면 이야기 비행기가 이륙, 남은 주행시간에 맞춰 이야기 길이 조절. Vehicle(도어/벨트)·NaviHelper(도착 이벤트). |
| z-ride | HeIIowrld | 0 | 2026-11-04 | NaviHelper, Vehicle | https://github.com/HeIIowrld/z-ride | Z-RIDE: Pleos 협동 게임 서버·API 계약·인프라 + Android 참조 클라이언트. Vehicle(시트 등)·NaviHelper probe 코드. |
| h-k | danmool78 | 0 | 2026-11-04 | NaviHelper, Vehicle | https://github.com/danmool78/h-k | 찰랑: 물컵이 넘치지 않게 운전하면 아이가 멀미하지 않는다는 콘셉트, 조향·브레이크·주행거리 신호로 멀미 누적량 계산. Vehicle·NaviHelper, docs/PLEOS_SDK.md에 SDK 정리. |
| tarang | woominsung1116 | 0 | 2026-11-04 | NaviHelper, Vehicle | https://github.com/woominsung1116/tarang | 타랑: 장롱면허·초보 운전자의 실제 출근/등하원길을 단계별 코스로 연습. 방향지시등·브레이크·경고등·내비 신호 활용. Vehicle·NaviHelper. |
| Spirit-AAOS | bbang208 | 0 | 2026-03-20 | Vehicle | https://github.com/bbang208/Spirit-AAOS | Spirit: Pleos Vehicle SDK와 AAOS CarPropertyManager를 함께 쓰는 앱(DI 모듈 예시). 3월 제작. |
| Novella | CMU02 | 0 | 2026-10-22 | Vehicle | https://github.com/CMU02/Novella | README 없음. Vehicle SDK 사용 코드가 검색됨(내용 미확인). |
| contest_project | tjdus3725 | 0 | 2026-10-24 | NaviHelper | https://github.com/tjdus3725/contest_project | 동행·가족 이동 케어: 쉼터 찾기·위기 상황 도움 연결 시제품. NaviHelper, 카카오 지도 연동 가이드·검증 문서·5분 시연 절차 문서 포함. |
| ConvoyStop | jonik2909 | 0 | 2026-10-24 | Vehicle | https://github.com/jonik2909/ConvoyStop | ConvoyStop: 여러 차량이 함께 이동할 때 정차 지점을 만장일치로 정하는 그룹 서버 + 차량별 내비 프로토타입(iOS 포함). docs/PLATFORM.md에 Pleos 문서 링크 정리. |
| restcar | boseungk | 0 | 2026-10-24 | Vehicle | https://github.com/boseungk/restcar | RestCar: Vehicle SDK 연결과 배터리 잔량 실시간 조회 검증용 Kotlin 시작 프로젝트(Pleos Connect Emulator 2.1.0 기준 환경 정리). |
| senga | khj777 | 0 | 2026-10-26 | NaviHelper | https://github.com/khj777/senga | README 없음. NaviHelper 문서 수집·API 감사 스크립트(arrival-tales/api-audit) 포함. |
| Roadio | joooon28 | 0 | 2026-11-04 | FusedLocation | https://github.com/joooon28/Roadio | Roadio(위치 기반 음성 캡슐 커뮤니티). FusedLocation 1.1.3을 Pleos AVD에서 반경 진입/이탈까지 검증한 기록(DEV-02-validation.md)이 참고할 만함. |
| every-moment-of-pause | xuxtaku7610-del | 0 | 2026-11-05 | STT | https://github.com/xuxtaku7610-del/every-moment-of-pause | 같이가개: 반려견을 태운 보호자용 차량 앱. STT 사용, FGS(포그라운드 서비스) 유형 검토 등 진행 기록(docs/PROGRESS.md). |
| vibetrip-pleos | oocheol | 1 | 2026-10-25 |  | https://github.com/oocheol/vibetrip-pleos | VibeTrip: 차량용 여행 동반자. |
| Hyundai-Pleos-Connect-App | highstar11 | 1 | 2026-10-28 |  | https://github.com/highstar11/Hyundai-Pleos-Connect-App | 플레오스 커넥트 앱 공모전 저장소. |
| HMG-app-Contest | bravenessofdonut | 1 | 2026-11-01 |  | https://github.com/bravenessofdonut/HMG-app-Contest | 현대자동차 앱 개발 공모전 저장소. |
| WiperObservatory | chadaeho | 0 | 2026-10-21 |  | https://github.com/chadaeho/WiperObservatory | 와이퍼 기상대: 와이퍼 신호를 활용한 날씨 관측 콘셉트 앱. |
| gildongmu | Omio04 | 0 | 2026-10-21 |  | https://github.com/Omio04/gildongmu | 길동무: 막힌 길에서 근처 차에 버튼 한 번으로 상황을 묻는 앱. |
| synapse_care | Lainholic | 0 | 2026-10-22 |  | https://github.com/Lainholic/synapse_care | 시냅스케어(SynapseCare) 출품작. |
| odomap | pyeonjaesik | 0 | 2026-10-22 |  | https://github.com/pyeonjaesik/odomap | Odomap: 달린 길이 지도가 되는 드라이브 발자취 앱. |
| softride | softride-app | 0 | 2026-10-22 |  | https://github.com/softride-app/softride | SoftRide: 뒷자리 멀미 예보. |
| pleos_playground_pet | tpgusgh | 0 | 2026-10-23 |  | https://github.com/tpgusgh/pleos_playground_pet | 차 타마고치: 연료·타이어·문/창문·와이퍼·운전습관이 캐릭터 상태가 되는 앱, 개발모드(가짜 신호)/차량연결 모드. |
| odolog | devdany | 0 | 2026-10-23 |  | https://github.com/devdany/odolog | 오도로그: 계기판이 직접 쓰는 법인차 운행기록부. |
| chadak-pleos-app | Mujin8623 | 0 | 2026-10-23 |  | https://github.com/Mujin8623/chadak-pleos-app | 차닥: Pleos 앱 공모전 저장소. |
| appdevchallenge | sj-park-kr | 0 | 2026-10-25 |  | https://github.com/sj-park-kr/appdevchallenge | 공모전 준비 저장소(아이디어 조사·선정 단계, 코드 없음). 심사 기준·SDK 현황 메모. |
| gleo-mafia | data-yeon | 0 | 2026-10-25 |  | https://github.com/data-yeon/gleo-mafia | 수사망(Gleo Mafia): 내비 지도 위 실제 장소에서 음성 추리 게임. |
| Togetherontheroad | hate1403 | 0 | 2026-10-25 |  | https://github.com/hate1403/Togetherontheroad | 플레오스 차량용 앱 공모전 저장소. |
| hmg-app-challenge-2026 | dohoon84 | 0 | 2026-10-26 |  | https://github.com/dohoon84/hmg-app-challenge-2026 | 미리(Miri): 렌터카·카셰어링으로 처음 타는 차의 기능을 묻기 전에 알려 주는 앱. |
| clearview-pleos | justinnn980 | 0 | 2026-10-26 |  | https://github.com/justinnn980/clearview-pleos | 클리어뷰: 홀로그래픽 HUD 시야 복구 앱. |
| mallo | MotoDNA | 0 | 2026-10-26 |  | https://github.com/MotoDNA/mallo | Mallo: 스마트워치 건강신호+차량정보로 피로 감지, 음성으로 차 설정. |
| pleos_pj | tlatlsgksp | 0 | 2026-10-27 |  | https://github.com/tlatlsgksp/pleos_pj | Pleos Connect 차량 밀착형 차계부 앱. |
| pleos-app | SYNC-Dongihwa | 0 | 2026-10-27 |  | https://github.com/SYNC-Dongihwa/pleos-app | 내 차 튜토리얼: 처음 타는 차 기능을 직접 조작하면 차량 신호로 확인. |
| pleos-autography | Kimi-Kimpan | 0 | 2026-10-28 |  | https://github.com/Kimi-Kimpan/pleos-autography | 플레오스 오토그래피: 운전 중 AI 인터뷰어와 음성 대화 → 챕터형 자서전·오디오 에피소드. APK 포함. |
| sumteum | Arcosium | 0 | 2026-10-28 |  | https://github.com/Arcosium/sumteum | 숨틈: 졸음보다 먼저 공기(공조)를 바꾸는 졸음운전 예방 앱. |
| PixelTMI | pixel-tmi | 0 | 2026-10-28 |  | https://github.com/pixel-tmi/PixelTMI | 픽셀 TMI: 차들이 함께 만드는 도로 TMI. |
| car-ask | ai-specialtime | 0 | 2026-10-28 |  | https://github.com/ai-specialtime/car-ask | 차가 알아서 물어봐: 차량 상태를 보고 AI가 대신 가게에 문의. |
| daedongchajido | geunbonGit | 0 | 2026-10-28 |  | https://github.com/geunbonGit/daedongchajido | 대동차지도: D→P 주행 경로 자동 기록, 처음 달린 도로·방문 시군구 집계. |
| goldentime | goldentime-app | 0 | 2026-10-29 |  | https://github.com/goldentime-app/goldentime | 골든타임: 차량용 건강·응급 코파일럿. |
| carpet | xpdlf1004 | 0 | 2026-10-29 |  | https://github.com/xpdlf1004/carpet | 카펫(CarPet): 내 차를 캐릭터로. |
| handleon | ralphpark | 0 | 2026-10-29 |  | https://github.com/ralphpark/handleon | 핸들온: 평소 운전 습관과 비교해 변화를 알려 줌(차량 앱·폰 앱·서버, 숏폼 영상 있음). |
| driver-captions | psm000125-crypto | 0 | 2026-10-29 |  | https://github.com/psm000125-crypto/driver-captions | 운전자 대화 자막(온디바이스 음성 자막) 시제품과 기획서. |
| carsignal-glass-auto | mh09088-byte | 0 | 2026-10-29 |  | https://github.com/mh09088-byte/carsignal-glass-auto | 카시그널: 긴급차량 안내·신호등 타임·정비 + 스마트글라스 콘셉트. |
| first30 | first30-app | 0 | 2026-10-30 |  | https://github.com/first30-app/first30 | 퍼스트30: 사고 직후 30초 동안 차가 먼저 해야 할 일을 알려 주는 앱. |
| hyundai-DQ | leesangwon393 | 0 | 2026-10-30 |  | https://github.com/leesangwon393/hyundai-DQ | 초보운전자 운전을 돕는 퀘스트 서비스. |
| h-rush-game | hmg-app-challenge-2026 | 0 | 2026-10-30 |  | https://github.com/hmg-app-challenge-2026/h-rush-game | H-Rush: 실제 차량 데이터로 맵이 바뀌는 뒷좌석 레인러너 웹게임. |
| appdevchallenge-apk | sj-park-kr | 0 | 2026-10-31 |  | https://github.com/sj-park-kr/appdevchallenge-apk | 차마고찌: 차량 신호에 반응해 자라는 차량 내장 펫 앱(P단에서만 상세 화면). |
| PleosDemo | minjae4650 | 0 | 2026-10-31 |  | https://github.com/minjae4650/PleosDemo | PARKSIDE: Pleos 입력으로 조작하는 Unity 주차 시뮬레이터 시작점. |
| autojangbu | chancec2026-source | 0 | 2026-10-31 |  | https://github.com/chancec2026-source/autojangbu | 계기판 기반 업무용 승용차 운행기록부. |
| Pleos-Car-App-Contest | Chaewon-Park-STUDY | 0 | 2026-10-31 |  | https://github.com/Chaewon-Park-STUDY/Pleos-Car-App-Contest | Pleos Playground SDK·API 기반 공모전 출품작. |
| hmg-charging-planner | Ku-machine365 | 0 | 2026-10-31 |  | https://github.com/Ku-machine365/hmg-charging-planner | 무의식 충전 플래너 핵심 엔진(프로토타입). |
| Win-Win-Challenge | CSJ-PJT | 0 | 2026-11-01 |  | https://github.com/CSJ-PJT/Win-Win-Challenge | 안심동행: 보호자·시설 담당자와 연결해 승하차 안내·대면 인계 확인 지원. |
| OdoLog | hanmin0211 | 0 | 2026-11-01 |  | https://github.com/hanmin0211/OdoLog | 오도로그(OdoLog): 업무용 차량 운행기록 앱. |
| yeongeo-hanip | jige-team | 0 | 2026-11-01 |  | https://github.com/jige-team/yeongeo-hanip | 영어한입: 빨간불에 짧은 영어 표현 말하기. |
| nanwota-pleos | ravenkim | 0 | 2026-11-02 |  | https://github.com/ravenkim/nanwota-pleos | 나눠타: QR 탑승, 좌석·주행거리·실연비로 동승자 기름값 구간별 자동 정산(차량 앱 + 폰). |
| Pleos_Playground | Choi0been | 0 | 2026-11-02 |  | https://github.com/Choi0been/Pleos_Playground | 2026 공모전 출품 저장소. |
| routinegongbang | LcokHcak | 0 | 2026-11-02 |  | https://github.com/LcokHcak/routinegongbang | 루틴공방: 차량 상태에 맞춰 루틴을 안전하게 실행하고 결과 안내. |
| arrival-brief | dbwls99706 | 0 | 2026-11-02 |  | https://github.com/dbwls99706/arrival-brief | 행사 안내문의 세부 목적지를 진입문·주차 브리핑으로 정리. |
| sleep-climate | ubin-star | 0 | 2026-11-02 |  | https://github.com/ubin-star/sleep-climate | 차박·정차 휴식용 자동 실내 온도 관리. |
| pleos-rental-check | neolabs120-mobile | 0 | 2026-11-02 |  | https://github.com/neolabs120-mobile/pleos-rental-check | 카셰어링 인수·반납 점검(차량 신호+소지품 음성 안내). |
| airang-drive | mkbvkz9r6m-lang | 0 | 2026-11-02 |  | https://github.com/mkbvkz9r6m-lang/airang-drive | 아이랑 드라이브: 기어 정보와 보호자 확인 연결. |
| Plain | kodh0206 | 0 | 2026-11-02 |  | https://github.com/kodh0206/Plain | 그날은(Plain): 외부 캘린더 연동·출발 전 일정 브리핑·장소 기반 내비 안내. |
| SafeDrive | jongwon0922 | 0 | 2026-11-02 |  | https://github.com/jongwon0922/SafeDrive | SafeDrive: Pleos App Market 공모전 출품. |
| Pleos-App- | Chaejinn | 0 | 2026-11-02 |  | https://github.com/Chaejinn/Pleos-App- | 플레오스 공모전 저장소. |
| pleos-contest | bucktbox | 0 | 2026-11-03 |  | https://github.com/bucktbox/pleos-contest | 세이프 카고: 깜빡이·부드러운 조향으로 '카고'를 운반하면 정차 시 보상. engine/sdk/ui 모듈 분리, SDK 검증용 poc 앱 별도. |
| pleos | wnwoghd22 | 0 | 2026-11-03 |  | https://github.com/wnwoghd22/pleos | 뿌지직(pzzk): 급한 변의 시 가장 빠른 화장실을 경유지로, 시트·공조·조명·음성으로 버티게 돕는 앱. 서명된 APK 포함. |
| kkaeum | leeshoun888 | 0 | 2026-11-03 |  | https://github.com/leeshoun888/kkaeum | 깨움 KKAEUM: 졸음운전 예방 코파일럿. |
| mosida | planaging | 0 | 2026-11-03 |  | https://github.com/planaging/mosida | 모시다: 주야간보호센터 어르신 등하원 차량용 앱. |
| next-stop | nextstop-submission | 0 | 2026-11-03 |  | https://github.com/nextstop-submission/next-stop | 다음집: 택배 부업용, P→문 열고 닫기→D만으로 배송 완료 처리 후 다음 주소를 내비로 넘김(숏폼 영상 있음). |
| AutoLog | CaligoIra | 0 | 2026-11-03 |  | https://github.com/CaligoIra/AutoLog | 오토로그: 계기판 연동 자동 운행기록부. |
| hyundai-family-carsenger | amecoder | 0 | 2026-11-03 |  | https://github.com/amecoder/hyundai-family-carsenger | 패밀리 카신저: 차가 가족 대화방에 함께 참여. |
| FAXI | saejinpark | 0 | 2026-11-03 |  | https://github.com/saejinpark/FAXI | FAXI: 가족 차를 가족 택시로, 차량 데이터로 요금 계산(차 앱+폰 앱). |
| plugrest | plugrest | 0 | 2026-11-03 |  | https://github.com/plugrest/plugrest | 충전쉼표: 충전 대기 휴식 모드 앱. |
| pleos-app-2026 | Univ-JH | 0 | 2026-11-03 |  | https://github.com/Univ-JH/pleos-app-2026 | 2026 공모전용 Pleos Connect 앱. |
| ThruOrder | IamSeonyeong | 0 | 2026-11-04 |  | https://github.com/IamSeonyeong/ThruOrder | ThruOrder: 드라이브스루 주문 앱. |
| plechu-ev-coach | RickYoon | 0 | 2026-11-04 |  | https://github.com/RickYoon/plechu-ev-coach | 플레츄: 전기차 에너지 코치. |
| peyya | Kkumteul-app | 0 | 2026-11-04 |  | https://github.com/Kkumteul-app/peyya | Peyya: 가족 다중 경유 여행 MVP. |
| SeatFit | suchan1819-hash | 0 | 2026-11-04 |  | https://github.com/suchan1819-hash/SeatFit | SeatFit: 정차 시 시트·조명·음성으로 운전자 스트레칭. |
| shimpyo | yq-team | 0 | 2026-11-04 |  | https://github.com/yq-team/shimpyo | 쉼표: 멈출 때를 아는 차. |
| paircar-vehicle | hyundai-full-accel | 0 | 2026-11-04 |  | https://github.com/hyundai-full-accel/paircar-vehicle | 페어카: Pleos 내비 연동 픽업·카풀 안내. |
| drivelog | hyundai-drivelog | 0 | 2026-11-04 |  | https://github.com/hyundai-drivelog/drivelog | 드라이브 로그. |
| pleos-reservation-assistant | arriveon-lab | 0 | 2026-11-04 |  | https://github.com/arriveon-lab/pleos-reservation-assistant | 차량용 AI 예약 비서 · Android SDK probe + Spring Boot 목 서버. |
| IVE | bear3283 | 0 | 2026-11-04 |  | https://github.com/bear3283/IVE | 현대차 차량용 앱 공모전 저장소. |
| Pleos-App-Development | yang2732781-cmd | 0 | 2026-11-04 |  | https://github.com/yang2732781-cmd/Pleos-App-Development | Pleos 앱 개발 저장소. |
| yeopjari26 | yeopjari-team | 0 | 2026-11-05 |  | https://github.com/yeopjari-team/yeopjari26 | 옆자리: 졸음이 오기 전에 먼저 말을 거는 음성 동승자. |
| pleos-today-stops | jin7744 | 0 | 2026-11-05 |  | https://github.com/jin7744/pleos-today-stops | 오늘 들를 곳: 안내 중인 경로를 유지한 채 약속 장소를 경유지로 넣고 도착 영향 표시. |
| iter-pleos | KimYeongBean | 0 | 2026-11-05 |  | https://github.com/KimYeongBean/iter-pleos | Iter: 하루 주행을 그래프 아트·레이싱 맵으로 기록. |
| roadschool | seunghyeon1004 | 0 | 2026-11-05 |  | https://github.com/seunghyeon1004/roadschool | 로드스쿨: 뒷좌석 아이용 위치 연동 음성 학습 앱(시연 영상 있음). |
| onptt-pleos | onptt | 0 | 2026-11-05 |  | https://github.com/onptt/onptt-pleos | onPTT: 여러 차량을 한 채널로 묶는 그룹 무전 앱. |
| careroute-pleos | minseogiiiii | 0 | 2026-11-05 |  | https://github.com/minseogiiiii/careroute-pleos | CareRoute: 동승자 조건에 맞는 하차·주차 지점 선정과 픽업 연결. |
| majung-vehicle-app | majung-team | 0 | 2026-11-05 |  | https://github.com/majung-team/majung-vehicle-app | 마중: Pleos 차량용 AAOS 앱. |
| pleos-voice-companion | DrivePersona | 0 | 2026-11-05 |  | https://github.com/DrivePersona/pleos-voice-companion | DrivePersona: 차량 음성 동반자. |

## C. 학습·스타터·조사 자료 (Pleos Connect 관련) — 8개

| 저장소 | 소유자 | ★ | 최근 푸시 | SDK(코드검색) | URL | 요약 |
|---|---|---|---|---|---|---|
| ulw-research 📁 | leetae9yu | 0 | 2026-10-28 | NaviHelper, STT, TTS, Vehicle | https://github.com/leetae9yu/ulw-research | 공모전용 조사 보고서: Pleos SDK가 제공하는 차량·내비·음성 데이터 범위(API별 권한·읽기/구독 여부 표)와 졸음·뒷좌석 아이·동승자 관련 현실 문제 정리. 단, 10/23 기준이라 'TTS/STT/FusedLocation 준비 중'으로 적혀 있음 — 10/28 설명회에서는 5종 모두 사용 가능하다고 안내됨. |
| Pleyground | IJLee0812 | 0 | 2026-11-04 | Vehicle | https://github.com/IJLee0812/Pleyground | Pleos Connect 인캐빈 앱 프로토타이핑 놀이터. Vehicle SDK로 Door 조회하는 Hello 예제. |
| smart-dashboard-prototype | jack0928 | 0 | 2026-06-13 |  | https://github.com/jack0928/smart-dashboard-prototype | Pleos Connect/AAOS 학습용 차량 데이터 대시보드(Kotlin, 6월). |
| pleos-simulator | nitocode | 0 | 2026-10-23 |  | https://github.com/nitocode/pleos-simulator | 비공식 웹 기반 차량 인포테인먼트 UI 재현(Ioniq 3 Pleos Connect 등). SDK와 무관한 UI 참고용. |
| PleosTest | LeonBogum | 0 | 2026-10-28 |  | https://github.com/LeonBogum/PleosTest | Pleos Starter: Vehicle SDK로 운전석 도어 위치를 조회하는 최소 Kotlin 앱(AGP 9.2 환경 정리). |
| Pleos-Connect-UI | punch5545 | 0 | 2026-10-28 |  | https://github.com/punch5545/Pleos-Connect-UI | Pleos Connect 스타일 UI 컴포넌트 모음. |
| pv5-meonjeo-allim | yonghwan1106 | 0 | 2026-10-30 |  | https://github.com/yonghwan1106/pv5-meonjeo-allim | PV5 출장 기사용 웹 시제품(Pleos Connect SDK 모양 어댑터). 기아 PBV 아이디어 공모전용. |
| pleos-ideas | cream284 | 0 | 2026-11-02 |  | https://github.com/cream284/pleos-ideas | 공모전 아이디어 검토 문서(태블릿용 정적 HTML). |

## D. 2025년 Pleos Connect SDK 프로젝트 (공모전 이전) — 6개

| 저장소 | 소유자 | ★ | 최근 푸시 | SDK(코드검색) | URL | 요약 |
|---|---|---|---|---|---|---|
| PleosAirWindow | regako | 0 | 2025-05-15 | LLM, NaviHelper, STT, TTS, Vehicle | https://github.com/regako/PleosAirWindow | 2025-05 프로젝트. 코드 검색상 Vehicle·NaviHelper·STT·TTS·LLM SDK 사용. |
| Campus-Safety-Pleos | junexi0828 | 1 | 2025-11-18 |  | https://github.com/junexi0828/Campus-Safety-Pleos | Pleos Connect SDK 기반 스마트 캠퍼스 안전 시스템(2025-11). |
| ai-driving-coach-Used-Pleos-SDK- | keepgrade | 0 | 2025-05-20 |  | https://github.com/keepgrade/ai-driving-coach-Used-Pleos-SDK- | Pleos SDK를 쓴 AI 운전 코치(2025-05). |
| pleosdrivecoach | keepgrade | 0 | 2025-05-21 |  | https://github.com/keepgrade/pleosdrivecoach | pleos drive coach(2025-05). |
| ComposeEx | guen-Kim | 0 | 2025-08-20 |  | https://github.com/guen-Kim/ComposeEx | Jetpack Compose + Pleos Connect SDK 학습(2025-08). |
| PBV | lleejk96 | 0 | 2025-09-26 |  | https://github.com/lleejk96/PBV | PBV Pleos 앱(2025-09). |

## E. 참고: 유럽 Pleos Vehicle Data API (클라우드 API, 이번 공모전 SDK와 별개) — 6개

| 저장소 | 소유자 | ★ | 최근 푸시 | SDK(코드검색) | URL | 요약 |
|---|---|---|---|---|---|---|
| ha-pleos | jouwdan | 22 | 2026-11-04 |  | https://github.com/jouwdan/ha-pleos | (공모전과 별개) 유럽 Pleos 개인 Vehicle Data API용 Home Assistant 통합. 클라우드 차량데이터 API 사례. |
| hyundai-pleos-europe-ha | Thrasher2020 | 16 | 2026-10-14 |  | https://github.com/Thrasher2020/hyundai-pleos-europe-ha | (공모전과 별개) 유럽 Pleos Vehicle Data API 읽기 전용 Home Assistant 통합. |
| pleos-client | jouwdan | 1 | 2026-11-03 |  | https://github.com/jouwdan/pleos-client | (공모전과 별개) Pleos 개인 Vehicle Data API 비동기 Python 클라이언트. |
| Hyundai-Kia-Genesis-Pleos-HA | Marcels-Custom-Coding | 0 | 2026-10-15 |  | https://github.com/Marcels-Custom-Coding/Hyundai-Kia-Genesis-Pleos-HA | (공모전과 별개) 공식 Pleos Vehicle Data API 기반 Home Assistant 통합. |
| kia-ev9-telemetry | michaelKjaer | 0 | 2026-10-17 |  | https://github.com/michaelKjaer/kia-ev9-telemetry | (공모전과 별개) Pleos API로 기아 EV9 텔레메트리 수집. |
| org.openhab.binding.pleos | ifs-net | 0 | 2026-10-18 |  | https://github.com/ifs-net/org.openhab.binding.pleos | (공모전과 별개) openHAB용 Pleos Vehicle Data 바인딩. |

## 제외·미확인

- 설명·README가 비어 있어 내용 확인이 안 된 저장소(이름만 pleos 관련): `devEricY/appdevchallenge-navi-app`, `KangYeSeo04/trace-pleos`, `Ss-Silver/pleoslog`, `changmin123456/pleos`, `gangmin0716/pleos_study`, `johnbr001/pleosapp`, `yunjjuice/pleos-example`, `meofnrk43/pleoszit`, `jemlul/pleos_test` — 표에서 뺐습니다.
- 이름만 비슷하고 무관한 것: Pleo(경비 관리 회사) 채용 과제들, PLEO 로봇, aster-system의 PLEOS 교육 소프트웨어, 테슬라용 Pleos 스타일 계기판(dev-huiya/horizon-cluster), 셔클 타임라인 등.
- 대부분의 출품작 저장소는 ★0, 마감(11/15) 직전이라 계속 바뀌는 중입니다. 다른 참가자의 아이디어·코드는 참고만 하고 그대로 가져다 쓰지 마세요(공모전 독창성·저작권 기준).

> 참고: `ev-fit`, `pleos-app-challenge`, `poopilot`, `ulw-research` 복제본은 라이선스가 없어서 저장소에는 올리지 않고 로컬에만 보관합니다 (`.gitignore`). 위 표의 링크로 원본을 볼 수 있습니다.
