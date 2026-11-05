> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/remove-waypoint

# removeWaypoint

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.NAVI_ROUTE_SEARCH")

fun [removeWaypoint](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/remove-waypoint)(waypointIndex: [WaypointIndex](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/entry))

Removes waypoint.

Related events such as onWaypointChanged may be delivered to the registered listener. The actual events delivered may vary depending on how the Navi app, implemented with the NaviProvider SDK, handles and responds to this request.

This method is protected by permissions.

``` block
Adds permissions in AndroidManifest.xml
Protection level: Normal
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH"/>
```

Content copied to clipboard

#### Parameters

*waypointIndex*

: waypoint index

#### See also

[WaypointIndex](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/entry)

[NaviHelperEventListener.onWaypointChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-waypoint-changed)
