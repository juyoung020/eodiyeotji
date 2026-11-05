> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions

# Declaring permissions

SpeechToText SDK API를 사용하려면 앱의 `AndroidManifest.xml`에 `STT_SERVICE` 권한을 선언해야 합니다.

AndroidManifest.xml

``` xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="pleos.car.permission.STT_SERVICE" />

    <application>
        <!-- ... -->
    </application>
</manifest>
```

이 권한은 SDK 초기화, 결과 리스너 등록, 앱 등록, 음성 인식 요청 및 오디오 데이터 전송 등 SpeechToText 서비스에 접근하는 API에 필요합니다.

주의

권한을 선언하지 않으면 SDK 초기화나 API 호출이 실패할 수 있습니다. 오류 콜백에서 `PERMISSION_DENIED`가 전달되는 경우 앱의 manifest와 실행 환경에서 권한이 허용되었는지 확인하세요.

권한 선언을 마쳤으면 [SDK를 초기화](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/initialization-and-release)합니다.
