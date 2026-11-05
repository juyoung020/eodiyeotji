> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object

# Obtaining a domain object

Vehicle SDK API는 진입점 [`Vehicle`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry)에서 도메인 객체를 얻은 뒤, 그 객체에서 네 가지 타입(Get·Set·Callback·Capability)의 API를 호출하는 방식으로 사용합니다. 도메인 구조는 [Introduction](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro#%EC%A7%84%EC%9E%85%EC%A0%90%EA%B3%BC-%EB%8F%84%EB%A9%94%EC%9D%B8-%EA%B5%AC%EC%A1%B0)을 참조하세요. 퍼미션은 [Permission](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)을, 오류 처리는 [Error code](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code)를 참조하세요.

[`Vehicle`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry) 인스턴스를 생성하고 `initialize()`로 차량 서비스에 연결한 뒤, `getDoor()`·`getDisplay()`와 같은 `getXxx()`로 도메인 객체를 얻습니다. 도메인 객체에서 Get·Set·Callback·Capability API를 호출하며, 사용이 끝나면 `release()`로 리소스를 해제합니다.

차량 통신은 IPC이므로 get·set·capability 결과는 즉시 반환되지 않고 `onComplete`·`onFailed` 콜백으로 비동기 전달됩니다. 비동기 콜백이 끝나기 전에 `release()`를 호출하지 않도록 주의하세요.

- Kotlin
- Java

``` kotlin
// Vehicle 인스턴스 생성 후 초기화
val vehicle = Vehicle(context)
vehicle.initialize()

// getXxx()로 도메인 객체 획득
val door = vehicle.getDoor()

// door 객체에서 Get / Set / Callback / Capability API 호출
// ...

// 사용 종료 후 리소스 해제
vehicle.release()
```

``` java
// Vehicle 인스턴스 생성 후 초기화
Vehicle vehicle = new Vehicle(context);
vehicle.initialize();

// getXxx()로 도메인 객체 획득
Door door = vehicle.getDoor();

// door 객체에서 Get / Set / Callback / Capability API 호출
// ...

// 사용 종료 후 리소스 해제
vehicle.release();
```

## 네 가지 API 타입[​](#네-가지-api-타입 "네 가지 API 타입에 대한 직접 링크")

타입

하는 일

결과 전달

문서

Capability

기능 지원 여부·허용 범위 확인

`onComplete(config, areaMap)` / `onFailed`

[Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability)

Get

현재 값 조회

`onComplete(값)` / `onFailed`

[Get](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/get-property)

Set

값 설정

`onFailed`(실패 시)

[Set](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/set-property)

Callback

상태 변화 구독

리스너 `onXxxUpdated` / `onFailed`

[Callback](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/callback)
