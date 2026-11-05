> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/glove-box-lock-state-listener/entry

# GloveBoxLockStateListener

interface [GloveBoxLockStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/glove-box-lock-state-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for the GloveBox lock state updates.

## Functions

[onGloveBoxLockStateUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/glove-box-lock-state-listener/on-glove-box-lock-state-updated)

Link copied to clipboard

abstract fun [onGloveBoxLockStateUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/glove-box-lock-state-listener/on-glove-box-lock-state-updated)(state: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?)

Callback invoked when the GloveBox lock state updates.
