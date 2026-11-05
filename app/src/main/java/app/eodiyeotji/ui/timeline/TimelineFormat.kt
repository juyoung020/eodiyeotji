package app.eodiyeotji.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.eodiyeotji.R
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceSource
import app.eodiyeotji.core.StaySummary
import app.eodiyeotji.core.TripSummary
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

// ---------------------------------------------------------------- 장소 이름
// 이름은 Rust가 붙여 준다(PlaceLabel). 아직 없으면 null → 좌표 대신 "장소 이름 찾는 중…"을 보여 준다.

/** 주행 제목: "서울시청 → 광장시장", 한쪽만 알면 "서울시청에서 출발" / "광장시장 도착", 둘 다 모르면 null. */
@Composable
@ReadOnlyComposable
internal fun tripPlacesText(t: TripSummary): String? {
    val from = t.startPlace?.name?.ifBlank { null }
    val to = t.endPlace?.name?.ifBlank { null }
    return when {
        from != null && to != null -> stringResource(R.string.timeline_from_to, from, to)
        from != null -> stringResource(R.string.place_trip_from, from)
        to != null -> stringResource(R.string.place_trip_to, to)
        else -> null
    }
}

/** "12.4km · 32분" (이름이 제목일 때 둘째 줄). */
@Composable
@ReadOnlyComposable
internal fun tripStatsText(t: TripSummary): String =
    distanceText(t.distanceM) + SEPARATOR + durationText(t.endMs - t.startMs)

/** 머문 곳 둘째 줄: "음식점 · 1시간 23분" (분류가 없으면 시간만). 아직 머무는 곳은 분류, 없으면 주소, 둘 다 없으면 null. */
@Composable
@ReadOnlyComposable
internal fun stayDetailText(s: StaySummary, place: PlaceLabel): String? {
    val category = place.category.ifBlank { null }
    return if (s.isOpen) {
        category ?: place.address.ifBlank { null }
    } else {
        listOfNotNull(category, durationText(s.leaveMs - s.arriveMs)).joinToString(SEPARATOR)
    }
}

/** 이름이 차량 내비에서 왔으면 작은 "내비" 표시를 붙인다 (주행은 출발·도착 중 하나라도). */
internal val TripSummary.hasNaviPlace: Boolean
    get() = startPlace?.source == PlaceSource.NAVI || endPlace?.source == PlaceSource.NAVI
