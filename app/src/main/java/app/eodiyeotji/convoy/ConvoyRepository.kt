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
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.convoyEnergyStop
import app.eodiyeotji.core.convoyPositionShareable
import app.eodiyeotji.platform.EnergySource
import app.eodiyeotji.platform.GaugeSource
import app.eodiyeotji.platform.LocationSource
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RouteStop
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
    /** 배터리 · 연료가 부족한 차 (있으면 "같이 충전소 들르기") */
    val low: ConvoyMemberView? = null,
    /** 모두 함께 들를 곳 (충전소 · 주유소) */
    val waypoint: ConvoyPlace? = null,
    /** "같이 충전소 들르기" 결과 안내 (문자열 리소스) */
    val stopNotice: Int? = null,
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
    private val navi: Navigator,
    private val core: EodiCore,
    private val energy: EnergySource,
    private val gauges: GaugeSource,
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

    /** 내 잔량 (전기차 여부, %, km — 모르면 음수). 차 판은 Pleos Vehicle(지원 확인 뒤), 폰은 모름. 1분마다 다시 읽는다. */
    private data class MyEnergy(val ev: Boolean, val pct: Double, val rangeKm: Double)
    @Volatile private var myEnergy = MyEnergy(false, -1.0, -1.0)
    /** 디버그: 시연용 내 잔량 (브로드캐스트 `my_pct` · `my_range_km`) */
    @Volatile private var debugEnergy: MyEnergy? = null

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

    /** "목적지로 함께 안내": 같은 목적지를 내비에 보낸다 (함께 들를 곳이 있으면 경유지로). */
    suspend fun navigate(): Boolean {
        val d = _state.value.dest ?: return false
        val w = _state.value.waypoint
        return runCatching {
            if (w != null) {
                navi.requestRouteWithWaypoints(RouteStop(d.name, d.lat, d.lon), listOf(RouteStop(w.name, w.lat, w.lon)))
            } else {
                guide.startPlace(d.name, d.lat, d.lon)
            }
        }.getOrDefault(false)
    }

    /**
     * "같이 충전소 들르기": 부족한 차 기준으로 예전에 들렀던 충전소(전기차) · 주유소 중 길에서 덜 돌아가는 곳을 골라(Rust)
     * 모임의 "함께 들를 곳"으로 올리고 내 내비에도 경유지로 넣는다. 다른 차는 받은 경유지를 "목적지로 함께 안내"에 쓴다.
     * 기록에 없으면 내비 장소 검색을 "충전소"/"주유소"로 연다.
     */
    suspend fun stopTogether(): Boolean {
        val s = session ?: return false
        val st = _state.value
        val low = st.low ?: return false
        val d = st.dest
        val from = lastFix?.let { LatLon(it.lat, it.lon) } ?: LatLon(low.lat, low.lon)
        val ev = lastGroup?.members?.firstOrNull { it.id == low.id }?.ev ?: true
        val stop = d?.let { core.convoyEnergyStop(from, LatLon(it.lat, it.lon), ev) }
        if (d == null || stop == null) {
            val ok = runCatching { navi.openPlaceSearch(context.getString(if (ev) R.string.convoy_search_charger else R.string.convoy_search_gas)) }.getOrDefault(false)
            _state.update { it.copy(stopNotice = R.string.convoy_stop_none) }
            return ok
        }
        val place = ConvoyPlace(stop.first, stop.second, stop.third)
        _state.update { it.copy(waypoint = place, stopNotice = R.string.convoy_stop_added) }
        withContext(Dispatchers.IO) { runCatching { blockingWithTimeout(NET_TIMEOUT_MS) { backend.setWaypoint(s, place) } } }
        speech.speak(context.getString(R.string.convoy_tts_stop, place.name))
        return navigate()
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
        var tick = 0
        while (isActive && session === s) {
            if (tick++ % ENERGY_EVERY_TICKS == 0) readEnergy()
            val e = debugEnergy ?: myEnergy
            val fix = lastFix
            // 집 · 회사 근처(1km)에서는 내 위치를 보내지 않는다 (공유 개인정보 원칙, Rust convoy::position_shareable)
            val shareable = fix != null && fix.tMs != sentFixMs && core.convoyPositionShareable(LatLon(fix.lat, fix.lon))
            val group = runCatching {
                blockingWithTimeout(NET_TIMEOUT_MS) {
                    if (fix != null && shareable) {
                        backend.post(s, ConvoyPosition(fix.lat, fix.lon, -1.0, 0.0, -1, fix.tMs, e.ev, e.pct, e.rangeKm)).also { sentFixMs = fix.tMs }
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

    /** 내 잔량 읽기 (차: Pleos Vehicle — 지원하지 않으면 null이 온다). 실패해도 모름(음수)으로 둔다. */
    private suspend fun readEnergy() {
        val level = runCatching { energy.level() }.getOrNull()
        val range = runCatching { gauges.rangeKm() }.getOrNull()
        myEnergy = MyEnergy(level?.ev ?: false, level?.pct ?: -1.0, range ?: -1.0)
    }

    private fun apply(s: ConvoySession, group: ConvoyGroupDto?, fix: LocationFix?) {
        val t = tracker ?: return
        if (group != null) lastGroup = group
        val g = group ?: lastGroup
        val dest = g?.dest ?: _state.value.dest
        val others = g?.members.orEmpty().filter { it.id != s.memberId }.map {
            ConvoyMemberIn(it.id, it.name, it.lat, it.lon, it.heading, it.speedMps, it.etaS, it.tsMs, it.ev, it.energyPct, it.rangeKm)
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
                low = view.low,
                waypoint = g?.waypoint ?: it.waypoint,
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
     * `adb shell am broadcast -a app.eodiyeotji.DEBUG_CONVOY --es name 민수 --ef behind_km 2.5 --ef speed_kmh 50 --ef range_km 30`
     * 내 잔량 시연 값: `... --ef my_pct 60 --ef my_range_km 240`
     */
    private fun registerDebugFake() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // 내 잔량 시연 값 (중계 · 기기 안 둘 다)
                if (i.hasExtra("my_pct") || i.hasExtra("my_range_km")) {
                    debugEnergy = MyEnergy(i.getBooleanExtra("my_ev", true), i.getFloatExtra("my_pct", -1f).toDouble(), i.getFloatExtra("my_range_km", -1f).toDouble())
                    return
                }
                val local = backend as? LocalConvoyBackend ?: return
                if (_state.value.phase != ConvoyPhase.ACTIVE) {
                    Log.i(TAG, "가짜 차: 먼저 같이 가기를 시작하세요")
                    return
                }
                local.addFake(
                    i.getStringExtra("name") ?: "민수",
                    i.getFloatExtra("behind_km", 2f).toDouble(),
                    i.getFloatExtra("speed_kmh", 45f).toDouble(),
                    ev = i.getBooleanExtra("ev", true),
                    pct = i.getFloatExtra("pct", -1f).toDouble(),
                    rangeKm = i.getFloatExtra("range_km", -1f).toDouble(),
                )
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DEBUG_ACTION), ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        private const val TAG = "EodiConvoy"
        private const val KEY_NAME = "name"
        const val SEND_EVERY_MS = 4_000L
        const val NET_TIMEOUT_MS = 3_000L
        /** 잔량은 15번에 한 번(약 1분) 읽는다 */
        private const val ENERGY_EVERY_TICKS = 15
        const val DEBUG_ACTION = "app.eodiyeotji.DEBUG_CONVOY"
    }
}
