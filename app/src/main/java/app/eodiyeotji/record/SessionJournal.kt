package app.eodiyeotji.record

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 진행 중인 주행 기록을 표시해 두는 작은 메모. 주행 중에 앱 프로세스가 죽어도
 * 다시 살아났을 때 "기록 중이었다"를 알아서, 끝 표시(markDriveEnd)를 빠뜨리거나 시작을 두 번 찍지 않게 한다.
 *
 * 디스크를 건드리므로 전부 Dispatchers.IO에서 한다.
 */
@Singleton
class SessionJournal @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("record_session", Context.MODE_PRIVATE) }

    data class Open(val startMs: Long, val lastFixMs: Long)

    suspend fun read(): Open? = withContext(Dispatchers.IO) {
        val start = prefs.getLong(KEY_START, 0L)
        if (start == 0L) null else Open(start, prefs.getLong(KEY_LAST_FIX, start))
    }

    suspend fun open(startMs: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_START, startMs).putLong(KEY_LAST_FIX, startMs).apply()
    }

    suspend fun touch(lastFixMs: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_LAST_FIX, lastFixMs).apply()
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_START = "start_ms"
        const val KEY_LAST_FIX = "last_fix_ms"
    }
}
