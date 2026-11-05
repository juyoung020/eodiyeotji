package app.eodiyeotji.place

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 요청 간격 지키기: [acquire]가 앞 요청 시작으로부터 [minIntervalMs]가 지날 때까지 기다린다.
 * 여러 코루틴이 동시에 불러도 순서대로 간격을 둔다 (Nominatim 1초 1회 정책 등).
 */
class RateGate(private val minIntervalMs: Long) {
    private val mutex = Mutex()
    private var nextAtMs = 0L

    suspend fun acquire() = mutex.withLock {
        val now = nowMs()
        val at = maxOf(now, nextAtMs)
        if (at > now) delay(at - now)
        nextAtMs = at + minIntervalMs
    }

    private fun nowMs() = System.nanoTime() / 1_000_000
}
