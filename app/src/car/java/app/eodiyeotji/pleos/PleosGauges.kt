package app.eodiyeotji.pleos

import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import android.util.Log
import app.eodiyeotji.platform.GaugeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 차 계기: Pleos Vehicle SDK.
 * - 주행거리계 `Odometer.getCurrentDistanceDriven` (km, 권한 CAR_DRIVE) → 운행기록부의 주행 전 · 후 계기판 거리
 * - 남은 주행 가능 거리 `Odometer.getRangeRemainingDistance` → 코스 안내 전에 충전 · 주유가 필요한지.
 *   값은 미터로 온다 (AAOS `RANGE_REMAINING`과 같은 단위, 에뮬레이터에서 확인) → km로 바꾼다.
 * - 와이퍼 `Wiper.getCurrentWiperState` · `getCurrentWiperSwitch` (권한 CAR_WINDOWS) → 비 온 날.
 *   앞 유리 구역을 `checkWiperStateCapabilities`로 받는다. 움직이는 중(ON)이거나 스위치가 간헐 · 연속 · 안개(MIST)면 비로 본다
 *   (AUTO는 비가 안 와도 켜 두므로 넣지 않는다).
 */
@Singleton
class PleosGauges @Inject constructor(private val sdk: PleosVehicle) : GaugeSource {
    @Volatile private var wiperArea: WindowArea? = null

    override suspend fun odometerKm(): Double? = withContext(Dispatchers.IO) {
        val v = sdk.get() ?: return@withContext null
        await<Float> { done -> v.getOdometer().getCurrentDistanceDriven(onComplete = { done(it) }, onFailed = { e -> fail("주행거리계", e); done(null) }) }
            ?.toDouble()
    }

    override suspend fun rangeKm(): Double? = withContext(Dispatchers.IO) {
        val v = sdk.get() ?: return@withContext null
        await<Float> { done -> v.getOdometer().getRangeRemainingDistance(onComplete = { done(it) }, onFailed = { e -> fail("남은 거리", e); done(null) }) }
            ?.let { it / 1000.0 }
    }

    override suspend fun wiping(): Boolean? = withContext(Dispatchers.IO) {
        val wiper = sdk.get()?.let { runCatching { it.getWiper() }.getOrNull() } ?: return@withContext null
        val area = wiperArea ?: await<WindowArea> { done ->
            wiper.checkWiperStateCapabilities(onComplete = { _, m -> done(m.keys.firstOrNull()) }, onFailed = { e -> fail("와이퍼 지원", e); done(null) })
        }?.also { wiperArea = it } ?: return@withContext null
        val state = await<WiperState> { done -> wiper.getCurrentWiperState(area, onComplete = { done(it) }, onFailed = { done(null) }) }
        if (state == WiperState.ON) return@withContext true
        val sw = await<WiperSwitch> { done -> wiper.getCurrentWiperSwitch(area, onComplete = { done(it) }, onFailed = { done(null) }) }
        when {
            sw != null -> sw.name.startsWith("CONTINUOUS") || sw.name.startsWith("INTERMITTENT") || sw == WiperSwitch.MIST
            state != null -> false
            else -> null
        }
    }

    private fun fail(what: String, e: Exception) = Log.d(TAG, "$what 읽기 실패: ${e.message}")

    private suspend fun <T> await(call: (done: (T?) -> Unit) -> Unit): T? = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { c ->
            runCatching { call { v -> if (c.isActive) c.resume(v) } }.onFailure { if (c.isActive) c.resume(null) }
        }
    }

    private companion object {
        const val TAG = "EodiGauges"
    }
}
