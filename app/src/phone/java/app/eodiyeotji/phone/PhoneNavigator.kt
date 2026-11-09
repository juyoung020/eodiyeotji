package app.eodiyeotji.phone

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.core.rust.GeoPoint
import app.eodiyeotji.core.rust.NavProgress
import app.eodiyeotji.core.rust.NavRoute
import app.eodiyeotji.core.rust.NavSession
import app.eodiyeotji.core.rust.navDirectionsUrl
import app.eodiyeotji.place.PlatformHttp
import app.eodiyeotji.platform.NaviEvent
import app.eodiyeotji.platform.NaviPoi
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RouteStop
import app.eodiyeotji.platform.SpeechOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** 안내 화면 상태. */
@Immutable
data class NavUi(
    /** 안내 중인 곳 이름 (마지막 목적지) */
    val title: String,
    val route: NavRoute? = null,
    val progress: NavProgress? = null,
    /** 지금 위치 (경로에 붙이기 전) */
    val me: GeoPoint? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val rerouting: Boolean = false,
    /** 방금 도착한 곳 (다음 안내를 기다리는 중) */
    val arrivedAt: String? = null,
    /** 다음에 안내할 곳 이름 (버튼을 누르면 시작) */
    val next: String? = null,
    /** 지금 목적지 다음의 코스 길 (지도에 흐리게, 아직 안내하지 않는 길) */
    val upcoming: List<GeoPoint> = emptyList(),
    /** 남은 코스 장소 (맨 앞 = 지금 목적지) */
    val stops: List<GeoPoint> = emptyList(),
)

/**
 * 폰 길 안내: 다른 지도 앱으로 넘기지 않고 앱 안에서 직접 안내한다.
 * - 경로: 카카오모빌리티 길찾기 (출발 = 지금 위치, 경유지 포함). 키는 카카오 로컬 API와 같은 REST 키.
 * - 진행 · 다음 안내 · 음성 시점 · 경로 이탈은 Rust `nav.rs`([NavSession])가 위치마다 계산한다.
 * - 음성은 폰 TTS([SpeechOutput]), 화면은 [ui]를 보는 안내 화면(phone/ui/PhoneNavScreen.kt).
 * - 코스는 한 번에 한 곳씩 안내한다: 지금 목적지에 도착해야 다음 곳이 열리고, 사람이 "다음 곳 안내"를 눌러야 시작한다
 *   (식당 · 카페에서 얼마나 머물지 모르므로). 도착은 [events]로 알린다.
 */
@Singleton
class PhoneNavigator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val location: PhoneLocationSource,
    private val speech: SpeechOutput,
) : Navigator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ui = MutableStateFlow<NavUi?>(null)
    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 16)
    private var job: Job? = null

    /** 안내 화면 상태 (안내 중이 아니면 null). */
    val ui: StateFlow<NavUi?> = _ui.asStateFlow()

    override fun eventsAvailable(): Boolean = true

    override fun events(refresh: Flow<Unit>): Flow<NaviEvent> = _events.asSharedFlow()

    override suspend fun requestRoute(name: String, lat: Double, lon: Double, address: String): Boolean =
        requestRouteWithWaypoints(RouteStop(name, lat, lon, address), emptyList())

    override suspend fun requestRouteWithWaypoints(destination: RouteStop, waypoints: List<RouteStop>): Boolean {
        val stops = waypoints + destination
        val u = _ui.value
        // 도착해서 쉬는 중에 코스의 다음 구간이 오면 바로 떠나지 않고 다음 곳으로 걸어 둔다
        if (u?.arrivedAt != null && u.next == null) {
            course = stops
            _ui.value = u.copy(next = stops.first().name)
        } else {
            start(stops)
        }
        return true
    }

    /** "근처 ○○ 찾아 안내": 지금 위치 가까운 순으로 카카오 장소 검색 → 첫 곳으로 안내. */
    override suspend fun openPlaceSearch(keyword: String): Boolean = withContext(Dispatchers.IO) {
        val here = currentFix() ?: return@withContext false
        val url = "https://dapi.kakao.com/v2/local/search/keyword.json?query=${URLEncoder.encode(keyword, "UTF-8")}" +
            "&x=${here.lon}&y=${here.lat}&sort=distance&size=1"
        val doc = runCatching { JSONObject(PlatformHttp.get(url, kakaoHeaders())) }.getOrNull() ?: return@withContext false
        val p = doc.optJSONArray("documents")?.optJSONObject(0) ?: return@withContext false
        start(listOf(RouteStop(p.optString("place_name"), p.optString("y").toDouble(), p.optString("x").toDouble(), p.optString("road_address_name"))))
        true
    }

    override suspend fun openPoiDetail(poiId: String, poiProvider: String): Boolean = false

    override suspend fun cancelRoute(): Boolean {
        stop(cancelled = true)
        return true
    }

    /** 안내 끝내기 (화면의 종료 버튼). */
    fun stop(cancelled: Boolean = true) {
        job?.cancel()
        job = null
        course = emptyList()
        _ui.value = null
        speech.stop()
        if (cancelled) _events.tryEmit(NaviEvent.RouteCancelled)
    }

    /** 도착한 뒤 "다음 곳 안내": 걸어 둔 다음 곳부터 다시 안내한다. */
    fun next() {
        if (course.isNotEmpty()) start(course, index = legIndex)
    }

    /** 남은 코스 (맨 앞 = 지금 목적지) */
    private var course: List<RouteStop> = emptyList()
    private var legIndex = 0

    private fun start(stops: List<RouteStop>, index: Int = 0) {
        if (stops.isEmpty()) return
        job?.cancel()
        course = stops
        legIndex = index
        val target = stops.first()
        _ui.value = NavUi(title = target.name, stops = stops.map { GeoPoint(it.lat, it.lon) })
        if (stops.size > 1) scope.launch { upcomingPath(stops)?.let { p -> _ui.value = _ui.value?.copy(upcoming = p) } }
        job = scope.launch {
            var session = route(listOf(target), first = true) ?: return@launch
            _events.tryEmit(NaviEvent.RouteStarted(target.name))
            _events.tryEmit(NaviEvent.Destination(target.poi()))
            location.updates().first { fix ->
                val p = session.update(fix.lat, fix.lon)
                _ui.value = _ui.value?.copy(progress = p, me = GeoPoint(fix.lat, fix.lon), rerouting = false)
                p.say?.let { speech.speak(it) }
                if (p.arrived) {
                    arrive(target)
                    return@first true
                }
                if (p.reroute) {
                    _ui.value = _ui.value?.copy(rerouting = true)
                    speech.speak("경로를 다시 찾습니다")
                    route(listOf(target), first = false, from = fix)?.let { session = it }
                }
                false
            }
        }
    }

    /** 지금 목적지 → 남은 곳들 길 (보여 주기만): 실패하면 장소끼리 곧은 선. */
    private suspend fun upcomingPath(stops: List<RouteStop>): List<GeoPoint>? = withContext(Dispatchers.IO) {
        val pts = stops.map { GeoPoint(it.lat, it.lon) }
        val url = navDirectionsUrl(pts.first(), pts.drop(1))
        runCatching { NavSession(PlatformHttp.get(url, kakaoHeaders())).route().points }.getOrNull() ?: pts
    }

    /** 지금 목적지 도착: 남은 곳이 있으면 다음 곳을 걸어 두고 기다린다. */
    private fun arrive(target: RouteStop) {
        val rest = course.drop(1)
        course = rest
        if (rest.isNotEmpty()) {
            _events.tryEmit(NaviEvent.WaypointArrived(legIndex, target.name, target.address))
            legIndex++
            _ui.value = _ui.value?.copy(arrivedAt = target.name, next = rest.first().name)
            speech.speak("${target.name}에 도착했어요. 다음은 ${rest.first().name}이에요. 출발할 때 다음 곳 안내를 눌러 주세요.")
        } else {
            legIndex = 0
            _ui.value = _ui.value?.copy(arrivedAt = target.name, next = null)
            speech.speak("${target.name}에 도착했어요.")
            _events.tryEmit(NaviEvent.DestinationArrived(target.name, target.address))
        }
    }

    /** 지금 위치에서 [stops]까지 경로를 받아 세션을 만든다. 실패하면 화면에 알리고 null. */
    private suspend fun route(stops: List<RouteStop>, first: Boolean, from: LocationFix? = null): NavSession? {
        val here = from ?: currentFix()
        if (here == null) {
            _ui.value = _ui.value?.copy(loading = false, error = "지금 위치를 알 수 없어요")
            return null
        }
        val url = navDirectionsUrl(GeoPoint(here.lat, here.lon), stops.map { GeoPoint(it.lat, it.lon) })
        val r0 = withContext(Dispatchers.IO) { runCatching { NavSession(PlatformHttp.get(url, kakaoHeaders())) } }
        val s = r0.getOrNull()
        if (s == null) {
            Log.w(TAG, "경로 찾기 실패", r0.exceptionOrNull())
            // 카카오가 준 사유가 있으면 그대로 ("경로의 총 거리가 허용된 최대 거리를 초과했습니다." 등)
            val why = r0.exceptionOrNull()?.message?.let { Regex("\"msg\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) ?: it.takeIf { m -> m.length < 60 } }
            if (first) _ui.value = _ui.value?.copy(loading = false, error = why?.let { "경로를 찾지 못했어요 · $it" } ?: "경로를 찾지 못했어요")
            return null
        }
        val r = s.route()
        _ui.value = _ui.value?.copy(route = r, me = GeoPoint(here.lat, here.lon), loading = false, error = null, progress = s.update(here.lat, here.lon))
        if (first) speech.speak("안내를 시작합니다. ${(r.durationS / 60).toInt().coerceAtLeast(1)}분 걸려요.")
        return s
    }

    /** 지금 위치: 새 위치를 잠깐 기다리고, 안 오면 기기가 마지막으로 안 위치(2분 안의 것)를 쓴다. */
    private suspend fun currentFix(): LocationFix? =
        withTimeoutOrNull(FIX_TIMEOUT_MS) { location.updates().first() } ?: lastKnown()

    @android.annotation.SuppressLint("MissingPermission")
    private fun lastKnown(): LocationFix? {
        val lm = context.getSystemService(android.location.LocationManager::class.java) ?: return null
        val l = runCatching { lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time } }.getOrNull() ?: return null
        if (System.currentTimeMillis() - l.time > LAST_KNOWN_MAX_AGE_MS) return null
        return LocationFix(l.latitude, l.longitude, l.time, l.accuracy)
    }

    private fun kakaoHeaders() = mapOf("Authorization" to "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")

    private fun RouteStop.poi() = NaviPoi(name, address, lat, lon)

    private companion object {
        const val TAG = "EodiPhoneNavi"
        const val FIX_TIMEOUT_MS = 4_000L
        const val LAST_KNOWN_MAX_AGE_MS = 2 * 60_000L
    }
}
