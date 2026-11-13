package app.eodiyeotji.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTeardownGuardTest {
    @Test
    fun runsWhileAliveAndReady() {
        val g = MapTeardownGuard()
        var n = 0
        assertTrue(g.run { n++ })
        assertEquals(1, n)
    }

    @Test
    fun skipsAfterClose() {
        val g = MapTeardownGuard()
        g.close()
        var n = 0
        assertFalse(g.run { n++ })
        assertFalse(g.run(ready = { error("ready는 닫힌 뒤 읽지 않는다") }) { n++ })
        assertEquals(0, n)
    }

    @Test
    fun skipsWhenStyleNotReady() {
        val g = MapTeardownGuard()
        var n = 0
        assertFalse(g.run(ready = { false }) { n++ })
        assertEquals(0, n)
    }

    @Test
    fun swallowsIllegalStateFromDestroyedMap() {
        val g = MapTeardownGuard()
        assertFalse(g.run { throw IllegalStateException("Calling getSourceAs when a newer style is loading/has loaded.") })
        assertFalse(g.run(ready = { throw IllegalStateException("destroyed") }) {})
    }
}
