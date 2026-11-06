package app.eodiyeotji.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.platform.NaviEvent
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RouteStop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/**
 * 폰 길 안내: 설치된 지도 앱으로 넘긴다 (지도 앱을 고르는 설정은 나중에, 지금은 아래 순서로 처음 찾은 앱).
 *
 * | 앱 | 목적지만 | 경유지 포함 | 장소 검색 |
 * |---|---|---|---|
 * | 네이버 지도 `nmap://` | ✓ | ✓ (v1·v2…) | ✓ |
 * | 카카오맵 `kakaomap://` | ✓ | ✗ (경유지 안내는 다음 앱으로) | ✓ |
 * | 티맵 `tmap://` | ✓ | ✗ | ✗ |
 * | 구글 지도 (`https://www.google.com/maps/dir/?api=1`) | ✓ | ✓ | ✓ — 앱이 없으면 브라우저로 열린다 (마지막 수단) |
 *
 * 지도 앱은 도착을 알려 주지 않는다 → [events]는 비어 있고, 코스 안내는 사용자가 "다음 구간"으로 넘긴다.
 * 화면에서 누른 직후 부르므로(앱이 앞에 있을 때) 다른 앱 화면을 띄울 수 있다.
 */
@Singleton
class PhoneNavigator @Inject constructor(
    @ApplicationContext private val context: Context,
) : Navigator {

    override fun eventsAvailable(): Boolean = false

    override fun events(refresh: Flow<Unit>): Flow<NaviEvent> = emptyFlow()

    override suspend fun requestRoute(name: String, lat: Double, lon: Double, address: String): Boolean =
        requestRouteWithWaypoints(RouteStop(name, lat, lon, address), emptyList())

    override suspend fun requestRouteWithWaypoints(destination: RouteStop, waypoints: List<RouteStop>): Boolean =
        launchFirst("길 안내 ${destination.name} (경유 ${waypoints.size})") {
            val wps = waypoints.take(MAX_WAYPOINTS)
            buildList {
                add(App.NAVER to naverRoute(destination, wps))
                if (wps.isEmpty()) {
                    add(App.KAKAO to kakaoRoute(destination))
                    add(App.TMAP to tmapRoute(destination))
                }
                add(App.GOOGLE to googleRoute(destination, wps))
                add(App.WEB to googleRoute(destination, wps))
            }
        }

    override suspend fun openPlaceSearch(keyword: String): Boolean =
        launchFirst("장소 검색") {
            val q = Uri.encode(keyword)
            listOf(
                App.NAVER to "nmap://search?query=$q&appname=${appName()}",
                App.KAKAO to "kakaomap://search?q=$q",
                App.GOOGLE to "https://www.google.com/maps/search/?api=1&query=$q",
                App.WEB to "https://www.google.com/maps/search/?api=1&query=$q",
            )
        }

    override suspend fun openPoiDetail(poiId: String, poiProvider: String): Boolean = false

    override suspend fun cancelRoute(): Boolean = false

    // ---- 안쪽 ----

    /** 지도 앱 (설치 확인용 패키지). [WEB]은 패키지를 정하지 않고 브라우저 등 아무 앱이나. */
    private enum class App(val pkg: String?) {
        NAVER("com.nhn.android.nmap"),
        KAKAO("net.daum.android.map"),
        TMAP("com.skt.tmap.ku"),
        GOOGLE("com.google.android.apps.maps"),
        WEB(null),
    }

    /** 후보 중 설치된 첫 앱으로 연다. 반환: 무언가를 열었으면 true. 패키지 조회가 있어 IO에서. */
    private suspend fun launchFirst(what: String, candidates: () -> List<Pair<App, String>>): Boolean = withContext(Dispatchers.IO) {
        for ((app, url) in candidates()) {
            if (app.pkg != null && !installed(app.pkg)) continue
            // 장소 좌표는 개인 기록이라 디버그 빌드에서만 남긴다
            if (BuildConfig.DEBUG) Log.d(TAG, "$what: ${app.name} 시도 $url")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 지도 앱은 링크로 여는 화면(BROWSABLE)으로 연다. 마지막 수단(웹)은 범주를 붙이지 않아 웹 주소를 여는 앱이면 무엇이든
            if (app.pkg != null) intent.addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.pkg)
            val ok = try {
                withContext(Dispatchers.Main) { context.startActivity(intent) }
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                Log.w(TAG, "$what: ${app.name} 열기 거부", e)
                false
            }
            if (ok) {
                Log.i(TAG, "$what → ${app.name}")
                return@withContext true
            }
        }
        Log.w(TAG, "$what: 열 수 있는 지도 앱이 없음")
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
    }
}
