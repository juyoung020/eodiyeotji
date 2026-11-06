package app.eodiyeotji.platform

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 듣는 중인지와 지금까지 알아들은 말 ([level]: 목소리 크기 0~1, 화면의 출렁임용). */
data class ListenState(val listening: Boolean = false, val partial: String = "", val level: Float = 0f)

/** 한 번 듣기의 끝. */
sealed interface SpeechResult {
    data class Final(val text: String) : SpeechResult
    /** 말소리가 없었다 (조용히 끝낸다). */
    data object NoSpeech : SpeechResult
    /** 들을 수 없었다 (화면·TTS에 그대로 보일 수 있는 한국어). */
    data class Failed(val message: String) : SpeechResult
}

/**
 * 말로 묻기 (음성 → 글). 계산 없음: 알아들은 글을 넘기기만 한다.
 * 차는 Pleos SpeechToText(없으면 Android 인식기), 폰은 Android [android.speech.SpeechRecognizer].
 */
interface SpeechInput {
    val state: StateFlow<ListenState>
    val results: SharedFlow<SpeechResult>

    /** 마이크 권한을 먼저 물어야 하면 true (화면이 권한을 물은 뒤 [start]). */
    fun needsMicPermission(): Boolean

    /** 듣기 시작 (이미 듣는 중이면 무시). 결과는 [results]로 한 번 온다. */
    fun start(maxMs: Long = MAX_LISTEN_MS)

    /** 말을 다 했다: 지금까지 들은 것으로 끝낸다. */
    fun stop()

    /** 듣기를 버린다. 결과를 내보내지 않는다. */
    fun cancel()

    companion object {
        /** 말이 끝나지 않아도 이만큼 들으면 끝낸다 */
        const val MAX_LISTEN_MS = 12_000L

        /** 디버그 빌드: 마이크 없는 에뮬레이터에서 "알아들은 말"을 넣는 브로드캐스트 (`--es text "..."`). */
        const val DEBUG_ACTION = "app.eodiyeotji.DEBUG_VOICE"
    }
}

/**
 * 답 읽어 주기 (글 → 음성). 내비 안내 음성과 겹치지 않게 오디오 포커스를 지킨다.
 * 차는 Pleos TextToSpeech(없으면 Android TTS), 폰은 Android [android.speech.tts.TextToSpeech].
 */
interface SpeechOutput {
    /** 지금 읽는 중 (포커스를 기다리는 동안 포함). */
    val speaking: StateFlow<Boolean>

    /** [text]를 읽는다 (읽던 것은 멈춘다). 다 읽거나 멈추면 [then]을 한 번 부른다. */
    fun speak(text: String, then: (() -> Unit)? = null)

    /** 읽기를 멈춘다. [speak]의 then은 부르지 않는다. */
    fun stop()
}
