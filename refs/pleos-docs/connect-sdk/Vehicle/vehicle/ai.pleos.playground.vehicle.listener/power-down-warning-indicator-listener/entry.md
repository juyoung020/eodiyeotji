> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/power-down-warning-indicator-listener/entry

# PowerDownWarningIndicatorListener

interface [PowerDownWarningIndicatorListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/power-down-warning-indicator-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for the PowerDownWarningIndicator updates.

## Functions

[onPowerDownWarningIndicatorUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/power-down-warning-indicator-listener/on-power-down-warning-indicator-updated)

Link copied to clipboard

abstract fun [onPowerDownWarningIndicatorUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/power-down-warning-indicator-listener/on-power-down-warning-indicator-updated)(state: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?)

Callback invoked when the PowerDownWarningIndicator updates.
