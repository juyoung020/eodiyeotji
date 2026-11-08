package app.eodiyeotji.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import app.eodiyeotji.R
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// 모든 화면이 같이 쓰는 표시 형식. 같은 거리·시간·"얼마 전"은 어느 화면에서나 같은 글자로 보인다.

/** 1km 미만은 "850m", 그 이상은 소수 한 자리 "12.0km". */
@Composable
@ReadOnlyComposable
fun distanceText(meters: Double): String =
    if (meters < 1000.0) {
        stringResource(R.string.timeline_m, meters.roundToInt())
    } else {
        stringResource(R.string.timeline_km, String.format(Locale.ROOT, "%.1f", meters / 1000.0))
    }

/** "32분", "2시간", "1시간 20분", 1분이 안 되면 "1분 미만". */
@Composable
@ReadOnlyComposable
fun durationText(millis: Long): String {
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

/** 날 단위 "얼마 전": 오늘 · 어제 · N일 전 · N주 전 · N개월 전 · N년 전 (기억 날짜). */
@Composable
@ReadOnlyComposable
fun agoText(date: LocalDate, today: LocalDate): String {
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

/** 시각 단위 "얼마 전": 방금 · 3분 전 · 5시간 전 · 2일 전 · 3주 전 · 2개월 전 (글·댓글 시각). */
@Composable
@ReadOnlyComposable
fun agoText(ms: Long, nowMs: Long = System.currentTimeMillis()): String {
    val min = (nowMs - ms).coerceAtLeast(0) / 60_000
    val hours = min / 60
    val days = hours / 24
    return when {
        min < 1 -> stringResource(R.string.community_ago_now)
        hours < 1 -> stringResource(R.string.community_ago_minutes, min.toInt())
        days < 1 -> stringResource(R.string.community_ago_hours, hours.toInt())
        days < 7 -> stringResource(R.string.community_ago_days, days.toInt())
        days < 31 -> stringResource(R.string.community_ago_weeks, (days / 7).toInt())
        else -> stringResource(R.string.community_ago_months, (days / 30).toInt())
    }
}
