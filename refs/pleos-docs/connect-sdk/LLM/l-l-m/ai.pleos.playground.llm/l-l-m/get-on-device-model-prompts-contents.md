> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/get-on-device-model-prompts-contents

# getOnDeviceModelPromptsContents

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.LLM_SERVICE")

fun [getOnDeviceModelPromptsContents](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/get-on-device-model-prompts-contents)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)

Gets the prompt content of the Model This API is only supported when the application is in ON_DEVICE mode.

Requires Permission: "pleos.car.permission.LLM_SERVICE"

#### Return

The current prompt content as a `String`.

#### Throws

[SecurityException](https://docs.oracle.com/javase/8/docs/api/java/lang/SecurityException.html)

if the required permission is not granted.
