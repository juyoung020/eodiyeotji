> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/entry

# FusedLocationListener

interface [FusedLocationListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/entry)

Defines the listener interface for receiving location updates.

## Functions

[onChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/on-changed)

Link copied to clipboard

abstract fun [onChanged](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/on-changed)(location: [LocationData](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/entry)?)

Invoked when a location update is available.

[onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/on-error)

Link copied to clipboard

abstract fun [onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/on-error)(exception: [Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html))
