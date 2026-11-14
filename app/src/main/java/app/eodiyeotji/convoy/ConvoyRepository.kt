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
import app.eodiyeotji.core.rust.GeoPoint
import app.eodiyeotji.core.rust.convoyDemoStart
import app.eodiyeotji.core.rust.convoyDemoStep
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
import kotlinx.coroutines.NonCancellable
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
    /** 내 차 잔량 막대 (Rust `convoy_energy`) */
    val myEnergy: app.eodiyeotji.core.rust.ConvoyEnergy? = null,
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
    private val prefs by lazy { context.getSharedPreferences("convoy", Context.MODE_PRIVATE) }

    // 저장해 둔 내 이름은 백그라운드에서 읽는다: 첫 화면을 그리는 중(Hilt가 메인 스레드에서 만든다)에 설정 파일을 읽어
    // StrictMode 디스크 읽기 위반이 났다 (디버그 측정 수백 ms)
    private val _state = MutableStateFlow(ConvoyUiState(local = backend.local))
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
        scope.launch {
            val saved = prefs.getString(KEY_NAME, null).orEmpty()
            if (saved.isNotEmpty()) _state.update { if (it.name.isEmpty()) it.copy(name = saved) else it }
        }
        if (BuildConfig.DEBUG) registerDebugFake()
    }

    fun openSheet() = _state.update { it.copy(sheetOpen = true, error = null) }
    fun closeSheet() = _state.update { it.copy(sheetOpen = false, error = null) }

    /**
     * 새 모임 ([dest]가 같이 갈 목적지, 없으면 나중에 장소 창에서 정한다).
     * 공모전 판(CONTEST_DEMO)에서는 시연용 차 2대가 몇 초 뒤 저절로 참여한다 ([runDemo]). 코드로 참여할 때는 넣지 않는다.
     */
    fun create(name: String, dest: ConvoyPlace?) = start(name, demo = BuildConfig.CONTEST_DEMO) { backend.create(name, dest) }

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
        // 시연 차도 같이 나간다 (runDemo의 finally가 중계 서버에서 뺀다)
        demoJob?.cancel()
        demoJob = null
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

    private fun start(name: String, demo: Boolean = false, open: () -> ConvoySession) {
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
            if (demo) demoJob = scope.launch { runDemo(s) }
        }
    }

    private var demoJob: Job? = null

    /** 시연 차 한 대 (중계 서버에서는 진짜 참여자와 똑같이 코드로 들어와 위치를 보낸다). */
    private class DemoCar(val spec: DemoSpec, val session: ConvoySession, var at: GeoPoint, var rangeKm: Double)

    /**
     * 공모전 시연용 함께 가는 차 (BuildConfig.CONTEST_DEMO일 때만, 앱마켓 판에는 없다).
     * 모임을 만들면 [DEMO_CARS] 순서대로 몇 초 뒤 참여해 목적지 쪽으로 달린다. scripts/convoy-fake.ps1을 앱 안으로 옮긴 것.
     * - 중계 서버가 있으면 코드로 참여(join)하고 위치 · 잔량을 [DEMO_POST_MS]마다 보낸다 → 화면은 실제 사용과 같은 길로 받는다.
     * - 중계 서버가 없으면 기기 안 모임에 [LocalConvoyBackend.addFake]로 넣는다.
     * - 움직임(출발점 · 한 걸음 · 남은 거리)은 Rust `convoy::demo_start` · `demo_step`.
     * - 내가 나가면(또는 모임이 끝나면) 시연 차도 나간다.
     */
    private suspend fun runDemo(s: ConvoySession) = withContext(Dispatchers.IO) {
        val local = backend as? LocalConvoyBackend
        val cars = mutableListOf<DemoCar>()
        val startMs = System.currentTimeMillis()
        var lastPostMs = startMs
        var next = 0
        try {
            while (isActive && session === s) {
                val now = System.currentTimeMillis()
                val dest = _state.value.dest?.let { GeoPoint(it.lat, it.lon) }
                while (next < DEMO_CARS.size && now - startMs >= DEMO_CARS[next].joinAfterMs) {
                    val spec = DEMO_CARS[next++]
                    if (local != null) {
                        local.addFake(spec.name, spec.behindKm, spec.speedKmh, ev = true, rangeKm = spec.rangeKm)
                        continue
                    }
                    val cs = runCatching { blockingWithTimeout(NET_TIMEOUT_MS) { backend.join(s.code, spec.name) } }.getOrNull()
                    if (cs == null) {
                        next-- // 다음 바퀴에 다시
                        break
                    }
                    // 출발 기준: 내 위치, 모르면 목적지 북쪽 6km (스크립트와 같다), 둘 다 없으면 서울 시청
                    val fix = lastFix
                    val base = fix?.let { GeoPoint(it.lat, it.lon) } ?: dest?.let { GeoPoint(it.lat + 6 / 111.0, it.lon) } ?: GeoPoint(37.5665, 126.9780)
                    cars += DemoCar(spec, cs, convoyDemoStart(base, dest, spec.behindKm), spec.rangeKm)
                }
                if (now - lastPostMs >= DEMO_POST_MS) {
                    val dt = (now - lastPostMs) / 1000.0
                    lastPostMs = now
                    for (c in cars) {
                        val step = convoyDemoStep(c.at, dest, c.spec.speedKmh / 3.6, dt, c.rangeKm, true)
                        c.at = GeoPoint(step.lat, step.lon)
                        c.rangeKm = step.rangeKm
                        val pos = ConvoyPosition(step.lat, step.lon, step.heading, step.speedMps, step.etaS, now, true, step.energyPct, step.rangeKm)
                        runCatching { blockingWithTimeout(NET_TIMEOUT_MS) { backend.post(c.session, pos) } }
                            .onFailure { Log.w(TAG, "시연 차 위치 보내기 실패: ${c.spec.name}", it) }
                    }
                }
                delay(DEMO_TICK_MS)
            }
        } finally {
            withContext(NonCancellable) {
                cars.forEach { c -> runCatching { blockingWithTimeout(NET_TIMEOUT_MS) { backend.leave(c.session) } } }
            }
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
        val e = debugEnergy ?: myEnergy
        val myGauge = app.eodiyeotji.core.rust.convoyEnergy(e.ev, e.pct, e.rangeKm)
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
                myEnergy = myGauge,
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

        /** 시연 차 (공모전 판): 이름 · 몇 ms 뒤 참여 · 목적지 반대쪽으로 몇 km 뒤에서 출발(음수 = 내 앞) · 시속 · 남은 주행 가능 km */
        private data class DemoSpec(val name: String, val joinAfterMs: Long, val behindKm: Double, val speedKmh: Double, val rangeKm: Double)
        /** 민수는 30km 남은 전기차 → "배터리 부족" · 같이 충전소 들르기 시연. 지현은 넉넉 (scripts/convoy-fake.ps1 기본값과 같다) */
        private val DEMO_CARS = listOf(
            DemoSpec("민수", 3_000, 2.5, 45.0, 30.0),
            DemoSpec("지현", 6_000, -0.8, 45.0, 180.0),
        )
        private const val DEMO_TICK_MS = 1_000L
        private const val DEMO_POST_MS = 3_000L
    }
}
