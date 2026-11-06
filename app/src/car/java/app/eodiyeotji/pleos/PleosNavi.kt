package app.eodiyeotji.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.platform.NaviEvent
import app.eodiyeotji.platform.NaviPoi
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RouteStop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos NaviHelper SDK 얇은 어댑터. 차량 내비의 목적지·경유지·도착 이벤트를 [NaviEvent] 흐름으로 바꾼다.
 *
 * 생명주기(공식 문서 순서): NaviHelper(context) → initialize(onConnected, onFailed) → addListener
 * → removeListener → release. release 뒤에는 같은 인스턴스를 다시 쓸 수 없어 [events]를 모을 때마다 새로 만든다.
 *
 * - 조회 API(getDestinationInfo 등)는 값을 바로 돌려주지 않고 리스너 콜백(onDestinationInfo)으로 준다.
 * - 내비가 없거나 연결에 실패하면 예외 없이 흐름을 끝낸다 → 기록은 내비 없이 그대로 돈다.
 * - SDK 호출은 모두 흐름을 모으는 코루틴(Dispatchers.Default 등)에서 한다. 콜백은 채널에 넣기만 한다.
 */
@Singleton
class PleosNavi @Inject constructor(
    @ApplicationContext private val context: Context,
) : Navigator {
    /** 이 차(에뮬레이터)에 Pleos 내비 서비스가 있는지. 패키지 조회라서 백그라운드 스레드에서 부른다. */
    fun isAvailable(): Boolean =
        runCatching { context.packageManager.queryIntentServices(Intent(ACTION_NAVI_SERVICE), 0).isNotEmpty() }
            .onFailure { Log.w(TAG, "내비 서비스 확인 실패", it) }
            .getOrDefault(false)

    override fun eventsAvailable(): Boolean = isAvailable()

    /**
     * 내비 이벤트 흐름. 모으는 동안만 SDK가 연결돼 있고, 취소하면 리스너 해제 → release 한다.
     *
     * 연결 직후와 경로가 바뀔 때(시작·탐색 완료·재탐색·목적지/경유지 변경)마다 목적지·경유지를 스스로 다시 조회해
     * [NaviEvent.Destination] / [NaviEvent.Waypoints]로 내보낸다. [refresh]가 값을 낼 때도 한 번 더 조회한다.
     */
    override fun events(refresh: Flow<Unit>): Flow<NaviEvent> = callbackFlow {
        val helper = NaviHelper(context)
        val connected = AtomicBoolean(false)
        // 조회 요청은 콜백 스레드가 아니라 이 흐름의 코루틴에서 한다 (같은 요청이 몰리면 하나로 합친다)
        val queries = Channel<Unit>(Channel.CONFLATED)

        /** [raw]: SDK가 준 원본 값. 디버그 로그로 실제 내비가 무엇을 주는지 확인할 때 쓴다. */
        fun emit(e: NaviEvent, raw: Any? = null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "event $e${if (raw != null) " ← $raw" else ""}")
            trySend(e)
        }

        fun requery(why: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, "$why → 목적지·경유지 다시 조회")
            queries.trySend(Unit)
        }

        val listener = object : NaviHelperEventListener {
            override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
                emit(NaviEvent.RouteStarted(routeStartInfo.name), routeStartInfo)
                requery("onRouteStarted")
            }

            override fun onRoutingCompleted() = requery("onRoutingCompleted")
            override fun onReRouteEnded() = requery("onReRouteEnded")
            override fun onDestinationChanged() = requery("onDestinationChanged")
            override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = requery("onWaypointChanged")

            override fun onRouteCancelled() {
                emit(NaviEvent.RouteCancelled)
            }

            override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                emit(NaviEvent.Destination(destinationInfo.toPoi()), destinationInfo)
            }

            override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
                emit(NaviEvent.Waypoints(waypointInfoList.mapNotNull { it.toPoi() }), waypointInfoList)
            }

            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                emit(NaviEvent.DestinationArrived(destinationArrivedInfo.name, destinationArrivedInfo.address), destinationArrivedInfo)
            }

            override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
                val i = if (waypointArrivedInfo.index == WaypointIndex.SECOND) 1 else 0
                emit(NaviEvent.WaypointArrived(i, waypointArrivedInfo.name, waypointArrivedInfo.address), waypointArrivedInfo)
            }

            override fun onError(errorCode: NaviErrorCode) {
                Log.w(TAG, "내비 onError $errorCode")
            }
        }

        launch {
            for (q in queries) {
                if (!connected.get()) continue
                runCatching {
                    helper.getDestinationInfo()
                    helper.getWaypointInfo()
                }.onFailure { Log.w(TAG, "목적지·경유지 조회 실패", it) }
            }
        }
        launch { refresh.collect { queries.trySend(Unit) } }

        try {
            helper.initialize(
                onConnected = {
                    if (connected.compareAndSet(false, true)) {
                        runCatching { helper.addListener(listener) }.onFailure { Log.w(TAG, "리스너 등록 실패", it) }
                        emit(NaviEvent.Connected)
                        requery("연결됨")
                    }
                },
                onFailed = { t ->
                    Log.i(TAG, "내비 연결 실패 → 내비 없이 진행: ${t.message}")
                    channel.close()
                },
            )
        } catch (e: Exception) {
            Log.i(TAG, "내비 초기화 실패 → 내비 없이 진행: $e")
            runCatching { helper.release() }
            channel.close()
        }

        awaitClose {
            runCatching { helper.removeListener(listener) }
            runCatching { helper.release() }
        }
    }

    /** 내비에 "그곳으로 안내"를 요청한다. 반환: 요청을 보냈으면 true (경로 탐색 성공 여부는 아니다). */
    override suspend fun requestRoute(name: String, lat: Double, lon: Double, address: String): Boolean =
        oneShot("requestRoute") { it.requestRoute(routeInfo(name, lat, lon, address)) }

    /** 경유지(최대 2개)를 거쳐 목적지로 안내를 요청한다. 3개째부터는 버린다 (NaviHelper 제한). */
    override suspend fun requestRouteWithWaypoints(destination: RouteStop, waypoints: List<RouteStop>): Boolean =
        oneShot("requestRouteWithWaypoints") { helper ->
            val wps = waypoints.take(2).mapIndexed { i, p ->
                RequestWaypointInfo(
                    latitude = p.lat, longitude = p.lon, poiName = p.name,
                    waypointIndex = if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
                    poiId = "", address = p.address, poiSubId = "",
                )
            }
            helper.requestRouteWithWaypoints(
                RouteWithWaypointsRequest(routeInfo(destination.name, destination.lat, destination.lon, destination.address), wps),
            )
        }

    /**
     * 차량 내비의 장소 검색 화면을 [keyword]로 연다 (Pleos SDK에 이름 검색 API는 이것뿐이다).
     * 사용자가 결과를 골라 안내를 시작하면 [events]의 RouteStarted → Destination으로 내비 공식 이름·좌표가 들어온다.
     */
    override suspend fun openPlaceSearch(keyword: String): Boolean =
        oneShot("openPlaceSearch") { it.requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword)) }

    /** 내비의 장소 상세 팝업을 연다 ([poiId]·[poiProvider]는 내비가 준 값 그대로). */
    override suspend fun openPoiDetail(poiId: String, poiProvider: String): Boolean =
        oneShot("openPoiDetail") { it.requestNavigationScreen(NavigationScreenRequest.POIDetailPopup(poiId, poiProvider)) }

    /** 안내 취소 (시연·디버그용). */
    override suspend fun cancelRoute(): Boolean = oneShot("cancelRoute") { it.cancelRoute() }

    /** 연결 → 요청 하나 → release. 내비가 없거나 연결이 [CONNECT_TIMEOUT_MS] 안에 안 되면 false. */
    private suspend fun oneShot(what: String, call: (NaviHelper) -> Unit): Boolean = withContext(Dispatchers.Default) {
        if (!isAvailable()) return@withContext false
        val helper = NaviHelper(context)
        try {
            val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { helper.connect() } ?: false
            if (ok) call(helper) else Log.i(TAG, "$what: 내비 연결 안 됨")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "$what 실패", e)
            false
        } finally {
            runCatching { helper.release() }
        }
    }

    private suspend fun NaviHelper.connect(): Boolean = suspendCancellableCoroutine { cont ->
        try {
            initialize(
                onConnected = { if (cont.isActive) cont.resume(true) },
                onFailed = { if (cont.isActive) cont.resume(false) },
            )
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(false)
        }
    }

    companion object {
        private const val TAG = "EodiPleosNavi"

        /** NaviHelper가 연결하는 내비 서비스 (SDK 매니페스트의 <queries>와 같은 값). */
        private const val ACTION_NAVI_SERVICE = "ai.pleos.playground.intent.action.NAVI_START"
        private const val CONNECT_TIMEOUT_MS = 5_000L

        private fun routeInfo(name: String, lat: Double, lon: Double, address: String) = RouteInfo(
            latitude = lat, longitude = lon, poiName = name, poiId = "", address = address, poiSubId = "",
        )
    }
}

/** 목적지가 없으면 내비가 빈 이름·0,0 좌표를 준다 → null. 0,0 좌표는 "모름"(null)으로 바꾼다. */
private fun DestinationInfo.toPoi(): NaviPoi? = poiOrNull(name, address, latitude, longitude, poiId, poiProvider)

private fun WaypointInfo.toPoi(): NaviPoi? = poiOrNull(name, address, latitude, longitude, poiId, poiProvider)

private fun poiOrNull(name: String?, address: String?, lat: Double, lon: Double, poiId: String?, poiProvider: String?): NaviPoi? {
    val n = name?.trim().orEmpty()
    if (n.isEmpty()) return null
    val known = !(lat == 0.0 && lon == 0.0) && !lat.isNaN() && !lon.isNaN()
    return NaviPoi(
        n, address?.trim().orEmpty(), if (known) lat else null, if (known) lon else null,
        poiId?.trim().orEmpty(), poiProvider?.trim().orEmpty(),
    )
}
