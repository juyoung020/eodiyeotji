package app.eodiyeotji.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import android.content.Context
import android.util.Log
import app.eodiyeotji.platform.CabinSource
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * 탑승 인원: Pleos Vehicle SDK의 좌석 탑승 여부(`Seat.getCurrentSeatOccupancy`, 권한 CAR_SEATS).
 * 차마다 센서가 있는 좌석이 다르므로 `checkSeatOccupancyCapabilities`로 지원 좌석을 먼저 받고, 그 좌석만 묻는다.
 * 몇 명인지만 센다 (누구인지는 모른다). 지원하지 않는 차 · 실패면 null → 기록에 인원을 남기지 않는다.
 * 운전석(ROW_1_LEFT)을 기준으로 값의 방향을 맞춘다 (아래 설명).
 */
@Singleton
class PleosCabin @Inject constructor(@ApplicationContext private val context: Context) : CabinSource {
    private val lock = Mutex()
    private var vehicle: Vehicle? = null
    private var areas: List<SeatArea>? = null

    override suspend fun occupants(): Int? = withContext(Dispatchers.IO) {
        lock.withLock {
            val v = vehicle ?: runCatching { Vehicle(context.applicationContext).also { it.initialize() } }
                .onFailure { Log.w(TAG, "Vehicle SDK 시작 실패", it) }.getOrNull()?.also { vehicle = it } ?: return@withLock null
            val seat = runCatching { v.getSeat() }.getOrNull() ?: return@withLock null
            val supported = areas ?: withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { c ->
                    seat.checkSeatOccupancyCapabilities(
                        onComplete = { _, list -> if (c.isActive) c.resume(list) },
                        onFailed = { e -> Log.w(TAG, "좌석 탑승 지원 확인 실패", e); if (c.isActive) c.resume(emptyList()) },
                    )
                }
            }?.also { areas = it } ?: return@withLock null
            if (supported.isEmpty()) return@withLock null
            val read = supported.mapNotNull { area ->
                val occupied = withTimeoutOrNull(TIMEOUT_MS) {
                    suspendCancellableCoroutine<Boolean?> { c ->
                        seat.getCurrentSeatOccupancy(
                            area,
                            onComplete = { b -> if (c.isActive) c.resume(b) },
                            onFailed = { if (c.isActive) c.resume(null) },
                        )
                    }
                }
                if (app.eodiyeotji.BuildConfig.DEBUG) Log.d(TAG, "좌석 ${area.values.joinToString()} → $occupied")
                occupied?.let { area to it }
            }
            if (read.isEmpty()) return@withLock null
            // 운전석으로 맞춘다: 달리는 중이면 운전석에는 반드시 사람이 있다.
            // Pleos 에뮬레이터는 앉음/빈자리가 뒤집혀 나온다(Virtual Console에서 조수석만 앉히면 조수석만 false) →
            // 운전석이 비어 있다고 나오면 전체를 뒤집어 읽는다. 실제 차에서 바르게 나오면 그대로 쓴다.
            val driver = read.firstOrNull { (area, _) -> SeatValue.ROW_1_LEFT in area.values }?.second
            val inverted = driver == false
            read.count { (_, occupied) -> occupied != inverted }.takeIf { it > 0 }
        }
    }

    private companion object {
        const val TAG = "EodiCabin"
        const val TIMEOUT_MS = 3_000L
    }
}
