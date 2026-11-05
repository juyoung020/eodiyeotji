> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-since-refill-driving-time

# getTripSinceRefillDrivingTime

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DRIVE")

abstract fun [getTripSinceRefillDrivingTime](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-since-refill-driving-time)(onComplete: ([Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets trip since refuel driving time.

Trip driving time since refuel in minutes.

The driving time of the vehicle measured since the last refuel.

**Permission:** [PleosPermission.CAR_DRIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-d-r-i-v-e) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />
```

Content copied to clipboard

#### Parameters

*onComplete*

[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html): Callback invoked when the trip driving time since refuel is successfully retrieved. The value represents driving time in minutes.

*onFailed*

[Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html): Callback invoked if retrieving the trip driving time since refuel fails.
