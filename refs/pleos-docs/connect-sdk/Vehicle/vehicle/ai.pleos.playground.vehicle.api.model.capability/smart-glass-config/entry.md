> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/entry

# SmartGlassConfig

data class [SmartGlassConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/entry)(val zoneCount: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val levelCount: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))

Describes the smart glass zones and levels for a window area.

## Constructors

[SmartGlassConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/-smart-glass-config)

Link copied to clipboard

constructor(zoneCount: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), levelCount: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))

## Properties

[levelCount](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/level-count)

Link copied to clipboard

val [levelCount](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/level-count): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)

Number of smart glass levels reported by the vehicle. This value does not indicate smart glass level support; check [ai.pleos.playground.vehicle.api.Window.checkSmartGlassLevelCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/check-smart-glass-level-capabilities) separately.

[zoneCount](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/zone-count)

Link copied to clipboard

val [zoneCount](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/zone-count): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)

Number of smart glass zones (panels) in the window area. Pass this many commands to [ai.pleos.playground.vehicle.api.Window.setSmartGlassZoneState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-smart-glass-zone-state).
