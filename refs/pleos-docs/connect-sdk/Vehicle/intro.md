> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro

# Introduction

## Vehicle SDK 소개[​](#vehicle-sdk-소개 "Vehicle SDK 소개에 대한 직접 링크")

Vehicle SDK는 차량 정보 조회 및 제어를 위한 API를 제공합니다. 3rd party 앱은 Vehicle SDK를 통해 Pleos Connect의 Vehicle Service에 연결합니다. Vehicle Service는 Android Automotive OS 위에서 동작하며, 3rd party 앱은 Vehicle SDK API로 차량 정보 조회 및 제어를 요청하고 Vehicle Service가 이를 처리합니다.

![](https://document.pleos.ai/assets/images/vehicle-sdk-diagram-9c2c1d6739316101e282f1543f1976b0.png)

SDK 버전

이 가이드는 Vehicle SDK v2.3.2과 Vehicle Service v2.3.21을 기준으로 작성되었습니다.

## 진입점과 도메인 구조[​](#진입점과-도메인-구조 "진입점과 도메인 구조에 대한 직접 링크")

Vehicle SDK API는 Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light 등 도메인별 인터페이스로 제공됩니다. [`Door`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry), [`Brake`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/entry) 같은 도메인 객체는 직접 생성하지 않고, Vehicle SDK의 진입점인 [`Vehicle`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry) 인스턴스의 [`getDoor()`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-door), [`getBrake()`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-brake)와 같은 `getXxx()`로만 얻습니다. 획득한 도메인 객체에서는 capability·get·set·callback API를 호출합니다. Capability(`checkXXXCapabilities`)는 기능 지원 여부와 허용 값 범위를 확인하고, get은 현재 값을 조회하며, set은 값을 설정합니다. Callback은 리스너를 등록해 상태 변화를 구독합니다.

차량 서비스 연결은 [`initialize()`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/initialize)로 시작하고, 사용이 끝나면 [`release()`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/release)로 해제합니다. 차량 통신은 IPC이므로 capability·get·set 결과는 즉시 반환되지 않고 `onComplete`·`onFailed` 비동기 람다로 전달됩니다. 비동기 콜백이 끝나기 전에 [`release()`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/release)를 호출하지 않도록 주의해 주시기 바랍니다.

도메인

설명

Brake

브레이크 페달, 주차 브레이크 상태 조회

CarInfo

차량 기본 정보(외부 크기, 제조사, 연식 등) 조회

Display

인포테인먼트 표시 데이터 단위(온도 단위 등) 조회/설정

Door

도어 열림/닫힘, 잠금/해제 제어 및 상태 조회

DrivingMode

드라이브 모드, 크루즈 컨트롤 정보 조회

EvBattery

EV 배터리·충전 상태 및 전비(효율) 조회

Fuel

연비 등 연료 관련 정보 조회

HVAC

난방·환기·공조(온도, 풍량, 습도 등) 제어 및 조회

Light

조명 상태 조회 및 앰비언트 라이트 등 제어

Odometer

총 주행 거리, 트립 주행 거리/시간, 주행 거리 단위 조회

Safety

차량 경고(Warning) 정보 조회

Seat

좌석 위치·각도·기능 제어 및 조회

SideMirror

사이드미러 열선 상태 조회/설정

Steeringwheel

핸들(스티어링휠) 상태, 열선 조회/제어

Tire

타이어 상태 조회

Toll

ETCS(하이패스) 음성 안내 음량 등 조회/설정

TurnSignal

방향 지시등 상태 조회

Window

창문 제어 및 상태 조회

Wiper

와이퍼 제어 및 상태 조회

## Emulator setup[​](#emulator-setup "Emulator setup에 대한 직접 링크")

Vehicle SDK는 Pleos Connect Emulator 환경에서 개발 및 검증합니다. 아래 순서로 개발 환경을 준비하세요.

1.  [소프트웨어 개발 환경 요구사항 확인](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#소프트웨어-개발-환경)
2.  [Pleos Connect Emulator 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#installing-the-pleos-connect-emulator)
3.  [AVD 생성 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator#setting-up-the-emulator)
4.  [Pleos Connect Virtual Console 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
5.  [마이크 접근 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator#마이크-접근-설정)
6.  [Cold boot 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator#cold-boot-설정)

## 다음 단계[​](#다음-단계 "다음 단계에 대한 직접 링크")

1.  [도메인 객체 획득](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object): 도메인 객체를 획득하고 Get·Set·Callback·Capability API를 호출하는 방법
2.  [Permission](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission): 도메인별 퍼미션
3.  [Error code](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code): 오류 코드 및 `onFailed` 처리
4.  [API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry): Vehicle SDK API 명세
5.  [Vehicle SDK Sample App 사용 가이드](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)
