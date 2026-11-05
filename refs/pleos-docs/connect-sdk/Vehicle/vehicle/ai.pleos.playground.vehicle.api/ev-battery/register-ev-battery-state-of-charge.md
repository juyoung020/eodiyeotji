> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/register-ev-battery-state-of-charge

# registerEvBatteryStateOfCharge

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_ENERGY")

abstract fun [registerEvBatteryStateOfCharge](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/register-ev-battery-state-of-charge)(listener: [EvBatteryStateOfChargeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ev-battery-state-of-charge-listener/entry))

Registers a listener for EV battery state of charge updates expressed as a percentage (%).

**Permission:** [PleosPermission.CAR_ENERGY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-e-n-e-r-g-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
```

Content copied to clipboard

#### Parameters

*listener*

[EvBatteryStateOfChargeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ev-battery-state-of-charge-listener/entry): The listener to receive EV battery state of charge updates as a percentage (%).
