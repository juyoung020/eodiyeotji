package app.eodiyeotji.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.eodiyeotji.R
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.LatLon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// 화면에 찍을 글자 만들기(표시 형식)만 한다. 값 계산은 Rust가 끝내고 온다.

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val SEPARATOR = " · "

/** "11월 7일 (금)", 오늘·어제는 "오늘 · 11월 7일 (금)", 다른 해는 연도를 붙인다. */
@Composable
@ReadOnlyComposable
internal fun dayLabel(day: String, today: LocalDate): String {
    val date = LocalDate.parse(day)
    val pattern = stringResource(
        if (date.year == today.year) R.string.timeline_date_pattern else R.string.timeline_date_pattern_year,
    )
    val text = date.format(DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0]))
    return when (date) {
        today -> stringResource(R.string.timeline_relative_date, stringResource(R.string.timeline_today), text)
        today.minusDays(1) -> stringResource(R.string.timeline_relative_date, stringResource(R.string.timeline_yesterday), text)
        else -> text
    }
}

/** "주행 2회 · 머문 곳 1곳 · 42.3km · 1시간 20분" (머문 곳이 없으면 그 칸은 뺀다). */
@Composable
@ReadOnlyComposable
internal fun daySummaryLine(s: DaySummary): String = buildList {
    add(stringResource(R.string.timeline_trips, s.tripCount))
    if (s.stayCount > 0) add(stringResource(R.string.timeline_stays, s.stayCount))
    add(distanceText(s.distanceM))
    add(durationText(s.driveMillis))
}.joinToString(SEPARATOR)

/** 1km 미만은 m, 그 이상은 소수 한 자리 km. */
@Composable
@ReadOnlyComposable
internal fun distanceText(meters: Double): String =
    if (meters < 1000.0) {
        stringResource(R.string.timeline_m, meters.roundToInt())
    } else {
        stringResource(R.string.timeline_km, String.format(Locale.ROOT, "%.1f", meters / 1000.0))
    }

/** "32분", "2시간", "1시간 20분", 1분이 안 되면 "1분 미만". */
@Composable
@ReadOnlyComposable
internal fun durationText(millis: Long): String {
    val totalMin = (millis / 60_000L).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        totalMin <= 0 -> stringResource(R.string.timeline_under_minute)
        h == 0 -> stringResource(R.string.timeline_minutes, m)
        m == 0 -> stringResource(R.string.timeline_hours, h)
        else -> stringResource(R.string.timeline_hours_minutes, h, m)
    }
}

/** "09:10 – 09:42" (기기 시간대 기준). */
@Composable
@ReadOnlyComposable
internal fun timeRangeText(startMs: Long, endMs: Long): String =
    stringResource(R.string.timeline_time_range, clockText(startMs), clockText(endMs))

internal fun clockText(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(TimeFormat)

/** 장소 이름은 다음 단계(역지오코딩)에서 붙인다. 지금은 좌표를 그대로 보여 준다. */
internal fun coordText(p: LatLon): String = String.format(Locale.ROOT, "%.5f, %.5f", p.lat, p.lon)
