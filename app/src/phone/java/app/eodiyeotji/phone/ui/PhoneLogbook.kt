package app.eodiyeotji.phone.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.eodiyeotji.R
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.rust.Logbook
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.community.SectionTitle
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface LogbookEntryPoint {
    fun core(): EodiCore
}

/**
 * 설정 시트 안 "운행기록부": 이번 달과 지난 두 달의 주행 수 · 거리(출퇴근 거리)를 보이고,
 * [내보내기]를 누르면 국세청 업무용승용차 운행기록부 칸에 맞춘 CSV(엑셀)를 만들어 공유 시트로 넘긴다.
 * 줄 만들기 · 계기판 거리 맞추기 · CSV 글은 Rust(`logbook.rs`)가 하고, 여기서는 파일로 저장해 보낼 뿐이다.
 */
@Composable
fun LogbookSection() {
    val context = LocalContext.current
    val core = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, LogbookEntryPoint::class.java).core() }
    val months = remember { (0L..2L).map { YearMonth.now().minusMonths(it) } }
    var books by remember { mutableStateOf<List<Logbook>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(core) {
        books = withContext(Dispatchers.IO) { months.mapNotNull { m -> runCatching { core.logbook(m.toString()) }.getOrNull() } }
    }
    val c = EodiTheme.colors
    val type = EodiTheme.type
    SectionTitle(stringResource(R.string.logbook_title), Modifier.padding(start = 4.dp, bottom = 4.dp))
    Text(stringResource(R.string.logbook_hint), style = type.meta, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(EodiTheme.space.radiusM)).background(c.surfaceHigh).padding(vertical = 8.dp)) {
        for (book in books) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val m = YearMonth.parse(book.month)
                    Text(stringResource(R.string.logbook_month, m.year, m.monthValue), style = type.bodyStrong, color = c.textPrimary)
                    Text(
                        stringResource(R.string.logbook_summary, book.rows.size, book.totalKm.toInt(), book.commuteKm.toInt()),
                        style = type.meta,
                        color = c.textSecondary,
                    )
                }
                if (book.rows.isNotEmpty()) {
                    SecondaryPill(stringResource(R.string.logbook_export), EodiIcons.Calendar, { scope.launch { share(context, core, book) } })
                }
            }
        }
    }
}

/** CSV를 캐시 share/에 저장하고 공유 시트(메일 · 드라이브 · 카톡)로 넘긴다. */
private suspend fun share(context: Context, core: EodiCore, book: Logbook) {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "운행기록부-${book.month}.csv")
            file.writeText(core.logbookCsv(book), Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        }.onFailure { Log.w("EodiLogbook", "운행기록부 저장 실패", it) }.getOrNull()
    } ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logbook_subject, book.month))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.logbook_export)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
