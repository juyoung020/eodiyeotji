> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/get-property

# Get

`getCurrentXxx`로 차량 상태 및 설정 값을 조회합니다. 결과는 `onComplete(값)` / `onFailed`로 비동기 전달됩니다. 도메인 객체 획득은 [Obtaining a domain object](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object)를, 상세 API 명세는 [API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry)를 참조하세요.

아래 예제는 [`Vehicle`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry) 인스턴스(`vehicle`)를 이미 초기화했다고 가정합니다.

- Kotlin
- Java

``` kotlin
val door = vehicle.getDoor()
door.getCurrentDoorPosition(
  area = DoorArea(DoorValue.ROW_1_LEFT),
  onComplete = { pos: Int? ->
    // do something
  },
  onFailed = { e: Exception ->
    // do something
  }
)

val display = vehicle.getDisplay()
display.getCurrentTemperatureDisplayUnit(
  onComplete = { unit: TemperatureUnit? ->
    // do something
  },
  onFailed = { e: Exception ->
    // do something
  }
)
```

``` java
Door door = vehicle.getDoor();
door.getCurrentDoorPosition(
  new DoorArea(DoorValue.ROW_1_LEFT),
  pos -> {
    // do something
    return Unit.INSTANCE;
  },
  e -> {
    // do something
    return Unit.INSTANCE;
  }
);

Display display = vehicle.getDisplay();
display.getCurrentTemperatureDisplayUnit(
  unit -> {
    // do something
    return Unit.INSTANCE;
  },
  e -> {
    // do something
    return Unit.INSTANCE;
  }
);
```
