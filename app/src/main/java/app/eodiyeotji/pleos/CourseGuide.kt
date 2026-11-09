package app.eodiyeotji.pleos

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Immutable
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.agent.AgentBridge
import app.eodiyeotji.core.CourseLeg
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.platform.NaviEvent
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RouteStop
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 코스 안내 진행 상황 (화면 아래 알림 카드가 그대로 그린다).
 *
 * @param courseId 커뮤니티 코스면 그 글 id, 에이전트 추천 코스면 null
 * @param leg 지금 내비가 안내 중인 구간 (1부터)
 * @param upcoming 이번 구간에서 아직 안 들른 곳 이름 (순서대로). 모르면 빈 목록
 * @param nextLeg 다음 구간에서 처음 들를 곳 (마지막 구간이면 null)
 * @param single 코스가 아니라 장소 하나로 가는 안내 ([title]이 그 장소 이름)
 */
@Immutable
data class CourseProgress(
    val courseId: String?,
    val title: String?,
    val leg: Int,
    val total: Int,
    val upcoming: List<String>,
    val nextLeg: String?,
    val finished: Boolean = false,
    val failed: Boolean = false,
    val single: Boolean = false,
) {
    val hasNextLeg: Boolean get() = leg < total
}

/**
 * 코스 안내를 끝까지 이어 주는 곳 (커뮤니티 코스·에이전트 추천 코스 공용).
 *
 * 내비 경유지는 2개까지라 코스는 구간으로 나뉜다. 예전에는 한 구간이 끝날 때마다 앱으로 돌아와 "다음 구간 안내"를
 * 눌러야 했다. 이제는 코스를 시작하면 내비 이벤트를 듣고 있다가 **목적지 도착(onDestinationArrived)이 오면
 * 다음 구간을 바로 내비에 보낸다.** 사용자는 내비 화면에서 계속 따라가기만 하면 된다.
 *
 * - 커뮤니티 코스: [startShared]가 구간 전부를 받아 첫 구간부터 보낸다.
 * - 에이전트 코스: 첫 구간은 AgentBridge가 이미 보냈다. 남은 구간([AgentBridge.pendingLegs])이 생기면 여기서
 *   이어받고, 도착할 때마다 [AgentBridge.continueCourse]를 부른다.
 * - 도착 이벤트가 안 오는 경우(내비에서 안내를 끈 경우 등)를 위해 [skipToNextLeg]로 손으로 넘길 수도 있다.
 *   폰은 앱 안 길 안내(phone/PhoneNavigator.kt)가 도착을 알려 주므로 차와 똑같이 자동으로 넘어간다.
 * - 장소 하나 "길안내"([startPlace], 에이전트의 장소 안내 포함)도 같은 카드로 보여 준다 (구간 1개짜리).
 * - 내비가 앱 화면을 덮으므로 빠져나올 길을 둔다: 내비에서 "안내 종료"를 누르면(onRouteCancelled) 카드를 닫고
 *   우리 앱을 앞으로 다시 불러 원래 보던 화면으로 돌아온다. 우리 카드의 "안내 종료"는 내비 안내까지 끈다([stop]).
 * 계산은 없다: 구간은 Rust `course::split_legs`가 나눈 그대로 쓴다.
 */
@Singleton
class CourseGuide @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navi: Navigator,
    private val agent: AgentBridge,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w(TAG, "코스 안내 오류", e) },
    )
    private val lock = Mutex()
    private val _progress = MutableStateFlow<CourseProgress?>(null)
    val progress: StateFlow<CourseProgress?> = _progress.asStateFlow()

    /** 지금 코스. 구간마다 들를 곳 이름 (에이전트 코스의 첫 구간은 모름 → null, 내비가 알려 준 값으로 채운다). */
    private var legs: List<CourseLeg?> = emptyList()
    private var fromAgent = false

    /** 에이전트가 들고 있을 남은 구간 (우리가 continueCourse로 줄인 값). 다르면 에이전트가 새 코스를 시작한 것. */
    private var agentExpected: List<CourseLeg> = emptyList()
    private var watchJob: Job? = null
    private var clearJob: Job? = null
    private var legSentAt = 0L

    /** 마지막으로 실패한 요청 (다시 보내기). */
    private var retryFailed: (suspend () -> Unit)? = null

    init {
        scope.launch {
            agent.placeGuided.collect { stop -> lock.withLock { beginPlace(stop) } }
        }
        scope.launch {
            agent.pendingLegs.collect { pending ->
                lock.withLock {
                    if (pending.isNotEmpty() && pending != agentExpected) {
                        // 에이전트가 첫 구간을 내비에 보내고 남은 구간을 넘겼다 → 여기서 이어 간다
                        agentExpected = pending
                        begin(fromAgent = true, courseId = null, title = null, legs = listOf(null) + pending)
                    }
                }
            }
        }
    }

    /** 커뮤니티 코스 안내 시작: 첫 구간을 내비에 보내고, 도착하면 다음 구간을 알아서 보낸다. 반환: 첫 구간을 보냈는지. */
    suspend fun startShared(courseId: String, title: String, legs: List<CourseLeg>): Boolean = lock.withLock {
        if (legs.isEmpty()) return@withLock false
        begin(fromAgent = false, courseId = courseId, title = title, legs = legs)
        sendFirst(legs[0])
    }

    /** 장소 하나로 "길안내": 내비에 보내고, 안내 중 카드(안내 종료 버튼)를 띄운다. 반환: 보냈는지. */
    suspend fun startPlace(name: String, lat: Double, lon: Double, address: String = ""): Boolean = lock.withLock {
        val stop = RouteStop(name, lat, lon, address)
        val ok = requestPlace(stop)
        if (ok) beginPlace(stop)
        ok
    }

    /** 도착을 기다리지 않고 다음 구간으로 (내비에서 안내를 끝냈거나 건너뛰고 싶을 때). 마지막 구간이면 지금 구간을 다시 보낸다. */
    fun skipToNextLeg() {
        scope.launch { lock.withLock { advance(manual = true) } }
    }

    /** 내비에 보내지 못한 구간을 다시 보낸다. */
    fun retry() {
        scope.launch {
            lock.withLock {
                val again = retryFailed ?: return@withLock
                retryFailed = null
                again()
            }
        }
    }

    /** 안내 종료: 우리 카드와 자동 이어 가기를 멈추고 내비 안내도 끈다 (이미 도착해 끝난 안내면 카드만 닫는다). */
    fun stop() {
        scope.launch {
            val cancelNavi = lock.withLock {
                val active = _progress.value?.let { !it.finished } == true
                reset() // 이벤트 듣기를 먼저 끊는다 → 우리가 끈 취소 알림으로 앱을 다시 불러오지 않는다
                active
            }
            if (!cancelNavi) return@launch
            val cancelled = runCatching { navi.cancelRoute() }.onFailure { Log.w(TAG, "안내 취소 실패", it) }.getOrDefault(false)
            if (cancelled) {
                // 차량 내비는 안내를 끄면서 제 화면을 앞으로 띄운다 → 누른 사람이 보던 우리 화면으로 다시 돌아온다
                delay(NAVI_SETTLE_MS)
                returnToApp()
            }
        }
    }

    // ---- 안쪽 (모두 lock 안에서 부른다) ----

    private suspend fun sendFirst(leg: CourseLeg): Boolean {
        val ok = request(leg)
        _progress.update { it?.copy(failed = !ok) }
        retryFailed = if (ok) null else ({ sendFirst(leg) })
        return ok
    }

    private fun beginPlace(stop: RouteStop) {
        begin(fromAgent = false, courseId = null, title = stop.name, legs = listOf(null))
        _progress.value = _progress.value?.copy(single = true, upcoming = listOf(stop.name))
        retryFailed = null
    }

    private suspend fun requestPlace(stop: RouteStop): Boolean =
        runCatching { navi.requestRoute(stop.name, stop.lat, stop.lon, stop.address) }
            .onFailure { Log.w(TAG, "장소 안내 요청 실패", it) }
            .getOrDefault(false)

    /** 내비가 앱 화면을 덮고 있을 때 우리 앱(원래 보던 화면 그대로)을 앞으로 불러온다. */
    private fun returnToApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "앱으로 돌아오기 실패", it) }
    }

    private fun begin(fromAgent: Boolean, courseId: String?, title: String?, legs: List<CourseLeg?>) {
        clearJob?.cancel()
        this.legs = legs
        this.fromAgent = fromAgent
        legSentAt = System.currentTimeMillis()
        _progress.value = CourseProgress(
            courseId = courseId,
            title = title,
            leg = 1,
            total = legs.size,
            upcoming = legs[0]?.names().orEmpty(),
            nextLeg = legs.getOrNull(1)?.firstName(),
        )
        if (watchJob?.isActive != true) watchJob = scope.launch { watchNavi() }
    }

    private suspend fun advance(manual: Boolean) {
        val p = _progress.value ?: return
        if (p.finished) return
        if (!p.hasNextLeg) {
            if (manual) {
                // 마지막 구간: 손으로 누르면 그 구간을 다시 보낸다 (내비 안내가 꺼졌을 때)
                legs.lastOrNull()?.let { request(it) }
            } else {
                finish()
            }
            return
        }
        val nextIndex = p.leg // 0부터 센 다음 구간 번호
        val leg = legs[nextIndex] ?: return
        val ok = if (fromAgent) {
            val before = agentExpected
            agentExpected = before.drop(1) // 에이전트가 줄일 남은 구간을 미리 맞춰 둔다 (새 코스로 오해하지 않게)
            agent.continueCourse().also { if (!it) agentExpected = before }
        } else {
            request(leg)
        }
        legSentAt = System.currentTimeMillis()
        retryFailed = if (ok) null else ({ advance(manual = true) })
        _progress.value = if (ok) {
            p.copy(leg = p.leg + 1, upcoming = leg.names(), nextLeg = legs.getOrNull(nextIndex + 1)?.firstName(), failed = false)
        } else {
            p.copy(failed = true)
        }
    }

    private fun finish() {
        _progress.update { it?.copy(finished = true, upcoming = emptyList(), nextLeg = null, failed = false) }
        watchJob?.cancel()
        clearJob = scope.launch {
            delay(FINISHED_SHOWN_MS)
            lock.withLock { if (_progress.value?.finished == true) reset() }
        }
    }

    private fun reset() {
        watchJob?.cancel()
        watchJob = null
        clearJob?.cancel()
        retryFailed = null
        legs = emptyList()
        agentExpected = agent.pendingLegs.value
        _progress.value = null
    }

    private suspend fun request(leg: CourseLeg): Boolean =
        runCatching { navi.requestRouteWithWaypoints(leg.destination.toRouteStop(), leg.waypoints.take(2).map { it.toRouteStop() }) }
            .onFailure { Log.w(TAG, "구간 요청 실패", it) }
            .getOrDefault(false)

    /** 코스가 이어지는 동안만 내비 이벤트를 듣는다 (끝나면 연결을 놓는다). */
    private suspend fun watchNavi() {
        var naviWaypoints: List<String> = emptyList()
        navi.events().collect { e ->
            if (BuildConfig.DEBUG) Log.d(TAG, "navi $e")
            lock.withLock {
                val p = _progress.value ?: return@withLock
                when (e) {
                    is NaviEvent.DestinationArrived -> {
                        // 방금 보낸 구간에 대한 늦은 도착 알림(이전 경로)은 무시한다
                        if (System.currentTimeMillis() - legSentAt >= ARRIVAL_GUARD_MS) advance(manual = false)
                    }
                    is NaviEvent.RouteCancelled -> {
                        // 내비에서 "안내 종료"를 눌렀다 → 카드를 닫고 우리 앱으로 돌아온다.
                        // 새 경로를 보낼 때 내비가 앞 경로를 취소하는 알림은 무시한다
                        if (!p.finished && System.currentTimeMillis() - legSentAt >= CANCEL_GUARD_MS) {
                            reset()
                            returnToApp()
                        }
                    }
                    is NaviEvent.WaypointArrived ->
                        if (p.upcoming.isNotEmpty()) _progress.value = p.copy(upcoming = p.upcoming.drop(1))
                    // 에이전트 코스 첫 구간처럼 들를 곳을 모르면 내비가 알려 준 경유지·목적지로 채운다
                    is NaviEvent.Waypoints -> naviWaypoints = e.pois.map { it.name }
                    is NaviEvent.Destination ->
                        if (p.upcoming.isEmpty() && legs.getOrNull(p.leg - 1) == null && e.poi != null) {
                            _progress.value = p.copy(upcoming = naviWaypoints + e.poi.name)
                        }
                    else -> Unit
                }
            }
        }
    }

    private companion object {
        const val TAG = "EodiCourseGuide"

        /** 구간을 보낸 직후 이 시간 안에 오는 도착 알림은 앞 경로의 것으로 본다. */
        const val ARRIVAL_GUARD_MS = 15_000L

        /** 경로를 보낸 직후 이 시간 안에 오는 취소 알림은 앞 경로를 바꿔 끼운 것으로 본다. */
        const val CANCEL_GUARD_MS = 5_000L

        /** 우리가 안내를 끈 뒤 내비가 제 화면을 띄우기를 기다리는 시간 (그 뒤에 우리 앱을 다시 앞으로). */
        const val NAVI_SETTLE_MS = 700L

        /** "코스 안내를 마쳤어요" 카드를 보여 주는 시간. */
        const val FINISHED_SHOWN_MS = 20_000L
    }
}

private fun CourseLeg.names(): List<String> = waypoints.take(2).map { it.name } + destination.name

private fun CourseLeg.firstName(): String = waypoints.firstOrNull()?.name ?: destination.name

private fun StopFact.toRouteStop() = RouteStop(name, lat, lon, address)
