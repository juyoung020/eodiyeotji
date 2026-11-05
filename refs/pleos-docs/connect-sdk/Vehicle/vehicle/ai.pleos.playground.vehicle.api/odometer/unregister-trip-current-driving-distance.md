> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-current-driving-distance

# unregisterTripCurrentDrivingDistance

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_DRIVE")

abstract fun [unregisterTripCurrentDrivingDistance](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-current-driving-distance)(listener: [DistanceDrivenListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/entry))

Unregisters Trip Current Driving Distance listener.

**Permission:** [PleosPermission.CAR_DRIVE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-d-r-i-v-e) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />
```

Content copied to clipboard

#### Parameters

*listener*

[DistanceDrivenListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/entry): The listener to unregister.
