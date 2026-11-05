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
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// 화면에 찍을 글자 만들기(표시 형식)만 한다. 값 계산은 Rust·에이전트가 끝내고 온다.

/**
 * 보조 글자(설명·분류·요약 줄). 차 화면 최소 글자 높이(18px, Pleos 타이포 가이드)를 지키려고
 * 테마의 titleMedium(16sp)을 18sp로 올려 쓴다. 테마가 바뀌어도 서체·색은 테마를 따른다.
 */
internal val AgentSubStyle: TextStyle
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp)

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

/** 오늘 · 어제 · N일 전 · N주 전 · N개월 전 · N년 전 */
@Composable
@ReadOnlyComposable
internal fun agoText(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today)
    val period = Period.between(date, today)
    return when {
        days <= 0L -> stringResource(R.string.agent_today)
        days == 1L -> stringResource(R.string.agent_yesterday)
        days < 7L -> stringResource(R.string.agent_days_ago, days.toInt())
        period.years >= 1 -> stringResource(R.string.agent_years_ago, period.years)
        period.months >= 1 -> stringResource(R.string.agent_months_ago, period.months)
        else -> stringResource(R.string.agent_weeks_ago, (days / 7).toInt())
    }
}

/** "421km" (10km 미만은 소수 한 자리). */
@Composable
@ReadOnlyComposable
internal fun kmText(meters: Double): String {
    val km = meters / 1000.0
    val text = if (km < 10.0) String.format(Locale.ROOT, "%.1f", km) else km.roundToInt().toString()
    return stringResource(R.string.agent_km, text)
}

/** "12시간 35분" */
@Composable
@ReadOnlyComposable
internal fun spanText(millis: Long): String {
    val totalMin = (millis / 60_000L).toInt().coerceAtLeast(1)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0 -> stringResource(R.string.agent_minutes, m)
        m == 0 -> stringResource(R.string.agent_hours, h)
        else -> stringResource(R.string.agent_hours_minutes, h, m)
    }
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
