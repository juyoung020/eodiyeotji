package app.eodiyeotji.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import app.eodiyeotji.core.rust.GeoPoint
import app.eodiyeotji.core.rust.NavSession
import app.eodiyeotji.core.rust.navDirectionsUrl
import app.eodiyeotji.core.rust.walkGuide
import app.eodiyeotji.place.ApiAccess
import app.eodiyeotji.place.PlatformHttp
import app.eodiyeotji.pleos.PleosNavi
import app.eodiyeotji.ui.nav.NavUi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 차 판의 내비 연결. 진짜 차(자동차용 안드로이드)에서는 Pleos 내비([PleosNavi]) 그대로이고,
 * 차가 아닌 기기(태블릿에 차 판을 깔았을 때)에서는 차 내비가 없으므로 시연용 내비([TabletNavigator])를 쓴다.
 */
@Singleton
class CarNavigator @Inject constructor(
    @ApplicationContext context: Context,
    pleos: PleosNavi,
    tablet: TabletNavigator,
) : Navigator by (if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) pleos else tablet)

/**
 * 태블릿용 시연 내비: "안내"를 누르면 인포 화면 안에 길 안내 화면(ui/nav/NavScreen.kt)을 띄우고, 차가 그 길을 따라 달리는 것처럼 보인다.
 * - 경로는 진짜다: 카카오모빌리티 길찾기로 시연 출발점([START])에서 목적지까지 받는다 (경유지 포함).
 * - 달리는 것은 가짜다: 받은 길을 1초에 [STEP_M]씩 따라가며 Rust `nav.rs`([NavSession])에 위치를 넣는다
 *   → 다음 안내 · 남은 거리 · 음성 안내 시점은 폰 판 길 안내와 같은 계산이다.
 * - 도착 · 취소는 [events]로 알려 코스가 다음 구간으로 넘어간다 (차 내비와 같은 신호).
 * - 경로를 못 받으면 (통신 · 키 없음) 기기의 지도 앱으로 넘긴다 (티맵 → 네이버 지도 → 카카오맵 → 좌표 열기).
 * 화면은 [ui]를 인포 화면(ui/shell/FakeCarShell.kt)이 본다.
 */
@Singleton
class TabletNavigator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicle: TabletVehicle,
    private val speech: SpeechOutput,
) : Navigator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ui = MutableStateFlow<NavUi?>(null)
    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 16)
    private var job: Job? = null

    /** 시연 내비 화면 상태 (안내 중이 아니면 null). */
    val ui: StateFlow<NavUi?> = _ui.asStateFlow()

    override fun eventsAvailable(): Boolean = true

    override fun events(refresh: Flow<Unit>): Flow<NaviEvent> = _events.asSharedFlow()

    override suspend fun requestRoute(name: String, lat: Double, lon: Double, address: String): Boolean =
        requestRouteWithWaypoints(RouteStop(name, lat, lon, address), emptyList())

    override suspend fun requestRouteWithWaypoints(destination: RouteStop, waypoints: List<RouteStop>): Boolean {
        val stops = waypoints.take(MAX_WAYPOINTS) + destination
        val session = withContext(Dispatchers.IO) {
            val url = ApiAccess.kakaoNavi(navDirectionsUrl(START, stops.map { GeoPoint(it.lat, it.lon) }))
            runCatching { NavSession(PlatformHttp.get(url, ApiAccess.kakaoHeaders)) }
                .onFailure { Log.w(TAG, "시연 내비 경로 받기 실패 → 지도 앱으로", it) }
                .getOrNull()
        } ?: return openMapApp(destination, waypoints)
        drive(session, stops)
        return true
    }

    override suspend fun openPlaceSearch(keyword: String): Boolean = withContext(Dispatchers.Main) {
        open("geo:0,0?q=${Uri.encode(keyword)}")
    }

    override suspend fun openPoiDetail(poiId: String, poiProvider: String): Boolean = false

    override suspend fun cancelRoute(): Boolean {
        stop()
        return true
    }

    /** 안내 끝내기 (화면의 종료 버튼 · 취소). */
    fun stop() {
        val was = _ui.value
        job?.cancel()
        job = null
        _ui.value = null
        vehicle.navDriving.value = false
        vehicle.speedKmh.value = 0f
        speech.stop()
        if (was != null && was.arrivedAt == null) _events.tryEmit(NaviEvent.RouteCancelled)
    }

    /** 받은 길을 따라 달리는 흉내: 1초마다 [STEP_M]씩 나아가며 안내 상태를 고친다. */
    private fun drive(session: NavSession, stops: List<RouteStop>) {
        job?.cancel()
        val target = stops.last()
        val route = session.route()
        _ui.value = NavUi(
            title = target.name,
            route = route,
            me = START,
            loading = false,
            progress = session.update(START.lat, START.lon),
            stops = stops.map { GeoPoint(it.lat, it.lon) },
        )
        vehicle.navDriving.value = true
        job = scope.launch {
            _events.tryEmit(NaviEvent.RouteStarted(stops.first().name))
            _events.tryEmit(NaviEvent.Destination(NaviPoi(target.name, target.address, target.lat, target.lon)))
            if (stops.size > 1) _events.tryEmit(NaviEvent.Waypoints(stops.dropLast(1).map { NaviPoi(it.name, it.address, it.lat, it.lon) }))
            speech.speak("안내를 시작합니다. ${(route.durationS / 60).toInt().coerceAtLeast(1)}분 걸려요.")
            val pts = route.points
            var passed = 0
            var i = 0
            // 지금 구간(i → i+1)에서 나아간 거리
            var into = 0.0
            while (i < pts.size - 1) {
                delay(TICK_MS)
                // 이번 1초에 나아갈 거리를 구간들에 나눠 쓴다 (구간 길이는 Rust가 잰다)
                var left = STEP_M
                var here = pts[i]
                while (i < pts.size - 1) {
                    val (a, b) = pts[i] to pts[i + 1]
                    val len = walkGuide(a.lat, a.lon, -1.0, b.lat, b.lon).distanceM
                    if (into + left < len) {
                        into += left
                        val f = into / len
                        here = GeoPoint(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
                        break
                    }
                    left -= len - into
                    into = 0.0
                    i++
                    here = pts[i]
                }
                val p = session.update(here.lat, here.lon)
                vehicle.speedKmh.value = SPEED_KMH
                _ui.value = _ui.value?.copy(progress = p, me = here)
                p.say?.let { speech.speak(it) }
                p.passedWaypoint?.let { w ->
                    val at = stops.getOrNull(w.toInt()) ?: return@let
                    if (w.toInt() >= passed && w.toInt() < stops.size - 1) {
                        passed = w.toInt() + 1
                        _events.tryEmit(NaviEvent.WaypointArrived(w.toInt(), at.name, at.address))
                    }
                }
                if (p.arrived) break
            }
            vehicle.navDriving.value = false
            vehicle.speedKmh.value = 0f
            _ui.value = _ui.value?.copy(arrivedAt = target.name, next = null)
            speech.speak("${target.name}에 도착했어요.")
            _events.tryEmit(NaviEvent.DestinationArrived(target.name, target.address))
        }
    }

    /** 경로를 못 받았을 때: 기기에 깔린 지도 앱에 목적지를 넘긴다. 경유지는 네이버 지도만 받는다 (나머지는 목적지만). */
    private suspend fun openMapApp(destination: RouteStop, waypoints: List<RouteStop>): Boolean = withContext(Dispatchers.Main) {
        val d = destination
        val name = Uri.encode(d.name.ifBlank { d.address })
        val naverVia = waypoints.take(MAX_WAYPOINTS).mapIndexed { i, w ->
            "&v${i + 1}lat=${w.lat}&v${i + 1}lng=${w.lon}&v${i + 1}name=${Uri.encode(w.name)}"
        }.joinToString("")
        val candidates = listOf(
            "tmap://route?goalname=$name&goalx=${d.lon}&goaly=${d.lat}",
            "nmap://navigation?dlat=${d.lat}&dlng=${d.lon}&dname=$name$naverVia&appname=${context.packageName}",
            "kakaomap://route?ep=${d.lat},${d.lon}&by=CAR",
            "geo:${d.lat},${d.lon}?q=${d.lat},${d.lon}($name)",
        )
        candidates.any(::open)
    }

    /** 그 주소를 받는 앱이 있으면 열고 true. 없으면 false (다음 후보로). */
    private fun open(uri: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "지도 앱을 열 수 없음", e)
        false
    }

    private companion object {
        const val TAG = "EodiNavi"
        const val MAX_WAYPOINTS = 2

        /** 시연 출발점: 서울시청 (태블릿의 시연 주행도 여기서 출발한다) */
        val START = GeoPoint(37.5663, 126.9779)
        const val TICK_MS = 1_000L

        /** 1초에 나아가는 거리 (시속 80km쯤) */
        const val STEP_M = 22.0
        const val SPEED_KMH = 80f
    }
}
