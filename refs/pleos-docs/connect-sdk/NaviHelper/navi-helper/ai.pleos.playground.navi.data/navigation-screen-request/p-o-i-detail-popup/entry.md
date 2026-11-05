> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/entry

# POIDetailPopup

@Serializable

@SerialName(value = "POIDetailPopup")

data class [POIDetailPopup](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/entry)(val poiId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val poiProvider: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = "") : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

POI details popup screen

## Constructors

[POIDetailPopup](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/-p-o-i-detail-popup)

Link copied to clipboard

constructor(poiId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), poiProvider: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = "")

## Properties

[poiId](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/poi-id)

Link copied to clipboard

val [poiId](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/poi-id): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)

POI ID is a unique identifier for a specific POI on a map

[poiProvider](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/poi-provider)

Link copied to clipboard

val [poiProvider](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/poi-provider): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)

POI Content Provider Name (e.g., 42dot, Google)
