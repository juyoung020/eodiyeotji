> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-belt-height-position

# unregisterSeatBeltHeightPosition

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_SEATS")

abstract fun [unregisterSeatBeltHeightPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-belt-height-position)(area: [SeatArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/entry), listener: [SeatBeltHeightPositionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-belt-height-position-listener/entry))

Stops getting property updates for the given SeatBeltHeightPosition.

**Permission:** [PleosPermission.CAR_SEATS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-s-e-a-t-s) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_SEATS" />
```

Content copied to clipboard

#### Parameters

*area*

The seat area for the vehicle.

*listener*

[SeatBeltHeightPositionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-belt-height-position-listener/entry): Listener is unregistered.
