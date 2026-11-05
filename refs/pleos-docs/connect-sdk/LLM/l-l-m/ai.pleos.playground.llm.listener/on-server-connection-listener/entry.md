> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/entry

# OnServerConnectionListener

interface [OnServerConnectionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/entry)

EIC server connection result listener

## Functions

[onConnected](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/on-connected)

Link copied to clipboard

abstract fun [onConnected](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/on-connected)()

Called when connected to the server

[onFailed](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/on-failed)

Link copied to clipboard

abstract fun [onFailed](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/on-failed)(msg: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html))

Called when connecting to the server fails
