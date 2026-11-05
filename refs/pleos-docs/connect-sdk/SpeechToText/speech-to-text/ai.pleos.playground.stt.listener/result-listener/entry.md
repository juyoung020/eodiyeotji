> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/entry

# ResultListener

interface [ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/entry)

Receives events from SpeechToText when recognition-related events occur. Adds and removes with addListener/removeListener APIs respectively.

## Functions

[onEndedRecognition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-ended-recognition)

Link copied to clipboard

abstract fun [onEndedRecognition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-ended-recognition)()

Called when caller recognition is finished.

[onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-error)

Link copied to clipboard

abstract fun [onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-error)(errorType: [ErrorType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entry))

Called when caller recognition fails.

[onReady](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-ready)

Link copied to clipboard

abstract fun [onReady](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-ready)()

Called when listener recognition is ready.

[onStartedRecognition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-started-recognition)

Link copied to clipboard

abstract fun [onStartedRecognition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-started-recognition)()

Called when caller recognition is started.

[onUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated)

Link copied to clipboard

abstract fun [onUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated)(stt: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), completed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))

Called when the SpeechToText result is updated.

[onUpdatedEpdData](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated-epd-data)

Link copied to clipboard

abstract fun [onUpdatedEpdData](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated-epd-data)(on: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), off: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html))

Called when the SpeechToText EPD result is updated.

[onUpdatedRms](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated-rms)

Link copied to clipboard

abstract fun [onUpdatedRms](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated-rms)(rms: [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-double/index.html))

Called when RMS is updated.
