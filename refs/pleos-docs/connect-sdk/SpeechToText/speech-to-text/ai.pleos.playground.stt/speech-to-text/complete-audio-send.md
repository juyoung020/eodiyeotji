> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/complete-audio-send

# completeAudioSend

@[RequiresPermission](https://developer.android.com/reference/kotlin/androidx/annotation/RequiresPermission.html)(value = "pleos.car.permission.STT_SERVICE")

fun [completeAudioSend](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/complete-audio-send)()

Completes the external audio buffer input started by [sendAudio](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/send-audio) and finishes recognition. Available only in [Mode.ON_DEVICE](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/o-n_-d-e-v-i-c-e/entry). In other modes the call is ignored. Requires the caller to have STT_PERMISSION.
