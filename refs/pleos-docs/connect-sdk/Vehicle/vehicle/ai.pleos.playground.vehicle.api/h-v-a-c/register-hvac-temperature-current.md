> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-temperature-current

# registerHvacTemperatureCurrent

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_CLIMATE")

abstract fun [registerHvacTemperatureCurrent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-temperature-current)(area: [SeatArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/entry), listener: [HvacTemperatureListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/entry))

Registers a listener for current interior temperature updates in degrees Celsius (°C).

**Permission:** [PleosPermission.CAR_CLIMATE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-c-l-i-m-a-t-e) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_CLIMATE" />
```

Content copied to clipboard

#### Parameters

*area*

[SeatArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/entry): The seat area for the vehicle.

*listener*

[HvacTemperatureListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/entry): Listener that receives current interior temperature values in degrees Celsius (°C).

------------------------------------------------------------------------

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_CLIMATE")

abstract fun [registerHvacTemperatureCurrent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-temperature-current)(area: [SeatArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/entry), listener: [HvacTemperatureValueListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/entry))

Registers HvacTemperatureCurrent updates in degrees Celsius (°C) and degrees Fahrenheit (°F).

The current temperature is delivered in degrees Celsius (°C), and a value in degrees Fahrenheit (°F) is provided by applying the vehicle-specific conversion rule when available (otherwise using a formula fallback).

**Permission:** [PleosPermission.CAR_CLIMATE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-c-l-i-m-a-t-e) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_CLIMATE" />
```

Content copied to clipboard

#### Parameters

*area*

[SeatArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/entry): The seat area for the vehicle.

*listener*

[HvacTemperatureValueListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/entry): Listener is registered.
