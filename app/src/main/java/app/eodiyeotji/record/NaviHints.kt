package app.eodiyeotji.record

import android.util.Log
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.core.NaviHint
import app.eodiyeotji.platform.NaviEvent
import app.eodiyeotji.platform.NaviPoi
import app.eodiyeotji.platform.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 한 번의 주행 동안 내비 이벤트를 [NaviHint]로 바꾼다. 값 옮겨 담기만 하고, 어느 머문 곳·도착 지점에
 * 붙일지(시각·거리 비교)는 Rust(`places::add_navi_hint`)가 정한다.
 *
 * - 도착 이벤트에는 좌표가 없다 → 이번 주행에서 조회한 목적지·경유지 중 같은 이름의 좌표를 쓴다.
 *   못 찾거나 좌표를 모르면(에뮬레이터 내비는 경유지 좌표가 0,0) 차의 마지막 위치(도착한 그 자리)를 쓴다.
 * - 경로 시작은 힌트로 보내지 않는다 (아직 도착 전). 목적지·경유지 좌표를 받아 두는 계기로만 쓴다.
 * - 주행 끝: 아직 설정돼 있는 목적지·경유지를 끝난 시각으로 보낸다 (도착 이벤트 없이 내비를 끈 채 세운 경우 대비).
 * - 같은 장소(종류·이름·좌표)는 한 주행에 한 번만 보낸다.
 * 한 코루틴에서만 부른다 (잠금 없음).
 */
internal class NaviHintTracker {
    private var destination: NaviPoi? = null
    private var waypoints: List<NaviPoi> = emptyList()

    /** 이번 주행에서 본 모든 목적지·경유지 (도착 이벤트의 이름 → 좌표). 목적지가 지워진 뒤 도착 이벤트가 와도 찾는다. */
    private val seen = LinkedHashMap<String, NaviPoi>()
    private val sent = HashSet<String>()

    fun onEvent(e: NaviEvent, nowMs: Long, here: LocationFix?): List<NaviHint> = when (e) {
        is NaviEvent.Destination -> {
            destination = e.poi
            e.poi?.let { seen[it.name] = it }
            emptyList()
        }
        is NaviEvent.Waypoints -> {
            waypoints = e.pois
            e.pois.forEach { seen[it.name] = it }
            emptyList()
        }
        NaviEvent.RouteCancelled -> {
            destination = null
            waypoints = emptyList()
            emptyList()
        }
        is NaviEvent.DestinationArrived ->
            listOfNotNull(arrived(e.name, e.address, seen[e.name.trim()] ?: destination, isWaypoint = false, nowMs, here))
        is NaviEvent.WaypointArrived ->
            listOfNotNull(arrived(e.name, e.address, seen[e.name.trim()] ?: waypoints.getOrNull(e.index), isWaypoint = true, nowMs, here))
        NaviEvent.Connected, is NaviEvent.RouteStarted -> emptyList()
    }

    /** 좌표를 모르는 경유지(에뮬레이터 내비)는 보낼 수 없다 → 빠진다. 도착 이벤트 때는 차 위치로 대신 보낸다. */
    fun onDriveEnd(endMs: Long): List<NaviHint> =
        (waypoints.map { it to true } + listOfNotNull(destination?.let { it to false }))
            .mapNotNull { (p, wp) ->
                val lat = p.lat ?: return@mapNotNull null
                val lon = p.lon ?: return@mapNotNull null
                once(NaviHint(p.name, lat, lon, endMs, isWaypoint = wp, address = p.address))
            }

    private fun arrived(name: String, address: String, poi: NaviPoi?, isWaypoint: Boolean, nowMs: Long, here: LocationFix?): NaviHint? {
        val n = name.trim().ifEmpty { poi?.name.orEmpty() }
        if (n.isEmpty()) return null
        val known = poi?.lat != null && poi.lon != null
        val lat = (if (known) poi?.lat else here?.lat) ?: return null
        val lon = (if (known) poi?.lon else here?.lon) ?: return null
        return once(NaviHint(n, lat, lon, nowMs, isWaypoint, address.trim().ifEmpty { poi?.address.orEmpty() }))
    }

    private fun once(h: NaviHint): NaviHint? {
        // 좌표는 소수 4자리(약 10m)로 묶어 같은 장소로 본다
        val key = "${h.isWaypoint}|${h.name}|${Math.round(h.lat * 1e4)}|${Math.round(h.lon * 1e4)}"
        return if (sent.add(key)) h else null
    }
}

/**
 * 주행 하나 동안 내비를 듣는 작업. [RecordingService]가 주행 시작에 만들고 주행 끝에 [finish]한다.
 * 내비가 없거나 연결에 실패하면 조용히 아무것도 하지 않는다.
 *
 * 비용: 내비 이벤트 중 목적지·경유지·도착·경로 시작/취소만 받는다(제한속도·TBT 등은 어댑터가 버린다).
 * 주행 중 타이머·폴링 없음 → 이벤트가 없으면 CPU를 쓰지 않는다.
 */
internal class NaviSession(
    navi: Navigator,
    scope: CoroutineScope,
    private val lastFix: () -> LocationFix?,
    private val send: suspend (NaviHint) -> Unit,
) {
    private val tracker = NaviHintTracker()
    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val replies = MutableStateFlow(0)
    private val connected = MutableStateFlow(false)

    private val job: Job = scope.launch(Dispatchers.Default) {
        navi.events(refresh).collect { e ->
            when (e) {
                NaviEvent.Connected -> connected.value = true
                is NaviEvent.Destination, is NaviEvent.Waypoints -> replies.update { it + 1 }
                else -> Unit
            }
            for (h in tracker.onEvent(e, System.currentTimeMillis(), lastFix())) send(h)
        }
        Log.i(TAG, "내비 연결 없음 → 이번 주행은 내비 이름 없이 기록")
    }

    /** 주행 끝: 목적지·경유지를 한 번 더 조회해(최대 [QUERY_TIMEOUT_MS]) 남은 것을 힌트로 보내고 내비 연결을 끊는다. */
    suspend fun finish(endMs: Long) {
        if (job.isActive && connected.value) {
            val before = replies.value
            refresh.tryEmit(Unit)
            withTimeoutOrNull(QUERY_TIMEOUT_MS) { replies.first { it >= before + 2 } }
        }
        job.cancelAndJoin()
        for (h in tracker.onDriveEnd(endMs)) send(h)
    }

    private companion object {
        const val TAG = "EodiRecord"
        const val QUERY_TIMEOUT_MS = 1_500L
    }
}
