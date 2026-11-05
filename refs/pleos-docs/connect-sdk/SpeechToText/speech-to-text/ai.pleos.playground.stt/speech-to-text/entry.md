> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/entry

# SpeechToText

class [SpeechToText](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/entry)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), mode: [Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/entry) = Mode.HYBRID)

Provides access to the SpeechToText service. This SDK allows access to the SpeechToText.

Important: the caller MUST invoke release() when it is no longer needed and the application is destroyed.

Streams audio to remote servers or on-device engines to perform SpeechToText.

Please note that the application must be registered in Pleos Playground to use the server-oriented speech recognizer.

#### Parameters

*context*

Application context

*mode*

The user can set the mode that they want. We support three modes. HYBRID, SERVER, and ON_DEVICE. If the user doesn't select anything, HYBRID will be set automatically.

#### See also

[Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/entry)

## Constructors

[SpeechToText](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/-speech-to-text)

Link copied to clipboard

constructor(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), mode: [Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/entry) = Mode.HYBRID)

## Types

[Companion](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/companion/entry)

Link copied to clipboard

object [Companion](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/companion/entry)

## Functions

[addListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/add-listener)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [addListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/add-listener)(listener: [ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/entry))

Adds a listener that will be notified of various events related to SpeechToText Requires the caller to have STT_PERMISSION.

[completeAudioSend](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/complete-audio-send)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [completeAudioSend](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/complete-audio-send)()

Completes the external audio buffer input started by [sendAudio](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/send-audio) and finishes recognition. Available only in [Mode.ON_DEVICE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/o-n_-d-e-v-i-c-e/entry). In other modes the call is ignored. Requires the caller to have STT_PERMISSION.

[initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/initialize)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/initialize)()

Initializes SDK to use SpeechToText whether the application is created or not. Requires the caller to have STT_PERMISSION.

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/initialize)(listener: [OnInitializeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/on-initialize-listener/entry))

Initializes SDK to use SpeechToText with a listener for initialization result. Requires the caller to have STT_PERMISSION.

[registerApp](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/register-app)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [registerApp](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/register-app)(clientID: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), clientSecret: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), listener: [OnServerConnectionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/on-server-connection-listener/entry))

Registers the application to use the server-oriented or hybrid SpeechToText service. Requires the caller to have STT_PERMISSION.

[release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/release)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/release)()

Releases SDK whether the application is destroyed or not. Requires the caller to have STT_PERMISSION.

[removeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/remove-listener)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [removeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/remove-listener)(listener: [ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/entry))

Removes a previously registered listener Requires the caller to have STT_PERMISSION.

[request](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/request)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [request](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/request)()

Requests for starting speech recognition. After the application invoke request(), microphone will be open, and then you can stream your audio through microphone.

[sendAudio](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/send-audio)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [sendAudio](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/send-audio)(bytes: [ByteArray](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-byte-array/index.html))

Sends an external audio buffer (PCM) for recognition. Available only in [Mode.ON_DEVICE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/o-n_-d-e-v-i-c-e/entry). In other modes the call is ignored and [ResultListener.onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-error) is not called. Recognition starts when the first buffer is sent; calling [request](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/request) is not required. In this path, [ResultListener.onReady](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-ready) and [ResultListener.onUpdatedRms](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated-rms) are not called. Call [completeAudioSend](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/complete-audio-send) after sending the last buffer. Requires the caller to have STT_PERMISSION.

[setLocale](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/set-locale)

Link copied to clipboard

fun [setLocale](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/set-locale)(locale: [Locale](https://developer.android.com/reference/kotlin/java/util/Locale.html))

Updates the locale that the user wants to set. Requires the caller to have STT_PERMISSION.

[stop](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/stop)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [stop](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/stop)()

Stops speech recognition. After the application invoke stop(), the microphone will be disabled.
