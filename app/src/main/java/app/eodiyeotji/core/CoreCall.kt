package app.eodiyeotji.core

import android.os.SystemClock
import android.util.Log
import app.eodiyeotji.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rust 코어 호출 한 번을 IO 스레드에서 돌린다 (UI 스레드를 막지 않는다, CLAUDE.md 원칙 2).
 * 디버그 빌드에서는 [SLOW_MS]보다 오래 걸린 호출(Rust + 값 옮겨 담기)을 logcat `EodiPerf`에 남긴다.
 */
internal suspend inline fun <T> coreCall(name: String, crossinline block: () -> T): T = withContext(Dispatchers.IO) {
    if (!BuildConfig.DEBUG) return@withContext block()
    val t0 = SystemClock.elapsedRealtimeNanos()
    val r = block()
    val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
    if (ms >= SLOW_MS || Log.isLoggable(PERF_TAG, Log.VERBOSE)) Log.i(PERF_TAG, "%s %.1fms".format(name, ms))
    r
}

@PublishedApi internal const val PERF_TAG = "EodiPerf"
@PublishedApi internal const val SLOW_MS = 8.0
