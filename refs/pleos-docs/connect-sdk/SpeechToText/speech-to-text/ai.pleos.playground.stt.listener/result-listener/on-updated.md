> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated

# onUpdated

abstract fun [onUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated)(stt: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), completed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))

Called when the SpeechToText result is updated.

#### Parameters

*stt*

The resulting text from speech recognition

*completed*

: The completion event of speech recognition. Once received, the application stops streaming audio.
