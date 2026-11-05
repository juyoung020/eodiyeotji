> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/etcs-voice-guidance-volume-listener/entry

# EtcsVoiceGuidanceVolumeListener

interface [EtcsVoiceGuidanceVolumeListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/etcs-voice-guidance-volume-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Interface definition for ETCS voice guidance volume updates.

## Functions

[onEtcsVoiceGuidanceVolumeUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/etcs-voice-guidance-volume-listener/on-etcs-voice-guidance-volume-updated)

Link copied to clipboard

abstract fun [onEtcsVoiceGuidanceVolumeUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/etcs-voice-guidance-volume-listener/on-etcs-voice-guidance-volume-updated)(volume: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?)

Callback invoked when ETCS voice guidance volume updates.
