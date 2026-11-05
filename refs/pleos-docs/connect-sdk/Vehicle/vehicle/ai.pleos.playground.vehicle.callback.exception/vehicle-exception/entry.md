> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/entry

# VehicleException

class [VehicleException](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/entry)(error: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null) : [Exception](https://developer.android.com/reference/kotlin/java/lang/Exception.html)

Exception type used by the vehicle SDK.

#### Parameters

*error*

The error value used to create the exception.

## Constructors

[VehicleException](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/-vehicle-exception)

Link copied to clipboard

constructor(error: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null)

## Properties

[errorCode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/error-code)

Link copied to clipboard

val [errorCode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/error-code): [ErrorCode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/entry)

[message](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/message)

Link copied to clipboard

open override val [message](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/message): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)
