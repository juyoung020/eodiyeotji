package app.eodiyeotji.ui.search

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * [디버그 빌드 전용] 열려 있는 검색 칸에 말을 넣는다 (에뮬레이터에서 `adb shell input text`로 한글을 칠 수 없어서).
 * 칠 때와 같은 길로 간다 (150ms 모았다가 Rust 검색).
 *
 * ```
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.ui.search.SearchDebugReceiver --es text "지난주 카페"
 * ```
 */
class SearchDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        Log.i("EodiSearch", "디버그 검색 입력: $text")
        SearchDebugInput.texts.tryEmit(text)
    }
}
