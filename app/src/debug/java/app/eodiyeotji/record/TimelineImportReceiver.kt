package app.eodiyeotji.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.importGoogleTimeline
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [디버그 빌드 전용] 폰에서 내보낸 구글 타임라인 파일을 주행 기록으로 가져온다 (차 이동만, 이미 기록이 있는 날은 건너뛴다).
 * 파일은 앱 폴더 `files/Timeline.json`에 둔다. 다른 이름이면 `--es file <이름>`, 그날부터만 넣으려면 `--es from 2024-01-01`.
 *
 * ```
 * adb shell "run-as app.eodiyeotji sh -c 'cat > files/Timeline.json'" < Timeline.json     (차 판 에뮬레이터는 run-as 뒤에 --user 10)
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.record.TimelineImportReceiver [--es from 2024-01-01]
 * ```
 * 결과는 logcat 태그 EodiDemo. 실제 사람의 타임라인 파일은 저장소에 올리지 않는다.
 */
@AndroidEntryPoint
class TimelineImportReceiver : BroadcastReceiver() {
    @Inject lateinit var core: EodiCore

    override fun onReceive(context: Context, intent: Intent) {
        val file = File(context.filesDir, intent.getStringExtra("file") ?: "Timeline.json")
        val from = intent.getStringExtra("from")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val t0 = System.nanoTime()
                val r = core.importGoogleTimeline(file.absolutePath, from)
                Log.i(
                    TAG,
                    "타임라인: 주행 ${r.drives}개 · 점 ${r.fixes}개 넣음 (파일의 차 이동 ${r.found}개, 건너뜀 ${r.skippedExisting}개, " +
                        "${r.firstDay} ~ ${r.lastDay}, ${(System.nanoTime() - t0) / 1_000_000}ms)",
                )
            } catch (e: Exception) {
                Log.w(TAG, "타임라인 가져오기 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "EodiDemo"
    }
}
