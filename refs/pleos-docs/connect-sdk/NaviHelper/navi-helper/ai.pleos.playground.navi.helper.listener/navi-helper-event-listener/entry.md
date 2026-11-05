> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/entry

# NaviHelperEventListener

interface [NaviHelperEventListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/entry)

Interface definition for callback data received from the Navi application.

## Functions

[onBookMarkChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-book-mark-changed)

Link copied to clipboard

open fun [onBookMarkChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-book-mark-changed)()

Called when arriving at or removing the bookmark.

[onBookmarkInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-bookmark-info)

Link copied to clipboard

open fun [onBookmarkInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-bookmark-info)(bookmarkInfoList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[BookmarkInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/entry)\>)

Called when the bookmark information is changed.

[onCameraAlert](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-camera-alert)

Link copied to clipboard

open fun [onCameraAlert](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-camera-alert)(cameraAlertList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[CameraAlert](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/entry)\>)

Called when camera alerts are notified. Received when speeding camera exists.

[onCautionPointInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-caution-point-info)

Link copied to clipboard

open fun [onCautionPointInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-caution-point-info)(cautionPointList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[CautionPoint](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/caution-point/entry)\>)

Called when caution point information is notified.

[onChargerOperatorInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-charger-operator-info)

Link copied to clipboard

open fun [onChargerOperatorInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-charger-operator-info)(chargerOperatorList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[ChargerOperator](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/charger-operator/entry)\>)

Called when charger operator information is notified.

[onCurrentLocationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-current-location-info)

Link copied to clipboard

open fun [onCurrentLocationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-current-location-info)(currentLocationInfo: [CurrentLocationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/current-location-info/entry))

Called when the current location information is changed.

[onCurrentRoadSpeedLimit](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-current-road-speed-limit)

Link copied to clipboard

open fun [onCurrentRoadSpeedLimit](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-current-road-speed-limit)(speedLimit: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))

Called when the road speed limit is notified. Received every second after the event listener registration.

[onDestinationArrived](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-arrived)

Link copied to clipboard

open fun [onDestinationArrived](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-arrived)(destinationArrivedInfo: [DestinationArrivedInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-arrived-info/entry))

Called when arriving at the destination.

[onDestinationChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-changed)

Link copied to clipboard

open fun [onDestinationChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-changed)()

Called when arriving at or removing the destination.

[onDestinationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-info)

Link copied to clipboard

open fun [onDestinationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-info)(destinationInfo: [DestinationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/entry))

Called when the destination information is changed.

[onDrivingInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-driving-info)

Link copied to clipboard

open fun [onDrivingInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-driving-info)(drivingInfo: [DrivingInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-info/entry))

Called when driving information is notified.

[onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-error)

Link copied to clipboard

open fun [onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-error)(errorCode: [NaviErrorCode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/entry))

Called when a navigation error occurs.

[onNavigationStatus](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-navigation-status)

Link copied to clipboard

open fun [onNavigationStatus](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-navigation-status)(status: [NavigationStatus](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entry))

Called when the navigation status is notified. Received whenever the driving status changes

[onRecentDestinationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-recent-destination-info)

Link copied to clipboard

open fun [onRecentDestinationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-recent-destination-info)(recentDestinationInfoList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[RecentDestinationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/entry)\>)

Called when the recent destination information is changed.

[onReRouteEnded](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-re-route-ended)

Link copied to clipboard

open fun [onReRouteEnded](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-re-route-ended)()

Called when navigation re-route is completed.

[onRouteCancelled](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-cancelled)

Link copied to clipboard

open fun [onRouteCancelled](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-cancelled)()

Called when the current route is canceled.

[onRouteOptionChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-option-changed)

Link copied to clipboard

open fun [onRouteOptionChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-option-changed)(routeOption: [RouteOption](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/entry))

Called when the route type is changed.

[onRouteStarted](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-started)

Link copied to clipboard

open fun [onRouteStarted](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-started)(routeStartInfo: [RouteStartInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/entry))

Called when navigation route search begins.

[onRouteStateInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-state-info)

Link copied to clipboard

open fun [onRouteStateInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-state-info)(routeStateInfo: [RouteStateInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/entry))

Called when the route state information is changed.

[onRoutingCompleted](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-routing-completed)

Link copied to clipboard

open fun [onRoutingCompleted](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-routing-completed)()

Called when navigation route search is completed.

[onTBTInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-t-b-t-info)

Link copied to clipboard

open fun [onTBTInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-t-b-t-info)(tbtInfoList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[TBTInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/t-b-t-info/entry)\>)

Called when the TBT information is notified.

[onTripInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-trip-info)

Link copied to clipboard

open fun [onTripInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-trip-info)(tripInfo: [TripInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-info/entry))

Called when trip information is notified.

[onWayPointArrived](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-way-point-arrived)

Link copied to clipboard

open fun [onWayPointArrived](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-way-point-arrived)(waypointArrivedInfo: [WaypointArrivedInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-arrived-info/entry))

Called when arriving at the waypoint.

[onWaypointChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-waypoint-changed)

Link copied to clipboard

open fun [onWaypointChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-waypoint-changed)(waypointChangedInfo: [WaypointChangedInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/entry))

Called when arriving at or removing the waypoint.

[onWayPointInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-way-point-info)

Link copied to clipboard

open fun [onWayPointInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-way-point-info)(waypointInfoList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[WaypointInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/entry)\>)

Called when the waypoint information is changed.
