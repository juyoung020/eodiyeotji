> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/on-response

# onResponse

abstract fun [onResponse](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/on-response)(response: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), completed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))

Called when results are updated.

#### Parameters

*response*

text result from server

*completed*

variable indicates whether creation is complete. If completed, set to true. Otherwise, set to false.
