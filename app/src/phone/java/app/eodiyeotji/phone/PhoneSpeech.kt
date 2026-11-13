package app.eodiyeotji.phone

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.platform.ListenState
import app.eodiyeotji.platform.SpeechInput
import app.eodiyeotji.platform.SpeechOutput
import app.eodiyeotji.platform.SpeechResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 폰 말로 묻기 — Android 표준 [SpeechRecognizer] (한국어). 계산 없음: 알아들은 글을 넘기기만 한다.
 * 인식기는 메인 스레드에서만 다룬다. 상태는 StateFlow/SharedFlow로만 내보낸다.
 *
 * 디버그 빌드: 마이크가 없는 에뮬레이터에서 `adb shell am broadcast -a app.eodiyeotji.DEBUG_VOICE -p app.eodiyeotji.phone
 * --es text "작년 여름 바다 어디였지"`로 "알아들은 말"을 넣는다.
 */
@Singleton
class PhoneSpeechInput @Inject constructor(@ApplicationContext private val context: Context) : SpeechInput {

    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(ListenState())
    override val state: StateFlow<ListenState> = _state.asStateFlow()
    private val _results = MutableSharedFlow<SpeechResult>(extraBufferCapacity = 4)
    override val results: SharedFlow<SpeechResult> = _results.asSharedFlow()

    @Volatile private var finished = true
    private var recognizer: SpeechRecognizer? = null
    private val timeout = Runnable { if (!finished) stop() }

    init {
        if (BuildConfig.DEBUG) registerDebugInput()
    }

    override fun needsMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED

    override fun start(maxMs: Long) {
        main.post {
            if (!finished) return@post
            finished = false
            _state.value = ListenState(listening = true)
            main.postDelayed(timeout, maxMs)
            listen()
        }
    }

    override fun stop() {
        main.post {
            if (finished) return@post
            runCatching { recognizer?.stopListening() }
            // 엔진이 곧 최종 결과를 준다. 안 주면 잠시 뒤 중간 결과로 끝낸다.
            main.postDelayed({ if (!finished) finish(partialOrNothing()) }, 1_200)
        }
    }

    override fun cancel() {
        main.post {
            if (finished) return@post
            finished = true
            main.removeCallbacks(timeout)
            runCatching { recognizer?.cancel() }
            _state.value = ListenState()
        }
    }

    private fun partialOrNothing(): SpeechResult =
        _state.value.partial.trim().takeIf { it.isNotEmpty() }?.let { SpeechResult.Final(it) } ?: SpeechResult.NoSpeech

    private fun finish(r: SpeechResult) {
        if (finished) return
        finished = true
        main.removeCallbacks(timeout)
        _state.value = ListenState()
        // 알아들은 말은 개인정보 → release 로그에는 글자 수만 (기획 가이드 보안: 로그 마스킹)
        Log.i(TAG, "듣기 끝: " + if (BuildConfig.DEBUG || r !is SpeechResult.Final) "$r" else "Final(${r.text.length}자)")
        _results.tryEmit(r)
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            finish(SpeechResult.Failed(MSG_UNAVAILABLE))
            return
        }
        if (needsMicPermission()) {
            finish(SpeechResult.Failed(MSG_NO_PERMISSION))
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                if (!finished) _state.value = _state.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                Log.w(TAG, "음성 인식 오류 $error")
                finish(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechResult.NoSpeech
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechResult.Failed(MSG_NO_PERMISSION)
                        else -> SpeechResult.Failed(MSG_FAILED)
                    },
                )
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                finish(if (text.isEmpty()) SpeechResult.NoSpeech else SpeechResult.Final(text))
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (!finished && text.isNotBlank()) _state.value = _state.value.copy(partial = text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        runCatching { r.startListening(intent) }.onFailure {
            Log.w(TAG, "음성 인식 시작 실패", it)
            finish(SpeechResult.Failed(MSG_FAILED))
        }
    }

    private fun registerDebugInput() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val text = intent.getStringExtra("text")?.trim().orEmpty()
                if (text.isEmpty()) return
                Log.i(TAG, "디버그 음성 입력: $text")
                main.post {
                    runCatching { recognizer?.cancel() }
                    main.removeCallbacks(timeout)
                    finished = false
                    _state.value = ListenState(listening = true, partial = text.take((text.length + 1) / 2), level = 0.6f)
                    main.postDelayed({ _state.value = _state.value.copy(partial = text, level = 0.3f) }, 450)
                    main.postDelayed({ finish(SpeechResult.Final(text)) }, 900)
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(SpeechInput.DEBUG_ACTION), ContextCompat.RECEIVER_EXPORTED)
    }

    private companion object {
        const val TAG = "EodiSpeech"
        const val MSG_FAILED = "말씀을 알아듣지 못했어요. 다시 말씀해 주세요."
        const val MSG_UNAVAILABLE = "이 폰에서는 음성 인식을 쓸 수 없어요."
        const val MSG_NO_PERMISSION = "마이크 권한이 있어야 말로 물을 수 있어요."
    }
}

/**
 * 폰 답 읽어 주기 — Android 표준 [TextToSpeech] (한국어).
 * 지도 앱 길 안내 음성과 겹치지 않게: 비서 음성으로 잠깐 오디오 포커스를 받아 읽고(음악은 줄어든다), 내비가 말하는 중이면
 * 끝날 때까지 기다리며(최대 [MAX_WAIT_MS]), 읽는 중 포커스를 잃으면 멈춘다.
 */
@Singleton
class PhoneSpeechOutput @Inject constructor(@ApplicationContext private val context: Context) : SpeechOutput {

    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _spokenText = MutableStateFlow<String?>(null)
    /** 지금 읽는 글 (읽지 않으면 null) — 화면이 자막처럼 보여 준다. */
    override val spokenText: StateFlow<String?> = _spokenText.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    /** 지금 읽기 차례 (새로 읽거나 멈추면 바뀐다 → 늦게 온 콜백을 버린다). */
    private var seq = 0
    private var pendingText: String? = null
    private var onDone: (() -> Unit)? = null
    private var focus: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> pendingText?.let { t -> pendingText = null; play(t) }
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    Log.i(TAG, "오디오 포커스 잃음($change) → 읽기 멈춤")
                    stop()
                }
                else -> Unit
            }
        }
    }

    override fun speak(text: String, then: (() -> Unit)?) {
        val clean = app.eodiyeotji.core.rust.speakableText(text) // 기호 · 단위 · 그림 문자 다듬기 (Rust speech_text)
        if (clean.isEmpty()) return
        main.post {
            stopNow()
            val my = ++seq
            onDone = then
            _speaking.value = true
            _spokenText.value = clean
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusListener, main)
                .build()
            focus = req
            when (audio?.requestAudioFocus(req) ?: AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> play(clean)
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    pendingText = clean
                    main.postDelayed({ if (seq == my && pendingText != null) done(my) }, MAX_WAIT_MS)
                }
                else -> done(my)
            }
        }
    }

    override fun stop() {
        main.post { onDone = null; stopNow(); _speaking.value = false; _spokenText.value = null }
    }

    private fun play(text: String) {
        val my = seq
        val engine = tts
        if (engine != null && ready) {
            engine.setAudioAttributes(attributes)
            // 선행 묵음 150ms: 출력 경로가 열리는 동안 첫 음절이 잘리지 않게
            engine.playSilentUtterance(PREROLL_MS, TextToSpeech.QUEUE_FLUSH, "eodi-pre")
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, "eodi-$my")
            return
        }
        tts = TextToSpeech(context) { status ->
            main.post {
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS 엔진 없음")
                    done(my)
                    return@post
                }
                ready = true
                tts?.language = Locale.KOREA
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = main.post { done(turnOf(utteranceId)) }.let { }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = main.post { done(turnOf(utteranceId)) }.let { }
                })
                if (seq == my) play(text)
            }
        }
    }

    private fun turnOf(utteranceId: String?): Int = utteranceId?.removePrefix("eodi-")?.toIntOrNull() ?: -1

    private fun done(my: Int) {
        if (my != seq) return
        seq++
        pendingText = null
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
        _speaking.value = false; _spokenText.value = null
        val then = onDone
        onDone = null
        then?.invoke()
    }

    private fun stopNow() {
        if (!_speaking.value) return
        runCatching { tts?.stop() }
        onDone = null
        done(seq)
    }

    private companion object {
        const val TAG = "EodiTts"
        const val MAX_WAIT_MS = 8_000L
        /** 읽기 앞 묵음 (Pleos 음성 안내 가이드 권장 150~200ms) */
        const val PREROLL_MS = 150L
    }
}
