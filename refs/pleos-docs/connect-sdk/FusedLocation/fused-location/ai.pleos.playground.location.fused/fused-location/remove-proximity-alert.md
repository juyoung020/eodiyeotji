> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/remove-proximity-alert

# removeProximityAlert

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.FUSED_LOCATION")

fun [removeProximityAlert](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/remove-proximity-alert)(alertId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))

Removes a previously registered proximity alert.

Ignores unknown or already removed alert IDs after checking permission.

Requires the following permission (protection level: dangerous).

``` block
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION"/>
```

Content copied to clipboard

#### Parameters

*alertId*

[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html): Identifier received through [ProximityAlertListener.onRegistered](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-registered).

#### See also

[FusedLocation.addProximityAlert](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/add-proximity-alert)

#### Throws

[SecurityException](https://developer.android.com/reference/kotlin/java/lang/SecurityException.html)

If the required permission is not granted.
