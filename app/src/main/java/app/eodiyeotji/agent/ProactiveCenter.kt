package app.eodiyeotji.agent

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.Immutable
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.lastKnownLocation
import app.eodiyeotji.core.rust.GeoPoint
import app.eodiyeotji.core.rust.NavTarget
import app.eodiyeotji.core.rust.ProactiveAction
import app.eodiyeotji.core.rust.ProactiveEvent
import app.eodiyeotji.core.rust.ProactiveSuggestion
import app.eodiyeotji.core.rust.ReplyIntent
import app.eodiyeotji.core.rust.VehicleSnapshot
import app.eodiyeotji.core.rust.proactiveReplyIntent
import app.eodiyeotji.platform.EnergySource
import app.eodiyeotji.platform.GaugeSource
import app.eodiyeotji.platform.NaviEvent
import app.eodiyeotji.platform.NaviPoi
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RouteStop
import app.eodiyeotji.platform.SpeechInput
import app.eodiyeotji.platform.SpeechOutput
import app.eodiyeotji.platform.SpeechResult
import app.eodiyeotji.pleos.CourseGuide
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 먼저 말 걸기 카드 상태. */
@Immutable
data class ProactiveUi(
    val id: Long,
    val suggestion: ProactiveSuggestion,
    val phase: Phase = Phase.Asking,
    /** 실행 결과 한 줄 (문자열 자원 id). */
    val result: Int? = null,
    /** Gleo Standby: 말과 듣기가 끝나 5초 뒤 내려가는 중. */
    val standby: Boolean = false,
) {
    enum class Phase { Asking, Listening, Running, Done, Failed }
}

/**
 * 에이전트가 먼저 말 걸기 (출발 · 잔량 부족 · 도착). **무엇을 말할지 · 몇 번까지 말할지는 Rust `proactive.rs`가 정하고**,
 * 여기서는 ① 차 상태(내비 목적지 · 잔량 · 위치 · 내비 음성)를 모아 넘기고 ② 카드 한 장([state]) + 짧은 음성으로 내보내고
 * ③ "응/아니"(말 또는 버튼)를 받아 Pleos 내비 · 저장소로 실행하기만 한다.
 *
 * - 출발: 기록 서비스가 주행 시작을 알리면([onDriveStart]) 내비 목적지 조회를 잠깐 기다린 뒤 묻는다 (목적지가 이미 있으면 Rust가 건너뛴다).
 * - 잔량: 주행 중 [ENERGY_EVERY_MS]마다, 그리고 내비 목적지가 바뀔 때.
 * - 도착: 내비 목적지 도착(onDestinationArrived) 또는 주행 끝(기어 P). 한 주행에 한 번.
 * - 말하는 중이거나 마이크가 듣는 중이면 끼어들지 않는다. 내비 안내 음성이 나오는 중이면 Rust가 막는다.
 *   TTS는 오디오 포커스를 지킨다 (`PleosTts`).
 * - "응/아니"를 들을 때는 [ProactiveVoice.claimed]로 에이전트 화면이 그 말을 질문으로 받지 않게 한다.
 */
@Singleton
class ProactiveCenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
    private val navi: Navigator,
    private val guide: CourseGuide,
    private val energy: EnergySource,
    private val gauges: GaugeSource,
    private val tts: SpeechOutput,
    private val speech: SpeechInput,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w(TAG, "먼저 말 걸기 오류", e) })
    private val _state = MutableStateFlow<ProactiveUi?>(null)

    /** 화면에 보일 카드 (없으면 null). */
    val state: StateFlow<ProactiveUi?> = _state.asStateFlow()

    /** 카드 말을 읽는 중 (Gleo Speaking 표시). */
    val speaking: StateFlow<Boolean> get() = tts.speaking

    @Volatile private var destination: NaviPoi? = null
    @Volatile private var waypoints: List<NaviPoi> = emptyList()
    @Volatile private var arrivedAtMs = 0L
    private var driveJob: Job? = null
    private var cardJob: Job? = null
    private var lastId = 0L

    /** 지금 내비 목적지 · 경유지 (주행 중 내비 이벤트로 받은 것). 길 위 도구 에이전트(`agent/roadside`)가 읽는다. */
    val naviDestination: NaviPoi? get() = destination
    val naviWaypoints: List<NaviPoi> get() = waypoints

    /** 시연용으로 바꿔 끼운 값 ([fireDebug]). */
    @Volatile private var override: DebugOverride? = null

    /** 주행 시작 (기록 서비스): 잠시 뒤 출발 제안, 주행 동안 내비 이벤트 · 잔량을 본다. */
    fun onDriveStart() {
        driveJob?.cancel()
        arrivedAtMs = 0L
        driveJob = scope.launch {
            if (withContext(Dispatchers.IO) { navi.eventsAvailable() }) launch { watchNavi() }
            delay(DEPARTURE_DELAY_MS) // 내비가 목적지를 알려 줄 시간
            fire(ProactiveEvent.DEPARTURE)
            delay(ENERGY_FIRST_MS)
            while (true) {
                fire(ProactiveEvent.LOW_ENERGY)
                delay(ENERGY_EVERY_MS)
            }
        }
    }

    /** 주행 끝 (기어 P): 이번 주행에서 도착 제안을 아직 안 했으면 한다. */
    fun onDriveEnd() {
        driveJob?.cancel()
        driveJob = null
        scope.launch {
            if (System.currentTimeMillis() - arrivedAtMs > ARRIVAL_DEDUPE_MS) fire(ProactiveEvent.ARRIVAL)
            destination = null
            waypoints = emptyList()
        }
    }

    /** 카드 "예" 또는 말 "응". */
    fun accept() {
        val cur = _state.value ?: return
        if (cur.phase == ProactiveUi.Phase.Running || cur.phase == ProactiveUi.Phase.Done) return
        speech.cancel()
        ProactiveVoice.claimed = false
        scope.launch { run(cur) }
    }

    /** 카드 "아니요" 또는 말 "아니": 오늘은 그 계기로 다시 묻지 않는다. */
    fun decline() {
        val cur = _state.value ?: return
        speech.cancel()
        tts.stop()
        ProactiveVoice.claimed = false
        _state.value = null
        if (cur.suggestion.ask) scope.launch { safely { core.rust.proactiveMarkDeclined(cur.suggestion.event, System.currentTimeMillis()) } }
    }

    /** 카드 닫기 (회상 카드 · 끝난 카드). */
    fun dismiss() {
        if (_state.value?.phase == ProactiveUi.Phase.Listening) speech.cancel()
        ProactiveVoice.claimed = false
        _state.value = null
    }

    /**
     * 디버그 · 시연: 계기 하나를 바로 일으킨다. [reset]이면 오늘 횟수 기록을 먼저 지운다.
     * 값을 주면 차 상태 대신 쓴다 (에뮬레이터는 잔량 · 남은 거리를 바꿀 수 없다).
     */
    fun fireDebug(event: ProactiveEvent, reset: Boolean, o: DebugOverride) {
        scope.launch {
            if (reset) safely { core.rust.proactiveReset() }
            override = o
            try {
                fire(event, force = true)
            } finally {
                override = null
            }
        }
    }

    private suspend fun watchNavi() {
        navi.events().collect { e ->
            when (e) {
                is NaviEvent.Destination -> {
                    val changed = e.poi?.name != destination?.name
                    destination = e.poi
                    if (changed && e.poi != null) scope.launch { delay(3_000); fire(ProactiveEvent.LOW_ENERGY) }
                }
                is NaviEvent.Waypoints -> waypoints = e.pois
                NaviEvent.RouteCancelled -> {
                    destination = null
                    waypoints = emptyList()
                }
                is NaviEvent.DestinationArrived -> {
                    arrivedAtMs = System.currentTimeMillis()
                    fire(ProactiveEvent.ARRIVAL)
                }
                else -> Unit
            }
        }
    }

    private suspend fun fire(event: ProactiveEvent, force: Boolean = false) {
        // 다른 말을 하거나 듣는 중이면 끼어들지 않는다 (시연은 지금 것을 멈추고)
        if (!force && (tts.speaking.value || speech.state.value.listening || _state.value?.phase == ProactiveUi.Phase.Listening)) return
        val now = System.currentTimeMillis()
        val snap = snapshot()
        val s = safely { withContext(Dispatchers.IO) { core.rust.proactiveSuggest(event, now, snap) } } ?: return
        Log.i(TAG, "먼저 말 걸기: ${s.kind} ${if (app.eodiyeotji.BuildConfig.DEBUG) s.line else ""}")
        safely { core.rust.proactiveMarkShown(event, now) }
        if (force) {
            tts.stop()
            speech.cancel()
        }
        val id = ++lastId
        _state.value = ProactiveUi(id, s)
        // 표시 시간 (Pleos 가이드 Gleo AI Standby): 말을 다 읽은 뒤 — 묻는 카드는 "응/아니"를 들은 뒤 — 5초 유지하고 내려간다.
        // 읽기 완료 알림이 오지 않는 경우(음성 엔진 없음 · 멈춤)를 위해 넉넉한 안전 시간도 둔다.
        cardJob?.cancel()
        cardJob = scope.launch {
            delay(SAFETY_SHOWN_MS)
            if (_state.value?.id == id && _state.value?.phase != ProactiveUi.Phase.Running) dismiss()
        }
        tts.speak(s.line) {
            if (s.ask && _state.value?.id == id && _state.value?.phase == ProactiveUi.Phase.Asking && !speech.needsMicPermission()) listen(id) else standby(id)
        }
    }

    /** Gleo Standby: [STANDBY_MS] 뒤 카드가 내려간다 (그 사이 버튼은 그대로 누를 수 있다). */
    private fun standby(id: Long) {
        if (_state.value?.id != id) return
        _state.update { if (it?.id == id) it.copy(standby = true) else it }
        cardJob?.cancel()
        cardJob = scope.launch {
            delay(STANDBY_MS)
            val cur = _state.value
            if (cur?.id == id && cur.phase != ProactiveUi.Phase.Running && cur.phase != ProactiveUi.Phase.Listening) dismiss()
        }
    }

    /** "응/아니" 한마디 듣기. 알아듣지 못하면 카드 버튼을 기다린다. */
    private fun listen(id: Long) {
        _state.update { if (it?.id == id) it.copy(phase = ProactiveUi.Phase.Listening) else it }
        ProactiveVoice.claimed = true
        scope.launch {
            val r = withTimeoutOrNull(LISTEN_MS + 3_000) {
                speech.start(LISTEN_MS)
                speech.results.first()
            }
            ProactiveVoice.claimed = false
            if (_state.value?.id != id) return@launch
            val intent = (r as? SpeechResult.Final)?.let { safely { proactiveReplyIntent(it.text) } } ?: ReplyIntent.UNKNOWN
            when (intent) {
                ReplyIntent.YES -> accept()
                ReplyIntent.NO -> decline()
                ReplyIntent.UNKNOWN -> {
                    _state.update { if (it?.id == id) it.copy(phase = ProactiveUi.Phase.Asking) else it }
                    standby(id)
                }
            }
        }
    }

    private suspend fun run(cur: ProactiveUi) {
        _state.update { if (it?.id == cur.id) it.copy(phase = ProactiveUi.Phase.Running, standby = false) else it }
        cardJob?.cancel()
        val ok = runCatching {
            when (val a = cur.suggestion.action) {
                is ProactiveAction.Navigate -> guide.startPlace(a.stop.name, a.stop.lat, a.stop.lon, a.stop.address)
                is ProactiveAction.AddWaypoint -> {
                    // 이미 있던 경유지(좌표를 아는 것) 하나는 남긴다 — 내비 경유지는 2개까지
                    val keep = waypoints.mapNotNull { p -> if (p.lat != null && p.lon != null) RouteStop(p.name, p.lat, p.lon, p.address) else null }.take(1)
                    navi.requestRouteWithWaypoints(
                        RouteStop(a.destination.name, a.destination.lat, a.destination.lon),
                        listOf(RouteStop(a.stop.name, a.stop.lat, a.stop.lon, a.stop.address)) + keep,
                    )
                }
                is ProactiveAction.MarkBiz -> {
                    core.setBizMark(a.day, true)
                    true
                }
                ProactiveAction.Inform -> true
            }
        }.onFailure { Log.w(TAG, "제안 실행 실패", it) }.getOrDefault(false)
        val msg = when {
            !ok -> app.eodiyeotji.R.string.proactive_failed
            cur.suggestion.action is ProactiveAction.MarkBiz -> app.eodiyeotji.R.string.proactive_done_biz
            cur.suggestion.action is ProactiveAction.AddWaypoint -> app.eodiyeotji.R.string.proactive_done_waypoint
            else -> app.eodiyeotji.R.string.proactive_done_navigate
        }
        _state.update { if (it?.id == cur.id) it.copy(phase = if (ok) ProactiveUi.Phase.Done else ProactiveUi.Phase.Failed, result = msg) else it }
        cardJob = scope.launch {
            delay(SAFETY_SHOWN_MS)
            if (_state.value?.id == cur.id) _state.value = null
        }
        tts.speak(context.getString(msg)) { standby(cur.id) }
    }

    private suspend fun snapshot(): VehicleSnapshot {
        val o = override
        val here = o?.here ?: safely { core.lastKnownLocation() }?.let { GeoPoint(it.lat, it.lon) }
        val dest = o?.destination ?: destination?.let { p -> if (p.lat != null && p.lon != null) NavTarget(p.name, p.lat, p.lon) else null }
        val level = if (o?.energyPct != null) null else safely { energy.level() }
        val range = o?.rangeKm ?: if (o?.energyPct != null) null else safely { gauges.rangeKm() }
        return VehicleSnapshot(
            here = here,
            destination = dest,
            waypointCount = (o?.waypointCount ?: waypoints.size).toUInt(),
            rangeKm = range,
            energyPct = o?.energyPct ?: level?.pct,
            ev = o?.ev ?: level?.ev ?: true,
            navVoice = o == null && navVoicePlaying(),
        )
    }

    /** 내비 안내 음성이 지금 나오는 중인가 (재생 중인 오디오의 용도로 본다, 권한 없음). */
    private fun navVoicePlaying(): Boolean = runCatching {
        val am = context.getSystemService(AudioManager::class.java)
        am.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE }
    }.getOrDefault(false)

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "먼저 말 걸기 호출 실패", e)
        null
    }

    /** 시연 때 차 상태 대신 쓸 값 (null이면 실제 값). */
    data class DebugOverride(
        val here: GeoPoint? = null,
        val destination: NavTarget? = null,
        val rangeKm: Double? = null,
        val energyPct: Double? = null,
        val ev: Boolean? = null,
        val waypointCount: Int? = null,
    )

    private companion object {
        const val TAG = "EodiProactive"
        const val DEPARTURE_DELAY_MS = 4_000L
        const val ENERGY_FIRST_MS = 60_000L
        const val ENERGY_EVERY_MS = 3 * 60_000L
        const val ARRIVAL_DEDUPE_MS = 10 * 60_000L
        const val LISTEN_MS = 6_000L
        /** Gleo Standby: 대화가 끝난 뒤 응답 화면 유지 (Pleos 가이드 Experiences/Gleo AI) */
        const val STANDBY_MS = 5_000L
        /** 읽기 완료 알림이 오지 않을 때만 쓰는 최대 표시 시간 */
        const val SAFETY_SHOWN_MS = 30_000L
    }
}

/** 먼저 말 걸기가 "응/아니"를 듣는 중이면 true — 에이전트 화면은 그 말을 질문으로 받지 않는다. */
object ProactiveVoice {
    @Volatile var claimed: Boolean = false
}

/** Hilt 주입이 안 되는 곳(디버그 수신기 · Compose)에서 꺼낸다. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProactiveEntryPoint {
    fun proactiveCenter(): ProactiveCenter
}

fun Context.proactiveCenter(): ProactiveCenter =
    EntryPointAccessors.fromApplication(applicationContext, ProactiveEntryPoint::class.java).proactiveCenter()
