> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/add-proximity-alert

# addProximityAlert

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.FUSED_LOCATION")

fun [addProximityAlert](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/add-proximity-alert)(request: [ProximityAlertRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-request/entry), listener: [ProximityAlertListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/entry))

Registers a proximity alert for the specified area and transitions.

Reports the alert ID through [ProximityAlertListener.onRegistered](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-registered) and invalid requests through [ProximityAlertListener.onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-error).

Requires the following permission (protection level: dangerous).

``` block
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION"/>
```

Content copied to clipboard

#### Parameters

*request*

[ProximityAlertRequest](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-request/entry): Area and transitions to monitor.

*listener*

[ProximityAlertListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/entry): Receives alert callbacks.

#### Throws

[SecurityException](https://developer.android.com/reference/kotlin/java/lang/SecurityException.html)

If the required permission is not granted.
