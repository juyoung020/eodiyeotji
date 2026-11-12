package app.eodiyeotji.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.eodiyeotji.R
import app.eodiyeotji.core.EpisodeKind
import app.eodiyeotji.ui.common.agoText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 화면에 찍을 글자 만들기(표시 형식)만 한다. 값 계산은 Rust·에이전트가 끝내고 온다.

/** "1년 전 · 2025년 8월 14일 (목)" */
@Composable
@ReadOnlyComposable
internal fun memoryDateLine(day: String, today: LocalDate): String =
    stringResource(R.string.agent_date_line, agoText(LocalDate.parse(day), today), memoryDateText(day))

/** "2025년 8월 14일 (목)" */
@Composable
@ReadOnlyComposable
internal fun memoryDateText(day: String): String {
    val pattern = stringResource(R.string.agent_date_pattern)
    return LocalDate.parse(day).format(DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0]))
}

@Composable
@ReadOnlyComposable
internal fun kindText(kind: EpisodeKind): String = stringResource(
    when (kind) {
        EpisodeKind.COMMUTE_TO_WORK -> R.string.agent_kind_commute_to_work
        EpisodeKind.COMMUTE_HOME -> R.string.agent_kind_commute_home
        EpisodeKind.ERRAND -> R.string.agent_kind_errand
        EpisodeKind.OUTING -> R.string.agent_kind_outing
        EpisodeKind.TRIP -> R.string.agent_kind_trip
    },
)

/** 조사 "로/으로" (받침이 있고 ㄹ이 아니면 "으로"): "안목항 바다횟집" → "으로", "경포해변" → "으로", "카페" → "로". */
internal fun roJosa(word: String): String {
    val c = word.lastOrNull() ?: return "로"
    if (c !in '가'..'힣') return "로"
    val batchim = (c - '가') % 28
    return if (batchim == 0 || batchim == 8) "로" else "으로"
}
