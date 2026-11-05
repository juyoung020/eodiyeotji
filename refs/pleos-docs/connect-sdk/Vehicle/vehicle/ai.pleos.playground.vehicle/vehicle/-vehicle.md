> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/-vehicle

# Constructor

constructor(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html))

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
