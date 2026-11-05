> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/start-and-stop

# Start and stop synthesis

## Send a synthesis request[​](#send-a-synthesis-request "Send a synthesis request에 대한 직접 링크")

`speak()`에 읽을 텍스트를 전달하여 음성 합성을 시작합니다. 기본적으로 `isFinal`은 `true`이며, 전달한 텍스트를 하나의 완성된 요청으로 처리합니다.

- Kotlin
- Java

``` kotlin
textToSpeech.speak("안녕하세요. 무엇을 도와드릴까요?")
```

``` java
textToSpeech.speak("안녕하세요. 무엇을 도와드릴까요?", true);
```

여러 텍스트 조각을 하나의 발화로 전달할 때는 마지막 조각을 제외한 요청의 `isFinal`을 `false`로 지정합니다.

``` kotlin
textToSpeech.speak("안녕하세요. ", isFinal = false)
textToSpeech.speak("TextToSpeech SDK입니다.", isFinal = true)
```

앱 등록이 필요한 모드에서는 `OnServerConnectionListener.onConnected()`가 호출된 후 요청하세요. 진행 상태와 결과는 [Event listeners](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners)에서 확인할 수 있습니다.

## Stop synthesis and playback[​](#stop-synthesis-and-playback "Stop synthesis and playback에 대한 직접 링크")

진행 중인 음성 합성과 재생을 중지하려면 `stop()`을 호출합니다.

- Kotlin
- Java

``` kotlin
textToSpeech.stop()
```

``` java
textToSpeech.stop();
```

중지가 완료되면 등록된 `EventListener`의 `onStop()`이 호출됩니다.
