> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/send-message

# sendMessage

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.LLM_SERVICE")

fun [sendMessage](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/send-message)(inputContent: [LLMContent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/entry), listener: [ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/entry))

Sends messages in the conversation Users can receive responses to messages

Requires Permission: "pleos.car.permission.LLM_SERVICE"

#### Parameters

*inputContent*

LLMContent: message content

*listener*

ResultListener: result listener

#### See also

[ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/entry)

#### Throws

[SecurityException](https://docs.oracle.com/javase/8/docs/api/java/lang/SecurityException.html)

if the required permission is not granted.
