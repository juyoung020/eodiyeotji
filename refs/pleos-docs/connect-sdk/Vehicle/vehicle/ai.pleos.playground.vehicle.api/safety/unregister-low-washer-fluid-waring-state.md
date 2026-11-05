> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-low-washer-fluid-waring-state

# unregisterLowWasherFluidWaringState

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_SAFETY")

abstract fun [unregisterLowWasherFluidWaringState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-low-washer-fluid-waring-state)(listener: [LowWasherFluidWaringListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/low-washer-fluid-waring-listener/entry))

Stops getting property updates for the given LowWasherFluidWaringState.

**Permission:** [PleosPermission.CAR_SAFETY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-s-a-f-e-t-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_SAFETY" />
```

Content copied to clipboard

#### Parameters

*listener*

[LowWasherFluidWaringListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/low-washer-fluid-waring-listener/entry): Listener is unregistered.
