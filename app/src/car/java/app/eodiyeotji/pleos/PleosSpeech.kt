package app.eodiyeotji.pleos

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.platform.ListenState
import app.eodiyeotji.platform.SpeechInput
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
 * 말로 묻기 — Pleos **SpeechToText SDK**(차량 마이크·차량 음성 인식 엔진)로 듣는다. 계산 없음: 알아들은 글을 넘기기만 한다.
 *
 * - 모드는 `ON_DEVICE`(차량 안 엔진): 앱 등록(Client ID) 없이 쓰고, 터널·지하주차장에서도 된다.
 * - Pleos 음성 서비스가 없거나(일반 에뮬레이터·폰) 초기화·인식이 엔진 문제로 실패하면 Android 표준 [SpeechRecognizer]로 대신 듣는다
 *   (이때만 `RECORD_AUDIO` 권한이 필요하다 → [needsMicPermission]).
 * - 디버그 빌드: 마이크가 없는 에뮬레이터에서 시연할 수 있게 `adb shell am broadcast --user 10 -a app.eodiyeotji.DEBUG_VOICE
 *   -p app.eodiyeotji --es text "작년 여름 바다 어디였지"`로 "알아들은 말"을 넣는다 (마이크로 들은 것과 같은 길로 간다).
 *
 * 콜백은 SDK 스레드에서 오므로 상태는 StateFlow/SharedFlow로만 내보낸다. Android 인식기는 메인 스레드에서만 다룬다.
 */
@Singleton
class PleosSpeech @Inject constructor(@ApplicationContext private val context: Context) : SpeechInput {

    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(ListenState())
    override val state: StateFlow<ListenState> = _state.asStateFlow()
    private val _results = MutableSharedFlow<SpeechResult>(extraBufferCapacity = 4)
    override val results: SharedFlow<SpeechResult> = _results.asSharedFlow()

    @Volatile private var pleosReady = false
    /** Pleos 인식을 쓸 수 없다고 판단함 → Android 인식기로 */
    @Volatile private var pleosBroken = false
    @Volatile private var finished = true
    @Volatile private var stt: SpeechToText? = null
    private var android: SpeechRecognizer? = null
    private val timeout = Runnable { if (!finished) stop() }

    private val pleosListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            if (finished) return
            if (completed) finish(stt.trim().takeIf { it.isNotEmpty() }?.let { SpeechResult.Final(it) } ?: SpeechResult.NoSpeech)
            else _state.value = _state.value.copy(partial = stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) {
            if (!finished) _state.value = _state.value.copy(level = (rms / 10.0).coerceIn(0.0, 1.0).toFloat())
        }
        override fun onEndedRecognition() {
            // 최종 결과 없이 끝나면 마지막 중간 결과를 쓴다
            if (!finished) finish(_state.value.partial.trim().takeIf { it.isNotEmpty() }?.let { SpeechResult.Final(it) } ?: SpeechResult.NoSpeech)
        }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "Pleos 음성 인식 오류: $errorType")
            if (finished) return
            when (errorType) {
                ErrorType.NOT_SUPPORTED_API, ErrorType.ON_DEVICE_ENGINE_ERROR, ErrorType.UNAVAILABLE_LANGUAGE,
                ErrorType.PERMISSION_DENIED, ErrorType.MIC_ERROR, ErrorType.AUDIO_ERROR -> {
                    // 이 차(에뮬레이터)에서는 Pleos 인식이 안 된다 → 이번 듣기를 Android 인식기로 이어 간다
                    pleosBroken = true
                    main.post { if (!finished) startAndroid() }
                }
                ErrorType.TIMEOUT -> finish(SpeechResult.NoSpeech)
                else -> finish(SpeechResult.Failed(MSG_FAILED))
            }
        }
    }

    /** 음성 인식 엔진 만들기는 오래 걸린다 → 전용 스레드(루퍼 있음)에서 만든다 (`PleosTts`와 같은 이유). 그 전의 듣기는 Android 인식기로. */
    init {
        val init = HandlerThread("EodiSttInit").apply { start() }
        Handler(init.looper).post(::initPleos) // SDK 콜백이 이 루퍼로 올 수 있어 스레드는 남겨 둔다
        if (BuildConfig.DEBUG) registerDebugInput()
    }

    private fun initPleos() {
        runCatching {
            SpeechToText(context, Mode.ON_DEVICE).also { s ->
                stt = s
                s.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        runCatching {
                            s.addListener(pleosListener)
                            s.setLocale(Locale.KOREA)
                        }.onSuccess { pleosReady = true; Log.i(TAG, "Pleos SpeechToText 준비됨 (ON_DEVICE)") }
                            .onFailure { pleosBroken = true; Log.w(TAG, "Pleos SpeechToText 설정 실패", it) }
                    }

                    override fun onFailure(errorType: ErrorType) {
                        pleosBroken = true
                        Log.w(TAG, "Pleos SpeechToText 초기화 실패: $errorType → Android 인식기로 대신 듣는다")
                    }
                })
            }
        }.onFailure { pleosBroken = true; Log.w(TAG, "Pleos SpeechToText 없음", it) }
    }

    /** Android 인식기로 대신 들어야 하는데 마이크 권한이 아직 없으면 true (화면이 권한을 물은 뒤 [start]). */
    override fun needsMicPermission(): Boolean =
        !usePleos() && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED

    /** 듣기 시작 (이미 듣는 중이면 무시). 결과는 [results]로 한 번 온다. */
    override fun start(maxMs: Long) {
        main.post {
            if (!finished) return@post
            finished = false
            _state.value = ListenState(listening = true)
            main.postDelayed(timeout, maxMs)
            if (usePleos()) {
                runCatching { stt?.request() }.onFailure {
                    Log.w(TAG, "Pleos 인식 요청 실패", it)
                    pleosBroken = true
                    startAndroid()
                }
            } else {
                startAndroid()
            }
        }
    }

    /** 말을 다 했다 (마이크를 다시 누름): 지금까지 들은 것으로 끝낸다. */
    override fun stop() {
        main.post {
            if (finished) return@post
            runCatching { stt?.stop() }
            runCatching { android?.stopListening() }
            // 엔진이 곧 최종 결과를 준다. 안 주면 잠시 뒤 중간 결과로 끝낸다.
            main.postDelayed({
                if (!finished) finish(_state.value.partial.trim().takeIf { it.isNotEmpty() }?.let { SpeechResult.Final(it) } ?: SpeechResult.NoSpeech)
            }, 1_200)
        }
    }

    /** 듣기를 버린다 (새 질문을 글로 했거나 화면을 닫음). 결과를 내보내지 않는다. */
    override fun cancel() {
        main.post {
            if (finished) return@post
            finished = true
            main.removeCallbacks(timeout)
            runCatching { stt?.stop() }
            runCatching { android?.cancel() }
            _state.value = ListenState()
        }
    }

    private fun usePleos() = pleosReady && !pleosBroken

    private fun finish(r: SpeechResult) {
        if (finished) return
        finished = true
        main.removeCallbacks(timeout)
        _state.value = ListenState()
        // 알아들은 말은 개인정보 → release 로그에는 글자 수만 (기획 가이드 보안: 로그 마스킹)
        Log.i(TAG, "듣기 끝: " + if (BuildConfig.DEBUG || r !is SpeechResult.Final) "$r" else "Final(${r.text.length}자)")
        _results.tryEmit(r)
    }

    // ---- Android 표준 인식기 (Pleos 음성 서비스가 없을 때) ----

    private fun startAndroid() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            finish(SpeechResult.Failed(MSG_UNAVAILABLE))
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            finish(SpeechResult.Failed(MSG_NO_PERMISSION))
            return
        }
        val r = android ?: SpeechRecognizer.createSpeechRecognizer(context).also { android = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                if (!finished) _state.value = _state.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                Log.w(TAG, "Android 음성 인식 오류 $error")
                finish(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechResult.NoSpeech
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechResult.Failed(MSG_NO_PERMISSION)
                        else -> SpeechResult.Failed(MSG_FAILED)
                    }
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
            Log.w(TAG, "Android 인식 시작 실패", it)
            finish(SpeechResult.Failed(MSG_FAILED))
        }
    }

    // ---- 디버그: 에뮬레이터에서 말 넣기 ----

    private fun registerDebugInput() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val text = intent.getStringExtra("text")?.trim().orEmpty()
                if (text.isEmpty()) return
                Log.i(TAG, "디버그 음성 입력: $text")
                main.post {
                    // 듣는 모습 → 중간 결과 → 최종 결과 (실제 듣기와 같은 순서)
                    runCatching { stt?.stop() }
                    runCatching { android?.cancel() }
                    main.removeCallbacks(timeout)
                    finished = false
                    _state.value = ListenState(listening = true, partial = text.take((text.length + 1) / 2), level = 0.6f)
                    main.postDelayed({ _state.value = _state.value.copy(partial = text, level = 0.3f) }, 450)
                    main.postDelayed({ finish(SpeechResult.Final(text)) }, 900)
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DEBUG_ACTION), ContextCompat.RECEIVER_EXPORTED)
    }

    private companion object {
        const val TAG = "EodiSpeech"
        const val DEBUG_ACTION = SpeechInput.DEBUG_ACTION
        const val MSG_FAILED = "말씀을 알아듣지 못했어요. 다시 말씀해 주세요."
        const val MSG_UNAVAILABLE = "이 차에서는 음성 인식을 쓸 수 없어요."
        const val MSG_NO_PERMISSION = "마이크 권한이 있어야 말로 물을 수 있어요."
    }
}
