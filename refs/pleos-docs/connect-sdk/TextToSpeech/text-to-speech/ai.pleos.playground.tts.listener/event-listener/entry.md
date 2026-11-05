> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/entry

# EventListener

interface [EventListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/entry)

Listener for events relating to the progress of Text-To-Speech. Adds and removes with addEventListener/removeEventListener APIs respectively.

## Functions

[onDone](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-done)

Link copied to clipboard

abstract fun [onDone](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-done)()

Called when TextToSpeech has successfully completed processing.

[onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-error)

Link copied to clipboard

abstract fun [onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-error)(errorType: [ErrorType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/entry))

Called when an error has occurred during processing.

[onReady](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-ready)

Link copied to clipboard

abstract fun [onReady](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-ready)()

Called when TextToSpeech speak has been called.

[onStart](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-start)

Link copied to clipboard

abstract fun [onStart](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-start)()

Called when TextToSpeech starts as perceived by the caller.

[onStop](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-stop)

Link copied to clipboard

abstract fun [onStop](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-stop)()

Called when TextToSpeech stops while in progress.

[onUpdatedRms](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-updated-rms)

Link copied to clipboard

abstract fun [onUpdatedRms](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-updated-rms)(rms: [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-double/index.html))

Called when RMS is updated.
