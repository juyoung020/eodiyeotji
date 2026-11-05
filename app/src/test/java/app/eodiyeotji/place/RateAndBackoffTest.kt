package app.eodiyeotji.place

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun `네트워크 오류 백오프 — 1분부터 두 배, 최대 6시간`() {
        assertEquals(60_000L, PlaceBackoff.networkRetryMs(1))
        assertEquals(120_000L, PlaceBackoff.networkRetryMs(2))
        assertEquals(480_000L, PlaceBackoff.networkRetryMs(4))
        assertEquals(6 * 60 * 60_000L, PlaceBackoff.networkRetryMs(30))
    }
}
