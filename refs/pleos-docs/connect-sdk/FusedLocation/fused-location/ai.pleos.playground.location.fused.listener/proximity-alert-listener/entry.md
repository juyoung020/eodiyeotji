> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/entry

# ProximityAlertListener

interface [ProximityAlertListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/entry)

Receives events and errors for a registered proximity alert.

Reports the alert identifier after registration and transitions for the configured area.

## Functions

[onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-error)

Link copied to clipboard

abstract fun [onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-error)(exception: [Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html))

Invoked when the alert cannot be registered or processed.

[onRegistered](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-registered)

Link copied to clipboard

abstract fun [onRegistered](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-registered)(alertId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))

Invoked when the alert is registered successfully.

[onTransition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-transition)

Link copied to clipboard

abstract fun [onTransition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-transition)(event: [ProximityAlertEvent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-event/entry))

Invoked when a configured proximity transition is detected.
