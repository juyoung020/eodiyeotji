> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission

# Declaring permission

Vehicle SDK API는 Android 매니페스트에 Pleos 차량 퍼미션을 선언해야 호출할 수 있습니다. 각 API의 `@RequiresPermission` 어노테이션과 상수 정의는 [PleosPermission](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/entry)을 참조하세요.

## 퍼미션 종류[​](#퍼미션-종류 "퍼미션 종류에 대한 직접 링크")

[PleosPermission](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/entry) 상수는 접두사로 구분됩니다. 매니페스트에는 `pleos.car.permission.{상수명}` 형식으로 선언합니다.

접두사

예시

용도

`CAR_*`

`CAR_DOORS` → `pleos.car.permission.CAR_DOORS`

도메인 데이터 조회(get)

`CONTROL_CAR_*`

`CONTROL_CAR_DOORS`

도메인 제어(set)

## 도메인별 대표 퍼미션 (`CAR_*`)[​](#도메인별-대표-퍼미션-car_ "도메인별-대표-퍼미션-car_에 대한 직접 링크")

아래는 도메인과 대표적으로 연결되는 조회용 퍼미션입니다. 세부 API마다 요구 퍼미션이 다를 수 있으므로, 호출 전 해당 함수의 `@RequiresPermission`을 확인하세요.

도메인

PleosPermission 상수

매니페스트 `android:name`

Door

`CAR_DOORS`

`pleos.car.permission.CAR_DOORS`

Window, Wiper

`CAR_WINDOWS`

`pleos.car.permission.CAR_WINDOWS`

HVAC

`CAR_CLIMATE`

`pleos.car.permission.CAR_CLIMATE`

DrivingMode, Brake, Odometer

`CAR_DRIVE`

`pleos.car.permission.CAR_DRIVE`

EvBattery, Fuel

`CAR_ENERGY`

`pleos.car.permission.CAR_ENERGY`

CarInfo, Display

`CAR_INFO`

`pleos.car.permission.CAR_INFO`

Light, TurnSignal

`CAR_LIGHTS`

`pleos.car.permission.CAR_LIGHTS`

SideMirror

`CAR_MIRRORS`

`pleos.car.permission.CAR_MIRRORS`

Safety, Tire

`CAR_SAFETY`

`pleos.car.permission.CAR_SAFETY`

Seat

`CAR_SEATS`

`pleos.car.permission.CAR_SEATS`

Toll

`CAR_TOLL`

`pleos.car.permission.CAR_TOLL`

## 퍼미션 선언 예시[​](#퍼미션-선언-예시 "퍼미션 선언 예시에 대한 직접 링크")

사용하는 도메인에 맞는 퍼미션만 선언하면 됩니다. 의존성 추가는 [Importing Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)를 참조하세요.

AndroidManifest.xml

``` xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="pleos.car.permission.CAR_DOORS" />
    <uses-permission android:name="pleos.car.permission.CAR_INFO" />

    <!-- 사용하는 도메인에 맞는 퍼미션만 선언 -->

</manifest>
```

## 퍼미션 누락 시[​](#퍼미션-누락-시 "퍼미션 누락 시에 대한 직접 링크")

퍼미션이 없으면 API `onFailed`에 `Exception`이 전달되며, `VehicleException`인 경우 `ERROR_SECURITY_PERMISSION`에 해당할 수 있습니다. 처리 방법은 [Error code](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code)를 참조하세요.
