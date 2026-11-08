package app.eodiyeotji.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import app.eodiyeotji.R
import app.eodiyeotji.core.EpisodeKind
import app.eodiyeotji.ui.common.agoText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 화면에 찍을 글자 만들기(표시 형식)만 한다. 값 계산은 Rust·에이전트가 끝내고 온다.

/**
 * 보조 글자(설명 · 분류 · 요약 줄). Pleos 글자 단계의 가장 작은 Label(20sp)에 맞춘다 → 앱 다른 화면의 작은 글자(caption)와 같은 크기.
 * 테마가 바뀌어도 서체 · 색은 테마를 따른다.
 */
internal val AgentSubStyle: TextStyle
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 28.sp)

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
