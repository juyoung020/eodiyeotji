> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/trip-ev-efficiency-listener/entry

# TripEvEfficiencyListener

interface [TripEvEfficiencyListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/trip-ev-efficiency-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for the trip EV efficiency updates.

## Functions

[onTripEvEfficiencyUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/trip-ev-efficiency-listener/on-trip-ev-efficiency-updated)

Link copied to clipboard

abstract fun [onTripEvEfficiencyUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/trip-ev-efficiency-listener/on-trip-ev-efficiency-updated)(statistics: [TripStatistics](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.common/trip-statistics/entry)\<[Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)\>?)

Callback invoked when trip EV efficiency updates.
