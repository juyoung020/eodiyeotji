> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/is-supported

# isSupported

fun [isSupported](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/is-supported)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)

Returns whether the Fused Location Service package is present on Pleos Connect.

This check is independent of SDK initialization and service connection state. It does not start or bind the service.

#### Return

[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html): `true` if the service package lookup succeeds; `false` if the package is absent or the lookup fails.
