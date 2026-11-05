> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-destination-info

# getDestinationInfo

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.NAVI_ROUTE")

fun [getDestinationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-destination-info)()

Gets destination info.

Related events such as onDestinationInfo may be delivered to the registered listener. The actual events delivered may vary depending on how the Navi app, implemented with the NaviProvider SDK, handles and responds to this request.

This method is protected by permissions.

``` block
Adds permissions in AndroidManifest.xml
Protection level: Normal
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE"/>
```

Content copied to clipboard

#### See also

[NaviHelperEventListener.onDestinationInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-info)
