> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/unregister-cruise-control-state

# unregisterCruiseControlState

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DRIVE")

abstract fun [unregisterCruiseControlState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/unregister-cruise-control-state)(listener: [CruiseControlStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-state-listener/entry))

Stops getting property updates for the given CruiseControlState.

**Permission:** [PleosPermission.CAR_DRIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-d-r-i-v-e) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />
```

Content copied to clipboard

#### Parameters

*listener*

[CruiseControlStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-state-listener/entry): Listener is unregistered.
