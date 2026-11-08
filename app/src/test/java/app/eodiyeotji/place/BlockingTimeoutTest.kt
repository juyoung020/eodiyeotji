package app.eodiyeotji.place

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 막는 HTTP(오프라인 DNS 대기 등)를 짧은 타임아웃 안에 놓아 주는지. */
class BlockingTimeoutTest {
    private fun slowCall(ms: Long): String {
        Thread.sleep(ms)
        return "late"
    }

    @Test
    fun givesUpOnTimeEvenWhenTheCallBlocks() = runBlocking {
        val t0 = System.nanoTime()
        val r = blockingWithTimeout(300) { slowCall(2_000) }
        val waited = (System.nanoTime() - t0) / 1_000_000
        assertNull(r)
        assertTrue("기다린 시간 ${waited}ms", waited < 1_000)
        // 예전 방식은 막힌 호출이 끝날 때까지 돌아오지 못했다 (비교용)
        val t1 = System.nanoTime()
        withTimeoutOrNull(300) { withContext(Dispatchers.IO) { slowCall(2_000) } }
        val old = (System.nanoTime() - t1) / 1_000_000
        println("blockingWithTimeout ${waited}ms, 예전 withTimeoutOrNull+withContext ${old}ms")
        assertTrue(old >= 1_900)
    }

    @Test
    fun returnsValueAndRethrows() = runBlocking {
        assertEquals("ok", blockingWithTimeout(1_000) { "ok" })
        val e = runCatching { blockingWithTimeout(1_000) { throw java.io.IOException("x") } }.exceptionOrNull()
        assertTrue(e is java.io.IOException)
    }
}
