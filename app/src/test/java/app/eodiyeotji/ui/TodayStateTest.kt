package app.eodiyeotji.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayStateTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun oneMinuteBeforeMidnight() {
        val now = ZonedDateTime.of(2026, 11, 13, 23, 59, 0, 0, seoul)
        assertEquals(60_000L + 50, millisUntilNextMidnight(now))
    }

    @Test
    fun exactlyMidnightWaitsFullDay() {
        val now = ZonedDateTime.of(2026, 11, 14, 0, 0, 0, 0, seoul)
        assertEquals(86_400_000L + 50, millisUntilNextMidnight(now))
    }
}
