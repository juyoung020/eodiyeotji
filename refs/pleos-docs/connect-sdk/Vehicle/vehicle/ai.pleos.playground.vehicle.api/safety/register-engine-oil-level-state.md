> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-engine-oil-level-state

# registerEngineOilLevelState

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_SAFETY")

abstract fun [registerEngineOilLevelState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-engine-oil-level-state)(listener: [OilLevelStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/oil-level-state-listener/entry))

Registers the EngineOilLevelState to get property updates.

**Permission:** [PleosPermission.CAR_SAFETY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-s-a-f-e-t-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_SAFETY" />
```

Content copied to clipboard

#### Parameters

*listener*

[OilLevelStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/oil-level-state-listener/entry): Listener is registered.
