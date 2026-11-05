> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-since-charge-ev-efficiency

# getSinceChargeEvEfficiency

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_ENERGY")

abstract fun [getSinceChargeEvEfficiency](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-since-charge-ev-efficiency)(onComplete: ([Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets EV efficiency since charge.

EV efficiency after recharge.

The EV efficiency of the vehicle measured since the last recharge. For instantaneous EV efficiency, see the `INSTANTANEOUS_EV_EFFICIENCY` vehicle property. Units follow km/kWh, the same as `INSTANTANEOUS_EV_EFFICIENCY`.

**Permission:** [PleosPermission.CAR_ENERGY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-e-n-e-r-g-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
```

Content copied to clipboard

#### Parameters

*onComplete*

[Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html): Callback invoked when the EV efficiency since charge is successfully retrieved. The value represents efficiency in km/kWh.

*onFailed*

[Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html): Callback invoked if retrieving the EV efficiency since charge fails.
