> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/register-cruise-control-state

# registerCruiseControlState

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DRIVE")

abstract fun [registerCruiseControlState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/register-cruise-control-state)(listener: [CruiseControlStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-state-listener/entry))

Registers CruiseControlState to get property updates.

**Permission:** [PleosPermission.CAR_DRIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-d-r-i-v-e) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />
```

Content copied to clipboard

#### Parameters

*listener*

[CruiseControlStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-state-listener/entry): Listener is registered.
