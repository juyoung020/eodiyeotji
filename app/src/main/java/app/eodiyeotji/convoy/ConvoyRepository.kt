package app.eodiyeotji.convoy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.R
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.core.MapBoundsLatLon
import app.eodiyeotji.core.rust.ConvoyDest
import app.eodiyeotji.core.rust.ConvoyMe
import app.eodiyeotji.core.rust.ConvoyMemberIn
import app.eodiyeotji.core.rust.ConvoyMemberView
import app.eodiyeotji.core.rust.ConvoyTracker
import app.eodiyeotji.pleos.CourseGuide
import app.eodiyeotji.place.blockingWithTimeout
import app.eodiyeotji.platform.LocationSource
import app.eodiyeotji.platform.SpeechOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConvoyPhase { IDLE, CONNECTING, ACTIVE }

/** 같이 가기 화면 상태 (계산은 Rust `convoy`가 끝낸 값). */
@Immutable
data class ConvoyUiState(
    val phase: ConvoyPhase = ConvoyPhase.IDLE,
    val code: String = "",
    val dest: ConvoyPlace? = null,
    /** 나를 뺀 차들 (색 번호 순) */
    val members: List<ConvoyMemberView> = emptyList(),
    /** 주행 중 큰 카드에 보일 차 */
    val focus: ConvoyMemberView? = null,
    val geojson: String = "",
    val bounds: MapBoundsLatLon? = null,
    /** 서버와 잠깐 끊김 (마지막으로 받은 위치로 계속 보여 준다) */
    val offline: Boolean = false,
    /** 중계 서버 없이 이 기기 안에서만 (디버그 시연) */
    val local: Boolean = false,
    /** 참여 · 만들기 실패 안내 (문자열 리소스) */
    val error: Int? = null,
    val sheetOpen: Boolean = false,
    val name: String = "",
)

/**
 * 같이 가기: 모임 만들기 · 코드로 참여 · 내 위치 보내기 · 다른 차 받기 · 알림.
 * - 위치는 같이 가기가 켜져 있는 동안만 보낸다 ([SEND_EVERY_MS]마다 마지막 위치 하나). 나가기 · 목적지 도착 · 12시간이면 끝.
 * - 네트워크는 IO 스레드에서 짧은 타임아웃 ([NET_TIMEOUT_MS]). 끊기면 마지막 상태를 그대로 보이며 다시 시도한다.
 * - 앞 · 뒤 · 도착 차이 · 알림 문구는 Rust [ConvoyTracker]가 계산한다. 여기서는 나르기만.
 */
@Singleton
class ConvoyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val location: LocationSource,
    private val speech: SpeechOutput,
    private val guide: CourseGuide,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backend: ConvoyBackend =
        BuildConfig.RELAY_URL.trimEnd('/').takeIf { it.isNotEmpty() }?.let { RelayConvoyBackend(it, BuildConfig.RELAY_TOKEN) }
            ?: LocalConvoyBackend()
    private val prefs = context.getSharedPreferences("convoy", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ConvoyUiState(local = backend.local, name = prefs.getString(KEY_NAME, null).orEmpty()))
    val state: StateFlow<ConvoyUiState> = _state.asStateFlow()

    private var session: ConvoySession? = null
    private var tracker: ConvoyTracker? = null
    private var loop: Job? = null
    @Volatile private var lastFix: LocationFix? = null
    @Volatile private var sentFixMs = 0L

    init {
        if (BuildConfig.DEBUG) registerDebugFake()
    }

    fun openSheet() = _state.update { it.copy(sheetOpen = true, error = null) }
    fun closeSheet() = _state.update { it.copy(sheetOpen = false, error = null) }

    /** 새 모임 ([dest]가 같이 갈 목적지, 없으면 나중에 장소 창에서 정한다). */
    fun create(name: String, dest: ConvoyPlace?) = start(name) { backend.create(name, dest) }

    /** 코드로 참여 (숫자 6자리). */
    fun join(code: String, name: String) = start(name) { backend.join(code.filter { it.isDigit() }, name) }

    /**
     * 장소 창의 "같이 가기": 같이 가는 중이면 목적지를 이곳으로 바꾸고, 아니면 이곳으로 가는 모임을 만든다.
     */
    fun goTogether(place: ConvoyPlace) {
        val s = session
        if (s == null) {
            _state.update { it.copy(dest = place, sheetOpen = true) }
            return
        }
        scope.launch {
            _state.update { it.copy(dest = place, sheetOpen = true) }
            runCatching { blockingWithTimeout(NET_TIMEOUT_MS) { backend.setDest(s, place) } }
                .onFailure { Log.w(TAG, "목적지 바꾸기 실패", it) }
        }
    }

    /** 나가기 (위치 공유를 바로 끝낸다). */
    fun leave() {
        val s = session ?: return
        session = null
        tracker = null
        loop?.cancel()
        loop = null
        _state.update { ConvoyUiState(local = backend.local, name = it.name) }
        // 서버에 알리는 건 기다리지 않는다 (실패해도 10분 뒤 서버가 지운다)
        scope.launch { runCatching { blockingWithTimeout(NET_TIMEOUT_MS) { backend.leave(s) } } }
    }

    /** "목적지로 함께 안내": 같은 목적지를 내비에 보낸다. */
    suspend fun navigate(): Boolean {
        val d = _state.value.dest ?: return false
        return runCatching { guide.startPlace(d.name, d.lat, d.lon) }.getOrDefault(false)
    }

    private fun start(name: String, open: () -> ConvoySession) {
        if (_state.value.phase != ConvoyPhase.IDLE) return
        prefs.edit().putString(KEY_NAME, name).apply()
        _state.update { it.copy(phase = ConvoyPhase.CONNECTING, error = null, name = name) }
        scope.launch {
            val s = runCatching { blockingWithTimeout(NET_TIMEOUT_MS * 2) { open() } }.getOrNull()
            if (s == null) {
                _state.update { it.copy(phase = ConvoyPhase.IDLE, error = R.string.convoy_error_connect) }
                return@launch
            }
            session = s
            tracker = ConvoyTracker()
            _state.update { it.copy(phase = ConvoyPhase.ACTIVE, code = s.code, dest = s.dest ?: it.dest) }
            // 장소 창에서 고른 목적지로 만들었으면 서버에도 (참여한 경우는 모임의 목적지를 따른다)
            val wanted = _state.value.dest
            if (s.dest == null && wanted != null) runCatching { blockingWithTimeout(NET_TIMEOUT_MS) { backend.setDest(s, wanted) } }
            loop = scope.launch { run(s) }
        }
    }

    private suspend fun run(s: ConvoySession) = withContext(Dispatchers.Default) {
        // 위치는 받는 대로 마지막 하나만 기억한다 (보내기는 아래에서 묶어서 띄엄띄엄)
        val locJob = launch {
            location.updates()
                .catch { Log.w(TAG, "위치를 받을 수 없음", it) }
                .collect { lastFix = it }
        }
        while (isActive && session === s) {
            val fix = lastFix
            val group = runCatching {
                blockingWithTimeout(NET_TIMEOUT_MS) {
                    if (fix != null && fix.tMs != sentFixMs) {
                        backend.post(s, ConvoyPosition(fix.lat, fix.lon, -1.0, 0.0, -1, fix.tMs)).also { sentFixMs = fix.tMs }
                    } else {
                        backend.state(s)
                    }
                }
            }.getOrNull()
            apply(s, group, fix)
            if (System.currentTimeMillis() > s.expiresAtMs) {
                leave()
                break
            }
            delay(SEND_EVERY_MS)
        }
        locJob.cancel()
    }

    private var lastGroup: ConvoyGroupDto? = null

    private fun apply(s: ConvoySession, group: ConvoyGroupDto?, fix: LocationFix?) {
        val t = tracker ?: return
        if (group != null) lastGroup = group
        val g = group ?: lastGroup
        val dest = g?.dest ?: _state.value.dest
        val others = g?.members.orEmpty().filter { it.id != s.memberId }.map {
            ConvoyMemberIn(it.id, it.name, it.lat, it.lon, it.heading, it.speedMps, it.etaS, it.tsMs)
        }
        val view = t.update(
            System.currentTimeMillis(),
            fix?.let { ConvoyMe(it.lat, it.lon, -1.0, 0.0, -1) },
            dest?.let { ConvoyDest(it.name, it.lat, it.lon) },
            others,
        )
        _state.update {
            it.copy(
                dest = dest,
                members = view.members,
                focus = view.focus,
                geojson = view.geojson,
                bounds = view.bounds?.let { b -> MapBoundsLatLon(b.minLat, b.minLon, b.maxLat, b.maxLon) },
                offline = group == null,
            )
        }
        // 알림은 Rust가 정한 것만 (도착 · 3km 넘게 뒤처짐, 60초 간격 · 횟수 제한). 내비 음성과는 SpeechOutput이 오디오 포커스로 비킨다.
        view.speak?.let { speech.speak(it) }
        if (view.meArrived) {
            speech.speak(context.getString(R.string.convoy_tts_ended))
            leave()
        }
    }

    /**
     * 디버그: 가짜 차 넣기 (중계 서버가 없을 때 · 기기 안 모임만).
     * `adb shell am broadcast -a app.eodiyeotji.DEBUG_CONVOY --es name 민수 --ef behind_km 2.5 --ef speed_kmh 50`
     */
    private fun registerDebugFake() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val local = backend as? LocalConvoyBackend ?: return
                if (_state.value.phase != ConvoyPhase.ACTIVE) {
                    Log.i(TAG, "가짜 차: 먼저 같이 가기를 시작하세요")
                    return
                }
                local.addFake(i.getStringExtra("name") ?: "민수", i.getFloatExtra("behind_km", 2f).toDouble(), i.getFloatExtra("speed_kmh", 45f).toDouble())
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DEBUG_ACTION), ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        private const val TAG = "EodiConvoy"
        private const val KEY_NAME = "name"
        const val SEND_EVERY_MS = 4_000L
        const val NET_TIMEOUT_MS = 3_000L
        const val DEBUG_ACTION = "app.eodiyeotji.DEBUG_CONVOY"
    }
}
