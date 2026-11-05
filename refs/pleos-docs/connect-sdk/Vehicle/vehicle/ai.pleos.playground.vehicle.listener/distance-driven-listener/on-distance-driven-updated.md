> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/on-distance-driven-updated

# onDistanceDrivenUpdated

abstract fun [onDistanceDrivenUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/on-distance-driven-updated)(value: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Called with the updated distance in kilometers (km) or meters (m), depending on the registration API.

#### Parameters

*value*

Updated distance in the unit documented by the registration API, or `null` if no value is available.
