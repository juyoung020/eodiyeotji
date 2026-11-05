> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/entry

# ai.pleos.playground.llm.data

## Types

[LLMContent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/entry)

Link copied to clipboard

data class [LLMContent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/entry)(val role: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = "user", var parts: [MutableList](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-mutable-list/index.html)\<[Part](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/part/entry)\> = arrayListOf())

Multi-turn data class.

[ModelParameter](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/entry)

Link copied to clipboard

enum [ModelParameter](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/entry) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-enum/index.html)\<[ModelParameter](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/entry)\>

Enum class representing the available model parameters for the LLM service. This class defines the types of parameters that can be set via the setModelParameter function.

[Part](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/part/entry)

Link copied to clipboard

interface [Part](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/part/entry)

Represents data sent to and received from requests.

[TextPart](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/text-part/entry)

Link copied to clipboard

class [TextPart](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/text-part/entry)(val text: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) : [Part](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/part/entry)

Represents text- or string- based data sent to and received from requests.
