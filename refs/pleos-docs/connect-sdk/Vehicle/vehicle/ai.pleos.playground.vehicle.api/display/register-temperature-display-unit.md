> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/display/register-temperature-display-unit

# registerTemperatureDisplayUnit

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_INFO")

abstract fun [registerTemperatureDisplayUnit](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/display/register-temperature-display-unit)(listener: [TemperatureDisplayUnitListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/temperature-display-unit-listener/entry))

Registers TemperatureDisplayUnit to get property updates.

**Permission:** [PleosPermission.CAR_INFO](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-i-n-f-o) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_INFO" />
```

Content copied to clipboard

#### Parameters

*listener*

[TemperatureDisplayUnitListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/temperature-display-unit-listener/entry): Listener is registered.
