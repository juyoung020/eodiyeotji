> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-ev-charge-switch-capabilities

# checkEvChargeSwitchCapabilities

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_ENERGY")

abstract fun [checkEvChargeSwitchCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-ev-charge-switch-capabilities)(onComplete: ([CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets EvChargeSwitch capabilities.

**Permission:** [PleosPermission.CAR_ENERGY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-e-n-e-r-g-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
```

Content copied to clipboard

#### Parameters

*onComplete*

[CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry): Callback invoked when the capability configuration for EvChargeSwitch is successfully retrieved.

*onFailed*

[Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html): Callback invoked if retrieving the capability information fails.
