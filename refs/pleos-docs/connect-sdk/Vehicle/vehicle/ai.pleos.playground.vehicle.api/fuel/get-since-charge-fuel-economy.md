> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-since-charge-fuel-economy

# getSinceChargeFuelEconomy

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_ENERGY")

abstract fun [getSinceChargeFuelEconomy](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-since-charge-fuel-economy)(onComplete: ([Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets fuel economy since refuel.

Fuel economy after refuel.

The fuel economy of the vehicle measured since the last refuel. For instantaneous fuel economy, see the `INSTANTANEOUS_FUEL_ECONOMY` vehicle property. Units follow L/100 km, the same as `INSTANTANEOUS_FUEL_ECONOMY`.

**Permission:** [PleosPermission.CAR_ENERGY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-e-n-e-r-g-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
```

Content copied to clipboard

#### Parameters

*onComplete*

[Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html): Callback invoked when the fuel economy since refuel is successfully retrieved. The value represents fuel economy in L/100 km.

*onFailed*

[Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html): Callback invoked if retrieving the fuel economy since refuel fails.
