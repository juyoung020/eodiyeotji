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
}

/**
 * 토큰 통: 쉬고 있으면 [capacity]개까지 한꺼번에 보내고(지점 하나의 요청을 동시에), 그 뒤로는 1초에 [perSecond]개씩.
 * 카카오는 짧은 시간에 요청이 몰리면 "API limit has been exceeded"(400, code -10)로 막는다 → 밀린 지점이 많을 때만 늦춘다.
 */
class TokenBucket(private val capacity: Int, private val perSecond: Double) {
    private val mutex = Mutex()
    private var tokens = capacity.toDouble()
    private var lastMs = nowMs()

    suspend fun acquire() = mutex.withLock {
        while (true) {
            val now = nowMs()
            tokens = minOf(capacity.toDouble(), tokens + (now - lastMs) * perSecond / 1000.0)
            lastMs = now
            if (tokens >= 1.0) {
                tokens -= 1.0
                return@withLock
            }
            delay(((1.0 - tokens) * 1000.0 / perSecond).toLong().coerceAtLeast(1))
        }
    }
}

private fun nowMs() = System.nanoTime() / 1_000_000
