> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ev-battery-state-of-charge-listener/entry

# EvBatteryStateOfChargeListener

interface [EvBatteryStateOfChargeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ev-battery-state-of-charge-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for the EV battery state of charge updates.

## Functions

[onEvBatteryStateOfChargeUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ev-battery-state-of-charge-listener/on-ev-battery-state-of-charge-updated)

Link copied to clipboard

abstract fun [onEvBatteryStateOfChargeUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ev-battery-state-of-charge-listener/on-ev-battery-state-of-charge-updated)(value: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?)

Callback invoked with the updated EV battery state of charge as a percentage (%).
