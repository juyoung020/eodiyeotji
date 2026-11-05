> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-smart-glass-state

# setSmartGlassState

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CONTROL_CAR_WINDOWS")

abstract fun [setSmartGlassState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-smart-glass-state)(value: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), onComplete: ([Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html) = { \_ -\> }, onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html) = {})

Sets the Smart Glass state.

**Permission:** [PleosPermission.CONTROL_CAR_WINDOWS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-w-i-n-d-o-w-s) (protection level: signature\|privileged)

``` block
<uses-permission android:name="pleos.car.permission.CONTROL_CAR_WINDOWS" />
```

Content copied to clipboard

#### Parameters

*value*

The Smart Glass state to set.

*onComplete*

[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html): Callback invoked when the Smart Glass state is successfully set.

*onFailed*

[Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html): Callback invoked if setting the Smart Glass state fails.
