> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-power-down-warning-indicator

# unregisterPowerDownWarningIndicator

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_SAFETY")

abstract fun [unregisterPowerDownWarningIndicator](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-power-down-warning-indicator)(listener: [PowerDownWarningIndicatorListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/power-down-warning-indicator-listener/entry))

Stops getting property updates for the given PowerDownWarningIndicator.

**Permission:** [PleosPermission.CAR_SAFETY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-s-a-f-e-t-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_SAFETY" />
```

Content copied to clipboard

#### Parameters

*listener*

[PowerDownWarningIndicatorListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/power-down-warning-indicator-listener/entry): Listener is unregistered.
