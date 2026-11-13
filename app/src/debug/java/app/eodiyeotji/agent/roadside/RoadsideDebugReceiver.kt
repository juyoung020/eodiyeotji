package app.eodiyeotji.agent.roadside

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.eodiyeotji.core.rust.GeoPoint

/**
 * [디버그 빌드 전용] 길 위 도구 에이전트 시연 (docs/소통/2026-11-13_길위_도구_에이전트.md).
 * 에뮬레이터는 내비 경로 · 진행 방향을 만들 수 없어서, 주행 상황을 바꿔 끼우고 말을 넣는다.
 *
 * ```
 * # 경부고속도로 수지 부근에서 대전 쪽으로 100km/h (목적지 대전역) + "나 똥마려"
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.agent.roadside.RoadsideDebugReceiver --es scenario gyeongbu --es say "나 똥마려"
 * # 상황은 그대로 두고 말만
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.agent.roadside.RoadsideDebugReceiver --es say "졸려"
 * # 직접 값: 위치 · 방향 · 속도 · 목적지 (목적지 없이 주면 진행 방향 직선으로 찾는다)
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.agent.roadside.RoadsideDebugReceiver --es lat 37.30 --es lon 127.10 --es heading 180 --es speed 100
 * # 실제 값으로 되돌리기
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.agent.roadside.RoadsideDebugReceiver --ez clear true
 * ```
 */
class RoadsideDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        fun num(k: String) = intent.getStringExtra(k)?.toDoubleOrNull() ?: intent.extras?.get(k)?.toString()?.toDoubleOrNull()
        if (intent.getBooleanExtra("clear", false)) {
            RoadsideDebug.sim = null
            Log.i(TAG, "시연 상황 지움 (실제 값)")
        }
        when (intent.getStringExtra("scenario")?.lowercase()) {
            // 경부고속도로 하행 (수지 부근 → 대전역), 2시간째 달리는 중
            "gyeongbu", "경부" -> RoadsideDebug.sim = RoadsideDebug.Sim(
                here = GeoPoint(37.3005, 127.1003),
                headingDeg = 175.0,
                speedKmh = 100.0,
                destination = RoadsideAgent.Dest("대전역", 36.3315, 127.4346),
                drivingMin = 118,
            )
            null -> Unit
            else -> Log.w(TAG, "scenario는 gyeongbu")
        }
        val lat = num("lat")
        val lon = num("lon")
        if (lat != null && lon != null) {
            val dLat = num("dest_lat")
            val dLon = num("dest_lon")
            RoadsideDebug.sim = RoadsideDebug.Sim(
                here = GeoPoint(lat, lon),
                headingDeg = num("heading"),
                speedKmh = num("speed"),
                destination = if (dLat != null && dLon != null) RoadsideAgent.Dest(intent.getStringExtra("dest_name") ?: "목적지", dLat, dLon) else null,
                drivingMin = num("driving_min")?.toInt(),
            )
        }
        Log.i(TAG, "시연 상황: ${RoadsideDebug.sim}")
        intent.getStringExtra("say")?.takeIf { it.isNotBlank() }?.let {
            Log.i(TAG, "말 넣기: $it")
            AgentUtterances.texts.tryEmit(it)
        }
    }

    private companion object {
        const val TAG = "EodiRoadside"
    }
}
