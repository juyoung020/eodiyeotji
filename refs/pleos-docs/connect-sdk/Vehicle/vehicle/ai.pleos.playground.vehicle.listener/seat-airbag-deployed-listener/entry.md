> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-airbag-deployed-listener/entry

# SeatAirbagDeployedListener

interface [SeatAirbagDeployedListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-airbag-deployed-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for the SeatAirbagDeployed updates

## Functions

[onSeatAirbagDeployUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-airbag-deployed-listener/on-seat-airbag-deploy-updated)

Link copied to clipboard

abstract fun [onSeatAirbagDeployUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-airbag-deployed-listener/on-seat-airbag-deploy-updated)(deployed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?)

Callback invoked when SeatAirbagDeployed updates.
