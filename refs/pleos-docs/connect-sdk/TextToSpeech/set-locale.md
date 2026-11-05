> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/set-locale

# Set locale

`setLocale(locale: Locale)`는 음성 합성에 사용할 언어/지역(Locale)을 업데이트합니다.

## Set locale before synthesis[​](#set-locale-before-synthesis "Set locale before synthesis에 대한 직접 링크")

음성 합성 요청 전에 `setLocale()`를 호출해 합성 언어/지역을 먼저 설정합니다.

- `speak()` 호출 전에 설정한 locale이 해당 합성 요청의 기준으로 사용됩니다.
- `setLocale()`로 설정한 locale은 다음 음성 합성 요청의 언어/지역 기준으로 사용됩니다.

## Supported locales[​](#supported-locales "Supported locales에 대한 직접 링크")

지원 locale은 다음과 같습니다.

- `ko-KR`: 한국어
- `en-US`: 영어
- `fr-CA`: 프랑스어
- `es-US`, `es-MX`: 스페인어

## Default behavior when `setLocale()` is not called[​](#default-behavior-when-setlocale-is-not-called "default-behavior-when-setlocale-is-not-called에 대한 직접 링크")

`setLocale()`을 호출하지 않으면 SDK는 같은 앱에서 이전에 `setLocale()`로 설정한 locale 값이 있는지 먼저 확인합니다.

- 이전 설정 값이 있으면 해당 locale 값을 사용합니다.
- 이전 설정 값이 없으면 시스템 locale을 사용합니다.

## Example[​](#example "Example에 대한 직접 링크")

- Kotlin
- Java

``` kotlin
import java.util.Locale

textToSpeech.setLocale(Locale.KOREA)
```

``` java
import java.util.Locale;

textToSpeech.setLocale(Locale.KOREA);
```
