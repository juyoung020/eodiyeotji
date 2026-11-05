> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/entry

# DistanceDrivenListener

interface [DistanceDrivenListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/entry) : [VehicleListener](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/entry)

Listener for distance updates.

The unit depends on the method used to register this listener:

- [ai.pleos.playground.vehicle.api.Odometer.registerDistanceDriven](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-distance-driven): kilometers (km).

- [ai.pleos.playground.vehicle.api.Odometer.registerTripCurrentDrivingDistance](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-current-driving-distance), [ai.pleos.playground.vehicle.api.Odometer.registerTripInstantaneousDrivingDistance](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-instantaneous-driving-distance), [ai.pleos.playground.vehicle.api.Odometer.registerTripCumulativeDrivingDistance](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-cumulative-driving-distance), and [ai.pleos.playground.vehicle.api.Odometer.registerTripSinceRefillDrivingDistance](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-since-refill-driving-distance): meters (m).

## Functions

[onDistanceDrivenUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/on-distance-driven-updated)

Link copied to clipboard

abstract fun [onDistanceDrivenUpdated](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/on-distance-driven-updated)(value: [Float](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-float/index.html)?)

Called with the updated distance in kilometers (km) or meters (m), depending on the registration API.
