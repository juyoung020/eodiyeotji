> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/speak

# speak

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.TTS_SERVICE")

fun [speak](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/speak)(text: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), isFinal: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true)

Converts Text-To-Speech and speaks the text

#### Parameters

*text*

String: The request message to read aloud

*isFinal*

Boolean: If you have several texts to be sent, you should set **isFinal** to false. ex) speak("Hello," false) speak("I'm," false) speak("Gleo") -\> This is the final text.
