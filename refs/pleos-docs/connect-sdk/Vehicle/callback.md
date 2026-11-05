> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/callback

# Callback

`registerXxx` / `unregisterXxx`로 리스너를 등록·해제해 차량 상태 값의 변화를 구독합니다. 등록한 값이 변경되면 리스너의 `onXxxUpdated`로, 실패 시 `onFailed`로 전달됩니다. 도메인 객체 획득은 [Obtaining a domain object](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object)를, 상세 API 명세는 [API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry)를 참조하세요.

아래 예제는 [`Vehicle`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry) 인스턴스(`vehicle`)를 이미 초기화했다고 가정합니다.

- Kotlin
- Java

``` kotlin
// Create callback listener
val callbackListener = object : DoorPositionListener {
  override fun onPositionUpdated(pos: Int?) {
    // do something
  }

  override fun onFailed(e: Exception) {
    // do something
  }
}

val door = vehicle.getDoor()

// register callback
door.registerDoorPosition(
  doorPositionListener = callbackListener
)
// unregister callback
door.unregisterDoorPosition(
  doorPositionListener = callbackListener
)
```

``` java
// Create callback listener
DoorPositionListener callbackListener = new DoorPositionListener() {
  @Override
  public void onPositionUpdated(@Nullable int pos) {
    // do something
  }

  @Override
  public void onFailed(@NonNull Exception e) {
    // do something
  }
};

Door door = vehicle.getDoor();

// register callback
door.registerDoorPosition(callbackListener);

// unregister callback
door.unregisterDoorPosition(callbackListener);
```
