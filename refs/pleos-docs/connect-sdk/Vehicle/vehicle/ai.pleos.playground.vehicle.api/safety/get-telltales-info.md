> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-telltales-info

# getTelltalesInfo

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_SAFETY")

abstract fun [getTelltalesInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-telltales-info)(onComplete: (telltales: [Telltales](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltales/entry)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets telltales info.

**Permission:** [PleosPermission.PRIVATE_CAR_SAFETY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-s-a-f-e-t-y) (protection level: signature\|privileged)

``` block
<uses-permission android:name="pleos.car.permission.PRIVATE_CAR_SAFETY" />
```

Content copied to clipboard

#### Parameters

*onComplete*

[Telltales](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltales/entry): Callback invoked when the current telltales information is successfully retrieved.

*onFailed*

[Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html): Callback invoked if retrieving the telltales information fails.
