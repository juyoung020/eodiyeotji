> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/set-property

# Set

`setXxx`로 차량 상태 및 설정 값을 변경합니다. 실패 시 `onFailed`로 전달됩니다. 도메인 객체 획득은 [Obtaining a domain object](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object)를, 상세 API 명세는 [API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry)를 참조하세요.

아래 예제는 [`Vehicle`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry) 인스턴스(`vehicle`)를 이미 초기화했다고 가정합니다.

- Kotlin
- Java

``` kotlin
val door = vehicle.getDoor()
door.setDoorPosition(
  area = DoorArea(DoorValue.ROW_1_LEFT),
  value = 1, // Integer type
  onFailed = { e: Exception ->
    // do something
  }
)
```

``` java
Door door = vehicle.getDoor();
door.setDoorPosition(
  new DoorArea(DoorValue.ROW_1_LEFT),
  1,
  e -> {
    // do something
    return Unit.INSTANCE;
  }
);
```
