> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry

# Mode

enum [Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-enum/index.html)\<[Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry)\>

## Entries

[SERVER](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/s-e-r-v-e-r/entry)

Link copied to clipboard

[SERVER](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/s-e-r-v-e-r/entry)

Server-oriented Streaming TextToSpeech will be running. The application gets an error event if the network connection fails.

[ON_DEVICE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/o-n_-d-e-v-i-c-e/entry)

Link copied to clipboard

[ON_DEVICE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/o-n_-d-e-v-i-c-e/entry)

OnDevice-oriented Streaming TextToSpeech will be running. The application doesn't need to be registered to Pleos Playground if it only uses ON_DEVICE mode.

[HYBRID](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/h-y-b-r-i-d/entry)

Link copied to clipboard

[HYBRID](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/h-y-b-r-i-d/entry)

Server-oriented Streaming TextToSpeech will be running. In case of network failure, SDK will stream TextToSpeech audio to the on-device engine automatically.

## Properties

[entries](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entries)

Link copied to clipboard

val [entries](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entries): [EnumEntries](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.enums/-enum-entries/index.html)\<[Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry)\>

Returns a representation of an immutable list of all enum entries, in the order they're declared.

## Functions

[valueOf](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/value-of)

Link copied to clipboard

fun [valueOf](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/value-of)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry)

Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.)

[values](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/values)

Link copied to clipboard

fun [values](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/values)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-array/index.html)\<[Mode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entry)\>

Returns an array containing the constants of this enum type, in the order they're declared.
