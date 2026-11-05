> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/get-c-r-n-info

# getCRNInfo

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_INFO")

abstract fun [~~getCRNInfo~~](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/get-c-r-n-info)(onComplete: ([String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

### Deprecated

Use getVINInfo to retrieve the Vehicle Identification Number (VIN).

#### Replace with

``` monospace
getVINInfo(onComplete, onFailed)
```

Content copied to clipboard

Gets the Connected Resource Name (CRN) Info.

**Permission:** [PleosPermission.CAR_INFO](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-i-n-f-o) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_INFO" />
```

Content copied to clipboard

#### Parameters

*onComplete*

[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html): Callback invoked when the Connected Resource Name (CRN) information is successfully retrieved.

*onFailed*

[Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html): Callback invoked if retrieving the Connected Resource Name (CRN) information fails.
