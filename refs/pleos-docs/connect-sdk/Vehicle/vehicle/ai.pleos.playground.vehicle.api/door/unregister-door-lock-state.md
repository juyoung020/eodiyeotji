> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-door-lock-state

# unregisterDoorLockState

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [unregisterDoorLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-door-lock-state)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), listener: [DoorLockStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/door-lock-state-listener/entry))

Stops getting property updates for the given door lock state.

**Permission:** [PleosPermission.CAR_DOORS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-d-o-o-r-s) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_DOORS" />
```

Content copied to clipboard

#### Parameters

*area*

[DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry): The door area for the vehicle.

*listener*

[DoorLockStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/door-lock-state-listener/entry): Listener is unregistered.
