> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/steering-wheel-angle-listener/entry

# SteeringWheelAngleListener

interface [SteeringWheelAngleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/steering-wheel-angle-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Listener for front-wheel steering angle updates.

## Functions

[onSteeringWheelAngleUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/steering-wheel-angle-listener/on-steering-wheel-angle-updated)

Link copied to clipboard

abstract fun [onSteeringWheelAngleUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/steering-wheel-angle-listener/on-steering-wheel-angle-updated)(angle: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Called when a front-wheel steering angle update is available.
