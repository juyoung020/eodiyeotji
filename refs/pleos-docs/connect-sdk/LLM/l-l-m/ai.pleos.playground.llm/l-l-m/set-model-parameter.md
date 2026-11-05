> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/set-model-parameter

# setModelParameter

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.LLM_SERVICE")

fun [setModelParameter](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/set-model-parameter)(type: [ModelParameter](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/entry), value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), listener: [ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/entry))

Sets the model parameters which control the behavior of the LLM model in SERVER mode. This API is only supported when the application is in SERVER mode.

Requires Permission: "pleos.car.permission.LLM_SERVICE"

#### Parameters

*type*

The parameter type.

*value*

The value to set for the parameter.

#### See also

[ModelParameter](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/entry)

#### Throws

[SecurityException](https://docs.oracle.com/javase/8/docs/api/java/lang/SecurityException.html)

if the required permission is not granted.
