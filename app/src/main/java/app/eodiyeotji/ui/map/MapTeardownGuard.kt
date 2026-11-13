package app.eodiyeotji.ui.map

/**
 * 지도가 아직 살아 있을 때만 스타일 · 층을 만지게 하는 문지기.
 *
 * 취소된 코루틴의 finally는 다음 디스패치에 돌기 때문에 MapView.onDestroy보다 늦게 올 수 있다.
 * 화면이 사라질 때 [close]를 (onDestroy 전에, 동기로) 부르면 그 뒤 [run]은 아무것도 하지 않는다.
 * 살아 있는 동안이라도 MapLibre가 이미 내려간 스타일에 IllegalStateException을 던지면 삼킨다.
 */
internal class MapTeardownGuard {
    @Volatile
    var alive: Boolean = true
        private set

    fun close() {
        alive = false
    }

    /** [ready]가 true이고 지도가 살아 있을 때만 [block]을 돌린다. 돌렸으면 true. */
    inline fun run(ready: () -> Boolean = { true }, block: () -> Unit): Boolean {
        if (!alive) return false
        return try {
            if (!ready()) return false
            block()
            true
        } catch (_: IllegalStateException) {
            false
        }
    }
}
