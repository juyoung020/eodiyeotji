# Pleos-Playground/pleos-playground-api Discussions 스냅샷 (2026-11-05 수집)

원본: https://github.com/Pleos-Playground/pleos-playground-api/discussions — 외부 게시물을 그대로 옮긴 참고자료입니다.


---

## #12 [Announcement] 문의 시 정보보안 주의사항 안내

- 작성일: 2025-11-21
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/12

# 문의 작성 시 정보보안 주의사항

>[!note]
>공개된 Discussion 공간에 문의를 작성할 때 반드시 확인해야 하는 기본 보안 안내입니다.
특정 정보는 외부에 공개될 경우 보안 사고, 제3자 악용, OEM·파트너사 정책 위반으로 이어질 수 있습니다.


---

## 1. 게시하면 안 되는 정보

아래 정보는 **절대 게시해서는 안 됩니다.**

> **본문, 댓글, 첨부파일, 이미지, 캡처, 코드 어디에도 포함되면 안 됩니다.**

### 🔐 인증·보안 관련

- 애플리케이션 Secret (`client_secret`)
- Access Token, Refresh Token 등 **유효한 인증 토큰 전체**
- 내부 시스템 구성, 운영 로그 전체, 내부 설정 값 등 외부에 공개되어서는 안 되는 정보

### 👤 개인·계정 정보

- 이름, 이메일, 전화번호 등 **모든 개인정보**
- 계정 ID, 로그인, 지불 수단, 주문 정보 관련 정보
- 사용자 식별값 등 외부 노출 시 계정 접근 가능성이 있는 정보

### 🚗 차량·OEM 대외비 정보

- 차량번호, VIN, CRN 등 외부 노출 시 인증정보 접근 가능성이 있는 정보
- 출시 전 차량 코드, 차량 내부 개발 코드명
- OEM에서 비공개로 관리하는 기술 정보
- 외부 공개가 제한된 차량·모빌리티 관련 기밀 데이터

### ⚠️ 기타 공개 시 악용될 수 있는 정보

- 인증 절차와 직접 연결되는 모든 값
- 운영 환경의 URL 또는 접근 포인트 중 외부 공개가 부적절한 정보

---

## 2. 금지 정보 노출 시 발생할 수 있는 조치

> **참고**: 아래 조치는 확정 프로세스는 아닙니다, 보안을 위해 이루어질 수 있는 조치범위입니다.

### 일반 조치

- 게시물의 일부 또는 전체가 관리자에 의해 **숨김 처리 또는 삭제**될 수 있음

### Secret이 노출된 경우

- 관리자가 해당 Secret을 **만료 처리 후 갱신**할 수 있음
- 만료 처리 시 기존 연동이 **일시적으로 중단**될 수 있음

### 토큰이 노출된 경우

- 보안 위험을 방지하기 위해 해당 토큰이 **무효화**될 수 있음

> 💡 **안내**  
> 운영·보안을 위한 조치는 상황에 따라 달라질 수 있으며,  
> 정보 유출은 서비스 이용자·OEM·파트너사 모두에게 위험을 초래할 수 있습니다.

---

## 3. 제한적으로 제공 가능한 정보

단독으로는 민감도가 낮은 정보는 필요 시 요청에 따라 **최소 범위에서 제공**할 수 있습니다.

> [!WARNING]
> 문제 해결과 직접 관련 없는 값은 제공하지 않는 것이 원칙입니다.

---

## 4. 첨부파일·스크린샷 업로드 전 점검사항

업로드 전 반드시 다음을 확인하세요.

- [ ] 로그에 Secret, Token, 사용자 정보, 차량 코드 등이 포함되지 않았는가?
- [ ] 스크린샷에 민감한 값이 노출되어 있지 않은가?
- [ ] 텍스트 파일·CSV·스프레드시트 등에 OEM 대외비 정보가 포함되어 있지 않은가?
- [ ] 마스킹하거나 제거해야 할 정보가 남아 있지 않은가?

---

## 5. 수정 요청을 받은 경우

관리자로부터 **"민감정보가 포함되어 있습니다"**라는 안내를 받은 경우:

1. 게시글에서 해당 내용을 **삭제**
2. 저장 후 우측 상단의 **`edited`** 클릭
3. 수정 이력 중 **민감정보가 노출된 이전 버전 삭제**

> [!IMPORTANT]
> GitHub는 수정 이력을 기본적으로 보관하므로,  
> 본문만 수정해도 과거 이력에 정보가 남아 있을 수 있습니다.

---

## 📝 요약 체크리스트

문의 작성 전, 아래 항목을 확인하세요:

- [ ] Secret, Token 등 인증 정보가 포함되지 않았는가?
- [ ] 개인정보 또는 계정 정보가 포함되지 않았는가?
- [ ] 차량 코드, OEM 대외비 정보가 포함되지 않았는가?
- [ ] 첨부파일/이미지에 민감정보가 없는가?
- [ ] 운영 환경의 URL 등이 노출되지 않았는가?

---

**문의 작성 시 보안을 함께 생각해주세요. 감사합니다.** 🙏


---

## #13 [FAQ] 앱을 등록하려면 어떤 자료가 필요하나요? / What is required to submit an app?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/13

A. 앱 패키지 파일(.apk), 앱 아이콘/스크린샷, 카테고리, 설명, 개인정보 처리방침 URL, 고객센터 정보 등이 필요합니다.

---

A. Required : APK file, app icon/screenshots, category, description, privacy policy URL, and customer support information.


---

## #14 [FAQ] 심사에는 얼마나 걸리나요? / How long does the review take?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/14

A. 평균 7영업일 소요되며, 앱 특성(보안/법적 검증)에 따라 추가 시간이 발생할 수 있습니다.

--- 

A. Average of 7 business days. Additional time may be required depending on security or legal checks


---

## #15 [FAQ] 심사에서 자주 반려되는 사유는 무엇인가요? / What are common reasons for app rejection?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/15

A. 차량 내 안전 규정 위반(주행 중 영상 재생 등) ▸ 불필요한 권한 요청 ▸ 개인정보 처리방침 미비 ▸ 저작권 위반 가능성 등이 있습니다.

---

A. Violations of in-vehicle safety regulations (e.g., video playback while driving) /Excessive or unnecessary permission requests / Missing privacy policy / Possible copyright violations


---

## #16 [FAQ] 기존 앱을 업데이트하려면 어떻게 하나요? / How do I update an app?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/16

A. App Market Console(https://appmarket.pleos.ai/console)에서 새 버전을 업로드하고, 변경사항(릴리즈 노트)을 작성하면 됩니다. 심사 통과 후 자동 업데이트됩니다.

---


A. Upload a new version in the App Market Console. Once reviewed and approved, the update is automatically released Console (https://appmarket.pleos.ai/console)



---

## #17 [FAQ] 긴급 패치는 가능한가요? / Are emergency patches supported?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/17

A. 보안/안정성 이슈일 경우 우선 심사가 가능합니다.

---

A. Priority review is available for security or stability issues.


---

## #18 [FAQ] 앱에서 광고 삽입이 가능한가요? / Can apps include advertisements?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/18

A. 가능하지만 ▸주행 중 방해 요소 최소화 ▸영상 광고 자동 재생 금지 ▸음성 안내 방해 금지 등의 조건이 있습니다.

---

A. Yes, but must comply with: Minimal driver distraction / No auto-play video ads/ Must not interfere with voice guidance.


---

## #19 [FAQ] 어떤 API를 제공하나요? / What APIs are available?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/19

A. 차량용 계기판 UI, 음성 인식, 네트워크 사용량, 위치정보 등을 SDK 형태로 제공합니다.

---

A. We provide vehicle data, speech recognition, fused location, network usage, and more through various SDK packages.


---

## #20 [FAQ] 디자인 관련 제약사항이 있나요? / Are there any design constraints that apply to in-vehicle apps?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/20

A. 디자인 관련하여서는 가이드를 제공하고 있습니다. (https://pleos.ai/playground/resources/docs/pleos-only/docs-design-pleos/intro)
구글에서 제공하는 디자인 가이드를 참고하실수 있습니다 (https://developers.google.com/cars/design/automotive-os?hl=ko)

---

A. Yes. Please follow the official design guidelines above. (https://pleos.ai/playground/resources/docs/pleos-only/docs-design-pleos/intro)



---

## #21 [FAQ] DO(Distraction Optimized)란 무엇인가요? / What is DO (Distraction Optimized)?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/21

A. 운전 중 차량이 이동 상태가 되면, 대부분의 일반 앱 액티비티는 UX 제한 상태(User Experience Restricted state) 하에서 자동으로 실행이 차단됩니다. 하지만 Distraction Optimized로 태그된 액티비티는 제한 상태에서도 실행할 수 있습니다. 즉, DO는 운전 중에도 HUD(헤드 유닛)에서 계속 실행될 수 있는 안전 인증 역할을 합니다.

---


A. When the vehicle is in motion during driving, most standard app activities are automatically blocked from running under the User Experience Restricted state. However, activities tagged as Distraction Optimized can run even under this restricted state. In other words, DO serves as a safety certification that allows it to continue running on the HUD (Head-Up Display) even while driving.



---

## #22 [FAQ] 운전 중 사용 불가 앱/기능은 무엇인가요? / What apps/features are restricted while driving?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/22

A. 
- 영상/멀티미디어: 주행 중에는 시청/조작 불가. 
> 동영상 스트리밍 앱 (예: YouTube, Netflix 등)
> 게임 앱
> 카메라 뷰어, 사진 갤러리
- 텍스트 입력/메시징: 운전 중에는 텍스트 입력 UI 차단. 음성 입력(ASR)이나 읽어주기(TTS)는 허용됨.
> 키보드 입력이 필요한 메신저/채팅 앱
> 이메일 작성 화면
> 소셜 네트워크 앱의 글쓰기 화면
- 웹 브라우저
> 일반적인 브라우저 앱 (예: Chrome, Firefox 등)
> 문서 뷰어(긴 텍스트 탐색)

--- 

- Video/Multimedia: Viewing/operating prohibited while driving.
> Video streaming apps (e.g., YouTube, Netflix, etc.)
> Gaming apps
> Camera viewer, photo gallery
- Text Input/Messaging: Text input UI blocked while driving. Voice input (ASR) or read-aloud (TTS) permitted.
> Messaging/chat apps requiring keyboard input
> Email composition screen
> Social network app writing screens
- Web Browser
> General browser apps (e.g., Chrome, Firefox, etc.)
> Document viewers (long text navigation)

Translated with [DeepL.com](https://www.deepl.com/?utm_campaign=product&utm_source=web_translator&utm_medium=web&utm_content=copy_free_translation) (free version)



---

## #23 [FAQ] 테스트 환경(Playground)은 제공되나요? / Is a Playground test environment available?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/23

A. 개발자 계정 등록 후 Emulator 환경에서 시뮬레이션할 수 있습니다.

--- 
A. Yes. After registering a developer account, you can test using our Emulator.


---

## #24 [FAQ] 차량 내 지도 앱이 2개 이상일 때 우선순위 설정이 가능한가요? / Can developers set default navigation priority if multiple map apps are installed?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/24

A. 
- 현재: 지원되지 않음
- 향후: 우선순위 선정 API 개발 중 (출시 시기 미정)
> 지금은 시스템에서 기본 지도 앱이 자동 선택되며, 추후 API 공개 시 개발자가 제어 가능해질 예정입니다.

---

- Currently: Not supported
- Future: priority API under development
> Currently, the system automatically selects the default map app, and developers will be able to control this once the API is released in the future.



---

## #25 [FAQ] Pleos Connect는 어떤 앱을 제공하나요? / Which applications does Pleos Connect provide?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/25

A. Pleos Connect는 다음과 같은 핵심 애플리케이션을 제공합니다.
| Application      | Remark                         |
| ---------------- | ------------------------------ |
| App library      | 애플리케이션을 실행하는 런처 |
| Media            | 블루투스 음악 및 라디오 |
| Phone            | 전화 및 문자 메시지|
| Driving view     | 차량 주행 및 상태 정보 표시  |
| Vehicle settings | 차량 설정 및 제어       |
| Keyboard         | 소프트웨어 키보              |
| Camera           | 주행/ 주차 시 외부 상황 모니터링   |


--- 


A. Pleos Connect provides the following core applications.
| Application      | Remark                         |
| ---------------- | ------------------------------ |
| App library      | Launcher for infotainment apps |
| Media            | Bluetooth music & radio        |
| Phone            | Phone & messaging              |
| Driving view     | Driving status & vehicle info  |
| Vehicle settings | Vehicle control/settings       |
| Keyboard         | Software keyboard              |
| Camera           | Surround/external monitoring   |



---

## #26 [FAQ] 주행 가능 거리를 확인할 수 있나요? / Can my app check the remaining driving range?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/26

A. [RANGE_REMAINING](https://developer.android.com/reference/android/car/VehiclePropertyIds#RANGE_REMAINING) 에서 확인 가능합니다.
read 권한은 필요 없으며 write시에 추가 권한이 필요합니다

---

A. Available [RANGE_REMAINING](https://developer.android.com/reference/android/car/VehiclePropertyIds#RANGE_REMAINING)
No read permission required. write permission required for updates


---

## #27 [FAQ] AAOS Vehicle Properties는 모두 호환되나요? / Are all Android Automotive Vehicle Properties supported?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/27

A. API policy
Pleos Connect SDK가 제공하는 API 정책은 동일 혹은 유사 기능의 API가 Android Automotive OS에 존재한다면 플랫폼 파편화를 최소화하기 위해 Android Automotive에 align하는 것입니다. 또한, 향후 Pleos Connect Vehicle SDK로 제공되었던 기능이 Android Automotive에 수용된다면 최소 2개의 major version까지 backward compatibility를 제공하는 것을 기본 정책으로 운영합니다.

---

A. The API policy provided by the Pleos Connect SDK aligns with Android Automotive, minimising platform fragmentation where APIs with identical or similar functionality exist on the Android Automotive OS. Furthermore, it operates under the fundamental policy of providing backward compatibility for at least two major versions of features previously offered by the Pleos Connect Vehicle SDK, should these be subsequently adopted by Android Automotive.



---

## #28 [FAQ] 앱을 Landscape로 실행할 수 있나요? / Can the app be run in landscape mode?

- 작성일: 2025-11-24
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/28

A. 차량에 따라 기준이 달라집니다. Infolink의 경우 단독실행이기 때문에 무관합니다.
Connect S/L/W의 경우 Portrait로 실행되는것을 기준으로 하며 Landscape는 허용되지 않습니다. 

---

A. The standard varies depending on the vehicle. For Infolink, since it runs as a standalone application, this is irrelevant.
For Connect S/L/W, the standard is based on running in Portrait mode; Landscape mode is not permitted. 


---

## #29 [Q&A (Technical)] 네비게이션 앱이 동작할 때 NaviHelperEventListener을 이용하여 이벤트를 받을 수 있는 시뮬레이션 데이터가 있는지 확인 부탁드립니다.

- 작성일: 2026-04-13
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/29

### NaviHelperEventListener을 add하여 이벤트를 받을 수 있도록 네비게이션 이동 시작 -> 도착 까지  시뮬레이션 할 수 있는 방법이 있는지 문의 드립니다. 
예를 들어 강남역 출발 역삼역 도착을 시뮬레이션 할 수 있는 방법이 있다면 구현하는데 도움이 될 것 같습니다. 

> 댓글 (usechan0528-max):

동일한 모의주행 방법이 필요하여 재현 결과와 함께 문의드립니다. Pleos 에뮬레이터에서 내비의 현재 위치, 남은 거리, 남은 시간을 연속 갱신하는 공식 절차를 확인하고 싶습니다.

- Windows / Pleos Connect Emulator 2.1.0, Android API 34 / Android Emulator 37.2.13
- FusedLocation SDK 1.1.3, 서비스 1.0.0
- NaviHelper SDK 3.0.5, 서비스 3.0.4
- 내비 앱 3.3.2-rc5-hotfix.3

2026년 11월 2일 수행한 시험의 재현 과정(이 문의 작성 중 새로 실행한 결과는 아닙니다):
1. 에뮬레이터 내비 UI에서 목적지를 선택해 실제 경로 안내를 시작했습니다.
2. 내비 화면을 전면에 유지하고 별도 foreground 진단 서비스에서 NaviHelper의 getCurrentLocationInfo, getDestinationInfo, getRouteStateInfo 및 DrivingInfo 콜백을 관찰했습니다.
3. adb emu geo fix로 합성 좌표를 이동시키면 FusedLocation에는 이동이 반영됩니다. 하지만 내비 위치와 남은 시간은 변하지 않았습니다.
4. 내비 GpsLocationSource의 NMEA checksum mismatch 로그가 있어 올바른 XOR 체크섬의 GGA/RMC도 입력했습니다. Android NMEA 리스너의 수신은 확인했지만 내비 갱신은 확인하지 못했습니다. 이 체크섬 문제가 유일한 원인이라는 뜻은 아닙니다.

최종 활성 안내 시험은 ROUTE_GUIDING, 내비 위치 조회 34회 중 변화 0회, remainTime 1080초 / remainDistance 12000m로 고정되었습니다. isRouting은 활성 안내 중에도 false였으므로 이 값만으로 비활성이라고 판정하지 않았습니다. 재부팅 직후 SAFE_GUIDING 시험은 활성 경로 결과에서 제외했습니다. 콘솔의 OK 응답은 내비의 입력 수용을 입증하지 않습니다.

확인 부탁드리는 사항:
1. SIMULATION_GUIDING으로 진입하거나 출발부터 도착까지 경로를 재생할 수 있는 공식 UI/API/샘플이 있나요?
2. 내비가 사용하는 위치 공급원과 지원되는 모의 위치 입력 방식은 무엇인가요? geo fix/geo nmea가 지원 대상인가요?
3. 위 버전 조합에서 가능한지, 별도 호환 이미지나 설정이 필요한지 확인 부탁드립니다.

실차가 아닌 에뮬레이터에서 합성 입력으로 시험했습니다. 모의주행이 지원되지 않는 경우 지원 범위도 알려주시면 감사하겠습니다.


---

## #30 [Ideas / Suggestions] Proposal: Global Developer Sandbox, Onboarding for Independent Developers & Gleo AI Integration

- 작성일: 2026-09-22
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/30

Hi Pleos Playground & 42dot Teams,

I am a software engineer and automotive enthusiast based in Türkiye (driving a Hyundai i20), actively exploring app development opportunities for the **Pleos Connect** / Android Automotive OS (AAOS) ecosystem.

As 42dot advances its SDV architecture with **Pleos Connect** and **Gleo AI**, I would like to share a structured proposal regarding international developer onboarding and third-party AI integration.

---

### 1. Global Developer Onboarding & Korean Identity Verification Barrier
While exploring developer resources across the Hyundai ecosystem, I encountered the local Korean mobile identity verification (*본인인증 - Bonin Injeung*) requirement on the **Hyundai Developer Portal**. As an international independent developer, this creates a hard barrier to entry when trying to access developer tools and APIs.

To foster a thriving global third-party app ecosystem from the ground up, I propose:
* **Public Developer Sandbox / Emulator:** Allowing independent international developers to test PoC apps locally using standard Android Automotive OS (AAOS) tools before official store publishing.
* **Global Onboarding & Streamlined NDA:** An alternative verification flow (via email / Adobe Sign) and simplified electronic NDA process tailored specifically for international software engineers without requiring a Korean phone number or business registration.

---

### 2. Gleo AI Agentic Integration & Intent Handling
Gleo AI represents a massive leap forward in contextual in-car user experience. To maximize its potential, third-party applications should be able to extend Gleo AI’s action capabilities:

* **Gleo Agent Intent Extensions / Function Calling:** Exposing structured intent interfaces or API hooks that allow Gleo AI to securely trigger third-party app functionalities via natural speech (e.g., *"Gleo, check my vehicle telemetry score"* or *"Gleo, set a reminder for my next maintenance schedule"*).
* **Safe Intent Contexts (Gleo Guard Alignment):** Ensuring third-party app actions seamlessly align with `Gleo Guard` safety layers to maintain driver distraction guidelines and vehicle security.

---

### 3. Proposed In-Car App Concepts for Pleos Market
I am actively prototyping concept apps built using standard Car Property APIs that can easily map to Pleos Connect & Gleo AI:

1. **Smart Vehicle Telemetry & Maintenance Tracker:** Real-time diagnostics overlay leveraging `Vehicle SDK` to monitor tire pressure, driving efficiency, and personalized maintenance schedules.
2. **Personalized Driving Dynamics & Safety Analytics:** Analyzing real-time driving signals (speed, steering angle, power distribution) to calculate safety scores and energy/fuel consumption optimization.

I have already built functional AAOS PoC applications and would love to adapt them natively to the Pleos Connect SDK and Gleo AI once access becomes available.

Looking forward to hearing the team's feedback and future roadmap plans for independent global developers!

Best regards,  
**Erim IŞIL**  
*Software Developer | Türkiye (GMT+3)*  
Email: troynando07@gmail.com  
GitHub: [@troynando](https://github.com/troynando)


---

## #31 [Q&A (General)] 개인 개발자 지원

- 작성일: 2026-10-17
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/31

현재는 법인 개발자만 지원되던데, 개인 개발자가 지원되는 대략적인 시기를 알 수 있을까요?


---

## #32 [FAQ] Pleos 기본 내비게이션의 현재 위치를 개발자가 임의의 테스트 좌표로 변경할 수 있는지 궁금합니다.

- 작성일: 2026-11-04
- URL: https://github.com/Pleos-Playground/pleos-playground-api/discussions/32

문의 사항

가장 궁금한 내용은 Pleos 기본 내비게이션의 현재 위치를 개발자가 임의의 테스트 좌표로 변경할 수 있는지입니다.

구체적으로 다음 사항을 확인 부탁드립니다.

Pleos Connect Emulator v2.1.0에서 기본 내비게이션의 현재 위치가 수정구 시흥동으로 고정되어 있는 것이 정상적인 시뮬레이터 동작인가요?

Android LocationManager와 Pleos NaviHelper / 기본 Navigation 앱이 서로 다른 위치 공급원을 사용하는 구조인가요?

기본 Navigation 앱의 현재 위치를 서울시청 등 임의의 좌표로 변경하기 위해 공식적으로 지원되는 설정, 명령어, Virtual Console 기능 또는 별도 시뮬레이션 도구가 있나요?

ADB를 통한 GPS 좌표 변경이 기본 내비게이션에 반영되지 않는 것이 현재 Emulator 버전의 제한 사항인지, 별도의 GNSS/VHAL 연동 설정이 필요한지 궁금합니다.

실제 차량 이동 상황을 시뮬레이션하여 기본 내비게이션의 현재 위치와 TBT 안내 정보를 함께 갱신할 수 있는 공식 테스트 방법이 있나요?

현재 저희 프로젝트에서는 차량이 특정 도로시설에 접근했을 때 해당 상황을 인식하고 다국어 음성으로 자동 안내하는 기능을 개발하고 있어, 기본 내비게이션의 위치 이동 시뮬레이션이 중요합니다.

또한  대학교 인터넷을 이용한 컴퓨터도 문제가 되는지 궁금합니다

지원되는 공식 방법이나 관련 문서, 예제 코드가 있다면 안내 부탁드립니다.

감사합니다.

