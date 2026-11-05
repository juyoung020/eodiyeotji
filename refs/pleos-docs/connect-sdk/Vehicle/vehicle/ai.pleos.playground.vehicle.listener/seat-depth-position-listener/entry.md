> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-depth-position-listener/entry

# SeatDepthPositionListener

interface [SeatDepthPositionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-depth-position-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for the SeatDepthPosition updates

## Functions

[onSeatDepthPositionUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-depth-position-listener/on-seat-depth-position-updated)

Link copied to clipboard

abstract fun [onSeatDepthPositionUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-depth-position-listener/on-seat-depth-position-updated)(position: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?)

Callback invoked when SeatDepthPosition updates.
