> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry

# NavigationScreenRequest

@Serializable

sealed class [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Sealed class representing a request to switch the navigation screen.

#### Inheritors

[Foreground](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/foreground/entry)

[FavoriteMain](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/favorite-main/entry)

[AddHomeFavorite](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/add-home-favorite/entry)

[AddWorkFavorite](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/add-work-favorite/entry)

[NearbyChargingStations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/nearby-charging-stations/entry)

[Settings](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/settings/entry)

[MoveToCurrentLocationOnMap](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/move-to-current-location-on-map/entry)

[OverviewRoute](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/overview-route/entry)

[SearchPlace](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/search-place/entry)

[POIDetailPopup](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/entry)

[RecentDestinations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/recent-destinations/entry)

[RouteOptionList](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/route-option-list/entry)

[EditRoute](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/edit-route/entry)

## Types

[AddHomeFavorite](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/add-home-favorite/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "AddHomeFavorite")

data object [AddHomeFavorite](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/add-home-favorite/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Add to home favorites screen

[AddWorkFavorite](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/add-work-favorite/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "AddWorkFavorite")

data object [AddWorkFavorite](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/add-work-favorite/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Add to work favorites screen

[EditRoute](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/edit-route/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "EditRoute")

data object [EditRoute](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/edit-route/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Route editing screen

[FavoriteMain](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/favorite-main/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "FavoriteMain")

data object [FavoriteMain](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/favorite-main/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Main favorites screen

[Foreground](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/foreground/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "Foreground")

data object [Foreground](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/foreground/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Navigation Foreground

[MoveToCurrentLocationOnMap](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/move-to-current-location-on-map/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "MoveToCurrentLocationOnMap")

data object [MoveToCurrentLocationOnMap](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/move-to-current-location-on-map/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Current location screen on map

[NearbyChargingStations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/nearby-charging-stations/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "NearbyChargingStations")

data object [NearbyChargingStations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/nearby-charging-stations/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Nearby charging stations search screen

[OverviewRoute](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/overview-route/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "OverviewRoute")

data class [OverviewRoute](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/overview-route/entry)(val info: [RouteOverviewShowInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-overview-show-info/entry)) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

View entire route

[POIDetailPopup](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "POIDetailPopup")

data class [POIDetailPopup](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/entry)(val poiId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val poiProvider: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = "") : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

POI details popup screen

[RecentDestinations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/recent-destinations/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "RecentDestinations")

data object [RecentDestinations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/recent-destinations/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Recent destinations screen

[RouteOptionList](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/route-option-list/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "RouteOptionList")

data object [RouteOptionList](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/route-option-list/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Route option list screen

[SearchPlace](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/search-place/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "SearchPlace")

data class [SearchPlace](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/search-place/entry)(val keyword: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Place search screen

[Settings](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/settings/entry)

Link copied to clipboard

@Serializable

@SerialName(value = "Settings")

data object [Settings](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/settings/entry) : [NavigationScreenRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/entry)

Navigation settings screen
