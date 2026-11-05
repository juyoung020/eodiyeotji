> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/unregister-etcs-voice-guidance-volume

# unregisterEtcsVoiceGuidanceVolume

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.CAR_TOLL")

abstract fun [unregisterEtcsVoiceGuidanceVolume](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/unregister-etcs-voice-guidance-volume)(listener: [EtcsVoiceGuidanceVolumeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/etcs-voice-guidance-volume-listener/entry))

Stops ETCS voice guidance volume updates.

**Permission:** [PleosPermission.CAR_TOLL](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-t-o-l-l) (protection level: normal)

``` block
<uses-permission android:name="pleos.car.permission.CAR_TOLL" />
```

Content copied to clipboard

#### Parameters

*listener*

[EtcsVoiceGuidanceVolumeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/etcs-voice-guidance-volume-listener/entry): Listener is unregistered.
