> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-target-speed-listener/entry

# CruiseControlTargetSpeedListener

interface [CruiseControlTargetSpeedListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-target-speed-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Listener for cruise control target speed updates.

## Functions

[onCruiseControlTargetSpeedUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-target-speed-listener/on-cruise-control-target-speed-updated)

Link copied to clipboard

abstract fun [onCruiseControlTargetSpeedUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-target-speed-listener/on-cruise-control-target-speed-updated)(speed: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Called with the updated cruise control target speed in meters per second (m/s).
