> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-tilt-position

# registerSeatTiltPosition

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_SEATS")

abstract fun [registerSeatTiltPosition](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-tilt-position)(area: [SeatArea](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/entry), listener: [SeatTiltPositionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-tilt-position-listener/entry))

Registers the SeatTiltPosition to get property updates.

**Permission:** [PleosPermission.CAR_SEATS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-s-e-a-t-s) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_SEATS" />
```

Content copied to clipboard

#### Parameters

*area*

The seat area for the vehicle.

*listener*

[SeatTiltPositionListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-tilt-position-listener/entry): Listener is registered.
