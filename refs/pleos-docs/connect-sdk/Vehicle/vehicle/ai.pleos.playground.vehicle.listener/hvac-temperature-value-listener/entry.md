> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/entry

# HvacTemperatureValueListener

interface [HvacTemperatureValueListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for HVAC temperature updates in degrees Celsius (°C) and degrees Fahrenheit (°F).

## Functions

[onHvacTemperatureUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/on-hvac-temperature-updated)

Link copied to clipboard

abstract fun [onHvacTemperatureUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/on-hvac-temperature-updated)(celsius: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?, fahrenheit: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Callback invoked when HVAC temperature updates.
