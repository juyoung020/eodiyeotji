> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/unregister-fuel-percent

# unregisterFuelPercent

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_ENERGY")

abstract fun [unregisterFuelPercent](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/unregister-fuel-percent)(listener: [FuelPercentListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/fuel-percent-listener/entry))

Unregisters Fuel Percent listener.

**Permission:** [PleosPermission.CAR_ENERGY](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-e-n-e-r-g-y) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
```

Content copied to clipboard

#### Parameters

*listener*

[FuelPercentListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/fuel-percent-listener/entry): The listener to unregister.
