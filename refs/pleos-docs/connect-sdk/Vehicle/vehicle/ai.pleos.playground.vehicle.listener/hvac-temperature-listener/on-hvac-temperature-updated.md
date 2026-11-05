> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/on-hvac-temperature-updated

# onHvacTemperatureUpdated

abstract fun [onHvacTemperatureUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/on-hvac-temperature-updated)(temperature: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Called with the updated HVAC temperature in degrees Celsius (°C).

#### Parameters

*temperature*

Updated target or current interior temperature, depending on the registration method, in degrees Celsius (°C); `null` if no value is available.
