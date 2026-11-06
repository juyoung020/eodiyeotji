package app.eodiyeotji.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.platform.NaviEvent
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RouteStop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 지도 앱이 하나도 없을 때 화면에 묻는 것 ("구글 지도 웹으로 열까요?"). [PhoneNavigator.answerNoMapApp]으로 답한다.
 * @param title 무엇을 하려던 참인지 ("경포해변 안내", "카페 검색")
 */
@Immutable
data class NoMapAppRequest(val id: Int, val title: String, val webUrl: String)

/**
 * 폰 길 안내: 설치된 지도 앱으로 넘긴다. 설정에서 고른 앱([MapApp], [PhoneSettings])을 먼저, 없으면 아래 순서로 처음 찾은 앱.
 *
 * | 앱 | 목적지만 | 경유지 포함 | 장소 검색 |
 * |---|---|---|---|
 * | 네이버 지도 `nmap://` | ✓ | ✓ (v1·v2…) | ✓ |
 * | 카카오맵 `kakaomap://` | ✓ | ✗ (경유지 안내는 다음 앱으로) | ✓ |
 * | 티맵 `tmap://` | ✓ | ✗ | ✗ |
 * | 구글 지도 앱 | ✓ | ✓ | ✓ |
 *
 * 지도 앱이 하나도 없으면 바로 웹을 열지 않고 화면에 묻는다 ([noMapApp]): "구글 지도 웹으로 열기 · 지도 앱 설치 · 닫기".
 * 화면이 보고 있지 않으면(묻을 곳이 없으면) 실패로 돌려준다.
 *
 * 지도 앱은 도착을 알려 주지 않는다 → [events]는 비어 있고, 코스 안내는 사용자가 "다음 구간"으로 넘긴다.
 * 화면에서 누른 직후 부르므로(앱이 앞에 있을 때) 다른 앱 화면을 띄울 수 있다.
 */
@Singleton
class PhoneNavigator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: PhoneSettings,
) : Navigator {

    private val _noMapApp = MutableStateFlow<NoMapAppRequest?>(null)

    /** 지도 앱이 없어 화면의 답을 기다리는 중인 요청 (없으면 null). */
    val noMapApp: StateFlow<NoMapAppRequest?> = _noMapApp.asStateFlow()

    private var pendingAnswer: CompletableDeferred<Boolean>? = null
    private val nextId = AtomicInteger(1)
    private val watchers = AtomicInteger(0)

    /** 화면이 [noMapApp]을 보기 시작/그만둘 때 (보는 화면이 없으면 묻지 않고 바로 실패). */
    fun attachUi() = watchers.incrementAndGet()
    fun detachUi() = watchers.decrementAndGet()

    /** 지도 앱이 없다는 물음에 대한 답. [openWeb]이면 구글 지도 웹으로 연다. */
    fun answerNoMapApp(openWeb: Boolean) {
        val req = _noMapApp.value ?: return
        _noMapApp.value = null
        val ok = openWeb && open(Intent(Intent.ACTION_VIEW, Uri.parse(req.webUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "웹")
        pendingAnswer?.complete(ok)
        pendingAnswer = null
    }

    /** 지도 앱 설치 화면 (플레이 스토어, 없으면 웹 스토어). */
    fun openStore(app: MapApp) {
        val pkg = App.entries.firstOrNull { it.name == app.name }?.pkg ?: App.NAVER.pkg ?: return
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!open(market, "스토어")) {
            open(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "웹 스토어")
        }
    }

    /** 설치된 지도 앱 (설정 화면 표시용). 패키지 조회라 백그라운드 스레드에서. */
    suspend fun installedApps(): Set<MapApp> = withContext(Dispatchers.IO) {
        App.entries.filter { it.pkg != null && installed(it.pkg) }.mapNotNull { a -> MapApp.entries.firstOrNull { it.name == a.name } }.toSet()
    }

    override fun eventsAvailable(): Boolean = false

    override fun events(refresh: Flow<Unit>): Flow<NaviEvent> = emptyFlow()

    override suspend fun requestRoute(name: String, lat: Double, lon: Double, address: String): Boolean =
        requestRouteWithWaypoints(RouteStop(name, lat, lon, address), emptyList())

    override suspend fun requestRouteWithWaypoints(destination: RouteStop, waypoints: List<RouteStop>): Boolean {
        val wps = waypoints.take(MAX_WAYPOINTS)
        val candidates = buildList {
            add(App.NAVER to naverRoute(destination, wps))
            if (wps.isEmpty()) {
                add(App.KAKAO to kakaoRoute(destination))
                add(App.TMAP to tmapRoute(destination))
            }
            add(App.GOOGLE to googleRoute(destination, wps))
        }
        return launchFirst("${destination.name} 안내 (경유 ${wps.size})", destination.name, candidates, googleRoute(destination, wps))
    }

    override suspend fun openPlaceSearch(keyword: String): Boolean {
        val q = Uri.encode(keyword)
        val web = "https://www.google.com/maps/search/?api=1&query=$q"
        val candidates = listOf(
            App.NAVER to "nmap://search?query=$q&appname=${appName()}",
            App.KAKAO to "kakaomap://search?q=$q",
            App.GOOGLE to web,
        )
        return launchFirst("장소 검색", keyword, candidates, web)
    }

    override suspend fun openPoiDetail(poiId: String, poiProvider: String): Boolean = false

    override suspend fun cancelRoute(): Boolean = false

    // ---- 안쪽 ----

    /** 지도 앱 (설치 확인용 패키지). */
    private enum class App(val pkg: String?) {
        NAVER("com.nhn.android.nmap"),
        KAKAO("net.daum.android.map"),
        TMAP("com.skt.tmap.ku"),
        GOOGLE("com.google.android.apps.maps"),
    }

    /**
     * 고른 앱을 맨 앞으로 옮긴 뒤 설치된 첫 앱으로 연다. 하나도 못 열면 화면에 웹으로 열지 묻고 답을 기다린다.
     * 반환: 무언가를 열었으면 true. 패키지 조회가 있어 IO에서.
     */
    private suspend fun launchFirst(what: String, title: String, candidates: List<Pair<App, String>>, webUrl: String): Boolean {
        val opened = withContext(Dispatchers.IO) {
            val preferred = settings.mapApp()
            val ordered = candidates.sortedBy { (app, _) -> if (app.name == preferred.name) 0 else 1 }
            for ((app, url) in ordered) {
                val pkg = app.pkg ?: continue
                if (!installed(pkg)) continue
                // 장소 좌표는 개인 기록이라 디버그 빌드에서만 남긴다
                if (BuildConfig.DEBUG) Log.d(TAG, "$what: ${app.name} 시도 $url")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setPackage(pkg)
                if (withContext(Dispatchers.Main) { open(intent, app.name) }) {
                    Log.i(TAG, "$what → ${app.name}")
                    return@withContext true
                }
            }
            false
        }
        if (opened) return true
        Log.w(TAG, "$what: 열 수 있는 지도 앱이 없음")
        return askUi(title, webUrl)
    }

    /** 지도 앱이 없을 때 화면에 묻고 답을 기다린다 (보는 화면이 없으면 바로 false). */
    private suspend fun askUi(title: String, webUrl: String): Boolean {
        if (watchers.get() <= 0) return false
        val answer = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            pendingAnswer?.complete(false)
            pendingAnswer = answer
            _noMapApp.value = NoMapAppRequest(nextId.getAndIncrement(), title, webUrl)
        }
        return withTimeoutOrNull(ASK_TIMEOUT_MS) { answer.await() } ?: run {
            withContext(Dispatchers.Main) {
                if (pendingAnswer === answer) {
                    pendingAnswer = null
                    _noMapApp.value = null
                }
            }
            false
        }
    }

    private fun open(intent: Intent, label: String): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "$label 열기 거부", e)
        false
    }

    private fun installed(pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    /** 네이버 지도는 앱 식별자(appname)를 꼭 달아야 한다. */
    private fun appName(): String = Uri.encode(context.packageName)

    /** 출발지를 비우면 지도 앱이 현재 위치에서 출발한다. */
    private fun naverRoute(d: RouteStop, wps: List<RouteStop>): String = buildString {
        append("nmap://route/car?dlat=${d.lat.c()}&dlng=${d.lon.c()}&dname=${Uri.encode(d.name)}")
        wps.forEachIndexed { i, w ->
            val n = i + 1
            append("&v${n}lat=${w.lat.c()}&v${n}lng=${w.lon.c()}&v${n}name=${Uri.encode(w.name)}")
        }
        append("&appname=${appName()}")
    }

    private fun kakaoRoute(d: RouteStop): String = "kakaomap://route?ep=${d.lat.c()},${d.lon.c()}&by=CAR"

    /** 티맵: X = 경도, Y = 위도. */
    private fun tmapRoute(d: RouteStop): String = "tmap://route?rGoName=${Uri.encode(d.name)}&rGoX=${d.lon.c()}&rGoY=${d.lat.c()}"

    private fun googleRoute(d: RouteStop, wps: List<RouteStop>): String = buildString {
        append("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=${d.lat.c()},${d.lon.c()}")
        if (wps.isNotEmpty()) append("&waypoints=" + Uri.encode(wps.joinToString("|") { "${it.lat.c()},${it.lon.c()}" }))
    }

    private fun Double.c(): String = String.format(Locale.US, "%.6f", this)

    private companion object {
        const val TAG = "EodiPhoneNavi"

        /** 구간마다 경유지 수 (Rust `course::split_legs`와 같은 2개. 구글 지도 모바일 웹은 3개까지). */
        const val MAX_WAYPOINTS = 2

        /** 지도 앱이 없다는 물음에 답을 기다리는 시간. 넘으면 실패로 본다. */
        const val ASK_TIMEOUT_MS = 120_000L
    }
}
