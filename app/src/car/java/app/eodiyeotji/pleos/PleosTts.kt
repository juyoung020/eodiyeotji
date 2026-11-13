package app.eodiyeotji.pleos

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.rust.speakableText
import app.eodiyeotji.platform.SpeechOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 답 읽어 주기 — Pleos **TextToSpeech SDK**(차량 음성 합성, 차량 오디오로 재생). 운전 중 짧은 답 한 문장을 읽는다.
 *
 * 내비 안내 음성과 겹치지 않게 오디오 포커스를 지킨다 (CLAUDE.md "회상 안내는 짧게"):
 * - 읽기 전에 비서 음성(`USAGE_ASSISTANT`)으로 잠깐 포커스를 달라고 한다. 음악은 줄어들고(duck),
 *   내비가 말하는 중이면 시스템이 미뤄 준다(`DELAYED`) → 내비가 끝나면 읽는다 ([MAX_WAIT_MS]가 지나면 버린다).
 * - 읽는 중에 내비 안내가 끼어들어 포커스를 잃으면 바로 멈춘다. 다 읽으면 포커스를 돌려준다.
 * - 모드: local.properties에 PLEOS_CLIENT_ID · PLEOS_CLIENT_SECRET이 있으면 `HYBRID`(가이드 기본 = 서버 고음질 우선, 끊기면 SDK가 기기 안 엔진으로)
 *   + `registerApp()`, 없거나 등록이 실패하면 `ON_DEVICE`(앱 등록 없이, 오프라인에서도). Pleos 음성 서비스가 없으면 Android 표준 TTS로 대신 읽는다.
 */
@Singleton
class PleosTts @Inject constructor(@ApplicationContext private val context: Context) : SpeechOutput {

    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _speaking = MutableStateFlow(false)
    /** 지금 읽는 중 (포커스를 기다리는 동안 포함). */
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _spokenText = MutableStateFlow<String?>(null)
    /** 지금 읽는 글 (읽지 않으면 null) — 화면이 자막처럼 보여 준다. */
    override val spokenText: StateFlow<String?> = _spokenText.asStateFlow()

    @Volatile private var pleosReady = false
    @Volatile private var pleosBroken = false
    @Volatile private var pleos: TextToSpeech? = null
    private var fallback: AndroidTts? = null
    @Volatile private var fallbackReady = false

    /** 지금 읽기 차례 (새로 읽거나 멈추면 바뀐다 → 늦게 온 콜백을 버린다). */
    private var seq = 0
    private var pendingText: String? = null
    private var onDone: (() -> Unit)? = null
    private var focus: AudioFocusRequest? = null
    /** Pleos 엔진에 넘긴 읽기 차례 (Pleos 콜백에는 차례 표시가 없어 이것으로 늦게 온 콜백을 가린다) */
    private var pleosTurn: Int? = null
    /** 읽기를 시작한 시각. 그 직후의 포커스 손실은 음성 서비스가 재생을 위해 포커스를 가져간 것이라 무시한다 */
    private var playAtMs = 0L

    private val pleosEvents = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = main.post { pleosTurn?.let(::done) }.let { }
        override fun onStop() = main.post { pleosTurn?.let(::done) }.let { }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "Pleos TTS 오류: $errorType")
            main.post {
                if (errorType == ErrorType.NOT_SUPPORTED_API || errorType == ErrorType.ON_DEVICE_ENGINE_ERROR || errorType == ErrorType.UNAVAILABLE_LANGUAGE) {
                    pleosBroken = true
                }
                pleosTurn?.let(::done)
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> pendingText?.let { t -> pendingText = null; play(t) }
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (System.currentTimeMillis() - playAtMs < OWN_FOCUS_GRACE_MS) {
                        Log.i(TAG, "읽기 시작 직후 포커스 이동($change) — 음성 서비스 재생으로 보고 계속")
                    } else {
                        // 내비 안내 등 더 중요한 소리가 시작됨 → 겹치지 않게 멈춘다
                        Log.i(TAG, "오디오 포커스 잃음($change) → 읽기 멈춤")
                        stop()
                    }
                }
                else -> Unit // LOSS_TRANSIENT_CAN_DUCK: 우리는 말소리라 줄이지 않고 계속 (짧다)
            }
        }
    }

    /**
     * Pleos 음성 엔진 만들기는 기기 안 합성 엔진을 읽느라 1초 넘게 걸린다 (에뮬레이터 실측 ~1.2 s).
     * 첫 화면을 만드는 UI 스레드에서 하면 그만큼 첫 화면이 멈추므로 전용 스레드(루퍼 있음)에서 만든다. 다 되기 전에 읽으라 하면 Android TTS로 읽는다.
     */
    init {
        val init = android.os.HandlerThread("EodiTtsInit").apply { start() }
        Handler(init.looper).post(::initPleos) // SDK가 이 스레드 루퍼로 콜백을 줄 수 있어 스레드는 남겨 둔다 (쉬는 스레드 1개)
    }

    private fun initPleos() = initPleos(hybrid = BuildConfig.PLEOS_CLIENT_ID.isNotEmpty() && BuildConfig.PLEOS_CLIENT_SECRET.isNotEmpty())

    private fun initPleos(hybrid: Boolean) {
        val mode = if (hybrid) Mode.HYBRID else Mode.ON_DEVICE
        runCatching {
            TextToSpeech(context, mode).also { t ->
                pleos = t
                t.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        runCatching {
                            t.addEventListener(pleosEvents)
                            t.setLocale(Locale.KOREA)
                            t.setAudioAttributes(attributes)
                        }.onFailure { pleosBroken = true; Log.w(TAG, "Pleos TextToSpeech 설정 실패", it); return }
                        if (!hybrid) { pleosReady = true; Log.i(TAG, "Pleos TextToSpeech 준비됨 (ON_DEVICE)"); return }
                        // 서버 합성: 초기화 뒤 앱 등록, onConnected 이후에 speak (가이드 app-registration). 그 전에는 Android TTS로 읽는다
                        runCatching {
                            t.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object : OnServerConnectionListener {
                                override fun onConnected() { pleosReady = true; Log.i(TAG, "Pleos TextToSpeech 준비됨 (HYBRID, 앱 등록 완료)") }
                                override fun onFailed(errorType: ErrorType) {
                                    Log.w(TAG, "Pleos TTS 앱 등록 실패: $errorType → ON_DEVICE로 다시 만든다")
                                    fallbackToOnDevice(t)
                                }
                            })
                        }.onFailure { Log.w(TAG, "Pleos TTS registerApp 실패 → ON_DEVICE", it); fallbackToOnDevice(t) }
                    }

                    override fun onFailure(errorType: ErrorType) {
                        if (hybrid) { Log.w(TAG, "Pleos TTS(HYBRID) 초기화 실패: $errorType → ON_DEVICE"); fallbackToOnDevice(t); return }
                        pleosBroken = true
                        Log.w(TAG, "Pleos TextToSpeech 초기화 실패: $errorType → Android TTS로 대신 읽는다")
                    }
                })
            }
        }.onFailure { if (hybrid) initPleos(hybrid = false) else { pleosBroken = true; Log.w(TAG, "Pleos TextToSpeech 없음", it) } }
    }

    private fun fallbackToOnDevice(old: TextToSpeech) {
        pleosReady = false
        runCatching { old.removeEventListener(pleosEvents) }
        runCatching { old.release() }
        initPleos(hybrid = false)
    }

    /** [text]를 읽는다 (읽던 것은 멈춘다). 다 읽거나 멈추면 [then]을 한 번 부른다. */
    override fun speak(text: String, then: (() -> Unit)?) {
        // 기호 · 단위 · 그림 문자를 읽기 좋게 (Rust speech_text). 엔진이 이상한 글자에서 끊기거나 "km"를 영어로 읽지 않게
        val clean = speakableText(text)
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
                    Log.i(TAG, "다른 안내 음성이 나오는 중 → 끝나면 읽는다")
                    pendingText = clean
                    main.postDelayed({ if (seq == my && pendingText != null) done(my) }, MAX_WAIT_MS)
                }
                else -> {
                    Log.i(TAG, "오디오 포커스를 받지 못함 → 이번 답은 읽지 않는다")
                    done(my)
                }
            }
        }
    }

    /** 읽기를 멈춘다 (새 질문·마이크·화면 닫기). [speak]의 then은 부르지 않는다. */
    override fun stop() {
        main.post { onDone = null; stopNow(); _speaking.value = false; _spokenText.value = null }
    }

    private fun play(text: String) {
        val my = seq
        playAtMs = System.currentTimeMillis()
        // 읽는 말에는 장소 · 질문 같은 개인 정보가 들어 있어 디버그 빌드에서만 내용을 남긴다
        Log.i(TAG, "읽기 #$my 한 번에 ${text.length}자" + if (BuildConfig.DEBUG) ": $text" else "")
        if (pleosReady && !pleosBroken) {
            pleosTurn = my
            // 선행 묵음(Pleos 음성 안내 가이드 150~200ms): Pleos SDK는 speak(text)만 받아 오디오 앞에 묵음을 넣을 수 없다.
            // 그래서 포커스를 받은 뒤 [PREROLL_MS]만큼 (UI 스레드를 막지 않고 postDelayed로) 기다렸다 읽는다 → 출력 경로가 열리는 동안 첫 음절이 잘리지 않는다
            main.postDelayed({ speakPleos(text, my) }, PREROLL_MS)
            return
        }
        playFallback(text, my)
    }

    private fun speakPleos(text: String, my: Int) {
        if (seq != my || pleosTurn != my) return // 묵음 동안 멈추거나 새로 읽기 시작함
        playAtMs = System.currentTimeMillis()
        Log.i(TAG, "읽기 #$my 선행 묵음 ${PREROLL_MS}ms 뒤 Pleos speak")
        runCatching { pleos?.speak(text, true) }
            .onFailure { Log.w(TAG, "Pleos TTS 읽기 실패 → Android TTS", it); pleosBroken = true; pleosTurn = null; playFallback(text, my) }
    }

    private fun playFallback(text: String, my: Int) {
        val tts = fallback
        if (tts != null && fallbackReady) {
            tts.setAudioAttributes(attributes)
            // 선행 묵음(Pleos 음성 안내 가이드 150~200ms): 출력 경로가 열리는 동안 첫 음절이 잘리지 않게
            tts.playSilentUtterance(PREROLL_MS, AndroidTts.QUEUE_FLUSH, "eodi-pre")
            tts.speak(text, AndroidTts.QUEUE_ADD, null, "eodi-$my")
            return
        }
        fallback = AndroidTts(context) { status ->
            main.post {
                if (status != AndroidTts.SUCCESS) {
                    Log.w(TAG, "Android TTS도 없음")
                    done(my)
                    return@post
                }
                fallbackReady = true
                fallback?.let(::useBestKoreanVoice)
                fallback?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    // 엔진은 한 번 만들어 계속 쓴다 → 차례는 처음 만들 때의 my가 아니라 읽기 id("eodi-N")에서 읽는다
                    override fun onDone(utteranceId: String?) = main.post { done(turnOf(utteranceId)) }.let { }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = main.post { done(turnOf(utteranceId)) }.let { }
                })
                if (seq == my) playFallback(text, my)
            }
        }
    }

    private fun turnOf(utteranceId: String?): Int = utteranceId?.removePrefix("eodi-")?.toIntOrNull() ?: -1

    /** Android TTS: 기기에 있는 한국어 목소리 중 가장 품질 높은 것 (네트워크가 필요 없는 것 먼저). */
    private fun useBestKoreanVoice(tts: AndroidTts) {
        tts.language = Locale.KOREA
        runCatching {
            tts.voices.orEmpty()
                .filter { it.locale.language == "ko" && AndroidTts.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
                .maxWithOrNull(compareBy({ !it.isNetworkConnectionRequired }, { it.quality }))
                ?.let { tts.voice = it; Log.i(TAG, "Android TTS 목소리: ${it.name} (quality=${it.quality})") }
        }
    }

    /** 읽기 하나가 끝남 (정상·멈춤·오류). 포커스를 돌려주고 then을 부른다. */
    private fun done(my: Int) {
        if (my != seq) return
        seq++
        if (pleosTurn == my) pleosTurn = null
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
        if (pleosTurn != null) runCatching { pleos?.stop() }
        runCatching { fallback?.stop() }
        onDone = null
        done(seq)
    }

    private companion object {
        const val TAG = "EodiTts"
        /** 내비 안내가 끝나기를 이만큼까지만 기다린다 (그 뒤의 답은 이미 늦다) */
        const val MAX_WAIT_MS = 8_000L
        const val OWN_FOCUS_GRACE_MS = 1_000L
        /** 읽기 앞 묵음 (Pleos 가이드 권장 150~200ms) */
        const val PREROLL_MS = 150L
    }
}
