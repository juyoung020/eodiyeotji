> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data

# Sending audio data

차량 마이크 대신 앱이 보유한 PCM 오디오를 인식하려면 `sendAudio()`에 `ByteArray`를 전달합니다. 이 기능은 ON_DEVICE 모드에서만 사용할 수 있습니다. 전체 데이터를 한 번에 보내거나 여러 청크로 나눠 전송할 수 있습니다.

정보

이 문서의 3,200-byte 청크와 100ms 간격은 16 kHz, 16-bit, mono PCM을 실시간 속도로 전송하는 예시입니다. 다른 오디오 형식을 사용하는 경우 해당 형식의 100ms 분량에 맞춰 청크 크기를 계산하세요.

## Send a complete buffer[​](#send-a-complete-buffer "Send a complete buffer에 대한 직접 링크")

- Kotlin
- Java

``` kotlin
val pcmData = applicationContext.assets
    .open("test_audio.pcm")
    .use { it.readBytes() }

speechToText.sendAudio(pcmData)
speechToText.completeAudioSend()
```

``` java
byte[] pcmData;
try (InputStream input = getAssets().open("test_audio.pcm");
     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
    byte[] buffer = new byte[8_192];
    int read;
    while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
    }
    pcmData = output.toByteArray();
}

speechToText.sendAudio(pcmData);
speechToText.completeAudioSend();
```

## Stream chunks[​](#stream-chunks "Stream chunks에 대한 직접 링크")

- Kotlin
- Java

``` kotlin
private const val BYTES_PER_100_MS = 3_200

val pcmData = applicationContext.assets
    .open("test_audio.pcm")
    .use { it.readBytes() }

pcmData.asList()
    .chunked(BYTES_PER_100_MS)
    .forEach { chunk ->
        speechToText.sendAudio(chunk.toByteArray())
        Thread.sleep(100L)
    }

speechToText.completeAudioSend()
```

``` java
private static final int BYTES_PER_100_MS = 3_200;

byte[] buffer = new byte[BYTES_PER_100_MS];
try (InputStream input = getAssets().open("test_audio.pcm")) {
    int read;
    while ((read = input.read(buffer)) != -1) {
        speechToText.sendAudio(Arrays.copyOf(buffer, read));
        Thread.sleep(100L);
    }
}

speechToText.completeAudioSend();
```

마지막 오디오 데이터를 보낸 후 반드시 `completeAudioSend()`를 호출하여 입력이 끝났음을 알리세요. 인식 결과는 미리 등록한 `ResultListener`로 전달됩니다.
