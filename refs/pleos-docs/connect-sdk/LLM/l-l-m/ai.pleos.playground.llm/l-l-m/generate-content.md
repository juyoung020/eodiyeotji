> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/generate-content

# generateContent

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.LLM_SERVICE")

fun [generateContent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/generate-content)(prompt: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), listener: [ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/entry))

Generates content

Requires Permission: "pleos.car.permission.LLM_SERVICE"

#### Parameters

*prompt*

*listener*

ResultListener: result listener

#### See also

[ResultListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/entry)

#### Throws

[SecurityException](https://docs.oracle.com/javase/8/docs/api/java/lang/SecurityException.html)

if the required permission is not granted.
