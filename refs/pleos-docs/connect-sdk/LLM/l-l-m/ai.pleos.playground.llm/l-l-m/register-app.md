> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/register-app

# registerApp

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.LLM_SERVICE")

fun [registerApp](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/register-app)(clientID: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), clientSecret: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), listener: [OnServerConnectionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/entry))

Registers the app in SERVER mode.

#### Parameters

*clientID*

Received after your app is registered in the Connect Gateway Portal.

*clientSecret*

Received after your app is registered in the Connect Gateway Portal.

*listener*

OnServerConnectionListener: EIC service connected successfully event

#### See also

ServiceType

[OnServerConnectionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/entry)
