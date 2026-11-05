> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-route

# requestRoute

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.NAVI_ROUTE_SEARCH")

fun [requestRoute](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-route)(routeInfo: [RouteInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/entry))

Requests route.

Related events such as onRouteStarted may be delivered to the registered listener. The actual events delivered may vary depending on how the Navi app, implemented with the NaviProvider SDK, handles and responds to this request.

This method is protected by permissions.

``` block
Adds permissions in AndroidManifest.xml
Protection level: Normal
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH"/>
```

Content copied to clipboard

#### Parameters

*routeInfo*

: route info

#### See also

[RouteInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/entry)

[NaviHelperEventListener.onRouteStarted](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-started)
