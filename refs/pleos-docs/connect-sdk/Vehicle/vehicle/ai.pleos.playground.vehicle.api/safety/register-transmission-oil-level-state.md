> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-transmission-oil-level-state

# registerTransmissionOilLevelState

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_SAFETY")

abstract fun [registerTransmissionOilLevelState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-transmission-oil-level-state)(listener: [OilLevelStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/oil-level-state-listener/entry))

Registers the TransmissionOilLevel to get property updates.

**Permission:** [PleosPermission.CAR_SAFETY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-s-a-f-e-t-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_SAFETY" />
```

Content copied to clipboard

#### Parameters

*listener*

[OilLevelStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/oil-level-state-listener/entry): Listener is registered.
