> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/entry

# TextToSpeech

class [TextToSpeech](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/entry)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), mode: [Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry) = Mode.HYBRID)

Provides access to the TextToSpeech service. This SDK allows access to the TextToSpeech.

Important: the caller MUST invoke release() when it is no longer needed and the application is destroyed.

Streams audio to remote servers or on-device engines to perform TextToSpeech.

Please note that the application must register your application in Pleos Playground to use the server-oriented speech synthesizer.

#### Parameters

*context*

Application context

*mode*

The user can set the mode that they want. We support three modes. HYBRID, SERVER, and ON_DEVICE. If the user doesn't select anything, HYBRID will be set automatically.

#### See also

[Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry)

## Constructors

[TextToSpeech](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/-text-to-speech)

Link copied to clipboard

constructor(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), mode: [Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry) = Mode.HYBRID)

## Types

[Companion](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/companion/entry)

Link copied to clipboard

object [Companion](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/companion/entry)

## Functions

[addEventListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/add-event-listener)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [addEventListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/add-event-listener)(listener: [EventListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/entry))

Adds a listener that will be notified of various events related to TextToSpeech

[initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize)()

Initializes TextToSpeech SDK When the app starts or is created, it should call the initialization of SDK

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize)(listener: [OnInitializeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/entry))

Initializes TextToSpeech SDK with a listener for initialization result. When the app starts or is created, it should call the initialization of SDK.

[registerApp](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/register-app)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [registerApp](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/register-app)(clientID: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), clientSecret: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), listener: [OnServerConnectionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-server-connection-listener/entry))

Register app

[release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/release)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/release)()

Releases TextToSpeech SDK When the app terminates or is destroyed, it should call the release of SDK

[removeEventListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/remove-event-listener)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [removeEventListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/remove-event-listener)(listener: [EventListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/entry))

Removes a previously registered listener

[setAudioAttributes](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-audio-attributes)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [setAudioAttributes](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-audio-attributes)(audioAttributes: [AudioAttributes](https://developer.android.com/reference/kotlin/android/media/AudioAttributes.html))

Sets the audio attributes to be used for TextToSpeech SDK

[setAudioZoneId](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-audio-zone-id)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [setAudioZoneId](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-audio-zone-id)(zoneId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))

Update audio zone id that user want to set. Requires the caller to have TTS_PERMISSION.

[setLocale](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-locale)

Link copied to clipboard

fun [setLocale](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-locale)(locale: [Locale](https://developer.android.com/reference/kotlin/java/util/Locale.html))

Updates the locale that the user wants to set. Requires the caller to have TTS_PERMISSION.

[setSpeakerType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-speaker-type)

Link copied to clipboard

fun [setSpeakerType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-speaker-type)(type: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))

Updates the speaker type (voice) used for speech synthesis. Requires the caller to have TTS_PERMISSION. If this method is not called, the speaker previously saved for the app is used. If no speaker has been saved for the app, the Gleo AI speaker setting is used. If neither is set, `haeun` is used.

[speak](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/speak)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [speak](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/speak)(text: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), isFinal: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true)

Converts Text-To-Speech and speaks the text

[stop](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/stop)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [stop](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/stop)()

Interrupts the current TextToSpeech playback
