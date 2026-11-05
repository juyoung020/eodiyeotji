> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/entry

# LLMContent

data class [LLMContent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/entry)(val role: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = "user", var parts: [MutableList](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-mutable-list/index.html)\<[Part](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/part/entry)\> = arrayListOf())

Multi-turn data class.

## Constructors

[LLMContent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/-l-l-m-content)

Link copied to clipboard

constructor(role: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = "user", parts: [MutableList](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-mutable-list/index.html)\<[Part](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/part/entry)\> = arrayListOf())

## Properties

[parts](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/parts)

Link copied to clipboard

var [parts](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/parts): [MutableList](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-mutable-list/index.html)\<[Part](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/part/entry)\>

Data in a conversation.

[role](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/role)

Link copied to clipboard

val [role](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/role): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)

The role associated with the content and received from the model.
