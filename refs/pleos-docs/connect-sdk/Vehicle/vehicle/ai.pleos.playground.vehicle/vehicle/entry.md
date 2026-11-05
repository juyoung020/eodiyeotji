> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry

# Vehicle

class [Vehicle](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html))

The entry point of the Vehicle SDK.

Create an instance with `Vehicle(context)`, then call [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/initialize) to connect to the vehicle service. Use factory methods such as [getDoor](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-door) and [getDisplay](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-display) to obtain domain API objects ([Door](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry), [Display](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/display/entry), and other interfaces in `ai.pleos.playground.vehicle.api`), then call property, capability, or callback APIs on those objects. Call [release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/release) when you no longer need the SDK.

Typical flow:

1.  `Vehicle(context)` → [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/initialize)

2.  `getXxx()` → domain API (for example [Door](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry))

3.  Capability / get / set / callback on that domain API

4.  [release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/release)

#### Parameters

*context*

Android [android.content.Context](https://developer.android.com/reference/kotlin/android/content/Context.html) used to bind to the vehicle service.

### Kotlin

**Get Property API example**

``` block
// Create Vehicle instance
val vehicle = Vehicle(context)

// Start Vehicle
vehicle.initialize()

val door = vehicle.getDoor()
door.getCurrentDoorPosition(
  area = DoorArea(DoorValue.ROW_1_LEFT),
  onComplete = { pos: Int? ->
    // do something
  },
  onFailed = { e: Exception ->
    // do something
  }
)

val display = vehicle.getDisplay()
display.getCurrentSpeedDisplayUnit(
  onComplete = { unit: SpeedDisplayUnit? ->
    // do something
  },
  onFailed = { e: Exception ->
    // do something
  }
)

// End Vehicle
vehicle.release()
```

Content copied to clipboard

**Set Property API example**

``` block
// Create Vehicle instance
val vehicle = Vehicle(context)

// Start Vehicle
vehicle.initialize()

val door = vehicle.getDoor()
door.setDoorPosition(
  area = DoorArea(DoorValue.ROW_1_LEFT),
  value = 1, // Integer type
  onFailed = { e: Exception ->
    // do something
  }
)

// End Vehicle
vehicle.release()
```

Content copied to clipboard

**Callback API example**

``` block
// Create Vehicle instance
val vehicle = Vehicle(context)

// Start Vehicle
vehicle.initialize()

// Create callback listener
val callbackListener = object : GearSelectionListener {
  override fun onSelected(selection: VehicleGear?) {
    // do something
  }

  override fun onFailed(e: Exception) {
    // do something
  }
}

val gear = vehicle.getGear()

// register callback
gear.registerGearSelection(
  gearSelectionListener = callbackListener
)
// unregister callback
gear.unregisterGearSelection(
  gearSelectionListener = callbackListener
)

// End Vehicle
vehicle.release()
```

Content copied to clipboard

**Capability API example**

Check whether a property is supported on the vehicle and inspect its [ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/entry) (for example access type and value bounds) before get/set calls.

``` block
// Create Vehicle instance
val vehicle = Vehicle(context)

// Start Vehicle
vehicle.initialize()

val door = vehicle.getDoor()
door.checkDoorPositionCapabilities(
  onComplete = { config, areaMap ->
    when (config.accessType) {
      AccessType.READ, AccessType.READ_WRITE -> {
        val bounds = areaMap[DoorArea(DoorValue.ROW_1_LEFT)]
        // Use bounds?.minValue / bounds?.maxValue, then call getCurrentDoorPosition
      }
      else -> {
        // NONE or WRITE only — disable UI or use an alternate flow
      }
    }
  },
  onFailed = { e: Exception ->
    if (e is VehicleException) {
      // Capability lookup failed: e.errorCode
    }
  }
)

// End Vehicle
vehicle.release()
```

Content copied to clipboard

### Java

**Get Property API example**

``` block
// Create Vehicle instance
Vehicle vehicle = new Vehicle(context);

// Start Vehicle
vehicle.initialize();

Door door = vehicle.getDoor();
door.getCurrentDoorPosition(
  new DoorArea(DoorValue.ROW_1_LEFT),
  pos -> {
    // do something
    return Unit.INSTANCE;
  },
  e -> {
    // do something
    return Unit.INSTANCE;
  }
);

Display display = vehicle.getDisplay();
display.getCurrentSpeedDisplayUnit(
  unit -> {
    // do something
    return Unit.INSTANCE;
  },
  e -> {
    // do something
    return Unit.INSTANCE;
  }
);

// End Vehicle
vehicle.release();
```

Content copied to clipboard

**Set Property API example**

``` block
// Create Vehicle instance
Vehicle vehicle = new Vehicle(context);

// Start Vehicle
vehicle.initialize();

Door door = vehicle.getDoor();
door.setDoorPosition(
  new DoorArea(DoorValue.ROW_1_LEFT),
  1,
  e -> {
    // do something
    return Unit.INSTANCE;
  }
);

// End Vehicle
vehicle.release();
```

Content copied to clipboard

**Callback API example**

``` block
// Create Vehicle instance
Vehicle vehicle = new Vehicle(context);

// Start Vehicle
vehicle.initialize();

// Create callback listener
GearSelectionListener callbackListener = new GearSelectionListener() {
  @Override
  public void onSelected(@Nullable VehicleGear gear) {
    // do something
  }

  @Override
  public void onFailed(@NonNull Exception e) {
    // do something
  }
};

Gear gear = vehicle.getGear();

// register callback
gear.registerGearSelection(callbackListener);

// unregister callback
gear.unregisterGearSelection(callbackListener);

// End Vehicle
vehicle.release();
```

Content copied to clipboard

## Constructors

[Vehicle](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/-vehicle)

Link copied to clipboard

constructor(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html))

## Functions

[getBrake](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-brake)

Link copied to clipboard

fun [getBrake](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-brake)(): [Brake](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/entry)

Get the object of Brake to use Brake APIs.

[getCarInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-car-info)

Link copied to clipboard

fun [getCarInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-car-info)(): [CarInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/entry)

Get the object of CarInfo to use CarInfo APIs.

[getDisplay](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-display)

Link copied to clipboard

fun [getDisplay](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-display)(): [Display](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/display/entry)

Get the object of Display to use Display APIs.

[getDoor](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-door)

Link copied to clipboard

fun [getDoor](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-door)(): [Door](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry)

Get the object of Door to use Door APIs.

[getDrivingMode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-driving-mode)

Link copied to clipboard

fun [getDrivingMode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-driving-mode)(): [DrivingMode](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/entry)

Get the object of DrivingMode to use DrivingMode APIs.

[getEvBattery](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-ev-battery)

Link copied to clipboard

fun [getEvBattery](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-ev-battery)(): [EvBattery](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/entry)

Get the object of EvBattery to use EvBattery APIs.

[getFuel](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-fuel)

Link copied to clipboard

fun [getFuel](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-fuel)(): [Fuel](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/entry)

Get the object of Fuel to use Fuel APIs.

[getHVAC](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-h-v-a-c)

Link copied to clipboard

fun [getHVAC](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-h-v-a-c)(): [HVAC](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry)

Get the object of HVAC to use HVAC APIs.

[getLight](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-light)

Link copied to clipboard

fun [getLight](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-light)(): [Light](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/entry)

Get the object of Light to use Light APIs.

[getOdometer](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-odometer)

Link copied to clipboard

fun [getOdometer](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-odometer)(): [Odometer](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/entry)

Get the object of Odometer to use Odometer APIs.

[getSafety](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-safety)

Link copied to clipboard

fun [getSafety](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-safety)(): [Safety](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/entry)

Get the object of Safety to use Safety APIs.

[getSeat](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-seat)

Link copied to clipboard

fun [getSeat](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-seat)(): [Seat](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/entry)

Get the object of Seat to use Seat APIs.

[getSideMirror](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-side-mirror)

Link copied to clipboard

fun [getSideMirror](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-side-mirror)(): [SideMirror](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/side-mirror/entry)

Get the object of SideMirror to use SideMirror APIs.

[getSteeringWheel](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-steering-wheel)

Link copied to clipboard

fun [getSteeringWheel](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-steering-wheel)(): [SteeringWheel](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/entry)

Get the object of SteeringWheel to use SteeringWheel APIs.

[getTire](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-tire)

Link copied to clipboard

fun [getTire](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-tire)(): [Tire](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/entry)

Get the object of Tire to use Tire APIs.

[getToll](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-toll)

Link copied to clipboard

fun [getToll](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-toll)(): [Toll](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/entry)

Get the object of Toll to use Toll APIs.

[getTurnSignal](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-turn-signal)

Link copied to clipboard

fun [getTurnSignal](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-turn-signal)(): [TurnSignal](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/entry)

Get the object of TurnSignal to use TurnSignal APIs.

[getWindow](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-window)

Link copied to clipboard

fun [getWindow](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-window)(): [Window](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/entry)

Get the object of Window to use Window APIs.

[getWiper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-wiper)

Link copied to clipboard

fun [getWiper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-wiper)(): [Wiper](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/entry)

Get the object of Wiper to use Wiper APIs.

[initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/initialize)

Link copied to clipboard

fun [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/initialize)()

Initialize sdk. It is called while the application is created.

[release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/release)

Link copied to clipboard

fun [release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/release)()

Release sdk. It is called while the application is destroyed.
