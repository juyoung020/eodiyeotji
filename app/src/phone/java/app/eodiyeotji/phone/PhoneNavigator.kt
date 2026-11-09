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
)

/**
 * 폰 길 안내: 다른 지도 앱으로 넘기지 않고 앱 안에서 직접 안내한다.
 * - 경로: 카카오모빌리티 길찾기 (출발 = 지금 위치, 경유지 포함). 키는 카카오 로컬 API와 같은 REST 키.
 * - 진행 · 다음 안내 · 음성 시점 · 경로 이탈은 Rust `nav.rs`([NavSession])가 위치마다 계산한다.
 * - 음성은 폰 TTS([SpeechOutput]), 화면은 [ui]를 보는 안내 화면(phone/ui/PhoneNavScreen.kt).
 * - 도착 · 경유지 통과를 [events]로 알린다 → 코스 안내가 다음 구간으로 알아서 넘어간다.
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
        start(waypoints + destination)
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
        _ui.value = null
        speech.stop()
        if (cancelled) _events.tryEmit(NaviEvent.RouteCancelled)
    }

    private fun start(stops: List<RouteStop>) {
        if (stops.isEmpty()) return
        job?.cancel()
        val dest = stops.last()
        _ui.value = NavUi(title = dest.name)
        job = scope.launch {
            var remaining = stops
            var session = route(remaining, first = true) ?: return@launch
            _events.tryEmit(NaviEvent.RouteStarted(remaining.first().name))
            _events.tryEmit(NaviEvent.Destination(dest.poi()))
            _events.tryEmit(NaviEvent.Waypoints(remaining.dropLast(1).map { it.poi() }))
            location.updates().collect { fix ->
                val p = session.update(fix.lat, fix.lon)
                _ui.value = _ui.value?.copy(progress = p, me = GeoPoint(fix.lat, fix.lon), rerouting = false)
                p.say?.let { speech.speak(it) }
                p.passedWaypoint?.let { i ->
                    val w = remaining.getOrNull(0)
                    _events.tryEmit(NaviEvent.WaypointArrived(i.toInt(), w?.name.orEmpty(), w?.address.orEmpty()))
                    if (remaining.size > 1) remaining = remaining.drop(1)
                }
                if (p.arrived) {
                    _events.tryEmit(NaviEvent.DestinationArrived(dest.name, dest.address))
                    return@collect
                }
                if (p.reroute) {
                    _ui.value = _ui.value?.copy(rerouting = true)
                    speech.speak("경로를 다시 찾습니다")
                    route(remaining, first = false, from = fix)?.let { session = it }
                }
            }
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
