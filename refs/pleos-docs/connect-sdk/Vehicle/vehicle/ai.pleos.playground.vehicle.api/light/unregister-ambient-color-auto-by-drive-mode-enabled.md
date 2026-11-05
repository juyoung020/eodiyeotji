> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-ambient-color-auto-by-drive-mode-enabled

# unregisterAmbientColorAutoByDriveModeEnabled

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_LIGHTS")

abstract fun [unregisterAmbientColorAutoByDriveModeEnabled](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-ambient-color-auto-by-drive-mode-enabled)(listener: [AmbientColorAutoByDriveModeEnabledListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ambient-color-auto-by-drive-mode-enabled-listener/entry))

Unregisters a previously registered Ambient Color Auto By Drive Mode Enabled listener.

**Permission:** [PleosPermission.CAR_LIGHTS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-l-i-g-h-t-s) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_LIGHTS" />
```

Content copied to clipboard

#### Parameters

*listener*

[AmbientColorAutoByDriveModeEnabledListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ambient-color-auto-by-drive-mode-enabled-listener/entry): The listener to unregister.
