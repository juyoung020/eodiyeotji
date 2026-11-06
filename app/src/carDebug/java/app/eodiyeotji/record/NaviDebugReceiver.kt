package app.eodiyeotji.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.eodiyeotji.pleos.PleosNavi
import app.eodiyeotji.platform.RouteStop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [디버그 빌드 전용] adb로 내비 연동을 확인한다. 결과는 logcat 태그 EodiNaviDebug / EodiPleosNavi.
 *
 * ```
 * R=app.eodiyeotji/.record.NaviDebugReceiver
 * adb shell am broadcast --user 10 -n $R --es cmd watch --ei sec 60        # 연결·현재 목적지/경유지·이벤트를 60초 동안 기록
 * adb shell am broadcast --user 10 -n $R --es cmd route --es name 광장시장 --es lat 37.5701 --es lon 126.9996
 *   (경유지 하나 추가: --es wname 경복궁 --es wlat 37.5796 --es wlon 126.9770)
 * adb shell am broadcast --user 10 -n $R --es cmd search --es keyword 광장시장   # 내비 장소 검색 화면 열기
 * adb shell am broadcast --user 10 -n $R --es cmd poi --es poiId <id> --es provider <제공자>
 * adb shell am broadcast --user 10 -n $R --es cmd cancel
 * ```
 * watch는 기록 서비스가 프로세스를 살려 두는 동안만 끝까지 돈다 (브로드캐스트는 바로 끝낸다).
 */
class NaviDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: "watch"
        // 디버그 도구라서 Hilt 주입 없이 직접 만든다 (PleosNavi는 상태가 없다)
        val navi = PleosNavi(context.applicationContext)
        if (cmd == "watch") {
            val sec = intent.getIntExtra("sec", 30).coerceIn(1, 600)
            scope.launch {
                Log.i(TAG, "cmd=watch ${sec}초 isAvailable=${navi.isAvailable()}")
                val done = withTimeoutOrNull(sec * 1_000L) {
                    navi.events().collect { Log.i(TAG, "watch: $it") }
                }
                Log.i(TAG, if (done == null) "watch 끝 (${sec}초)" else "watch: 흐름이 끝남 (내비 연결 실패)")
            }
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                Log.i(TAG, "cmd=$cmd isAvailable=${navi.isAvailable()}")
                when (cmd) {
                    "route" -> {
                        val name = intent.getStringExtra("name") ?: "테스트 목적지"
                        val lat = intent.getStringExtra("lat")?.toDoubleOrNull() ?: 0.0
                        val lon = intent.getStringExtra("lon")?.toDoubleOrNull() ?: 0.0
                        val wname = intent.getStringExtra("wname")
                        if (wname == null) {
                            Log.i(TAG, "requestRoute($name, $lat, $lon) → ${navi.requestRoute(name, lat, lon)}")
                        } else {
                            val wp = RouteStop(
                                wname,
                                intent.getStringExtra("wlat")?.toDoubleOrNull() ?: 0.0,
                                intent.getStringExtra("wlon")?.toDoubleOrNull() ?: 0.0,
                            )
                            val ok = navi.requestRouteWithWaypoints(RouteStop(name, lat, lon), listOf(wp))
                            Log.i(TAG, "requestRouteWithWaypoints($name via $wp) → $ok")
                        }
                    }
                    "search" -> {
                        val keyword = intent.getStringExtra("keyword").orEmpty()
                        Log.i(TAG, "openPlaceSearch($keyword) → ${navi.openPlaceSearch(keyword)}")
                    }
                    "poi" -> {
                        val id = intent.getStringExtra("poiId").orEmpty()
                        val provider = intent.getStringExtra("provider").orEmpty()
                        Log.i(TAG, "openPoiDetail($id, $provider) → ${navi.openPoiDetail(id, provider)}")
                    }
                    "cancel" -> Log.i(TAG, "cancelRoute → ${navi.cancelRoute()}")
                    else -> Log.w(TAG, "모르는 cmd=$cmd")
                }
            } catch (e: Exception) {
                Log.e(TAG, "cmd=$cmd 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "EodiNaviDebug"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
