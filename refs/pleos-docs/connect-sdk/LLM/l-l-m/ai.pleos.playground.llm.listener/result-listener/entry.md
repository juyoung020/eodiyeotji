> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/entry

# ResultListener

interface [ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/entry)

LLM data listener.

## Functions

[onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/on-error)

Link copied to clipboard

abstract fun [onError](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/on-error)(reason: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))

Called when an error occurs.

[onResponse](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/on-response)

Link copied to clipboard

abstract fun [onResponse](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/on-response)(response: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), completed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))

Called when results are updated.
