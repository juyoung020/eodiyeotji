> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability

# Capability

`checkXxxCapabilities`로 기능 지원 여부와 허용 범위를 확인합니다. `onComplete`는 기능 단위 지원 정보를 반환합니다. 반환 형태는 API마다 다르며, 공통 모델은 [capability 패키지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/entry)를 참조하세요. 도메인 객체 획득은 [Obtaining a domain object](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object)를, 상세 API 명세는 [API Reference](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry)를 참조하세요.

이 문서 트리는 `CapabilityConfig`를 함께 반환하는 capability API를 기준으로 설명합니다.

타입

설명

[CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry)

기능 단위 접근 수준([AccessType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/access-type/entry): `NONE`, `READ`, `WRITE`, `READ_WRITE`)

[AreaBounds](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/entry)

영역별 허용 값 범위(`minValue`, `maxValue`)

`Map<Area, AreaBounds>`

영역(예: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry))별 `AreaBounds` 맵

아래 예제는 [`Vehicle`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry) 인스턴스(`vehicle`)를 이미 초기화했다고 가정합니다.

- Kotlin
- Java

``` kotlin
val door = vehicle.getDoor()
door.checkDoorPositionCapabilities(
  onComplete = { config, areaMap ->
    when (config.accessType) {
      AccessType.READ, AccessType.READ_WRITE -> {
        val bounds = areaMap[DoorArea(DoorValue.ROW_1_LEFT)]
        // Use bounds?.minValue / bounds?.maxValue, then call getCurrentDoorPosition
      }
      else -> {
        // NONE or WRITE only — disable UI or use an alternate flow
      }
    }
  },
  onFailed = { e: Exception ->
    if (e is VehicleException) {
      // Capability lookup failed: e.errorCode
    }
  }
)
```

``` java
Door door = vehicle.getDoor();
door.checkDoorPositionCapabilities(
  (config, areaMap) -> {
    if (config.getAccessType() == AccessType.READ
        || config.getAccessType() == AccessType.READ_WRITE) {
      AreaBounds<Integer> bounds = areaMap.get(new DoorArea(DoorValue.ROW_1_LEFT));
      // Use bounds.getMinValue() / bounds.getMaxValue(), then call getCurrentDoorPosition
    } else {
      // NONE or WRITE only — disable UI or use an alternate flow
    }
    return Unit.INSTANCE;
  },
  e -> {
    if (e instanceof VehicleException) {
      // Capability lookup failed: ((VehicleException) e).getErrorCode()
    }
    return Unit.INSTANCE;
  }
);
```
