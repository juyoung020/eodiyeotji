> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners

# Event listeners

`ResultListener`를 등록하면 음성 인식의 준비, 시작, 중간·최종 결과, 음성 구간, 입력 크기 및 오류 이벤트를 받을 수 있습니다.

Callback

Description

`onReady()`

음성 인식 요청을 처리할 준비가 되었을 때 호출됩니다.

`onStartedRecognition()`

음성 인식이 시작될 때 호출됩니다.

`onUpdated(stt, completed)`

인식 텍스트가 갱신될 때 호출됩니다. `completed`가 `true`이면 해당 인식 결과가 완료된 것입니다.

`onUpdatedEpdData(on, off)`

EPD(End Point Detection)가 감지한 음성 시작(`on`)과 종료(`off`) 위치가 갱신될 때 호출됩니다. 위치 값은 입력 오디오 버퍼의 byte offset입니다.

`onUpdatedRms(rms)`

입력 음성 신호의 RMS(Root Mean Square) 값이 갱신될 때 호출됩니다. RMS는 신호 크기를 나타내는 값이며 dB 값이 아닙니다.

`onEndedRecognition()`

음성 인식이 종료될 때 호출됩니다.

`onError(errorType)`

처리 중 오류가 발생하면 호출됩니다.

- Kotlin
- Java

``` kotlin
private val resultListener = object : ResultListener {
    override fun onReady() = Unit
    override fun onStartedRecognition() = Unit

    override fun onUpdated(stt: String, completed: Boolean) {
        // 중간 또는 최종 인식 결과를 처리합니다.
    }

    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
    override fun onUpdatedRms(rms: Double) = Unit
    override fun onEndedRecognition() = Unit

    override fun onError(errorType: ErrorType) {
        // 오류를 처리합니다.
    }
}

speechToText.addListener(resultListener)
```

``` java
private final ResultListener resultListener = new ResultListener() {
    @Override public void onReady() {}
    @Override public void onStartedRecognition() {}

    @Override
    public void onUpdated(String stt, boolean completed) {
        // 중간 또는 최종 인식 결과를 처리합니다.
    }

    @Override public void onUpdatedEpdData(long on, long off) {}
    @Override public void onUpdatedRms(double rms) {}
    @Override public void onEndedRecognition() {}

    @Override
    public void onError(ErrorType errorType) {
        // 오류를 처리합니다.
    }
};

speechToText.addListener(resultListener);
```

인식 이벤트를 놓치지 않도록 `request()` 또는 `sendAudio()`를 호출하기 전에 리스너를 등록하세요. 리스너가 더 이상 필요하지 않거나 SDK를 해제하기 전에는 등록할 때 사용한 동일한 인스턴스를 제거합니다.

``` kotlin
speechToText.removeListener(resultListener)
```

오류별 처리 방법은 [Error code](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/error-code)를 참고하세요.
