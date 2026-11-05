> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/app-registration

# App registration

서버 기반 음성 인식을 사용하려면 Pleos Playground에서 My Project를 생성할 때 발급받은 `Client ID`와 `Client Secret`으로 앱을 등록합니다. SDK 초기화가 완료된 후 `registerApp()`을 호출하세요.

정보

`SERVER` 모드에서는 앱 등록이 필요합니다. `HYBRID` 모드에서도 서버 기반 인식을 사용하려면 앱을 등록해야 합니다. `ON_DEVICE` 모드만 사용하는 경우에는 등록하지 않아도 됩니다.

- Kotlin
- Java

``` kotlin
speechToText.registerApp(
    clientID = clientId,
    clientSecret = clientSecret,
    listener = object : OnServerConnectionListener {
        override fun onConnected() {
            // 서버 기반 음성 인식을 요청할 수 있습니다.
        }

        override fun onFailed(errorType: ErrorType) {
            // 등록 또는 서버 연결 실패를 처리합니다.
        }
    }
)
```

``` java
speechToText.registerApp(
        clientId,
        clientSecret,
        new OnServerConnectionListener() {
            @Override
            public void onConnected() {
                // 서버 기반 음성 인식을 요청할 수 있습니다.
            }

            @Override
            public void onFailed(ErrorType errorType) {
                // 등록 또는 서버 연결 실패를 처리합니다.
            }
        }
);
```

`onConnected()`가 호출된 이후 서버 기반 요청을 시작하세요.
