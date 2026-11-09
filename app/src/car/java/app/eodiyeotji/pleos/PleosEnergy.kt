package app.eodiyeotji.pleos

import android.util.Log
import app.eodiyeotji.platform.EnergyLevel
import app.eodiyeotji.platform.EnergySource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 배터리 · 연료 잔량: Pleos Vehicle SDK. 전기차 배터리(`EvBattery.getEvBatteryStateOfCharge`, %)를 먼저 묻고,
 * 없으면 연료(`Fuel.getFuelPercent`, %). 권한 CAR_ENERGY. 둘 다 못 읽으면 null.
 */
@Singleton
class PleosEnergy @Inject constructor(private val sdk: PleosVehicle) : EnergySource {
    override suspend fun level(): EnergyLevel? = withContext(Dispatchers.IO) {
        val v = sdk.get() ?: return@withContext null
        val soc = read { ok, fail -> v.getEvBattery().getEvBatteryStateOfCharge(onComplete = ok, onFailed = fail) }
        if (soc != null) return@withContext EnergyLevel(soc.toDouble(), ev = true)
        read { ok, fail -> v.getFuel().getFuelPercent(onComplete = ok, onFailed = fail) }?.let { EnergyLevel(it.toDouble(), ev = false) }
    }

    private suspend fun read(call: (ok: (Int?) -> Unit, fail: (Exception) -> Unit) -> Unit): Int? = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { c ->
            runCatching {
                call(
                    { x -> if (c.isActive) c.resume(x) },
                    { e ->
                        Log.d("EodiEnergy", "잔량 읽기 실패: ${e.message}")
                        if (c.isActive) c.resume(null)
                    },
                )
            }.onFailure { if (c.isActive) c.resume(null) }
        }
    }
}
