> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize

# initialize

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize)()

Initializes TextToSpeech SDK When the app starts or is created, it should call the initialization of SDK

------------------------------------------------------------------------

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize)(listener: [OnInitializeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/entry))

Initializes TextToSpeech SDK with a listener for initialization result. When the app starts or is created, it should call the initialization of SDK.

#### Parameters

*listener*

OnInitializeListener: Receives onSuccess when the TTS service is successfully bound, or onFailure with an ErrorType when binding fails.

#### See also

[OnInitializeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/entry)
