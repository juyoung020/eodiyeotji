> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/unregister-fused-location-callback

# unregisterFusedLocationCallback

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.FUSED_LOCATION")

fun [unregisterFusedLocationCallback](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/unregister-fused-location-callback)(listener: [FusedLocationListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/entry))

Stops receiving location updates through the specified listener.

The method is protected by the permission

``` block
Add permission in AndroidManifest.xml
protection level : dangerous
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION"/>
```

Content copied to clipboard

#### Parameters

*listener*

[FusedLocationListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/entry): A Listener registered.
