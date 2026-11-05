> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entry

# NavigationStatus

@Serializable

enum [NavigationStatus](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entry) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-enum/index.html)\<[NavigationStatus](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entry)\>

## Entries

[INACTIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/i-n-a-c-t-i-v-e/entry)

Link copied to clipboard

[INACTIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/i-n-a-c-t-i-v-e/entry)

The navigation is not functioning or has completely shut down.

[INITIALIZING](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/i-n-i-t-i-a-l-i-z-i-n-g/entry)

Link copied to clipboard

[INITIALIZING](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/i-n-i-t-i-a-l-i-z-i-n-g/entry)

The navigation service or map UI is being prepared. This includes initialization of settings, map rendering, and GPS sensor preparation.

[FREE_DRIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/f-r-e-e_-d-r-i-v-e/entry)

Link copied to clipboard

[FREE_DRIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/f-r-e-e_-d-r-i-v-e/entry)

A state where only the map is shown following the user's location without route guidance. Normal driving mode.

[ROUTE_DRIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/r-o-u-t-e_-d-r-i-v-e/entry)

Link copied to clipboard

[ROUTE_DRIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/r-o-u-t-e_-d-r-i-v-e/entry)

A state where the user has set a destination and route guidance is activated. Real-time navigation is in operation.

## Properties

[entries](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entries)

Link copied to clipboard

val [entries](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entries): [EnumEntries](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.enums/-enum-entries/index.html)\<[NavigationStatus](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entry)\>

Returns a representation of an immutable list of all enum entries, in the order they're declared.

## Functions

[valueOf](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/value-of)

Link copied to clipboard

fun [valueOf](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/value-of)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [NavigationStatus](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entry)

Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.)

[values](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/values)

Link copied to clipboard

fun [values](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/values)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-array/index.html)\<[NavigationStatus](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entry)\>

Returns an array containing the constants of this enum type, in the order they're declared.
