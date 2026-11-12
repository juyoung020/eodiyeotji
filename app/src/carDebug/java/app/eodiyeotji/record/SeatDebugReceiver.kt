package app.eodiyeotji.record

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [디버그 빌드 전용] adb로 좌석 자세 값(앞뒤 · 등받이 · 높이 · 기울기 · 안전벨트)이 앱까지 오는지 확인한다.
 * 결과는 logcat 태그 EodiSeatDebug.
 *
 * ```
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.record.SeatDebugReceiver
 * ```
 * 값은 Virtual Console의 숨은 좌석 위치 항목으로 바꾼다:
 * `curl -X PUT "http://127.0.0.1:8088/vehicle/seat?kind=position&control=slide&area=driver&value=37"`
 */
class SeatDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                val seat = Vehicle(context.applicationContext).also { it.initialize() }.getSeat()
                val areas = ask<List<SeatArea>> { done ->
                    seat.checkSeatOccupancyCapabilities({ _, list -> done(list) }, { e -> Log.w(TAG, "좌석 목록 실패", e); done(null) })
                }.orEmpty()
                Log.i(TAG, "좌석 ${areas.size}곳")
                ask<String> { done ->
                    seat.checkSeatForeAftPositionCapabilities({ cfg, map -> done("$cfg $map") }, { e -> done("실패 $e") })
                }.let { Log.i(TAG, "앞뒤 위치 지원: $it") }
                for (area in areas) {
                    val name = area.values.joinToString()
                    val foreAft = ask<Any> { d -> seat.getCurrentSeatForeAftPosition(area, { d(it ?: "null") }, { e -> d("실패 ${e.message}") }) }
                    val backrest = ask<Any> { d -> seat.getCurrentSeatBackrestAnglePosition(area, { d(it ?: "null") }, { e -> d("실패 ${e.message}") }) }
                    val height = ask<Any> { d -> seat.getCurrentSeatHeightPosition(area, { d(it ?: "null") }, { e -> d("실패 ${e.message}") }) }
                    val tilt = ask<Any> { d -> seat.getCurrentSeatTiltPosition(area, { d(it ?: "null") }, { e -> d("실패 ${e.message}") }) }
                    val belt = ask<Any> { d -> seat.getCurrentSeatBeltBuckledState(area, { d(it ?: "null") }, { e -> d("실패 ${e.message}") }) }
                    Log.i(TAG, "$name: 앞뒤=$foreAft 등받이=$backrest 높이=$height 기울기=$tilt 벨트=$belt")
                }
            } catch (e: Exception) {
                Log.w(TAG, "좌석 확인 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** 콜백 하나를 기다린다. 3초 안에 안 오면 null. */
    private suspend fun <T> ask(call: (done: (T?) -> Unit) -> Unit): T? = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { c -> call { v -> if (c.isActive) c.resume(v) } }
    }

    private companion object {
        const val TAG = "EodiSeatDebug"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
