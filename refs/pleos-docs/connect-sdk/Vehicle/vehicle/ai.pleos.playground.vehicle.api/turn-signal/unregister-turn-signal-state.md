> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/unregister-turn-signal-state

# unregisterTurnSignalState

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_LIGHTS")

abstract fun [unregisterTurnSignalState](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/unregister-turn-signal-state)(listener: [TurnSignalStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/turn-signal-state-listener/entry))

Stops getting property updates for the given turn signal state.

**Permission:** [PleosPermission.CAR_LIGHTS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-l-i-g-h-t-s) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_LIGHTS" />
```

Content copied to clipboard

#### Parameters

*listener*

[TurnSignalStateListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/turn-signal-state-listener/entry): Listener is unregistered.
