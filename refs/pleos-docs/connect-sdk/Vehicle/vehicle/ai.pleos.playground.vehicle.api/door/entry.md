> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry

# Door

interface [Door](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry)

Provides APIs for door lock, door position, and glove box.

Obtain an instance from [ai.pleos.playground.vehicle.Vehicle.getDoor](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-door).

## Functions

[checkDoorLockCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-lock-capabilities)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [~~checkDoorLockCapabilities~~](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-lock-capabilities)(onComplete: ([List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry)\>) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

\

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [checkDoorLockCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-lock-capabilities)(onComplete: ([CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry), [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)\<[DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry)\>) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets DoorLock capabilities.

[checkDoorPositionCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-position-capabilities)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [~~checkDoorPositionCapabilities~~](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-position-capabilities)(onComplete: ([Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-map/index.html)\<[DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), [AreaBounds](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/entry)\<[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)\>\>) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

\

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [checkDoorPositionCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-position-capabilities)(onComplete: ([CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry), [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-map/index.html)\<[DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), [AreaBounds](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/entry)\<[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)\>\>) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets DoorPosition capabilities.

[checkGloveBoxLockCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-glove-box-lock-capabilities)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [checkGloveBoxLockCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-glove-box-lock-capabilities)(onComplete: ([CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets the glove box lock capabilities.

[getCurrentDoorPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-door-position)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [getCurrentDoorPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-door-position)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), onComplete: ([Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets the current door position state.

[getCurrentGloveBoxLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-glove-box-lock-state)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [getCurrentGloveBoxLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-glove-box-lock-state)(onComplete: ([Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets the current glove lock state.

[getDoorLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-door-lock-state)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [getDoorLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-door-lock-state)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), onComplete: ([Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html))

Gets current door lock state.

[registerDoorLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-door-lock-state)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [registerDoorLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-door-lock-state)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), listener: [DoorLockStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/door-lock-state-listener/entry))

Registers the door lock state to get property updates.

[registerDoorPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-door-position)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [registerDoorPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-door-position)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), listener: [DoorPositionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/door-position-listener/entry))

Registers the door area to get property updates.

[registerGloveBoxLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-glove-box-lock-state)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [registerGloveBoxLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-glove-box-lock-state)(listener: [GloveBoxLockStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/glove-box-lock-state-listener/entry))

Registers the glove box lock state to get property updates.

[setDoorLock](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-door-lock)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CONTROL_CAR_DOORS")

abstract fun [setDoorLock](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-door-lock)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), value: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), onComplete: ([DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html) = { \_, \_ -\> }, onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html) = {})

Sets the current door lock state.

[setDoorPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-door-position)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CONTROL_CAR_DOORS")

abstract fun [setDoorPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-door-position)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), value: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), onComplete: ([DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html) = { \_, \_ -\> }, onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html) = {})

Sets the current door position state. The maximum (fully open) value is vehicle-dependent and is not fixed. Call [checkDoorPositionCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-position-capabilities) first. That API's callback returns [CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry) plus a [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-map/index.html)\<[DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), [AreaBounds](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/entry)\<[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)\>\>; read each area's [AreaBounds.minValue](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/min-value)/[AreaBounds.maxValue](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/max-value) from that map (not from [CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry)). Min is typically 0 for fully closed.

[setGloveBoxLock](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-glove-box-lock)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CONTROL_CAR_DOORS")

abstract fun [setGloveBoxLock](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-glove-box-lock)(value: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), onComplete: ([Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)?) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html) = {}, onFailed: ([Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-exception/index.html)) -\> [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html) = {})

Sets the current glove lock state.

[unregisterDoorLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-door-lock-state)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [unregisterDoorLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-door-lock-state)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), listener: [DoorLockStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/door-lock-state-listener/entry))

Stops getting property updates for the given door lock state.

[unregisterDoorPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-door-position)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [unregisterDoorPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-door-position)(area: [DoorArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/entry), listener: [DoorPositionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/door-position-listener/entry))

Stops getting property updates for the given door area.

[unregisterGloveBoxLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-glove-box-lock-state)

Link copied to clipboard

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DOORS")

abstract fun [unregisterGloveBoxLockState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-glove-box-lock-state)(listener: [GloveBoxLockStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/glove-box-lock-state-listener/entry))

Stops getting property updates for the given glove box lock state.
