> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entry

# ErrorType

enum [ErrorType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entry) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-enum/index.html)\<[ErrorType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entry)\>

Represents the types of errors that can occur within the system.

## Entries

[AUDIO_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/a-u-d-i-o_-e-r-r-o-r/entry)

Link copied to clipboard

[AUDIO_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/a-u-d-i-o_-e-r-r-o-r/entry)

Audio data processing error (e.g. failed to read the audio buffer).

[MIC_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/m-i-c_-e-r-r-o-r/entry)

Link copied to clipboard

[MIC_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/m-i-c_-e-r-r-o-r/entry)

Microphone input/recording related error.

[SERVER_INTERNAL_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/s-e-r-v-e-r_-i-n-t-e-r-n-a-l_-e-r-r-o-r/entry)

Link copied to clipboard

[SERVER_INTERNAL_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/s-e-r-v-e-r_-i-n-t-e-r-n-a-l_-e-r-r-o-r/entry)

Server-side processing failure (5xx, gRPC internal issues).

[NETWORK_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/n-e-t-w-o-r-k_-e-r-r-o-r/entry)

Link copied to clipboard

[NETWORK_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/n-e-t-w-o-r-k_-e-r-r-o-r/entry)

Network connectivity failure.

[INVALID_REQUEST_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/i-n-v-a-l-i-d_-r-e-q-u-e-s-t_-e-r-r-o-r/entry)

Link copied to clipboard

[INVALID_REQUEST_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/i-n-v-a-l-i-d_-r-e-q-u-e-s-t_-e-r-r-o-r/entry)

Client-side invalid request.

[TIMEOUT](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/t-i-m-e-o-u-t/entry)

Link copied to clipboard

[TIMEOUT](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/t-i-m-e-o-u-t/entry)

Request/response timeout.

[UNAUTHENTICATED](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-a-u-t-h-e-n-t-i-c-a-t-e-d/entry)

Link copied to clipboard

[UNAUTHENTICATED](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-a-u-t-h-e-n-t-i-c-a-t-e-d/entry)

Authentication failure (invalid/expired token).

[PERMISSION_DENIED](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/p-e-r-m-i-s-s-i-o-n_-d-e-n-i-e-d/entry)

Link copied to clipboard

[PERMISSION_DENIED](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/p-e-r-m-i-s-s-i-o-n_-d-e-n-i-e-d/entry)

Permission denied or insufficient access rights.

[ON_DEVICE_ENGINE_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/o-n_-d-e-v-i-c-e_-e-n-g-i-n-e_-e-r-r-o-r/entry)

Link copied to clipboard

[ON_DEVICE_ENGINE_ERROR](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/o-n_-d-e-v-i-c-e_-e-n-g-i-n-e_-e-r-r-o-r/entry)

On-device engine (e.g. Speech SDK) failure.

[UNKNOWN](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-k-n-o-w-n/entry)

Link copied to clipboard

[UNKNOWN](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-k-n-o-w-n/entry)

Unknown error type.

[UNAVAILABLE_LANGUAGE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-a-v-a-i-l-a-b-l-e_-l-a-n-g-u-a-g-e/entry)

Link copied to clipboard

[UNAVAILABLE_LANGUAGE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-a-v-a-i-l-a-b-l-e_-l-a-n-g-u-a-g-e/entry)

Requested language is not available.

[NOT_SUPPORTED_API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/n-o-t_-s-u-p-p-o-r-t-e-d_-a-p-i/entry)

Link copied to clipboard

[NOT_SUPPORTED_API](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/n-o-t_-s-u-p-p-o-r-t-e-d_-a-p-i/entry)

Requested API is not supported.

## Properties

[entries](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entries)

Link copied to clipboard

val [entries](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entries): [EnumEntries](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.enums/-enum-entries/index.html)\<[ErrorType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entry)\>

Returns a representation of an immutable list of all enum entries, in the order they're declared.

## Functions

[valueOf](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/value-of)

Link copied to clipboard

fun [valueOf](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/value-of)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)): [ErrorType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entry)

Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.)

[values](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/values)

Link copied to clipboard

fun [values](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/values)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-array/index.html)\<[ErrorType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entry)\>

Returns an array containing the constants of this enum type, in the order they're declared.
