package app.eodiyeotji.pleos

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import android.util.Log
import app.eodiyeotji.platform.MoodLight
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 무드등: Pleos Vehicle SDK `Light` (권한 CAR_LIGHTS). 지원 구역을 `checkAmbientLightBrightnessColorCapabilities`로 받고,
 * 처음 물들일 때 구역마다 지금 색을 읽어 두었다가 [restore]에서 그대로 되돌린다.
 */
@Singleton
class PleosMoodLight @Inject constructor(private val sdk: PleosVehicle) : MoodLight {
    private val lock = Mutex()
    /** 물들이기 전 원래 색 (구역 → ARGB). 비어 있으면 지금 물들어 있지 않다. */
    private val saved = LinkedHashMap<SeatArea, Int>()

    override suspend fun tint(argb: Int) = withContext(Dispatchers.IO) {
        lock.withLock {
            val light = sdk.get()?.let { runCatching { it.getLight() }.getOrNull() } ?: return@withLock
            val areas = await<List<SeatArea>> { done ->
                light.checkAmbientLightBrightnessColorCapabilities(onComplete = { _, a -> done(a) }, onFailed = { done(null) })
            }.orEmpty()
            for (area in areas) {
                if (area !in saved) {
                    await<Int> { done -> light.getAmbientLightBrightnessColor(area, onComplete = { done(it) }, onFailed = { done(null) }) }
                        ?.let { saved[area] = it }
                }
                await<Int> { done -> light.setAmbientLightBrightnessColor(area, argb or OPAQUE, onComplete = { _, v -> done(v) }, onFailed = { e -> Log.d(TAG, "무드등 바꾸기 실패: ${e.message}"); done(null) }) }
            }
        }
    }

    override suspend fun restore() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (saved.isEmpty()) return@withLock
            val light = sdk.get()?.let { runCatching { it.getLight() }.getOrNull() } ?: return@withLock
            for ((area, color) in saved) {
                await<Int> { done -> light.setAmbientLightBrightnessColor(area, color, onComplete = { _, v -> done(v) }, onFailed = { done(null) }) }
            }
            saved.clear()
        }
    }

    private suspend fun <T> await(call: (done: (T?) -> Unit) -> Unit): T? = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { c ->
            runCatching { call { v -> if (c.isActive) c.resume(v) } }.onFailure { if (c.isActive) c.resume(null) }
        }
    }

    private companion object {
        const val TAG = "EodiMood"
        const val OPAQUE = 0xFF000000.toInt()
    }
}
