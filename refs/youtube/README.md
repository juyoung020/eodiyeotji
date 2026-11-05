# YouTube 참고 영상 — 현대자동차그룹 차량용 앱 개발 공모전 (Pleos Connect)

수집일: 2026-11-05. YouTube 검색(플레오스 커넥트, Pleos Connect, Pleos Playground, Pleos SDK, 앱 개발 공모전 설명회, 해시태그 #HMG차량용앱개발공모전 / #PleosAppMarket / #HMG앱공모전 등)과 공식 홈페이지·Pleos 공식 채널을 직접 확인해 모았습니다. 모든 영상은 영상 페이지를 열어 제목·채널·게시일을 확인했습니다. 날짜는 YouTube 게시일입니다.

- **자막 파일(.txt)**: yt-dlp가 없어서 YouTube 자막 트랙을 직접 받아 `[mm:ss] 문장` 형식으로 저장했습니다. "수동"은 업로더가 올린 자막, "자동"은 자동 생성 자막이라 오타가 있을 수 있습니다.
- 공식 홈페이지: https://appdevchallenge.auto-hmg.io/ · Pleos Playground: https://pleos.ai/playground · 개발 문서: https://document.pleos.ai

---

## 1. 공모전 공식 설명회·소식 (3)

| 제목 | 채널 | 날짜 | URL | 왜 보면 좋은가 |
|---|---|---|---|---|
| **현대자동차그룹 차량용 앱 개발 공모전 통합 온라인 설명회** (42분) | agency_ | 2026-10-29 | https://youtu.be/na6tSJ44pWc | 10/28 온라인 설명회 **다시보기 원본**(공식 홈페이지 스크립트에 포함된 링크). 제출 방법(APK는 이메일 첨부 말고 GitHub `apk/` 폴더나 Releases, README에 앱 이름·한 줄 소개·실행 방법·테스트 환경), 심사 기준(차량 특화성·창의성·기술 활용성·사업성 + 가산점), 숏폼 챌린지(가산점 없음, 1팀 50만 원), Q&A(생성형 AI 사용 가능하지만 PT에서 직접 설명해야 함, 실차 시연 없음 → 에뮬레이터로 검증, 외부 API·공용 네트워크 사용 가능, 공모전 개발 중에는 SDK 권한 승인 없이 매니페스트 선언만으로 5개 SDK 모두 사용 가능)까지 전부 나옴. 설명 란에 타임스탬프 있음. → 자막: `2026-10-28_온라인설명회_다시보기.txt` (수동) |
| [현대자동차·기아] 현대차ㆍ기아, 차량용 앱 개발 공모전 개최｜26.10.21. | 모빌리티인박스 | 2026-10-20 | https://youtu.be/BbN_6nAUL30 | 그룹 보도자료를 AI 음성으로 요약한 55초 영상. 공모전 개요(대상·접수 기간·12월 시연 평가)를 빠르게 확인용. |
| [숏페이퍼] 내 아이디어가 실제 차에 들어간다? 현대차·기아 공모전 총정리 | 숏페이퍼 | 2026-10-22 | https://youtu.be/CJmShKaquUo | 뉴스 요약 쇼츠. 내용은 보도자료 수준이라 참고 정도. |

## 2. 숏폼 챌린지 출품작 (#HMG차량용앱개발공모전 / #PleosAppMarket / #HMG앱공모전) (16)

다른 참가자들이 어떻게 문제 상황 → 차량 맥락 → 앱 해결을 1분 안에 보여 주는지 참고용. 대부분 "Pleos Connect 에뮬레이터 실화면 + AI 연출 장면"을 섞고, 그 사실을 설명란에 밝힘.

| 제목 | 채널 | 날짜 | URL | 요약 |
|---|---|---|---|---|
| 로드스쿨 시연 영상 — HMG 차량용 앱 개발 공모전 (Pleos App Market) | Fox's After-School_Lab | 2026-11-04 | https://youtu.be/t3vJy4xiwxs | 뒷좌석 아이용 위치 연동 음성 학습 앱. 주행 중 화면 OFF / 서행 시 새 질문 멈춤 / P에서만 지도 ON 같은 운전자 안전 3단계 설계를 잘 보여 줌(웹 프로토타입, SDK 호출은 시뮬레이션). 같은 이름의 저장소: seunghyeon1004/roadschool |
| 스몰톡톡 내향인을 위한 가장 안전한 대화 연습실 | 스몰톡톡 | 2026-11-04 | https://youtu.be/CEC_IwDtP2E | 혼자 타는 출퇴근 차 안을 스몰토크 연습 공간으로. 문제 정의(내향형 직장인)를 짧게 잘 잡은 예. |
| HMG차량용앱개발공모전_부릉탐험대 | Mikang | 2026-11-03 | https://youtu.be/GjIGYnot6tg | 주행을 레벨·칭호·지역 도감으로 쌓는 게이미피케이션 + 키즈모드. 오픈 지도/행정구역 데이터 라이선스 표기 예시도 있음. |
| 아빠가 차에 남긴 한마디, 10년 뒤 아들이 들었다 | DriveSpin | 2026-11-03 | https://youtu.be/QIE3QeQTUvA | 남은 주행거리로 갈 수 있는 축제를 돌림판으로 고르고, 그날의 한마디·사진을 차가 기억. 한국관광공사 공공데이터 활용. |
| 레전드 육아꿀팁 스토리드라이브 찍먹 영상 | (채널명 없음) | 2026-11-02 | https://youtu.be/Wndjz3isqI4 | 차량 신호·내비 경로로 진행되는 오디오 동화(스토리 드라이브). 같은 이름의 저장소: soojeongmin/StoryDrive (동일 팀인지는 미확인) |
| 우리의 무대는 계속되어야 한다 / CarStage | CarStage | 2026-11-02 | https://youtu.be/zM3hpqTZjd8 | 차 안 노래방·노래 대결 앱, 정차 중에만 동작. "영상 속 앱 화면은 Pleos 에뮬레이터 실제 화면"이라고 명시. |
| 차가 기억하는 평소의 내 운전, 핸들온 | 핸들온 | 2026-10-31 | https://youtu.be/HRmg4CpGp7I | 평소 운전 패턴과 달라지면 먼저 알려 주는 앱. 이야기 장면은 AI(Google Flow), 내레이션은 합성 음성이라고 표기. 같은 이름의 저장소: ralphpark/handleon |
| 다음집 — 문 닫고 D 넣으면, 다음 집까지 차가 챙깁니다 | 다음집 | 2026-10-30 | https://youtu.be/Rp0s1o_s3lo | 택배 부업: P → 문 열고 닫기 → D 만으로 배송 완료, 다음 주소를 내비로. 차량 신호(기어·도어)를 핵심 UX로 쓰는 좋은 예. 같은 이름의 저장소: nextstop-submission/next-stop |
| 32초로 보는 차량용 게임 / Bustbound for Pleos | SW비전공자 국진맨 | 2026-10-30 | https://youtu.be/BUz4qhIOm78 | 후석용 카드 게임, D 전환 시 운전석 게임 잠금 → P 복귀 재개 시연. adb 터치 위치 표시 등 촬영 방식 설명이 꼼꼼함. |
| Bustbound for Pleos / 스테이지 1 클리어·콜렉션·주행 잠금 시연 (2분 29초) | SW비전공자 국진맨 | 2026-10-30 | https://youtu.be/ShEZvtTrzmc | 위 숏폼의 전체 시연판(에뮬레이터 실제 플레이). |
| 차 안에서도 하고 싶은 게임 / Bustbound for Pleos | SW비전공자 국진맨 | 2026-10-29 | https://youtu.be/LBwYzNWc_6U | 같은 팀의 숏폼 챌린지 참여 영상. |
| 차 안에서 귀로 듣고 말로 푸는 수학 이야기 게임! 스토리매스 | 안상현 | 2026-10-28 | https://youtu.be/7VHlK50CPBg | 화면 없이 듣고 말로 답하는 수학 이야기 게임. Pleos STT로 2지선다 응답, 완전 오프라인. 음성 중심 UX 참고. |
| [차량용 앱 공모전] 길눈 — 경치 좋은 길을, 경유지째로 내비에 | 김수빈 | 2026-10-25 | https://youtu.be/2YD0zToNxcM | 경유지 8곳을 NaviHelper로 한 번에 넣고 도착 시 음성·창문 연출. "에뮬레이터 + 차량 시뮬레이터 화면 녹화, 합성 아님"이라고 밝힘. 같은 이름의 저장소: tsetse012/gilnun |
| 리어가드 — 뒷자리를 먼저 챙기는 차 | 김수빈 | 2026-10-24 | https://youtu.be/Fj7tbc3JILI | 뒷문 열리면 뒷창문 잠금·열선/실내등 끄기 등. 설명란에 소스·APK 링크(tsetse012/rearguard). |
| 운전자는 자기 운전이 얼마나 거친지 모른다 / 코너노트 | 김수빈 | 2026-10-22 | https://youtu.be/_Hvj39J5mhU | 속도(초당 45.6회)·조향각·브레이크로 코너별 원인과 처방. "폰 GPS로는 안 되는 것"을 강조해 차량 특화성을 어필하는 방식이 참고됨. 같은 이름의 저장소: tsetse012/cornernote |
| 부모님은 늘 "괜찮다"고만 하십니다 / 케어로드 | 빡과장 | 2026-10-21 | https://youtu.be/y_duyNuuc9w | 차가 먼저 부모님께 안부를 묻고 응답이 없을 때만 가족에게 알리는 아이디어(41초). |

## 3. Pleos Playground·SDK 개발자 세션 (Pleos 공식 채널, Pleos 25 개발자 컨퍼런스) (10)

2025-04 Pleos 25 행사 세션 녹화. SDK 버전은 지금과 다를 수 있으니 API 세부는 document.pleos.ai와 공식 샘플로 다시 확인할 것.

| 제목 | 채널 | 날짜 | URL | 왜 보면 좋은가 |
|---|---|---|---|---|
| **Pleos Connect SDK** / Pleos 25 (40분) | Pleos | 2025-04-18 | https://youtu.be/5-AjSKLBW3Y | SDK를 만든 이유(차량에서는 Google Play 서비스의 fused location 등을 못 씀)와 In-vehicle·Companion·Cloud SDK 구성, SDK별 설명과 샘플. 공모전 기술 활용성 준비에 가장 직접적. → 자막: `pleos25_pleos-connect-sdk.txt` (수동) |
| **Gleo AI SDK** / Pleos 25 (38분) | Pleos | 2025-04-18 | https://youtu.be/WTP-wqC-PyY | 차량 AI 에이전트(Gleo AI) 연동 SDK 세션. 음성·LLM 기능을 앱에 붙일 아이디어 참고. → 자막: `pleos25_gleo-ai-sdk.txt` (수동) |
| **Connect Application: From Development to Deployment** / Pleos 25 | Pleos | 2025-04-18 | https://youtu.be/RWFIEbB3lYA | 개발 → 테스트 → App Market 등록·심사·배포 흐름. → 자막: `pleos25_connect-app-development-to-deployment.txt` (수동) |
| **What to Know Before Porting to Pleos Connect** / Pleos 25 | Pleos | 2025-04-18 | https://youtu.be/0hb94WVKQiM | 모바일 앱을 차량용(AAOS)으로 옮길 때 제약(운전 중 제한, 화면, 권한 등). → 자막: `pleos25_porting-to-pleos-connect.txt` (수동) |
| Pleos Connect: Unlocking New Opportunities for Developers / Pleos 25 | Pleos | 2025-04-18 | https://youtu.be/LG7XssVIYKQ | 외부 개발자에게 열린 Pleos Connect 생태계·App Market 소개. → 자막: `pleos25_opportunities-for-developers.txt` (수동) |
| Shorten In-Car Experience Design Process Without Compromising Quality / Pleos 25 | Pleos | 2025-04-18 | https://youtu.be/Z6rXsbDxDTU | 차량 UX 디자인 프로세스. 화면 설계·주행 중 UX 고민에 참고. → 자막: `pleos25_in-car-ux-design-process.txt` (수동) |
| Pleos Vehicle OS API: Quick Start Guide / Pleos 25 | Pleos | 2025-04-18 | https://youtu.be/11yiGXbRKfQ | Vehicle OS(차량 제어 쪽) API 퀵스타트. 공모전 SDK(Pleos Connect SDK)와는 층위가 다르지만 차량 데이터 구조 이해에 도움. → 자막: `pleos25_vehicle-os-api-quickstart.txt` (수동) |
| Keynote Live / Pleos 25 (78분) | Pleos | 2025-04-18 | https://youtu.be/E_DxpnmVDUc | Pleos 브랜드·SDV 전략·App Market 비전 전체 키노트. 기획서의 "사업성/생태계" 논리 근거로 쓸 만함. → 자막: `pleos25_keynote.txt` (수동) |
| Pleos Playground : Learn, Connect, Build! / Pleos 25 | Pleos | 2025-04-18 | https://youtu.be/dDncCuCxPR0 | Pleos Playground 소개 영상(음악 위주라 자막 없음). |
| Pleos Playground film | Pleos | 2025-04-18 | https://youtu.be/cVR_bRRtGWI | Playground 개발자 포털 홍보 영상. → 자막: `pleos-playground-film.txt` (자동) |

같은 채널의 다른 Pleos 25 세션(차량 데이터·Vehicle OS·자율주행 위주, 공모전과는 거리가 있음): Unlocking the Potential of Vehicle Data(https://youtu.be/rn-pioZz_zk), Leveraging Vehicle Data for Safety, Autonomy, and Business(https://youtu.be/VxlO8Mdq7_8), Decoupling Hardware and Software via Vehicle HAL(https://youtu.be/8SB08cmn1qQ), Pleos Vehicle OS Security(https://youtu.be/1MRgXPsZMaA) 등 — 채널: https://www.youtube.com/channel/UCJocw43qnxZ6h-6gNdo7RwA

## 4. Pleos Connect 제품·UX·Gleo AI (공식) (9)

내 앱이 들어갈 "환경"(대화면/슬림 디스플레이, 내비, 음성 에이전트)을 이해하는 용도.

| 제목 | 채널 | 날짜 | URL | 왜 보면 좋은가 |
|---|---|---|---|---|
| **SDV 시대의 시작, 차세대 인포테인먼트 Pleos Connect 미디어데이** (40분) | 현대자동차그룹(HYUNDAI) | 2026-04-29 | https://youtu.be/HZ4yq4aP8gc | Pleos Connect 최초 공개 행사 전체. 비전·디자인·내비·Gleo AI·앱 마켓·향후 계획. → 자막: `pleos-connect-mediaday.txt` (자동) |
| 직관성과 안전성을 모두 고려한 Pleos Connect UX 설계 포인트 | 현대자동차그룹(HYUNDAI) | 2026-04-29 | https://youtu.be/DArYvPjliLA | 대화면/슬림 디스플레이로 주행 정보와 앱 영역 분리, 터치+물리 버튼. 앱 화면 배치·운전 중 조작 설계 근거. |
| 복잡함은 줄이고, 편리함을 높인 Pleos Connect 내비게이션 | 현대자동차그룹(HYUNDAI) | 2026-04-29 | https://youtu.be/0bnvu7WKsAs | 기본 내비 구조와 AI 에이전트 음성 검색. NaviHelper로 연동할 대상 이해. |
| 차량과 대화하는 경험, Pleos Connect Gleo AI의 핵심 기능은? | 현대자동차그룹(HYUNDAI) | 2026-04-29 | https://youtu.be/Y_SmZx1s7BM | LLM 기반 Gleo AI가 복합 요청을 순차 처리하는 모습. → 자막: `pleos-connect-gleo-ai.txt` (수동) |
| Pleos Connect Introduction Film (Kor.) | Pleos | 2026-05-15 | https://youtu.be/tJi6JTYrkgw | 공식 소개 필름(6분). → 자막: `pleos-connect-intro-film.txt` (수동) |
| Pleos Connect Introduction Film (Eng.) | Pleos | 2026-05-15 | https://youtu.be/njCB815F5Ek | 위 영상 영어판. |
| 42dot Work&Way / 개발자가 일하는 방식 - Gleo AI 편 | 현대자동차그룹(HYUNDAI) | 2026-04-29 | https://youtu.be/zNvcGgYTsqc | Gleo AI 개발자 인터뷰. 기술 배경 참고. |
| 차량용 인포테인먼트 시스템이 바꾸는 이동의 경험 / 현대진행형 EP.15 (26분) | 현대자동차그룹(HYUNDAI) | 2026-03-12 | https://youtu.be/LTKtYbBjEno | 오비고(차량용 앱 개발사) 대표 등이 나오는 인포테인먼트 팟캐스트. 차량 콘텐츠·커넥티드 서비스의 사업성 관점. |
| [현대자동차] 플레오스 커넥트 OTA 업데이트｜26.10.30. | 모빌리티인박스 | 2026-10-29 | https://youtu.be/l16nxCVcnQY | 보도자료 AI 요약. Gleo AI에 경로 기반 장소 검색·문자 읽기 추가 등 최신 기능 변화. |

## 5. Pleos App Market (4)

| 제목 | 채널 | 날짜 | URL | 왜 보면 좋은가 |
|---|---|---|---|---|
| 스마트폰의 사용 경험을 구현한 Pleos Connect 앱 마켓 | 현대자동차그룹(HYUNDAI) | 2026-04-29 | https://youtu.be/TK3sZiq3fp0 | 네이버 지도·유튜브·스포티파이 등 앱 마켓 구성과 외부 개발사 확장 계획. 공모전 슬로건 "Code your App into Pleos App Market"의 배경. |
| 픽클 설치 가이드 / Pleos App Market / 현대·기아·제네시스 | 오비고 | 2026-09-14 | https://youtu.be/tUgIfaJbITg | 실제 서드파티 앱이 App Market에 올라가 설치되는 모습(음악/오디오 카테고리). 카테고리·설치 흐름 참고. |
| 기아 / PBV 솔루션 / 2. Pleos App Market | 기아 (Kia Korea) | 2026-03-19 | https://youtu.be/gReOnquckfY | PBV(업무용 차)에서 업무 앱을 App Market으로 내려받는 시나리오. B2B 앱 아이디어 참고. |
| 현대차 'PLEOS'. 자동차용 앱마켓에는 어떤 인포테인먼트 컨텐츠가 어울릴까? | PDFMAN | 2025-04-02 | https://youtu.be/gJwDTRWuF9M | 개인 채널 의견 영상(2분). 앱마켓에 맞는 콘텐츠 유형 브레인스토밍 정도. |

## 6. 플레오스 커넥트 앱 아이디어 공모전 (2026년 6~7월, 부산모빌리티쇼) (3)

| 제목 | 채널 | 날짜 | URL | 왜 보면 좋은가 |
|---|---|---|---|---|
| [현대자동차] 현대차, 플레오스 커넥트 앱 최우수 고객 제안 아이디어 공개｜26.10.01 | 모빌리티인박스 | 2026-10-12 | https://youtu.be/7PpvAhZKZLM | 대상작 '창밖동화'(차량 위치 + 생성형 AI로 경로·지역 맞춤 동화·퀴즈) 소개. 이번 공모전에서도 "뒷좌석 아이 + 위치 기반 이야기" 계열 출품이 많다는 점과 겹침 — 차별화 필요. |
| 플레오스 커넥트와 전동화 라인업으로 만나는 현대자동차의 현재와 미래 / 2026 부산모빌리티쇼 | 현대자동차그룹(HYUNDAI) | 2026-06-28 | https://youtu.be/vPG9Oa_kkeo | 아이디어 공모전이 열린 부산모빌리티쇼 현대차 부스 영상. |
| 현대 플레오스 커넥트 소개 #부산모빌리티쇼 #앱마켓 | 9민선생 | 2026-06-26 | https://youtu.be/i4HqzGxVX2c | 부산모빌리티쇼 현장 Pleos Connect·앱마켓 체험 1분 영상(개인 채널). |

아이디어 공모전 결과 원문: https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-pleos-connect-app-best-customer-idea

## 7. HMG 사내 앱 해커톤 (2026년 8월, '24H 코드 레이스') — 영상 없음

YouTube에서 관련 영상을 찾지 못했습니다(검색어: 24H 코드 레이스, 현대차 해커톤 테일싱크 등). 뉴스룸 기사만 있음: https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-24h-code-race-hackathon (8/20~21 판교, 25개 팀, 대상 '테일싱크' — 차량용 인터랙티브 오디오 스토리). 검색에 걸린 "HMGICS 현대 해커톤"(싱가포르 혁신센터)은 이 해커톤과 무관해서 뺐습니다.

## 8. 실사용 리뷰 (선택, 실제 화면·반응 파악용) (4)

| 제목 | 채널 | 날짜 | URL | 왜 보면 좋은가 |
|---|---|---|---|---|
| Hyundai Pleos Connect DEMO Complete Walkthrough (16분) | KondorCars | 2026-10-03 | https://youtu.be/BTbE2jsI0ws | 투싼 Pleos Connect 전체 메뉴 시연(영어). 실제 화면 크기·앱 런처 구조 확인. |
| Testing Hyundai's ALL-NEW Pleos Connect in the Grandeur Facelift! (15분) | KoreanCarBlog TV | 2026-09-16 | https://youtu.be/oL-nLqB_yL4 | 그랜저 17인치 화면·안드로이드 기반 OS 실사용 테스트(영어). |
| 플레오스가 제대로 바뀌나? 6가지 바뀐 플레오스 OTA 업데이트 (13분) | 유자차 개러지 | 2026-10-27 | https://youtu.be/qb64wyMENyo | 최신 OTA 변경점과 사용자 불만 포인트 → 사용자가 아쉬워하는 부분에서 앱 아이디어를 찾을 수 있음. |
| 현대차 플레오스 실물 충격! 차 안에서 AI, 게임, 노래방, 앱스토어까지! (29분) | 김한용의 MOCAR | 2025-03-28 | https://youtu.be/6f7ecnebcyY | Pleos 25 현장 부스 투어(앱마켓·플러그앤플레이·파트너사 부스). |

---

### 메모
- 설명회 다시보기(https://youtu.be/na6tSJ44pWc)는 공식 홈페이지 스크립트에 들어 있는 유일한 YouTube 링크이고(첫 화면 "YouTube 다시보기" 팝업), 영상 설명도 "통합 온라인 설명회 다시보기"이며, 공식 공지 "온라인 설명회 안내"(10/28 14:00, 라이브 1회 후 다시보기 제공)와 일치합니다.
- 숏폼 출품작은 해시태그 페이지·검색에 노출된 것만 모은 것이라 전부는 아닐 수 있습니다(마감 11/15 전까지 계속 늘어날 것).
- Pleos Connect 일반 리뷰 쇼츠(그랜저·아반떼 실내 소개 등)는 수십 개 있지만 앱 개발과 관련이 약해 뺐습니다.
