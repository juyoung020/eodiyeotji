package app.eodiyeotji.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.eodiyeotji.core.EodiCore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [디버그 빌드 전용] 시연용 1년치 기록(출퇴근·볼일·나들이·1년 전 강릉 가족 여행 등)을 넣는다. 이미 넣었으면 아무것도 안 한다.
 *
 * ```
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.record.DemoSeedReceiver
 * ```
 * 결과는 logcat 태그 EodiDemo.
 */
@AndroidEntryPoint
class DemoSeedReceiver : BroadcastReceiver() {
    @Inject lateinit var core: EodiCore

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val t0 = System.nanoTime()
                val n = core.seedDemoData()
                Log.i(TAG, "시연 데이터: ${n}건 넣음 (${(System.nanoTime() - t0) / 1_000_000}ms, 0이면 이미 있음)")
            } catch (e: Exception) {
                Log.w(TAG, "시연 데이터 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "EodiDemo"
    }
}
