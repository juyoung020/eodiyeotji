> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/steering-wheel-angle-listener/on-steering-wheel-angle-updated

# onSteeringWheelAngleUpdated

abstract fun [onSteeringWheelAngleUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/steering-wheel-angle-listener/on-steering-wheel-angle-updated)(angle: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Called when a front-wheel steering angle update is available.

Angles are reported in degrees, and leftward steering angles are negative. The `angle` parameter is `null` if no angle is available. Otherwise, it represents the front-wheel steering angle, not the rotation angle of the steering wheel.

#### Parameters

*angle*

Current front-wheel steering angle in degrees, or `null` if no value is available.
