> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/register-app

# registerApp

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [registerApp](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/register-app)(clientID: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), clientSecret: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), listener: [OnServerConnectionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/on-server-connection-listener/entry))

Registers the application to use the server-oriented or hybrid SpeechToText service. Requires the caller to have STT_PERMISSION.

#### Parameters

*clientID*

Received after your app is registered in Pleos Playground.

*clientSecret*

Received after your app is registered in Pleos Playground.

#### See also

[OnServerConnectionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/on-server-connection-listener/entry)

Gets the completion event after connecting to the server.
