> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entry

# SmartGlassZoneCommand

enum [SmartGlassZoneCommand](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entry) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-enum/index.html)\<[SmartGlassZoneCommand](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entry)\>

Represents the command for a single smart glass panel.

Used by [ai.pleos.playground.vehicle.api.Window.setSmartGlassZoneState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-smart-glass-zone-state) with one entry per panel in panel order.

## Entries

[SKIP](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/s-k-i-p/entry)

Link copied to clipboard

[SKIP](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/s-k-i-p/entry)

Requests no command for the panel and leaves it unchanged.

[OFF](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/o-f-f/entry)

Link copied to clipboard

[OFF](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/o-f-f/entry)

Turns the panel off.

[ON](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/o-n/entry)

Link copied to clipboard

[ON](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/o-n/entry)

Turns the panel on.

## Properties

[entries](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entries)

Link copied to clipboard

val [entries](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entries): [EnumEntries](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.enums/-enum-entries/index.html)\<[SmartGlassZoneCommand](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entry)\>

Returns a representation of an immutable list of all enum entries, in the order they're declared.

## Functions

[valueOf](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/value-of)

Link copied to clipboard

fun [valueOf](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/value-of)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [SmartGlassZoneCommand](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entry)

Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.)

[values](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/values)

Link copied to clipboard

fun [values](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/values)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-array/index.html)\<[SmartGlassZoneCommand](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entry)\>

Returns an array containing the constants of this enum type, in the order they're declared.
