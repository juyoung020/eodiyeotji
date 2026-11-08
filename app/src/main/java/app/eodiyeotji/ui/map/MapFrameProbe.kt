package app.eodiyeotji.ui.map

import android.os.SystemClock
import android.util.Log
import org.maplibre.android.maps.MapView

/**
 * 지도 한 장의 체감 성능 기록 (logcat `EodiPerf`). 평소에는 꺼져 있고(붙이지도 않는다), 측정할 때만
 * `adb shell setprop log.tag.EodiPerf DEBUG` 로 켠다. 지도가 화면에서 빠질 때 한 줄로 남긴다:
 * 스타일이 뜬 시각 · 첫 장면이 다 그려진 시각(만든 때부터) · 그린 프레임 수 · 프레임 간격(p50/p90/최대) ·
 * 한 프레임 GPU 명령 만들기/그리기 시간(평균 · p90).
 */
internal class MapFrameProbe private constructor(private val label: String) {
    private val created = SystemClock.uptimeMillis()
    private var styleMs = -1L
    private var firstFullMs = -1L
    private var last = 0L
    private val intervals = ArrayList<Long>(2048)
    private val encode = ArrayList<Double>(2048)
    private val render = ArrayList<Double>(2048)

    fun dump() {
        if (intervals.isEmpty() && firstFullMs < 0) return
        fun <T : Comparable<T>> List<T>.q(p: Double): T? = sorted().let { if (it.isEmpty()) null else it[((it.size - 1) * p).toInt()] }
        Log.d(
            TAG,
            "map[$label] style=${styleMs}ms firstFull=${firstFullMs}ms frames=${intervals.size + 1} " +
                "interval p50=${intervals.q(0.5)} p90=${intervals.q(0.9)} max=${intervals.maxOrNull()}ms >50ms=${intervals.count { it > 50 }} " +
                "encode avg=${"%.1f".format(encode.average())} p90=${"%.1f".format(encode.q(0.9) ?: 0.0)} " +
                "render avg=${"%.1f".format(render.average())} p90=${"%.1f".format(render.q(0.9) ?: 0.0)}ms",
        )
    }

    companion object {
        private const val TAG = "EodiPerf"

        /** 측정이 켜져 있을 때만 붙인다. */
        fun attach(view: MapView, label: String): MapFrameProbe? {
            if (!Log.isLoggable(TAG, Log.DEBUG)) return null
            val p = MapFrameProbe(label)
            view.addOnDidFinishLoadingStyleListener { if (p.styleMs < 0) p.styleMs = SystemClock.uptimeMillis() - p.created }
            view.addOnDidFinishRenderingFrameListener { fully, enc, ren ->
                val now = SystemClock.uptimeMillis()
                if (fully && p.firstFullMs < 0) p.firstFullMs = now - p.created
                if (p.last != 0L) p.intervals += now - p.last
                p.last = now
                p.encode += enc
                p.render += ren
            }
            return p
        }
    }
}
