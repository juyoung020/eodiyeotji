> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/entry

# HvacTemperatureListener

interface [HvacTemperatureListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Listener for HVAC temperature updates.

Reports the target temperature when registered with [ai.pleos.playground.vehicle.api.HVAC.registerHvacTemperature](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-temperature), or the current interior temperature when registered with [ai.pleos.playground.vehicle.api.HVAC.registerHvacTemperatureCurrent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-temperature-current). Both registration methods report values in degrees Celsius (°C).

## Functions

[onHvacTemperatureUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/on-hvac-temperature-updated)

Link copied to clipboard

abstract fun [onHvacTemperatureUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/on-hvac-temperature-updated)(temperature: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Called with the updated HVAC temperature in degrees Celsius (°C).
