> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code

# Error code

Vehicle SDK가 제공하는 비동기 API는 onComplete와 onFailed 콜백으로 결과를 전달합니다. 앱에서는 onFailed로 받은 Exception이 VehicleException이면 errorCode로 ErrorCode 값을 확인합니다. 모든 onFailed가 VehicleException은 아닐 수 있습니다.

- onFailed 시그니처: Get/Set APIs, checkXXXCapabilities, VehicleListener는 모두 Kotlin 표준 [Exception](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-exception/)을 사용합니다. ErrorCode가 콜백 인자로 직접 전달되지는 않습니다.
- ErrorCode 정의: ai.pleos.playground.vehicle.constant 패키지의 enum. Vehicle SDK 내부 오류 분류에 쓰이며, 공개 API에서는 VehicleException.errorCode로 노출됩니다.

사전에 [checkXXXCapabilities](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability)로 미지원 호출을 줄이고, 퍼미션 오류는 [Permission](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)을 확인하세요.

## 오류 전달 흐름[​](#오류-전달-흐름 "오류 전달 흐름에 대한 직접 링크")

## onFailed 처리 예시[​](#onfailed-처리-예시 "onFailed 처리 예시에 대한 직접 링크")

- Kotlin
- Java

``` kotlin
door.getCurrentDoorPosition(
  area = DoorArea(DoorValue.ROW_1_LEFT),
  onComplete = { pos -> },
  onFailed = { e ->
    if (e is VehicleException) {
      when (e.errorCode) {
        ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY,
        ErrorCode.ERROR_NOT_SUPPORTED_AREA -> { /* UI 비활성 */ }
        ErrorCode.ERROR_SECURITY_PERMISSION -> { /* 매니페스트 퍼미션 확인 */ }
        ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE,
        ErrorCode.ERROR_INTERNAL_SERVICE -> { /* initialize·재시도 */ }
        else -> Log.e("Vehicle API 오류: ${e.errorCode} — ${e.message}")
      }
    } else {
      Log.e("Vehicle API 오류: ${e.message}")
    }
  }
)
```

``` java
door.getCurrentDoorPosition(
  new DoorArea(DoorValue.ROW_1_LEFT),
  pos -> Unit.INSTANCE,
  e -> {
    if (e instanceof VehicleException) {
      ErrorCode code = ((VehicleException) e).getErrorCode();
      // code에 따라 분기 처리
    }
    return Unit.INSTANCE;
  }
);
```

## ErrorCode 목록[​](#errorcode-목록 "ErrorCode 목록에 대한 직접 링크")

ErrorCode

설명

권장 처리

ERROR_NOT_SUPPORTED_PROPERTY

해당 속성(기능) 미지원

UI 비활성·대체 흐름

ERROR_NOT_SUPPORTED_AREA

해당 영역 미지원

영역 선택 변경

ERROR_NOT_SUPPORTED_VALUE_TYPE

지원하지 않는 값 타입

파라미터·타입 재확인

ERROR_SECURITY_PERMISSION

퍼미션 부족

[Permission](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)·@RequiresPermission 확인

ERROR_SERVICE_CONNECTION_FAILURE

Vehicle 서비스 연결 실패

initialize()·에뮬레이터 상태 확인

ERROR_INTERNAL_SERVICE

내부 서비스 오류

재시도·일시 오류 안내

ERROR_DRIVING_RESTRICTED

주행 중 기능 제한

주행 제한 안내

ERROR_EMPTY_RESULT

결과 없음

빈 상태 UI 처리

ERROR_UNDEFINED

정의되지 않은 오류

로그·재시도
