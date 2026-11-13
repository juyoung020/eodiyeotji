package app.eodiyeotji.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.deleteDay
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [디버그 빌드 전용] 테스트 · 경로 재생으로 쌓인 하루 기록을 지운다. 앱의 "이 날 지우기"와 같은 길(Rust `delete_day`)을 쓴다.
 *
 * ```
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.record.DeleteDayReceiver --es day 2026-11-13
 * ```
 * 결과는 logcat 태그 EodiDemo.
 */
@AndroidEntryPoint
class DeleteDayReceiver : BroadcastReceiver() {
    @Inject lateinit var core: EodiCore

    override fun onReceive(context: Context, intent: Intent) {
        val day = intent.getStringExtra("day")
        if (day == null) {
            Log.w(TAG, "day 값이 없다 (--es day YYYY-MM-DD)")
            return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                core.deleteDay(day)
                Log.i(TAG, "하루 기록 지움: $day")
            } catch (e: Exception) {
                Log.w(TAG, "하루 기록 지우기 실패: $day", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "EodiDemo"
    }
}
