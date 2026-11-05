> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/add-listener

# addListener

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [addListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/add-listener)(listener: [ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/entry))

Adds a listener that will be notified of various events related to SpeechToText Requires the caller to have STT_PERMISSION.

#### Parameters

*listener*

ResultListener: SpeechToText result text listener

#### See also

[ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/entry)
