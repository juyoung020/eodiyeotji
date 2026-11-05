> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-telltales

# unregisterTelltales

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.PRIVATE_CAR_SAFETY")

abstract fun [unregisterTelltales](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-telltales)(listener: [TelltaleInfoListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/telltale-info-listener/entry))

Unregisters a previously registered Telltales listener.

**Permission:** [PleosPermission.PRIVATE_CAR_SAFETY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-s-a-f-e-t-y) (protection level: signature\|privileged)

``` block
<uses-permission android:name="pleos.car.permission.PRIVATE_CAR_SAFETY" />
```

Content copied to clipboard

#### Parameters

*listener*

[TelltaleInfoListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/telltale-info-listener/entry): The listener to unregister from telltale updates.
