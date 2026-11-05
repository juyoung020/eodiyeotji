> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-occupancy-listener/entry

# SeatOccupancyListener

interface [SeatOccupancyListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-occupancy-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for the SeatOccupancy updates

## Functions

[onSeatOccupancyUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-occupancy-listener/on-seat-occupancy-updated)

Link copied to clipboard

abstract fun [onSeatOccupancyUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-occupancy-listener/on-seat-occupancy-updated)(occupancy: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?)

Callback invoked when SeatOccupancy updates.
