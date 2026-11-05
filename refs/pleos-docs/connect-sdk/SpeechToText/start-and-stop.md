> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop

# Start and stop recognition

## Start recognition[​](#start-recognition "Start recognition에 대한 직접 링크")

차량 마이크로 음성 인식을 시작하려면 `request()`를 호출합니다. 호출 시 SDK가 마이크를 활성화하고 입력 음성의 인식을 시작합니다.

`request()`를 호출하기 전에 SDK 초기화와 결과 리스너 등록을 완료하세요. 서버 기반 인식이 필요한 모드에서는 앱 등록 또는 UA 인증도 완료해야 합니다.

- Kotlin
- Java

``` kotlin
speechToText.addListener(resultListener)
speechToText.request()
```

``` java
speechToText.addListener(resultListener);
speechToText.request();
```

준비 및 인식 결과는 등록한 `ResultListener`에서 확인합니다.

## Stop recognition[​](#stop-recognition "Stop recognition에 대한 직접 링크")

진행 중인 음성 인식을 중지하고 마이크 사용을 종료하려면 `stop()`을 호출합니다.

- Kotlin
- Java

``` kotlin
speechToText.stop()
```

``` java
speechToText.stop();
```

인식이 종료되면 `ResultListener.onEndedRecognition()`이 호출됩니다.
