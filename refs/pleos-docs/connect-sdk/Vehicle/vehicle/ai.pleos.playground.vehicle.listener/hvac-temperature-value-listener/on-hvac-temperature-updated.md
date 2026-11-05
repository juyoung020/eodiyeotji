> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/on-hvac-temperature-updated

# onHvacTemperatureUpdated

abstract fun [onHvacTemperatureUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/on-hvac-temperature-updated)(celsius: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?, fahrenheit: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Callback invoked when HVAC temperature updates.

#### Parameters

*celsius*

currently set temperature value in degrees Celsius (°C).

*fahrenheit*

converted temperature value in degrees Fahrenheit (°F) using vehicle rule when possible, otherwise formula fallback.
