package app.eodiyeotji.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.eodiyeotji.core.rust.GeoPoint
import app.eodiyeotji.core.rust.NavTarget
import app.eodiyeotji.core.rust.ProactiveEvent

/**
 * [디버그 빌드 전용] 에이전트가 먼저 말 걸기를 바로 일으킨다 (시연 · 확인용, docs/소통/ 시연 문서 참고).
 * 오늘 횟수 기록을 먼저 지우고(`--ez reset false`로 끔) 차 상태 대신 준 값을 쓴다 (에뮬레이터는 잔량 · 남은 거리를 바꿀 수 없다).
 *
 * ```
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.agent.ProactiveDebugReceiver --es event departure
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.agent.ProactiveDebugReceiver --es event lowbattery \
 *     --es range_km 40 --es dest_name 경포해변 --es dest_lat 37.8055 --es dest_lon 128.9081 --ez ev false
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.agent.ProactiveDebugReceiver --es event arrival --es lat 37.8055 --es lon 128.9081
 * ```
 * 값: `range_km`(남은 거리) · `pct`(잔량 %) · `ev`(전기차, 기본 차 값) · `dest_name/dest_lat/dest_lon`(내비 목적지) ·
 * `lat/lon`(지금 위치, 기본 마지막 위치) · `waypoints`(이미 있는 경유지 수).
 */
class ProactiveDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = when (intent.getStringExtra("event")?.lowercase()) {
            "departure", "depart", "start" -> ProactiveEvent.DEPARTURE
            "lowbattery", "lowenergy", "energy", "fuel" -> ProactiveEvent.LOW_ENERGY
            "arrival", "arrive", "park" -> ProactiveEvent.ARRIVAL
            else -> {
                Log.w(TAG, "event는 departure | lowbattery | arrival")
                return
            }
        }
        fun num(k: String) = intent.getStringExtra(k)?.toDoubleOrNull() ?: intent.extras?.get(k)?.toString()?.toDoubleOrNull()
        val lat = num("lat")
        val lon = num("lon")
        val dLat = num("dest_lat")
        val dLon = num("dest_lon")
        val o = ProactiveCenter.DebugOverride(
            here = if (lat != null && lon != null) GeoPoint(lat, lon) else null,
            destination = if (dLat != null && dLon != null) NavTarget(intent.getStringExtra("dest_name") ?: "목적지", dLat, dLon) else null,
            rangeKm = num("range_km"),
            energyPct = num("pct"),
            ev = if (intent.hasExtra("ev")) intent.getBooleanExtra("ev", true) else null,
            waypointCount = num("waypoints")?.toInt(),
        )
        Log.i(TAG, "디버그 먼저 말 걸기: $event $o")
        context.proactiveCenter().fireDebug(event, reset = intent.getBooleanExtra("reset", true), o)
    }

    private companion object {
        const val TAG = "EodiProactive"
    }
}
