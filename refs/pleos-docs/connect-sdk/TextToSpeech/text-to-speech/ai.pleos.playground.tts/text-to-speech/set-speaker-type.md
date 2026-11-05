> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-speaker-type

# setSpeakerType

fun [setSpeakerType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-speaker-type)(type: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))

Updates the speaker type (voice) used for speech synthesis. Requires the caller to have TTS_PERMISSION. If this method is not called, the speaker previously saved for the app is used. If no speaker has been saved for the app, the Gleo AI speaker setting is used. If neither is set, `haeun` is used.

#### Parameters

*type*

String: Speaker type to use. Available speakers by language:

- Korean: seoyoon, eunwoo, haeun, sanghyeok

- English: lyra, finley

- Spanish: iris, arlo

- French: sabine, noah
