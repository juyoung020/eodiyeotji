package app.eodiyeotji.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import app.eodiyeotji.pleos.PleosNavi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/**
 * 차 판의 내비 연결. 진짜 차(자동차용 안드로이드)에서는 Pleos 내비([PleosNavi]) 그대로이고,
 * 차가 아닌 기기(태블릿에 차 판을 깔았을 때)에서는 차 내비가 없으므로 그 기기의 지도 앱으로 넘긴다([TabletNavigator]).
 */
@Singleton
class CarNavigator @Inject constructor(
    @ApplicationContext context: Context,
    pleos: PleosNavi,
    tablet: TabletNavigator,
) : Navigator by (if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) pleos else tablet)

/**
 * 태블릿용 길 안내: 기기에 깔린 지도 앱에 목적지를 넘겨 그 앱이 안내하게 한다.
 * 티맵 → 네이버 지도 → 카카오맵 순으로 열어 보고, 다 없으면 좌표를 여는 일반 요청(geo:)으로 넘긴다.
 * 넘긴 뒤의 일(도착 · 경유지)은 알 수 없어 이벤트는 내보내지 않는다. 경유지는 네이버 지도만 받는다 (나머지는 목적지만).
 */
@Singleton
class TabletNavigator @Inject constructor(@ApplicationContext private val context: Context) : Navigator {
    override fun eventsAvailable(): Boolean = false

    override fun events(refresh: Flow<Unit>): Flow<NaviEvent> = emptyFlow()

    override suspend fun requestRoute(name: String, lat: Double, lon: Double, address: String): Boolean =
        requestRouteWithWaypoints(RouteStop(name, lat, lon, address), emptyList())

    override suspend fun requestRouteWithWaypoints(destination: RouteStop, waypoints: List<RouteStop>): Boolean = withContext(Dispatchers.Main) {
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

    override suspend fun openPlaceSearch(keyword: String): Boolean = withContext(Dispatchers.Main) {
        open("geo:0,0?q=${Uri.encode(keyword)}")
    }

    override suspend fun openPoiDetail(poiId: String, poiProvider: String): Boolean = false

    override suspend fun cancelRoute(): Boolean = false

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
    }
}
