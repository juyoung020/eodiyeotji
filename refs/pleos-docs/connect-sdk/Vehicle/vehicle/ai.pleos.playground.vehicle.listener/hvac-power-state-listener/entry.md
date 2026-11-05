> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-power-state-listener/entry

# HvacPowerStateListener

interface [HvacPowerStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-power-state-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for the HvacPowerState updates

## Functions

[onHvacPowerStateUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-power-state-listener/on-hvac-power-state-updated)

Link copied to clipboard

abstract fun [onHvacPowerStateUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-power-state-listener/on-hvac-power-state-updated)(state: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?)

Callback invoked when HvacPowerState updates.
