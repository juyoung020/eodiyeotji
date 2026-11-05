> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/entry

# OnInitializeListener

interface [OnInitializeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/entry)

Listener for the result of TextToSpeech SDK initialization.

#### See also

[TextToSpeech.initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize)

## Functions

[onFailure](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/on-failure)

Link copied to clipboard

abstract fun [onFailure](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/on-failure)(errorType: [ErrorType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/entry))

Called when the SDK initialization has failed.

[onSuccess](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/on-success)

Link copied to clipboard

abstract fun [onSuccess](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/on-success)()

Called when the SDK has been successfully initialized and bound to the TTS service.
