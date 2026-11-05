package app.eodiyeotji.place

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateAndBackoffTest {
    @Test
    fun `요청 간격 — 동시에 불러도 순서대로 간격을 둔다`() = runBlocking {
        val gate = RateGate(100)
        val t0 = System.nanoTime()
        (1..4).map { async { gate.acquire(); (System.nanoTime() - t0) / 1_000_000 } }.awaitAll()
        val elapsed = (System.nanoTime() - t0) / 1_000_000
        assertTrue("4번째는 300 ms 뒤 (실제 $elapsed ms)", elapsed >= 290)
    }

    @Test
    fun `토큰 통 — 처음 몇 개는 바로, 그 뒤로는 초당 정해진 만큼`() = runBlocking {
        val bucket = TokenBucket(capacity = 5, perSecond = 20.0)
        val t0 = System.nanoTime()
        repeat(5) { bucket.acquire() }
        val burstMs = (System.nanoTime() - t0) / 1_000_000
        repeat(4) { bucket.acquire() } // 4개 더 = 20/s로 약 200 ms
        val totalMs = (System.nanoTime() - t0) / 1_000_000
        assertTrue("처음 5개는 바로 ($burstMs ms)", burstMs < 50)
        assertTrue("다음 4개는 약 200 ms ($totalMs ms)", totalMs in 180..400)
    }

    @Test
    fun `카카오 한도 초과(400 code -10)도 요청 과다로 본다`() {
        val kakao = """{"errorType":"BadRequest","message":"API limit has been exceeded.","code":-10,"msg":"API limit has been exceeded."}"""
        assertTrue(HttpStatusException(400, kakao).isRateLimited)
        assertTrue(HttpStatusException(429, "").isRateLimited)
        assertFalse(HttpStatusException(400, """{"code":-2,"msg":"bad param"}""").isRateLimited)
        assertTrue(HttpStatusException(401, "").isAuthError)
    }

    @Test
    fun `네트워크 오류 백오프 — 10초부터 두 배, 최대 1시간`() {
        assertEquals(10_000L, PlaceBackoff.networkRetryMs(1))
        assertEquals(20_000L, PlaceBackoff.networkRetryMs(2))
        assertEquals(80_000L, PlaceBackoff.networkRetryMs(4))
        assertEquals(60 * 60_000L, PlaceBackoff.networkRetryMs(30))
    }
}
