> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/release

# release

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.LLM_SERVICE")

fun [release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/release)()

Release LLM SDK When the app terminates or is destroyed, it should call the release of SDK

Requires Permission: "pleos.car.permission.LLM_SERVICE"

#### Throws

[SecurityException](https://docs.oracle.com/javase/8/docs/api/java/lang/SecurityException.html)

if the required permission is not granted.
