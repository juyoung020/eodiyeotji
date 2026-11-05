> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/initialize

# initialize

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/initialize)()

Initializes NaviHelper SDK. When the app starts or is created, it should call the initialization of SDK.

------------------------------------------------------------------------

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/initialize)(onConnected: () -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-throwable/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Initializes NaviHelper SDK with connection callbacks. Success is called when the first service connection is established. Failure is called when the initial connection retries are exhausted.
