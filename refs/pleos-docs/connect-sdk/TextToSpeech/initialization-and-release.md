> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release

# Initialization

`TextToSpeech` 객체를 생성한 후 `initialize()`를 호출해야 API를 사용할 수 있습니다. SDK 사용을 마치면 `release()`를 호출하여 연결과 내부 리소스를 해제합니다.

## Initialize the SDK[​](#initialize-the-sdk "Initialize the SDK에 대한 직접 링크")

초기화 결과가 필요한 경우 `OnInitializeListener`를 전달합니다.

- Kotlin
- Java

``` kotlin
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.constant.ErrorType

textToSpeech.initialize(object : OnInitializeListener {
    override fun onSuccess() {
        // TextToSpeech API를 사용할 수 있습니다.
    }

    override fun onFailure(errorType: ErrorType) {
        // 초기화 실패 원인을 처리합니다.
    }
})
```

``` java
import ai.pleos.playground.tts.constant.ErrorType;
import ai.pleos.playground.tts.listener.OnInitializeListener;

textToSpeech.initialize(new OnInitializeListener() {
    @Override
    public void onSuccess() {
        // TextToSpeech API를 사용할 수 있습니다.
    }

    @Override
    public void onFailure(ErrorType errorType) {
        // 초기화 실패 원인을 처리합니다.
    }
});
```

결과 콜백이 필요하지 않으면 인자 없이 호출할 수 있습니다.

``` kotlin
textToSpeech.initialize()
```

## Release the SDK[​](#release-the-sdk "Release the SDK에 대한 직접 링크")

SDK를 더 이상 사용하지 않을 때 호출합니다. 등록한 이벤트 리스너는 해제한 후 `release()`를 호출하는 것을 권장합니다.

팁

`TextToSpeech` 객체는 사용하는 컴포넌트의 수명주기에 맞춰 재사용하세요. 더 이상 필요하지 않거나 애플리케이션이 종료될 때는 `release()`를 호출하여 SDK 리소스를 해제해야 합니다.

- Kotlin
- Java

``` kotlin
override fun onDestroy() {
    textToSpeech.removeEventListener(eventListener)
    textToSpeech.release()
    super.onDestroy()
}
```

``` java
@Override
protected void onDestroy() {
    textToSpeech.removeEventListener(eventListener);
    textToSpeech.release();
    super.onDestroy();
}
```

`release()` 이후 SDK를 다시 사용하려면 새 객체를 생성하고 초기화 절차를 다시 수행하세요.
