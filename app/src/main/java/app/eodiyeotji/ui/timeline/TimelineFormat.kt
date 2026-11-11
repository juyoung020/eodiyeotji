package app.eodiyeotji.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.eodiyeotji.R
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceSource
import app.eodiyeotji.core.StaySummary
import app.eodiyeotji.core.TripSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

/** "오늘" / "어제", 그 밖의 날은 null. */
@Composable
@ReadOnlyComposable
internal fun relativeDayName(day: String, today: LocalDate): String? = when (LocalDate.parse(day)) {
    today -> stringResource(R.string.timeline_today)
    today.minusDays(1) -> stringResource(R.string.timeline_yesterday)
    else -> null
}

/** 하루 상세 제목: "11월 5일 목요일", 다른 해는 "2025년 12월 24일 수요일". */
@Composable
@ReadOnlyComposable
internal fun dayTitle(day: String, today: LocalDate): String =
    formatDay(day, today, R.string.day_title_pattern, R.string.day_title_pattern_year)

/** 날짜 목록 행 제목: "11월 4일", 다른 해는 "2025년 12월 24일" (요일은 타일에). */
@Composable
@ReadOnlyComposable
internal fun dayListTitle(day: String, today: LocalDate): String =
    formatDay(day, today, R.string.day_list_pattern, R.string.day_list_pattern_year)

@Composable
@ReadOnlyComposable
private fun formatDay(day: String, today: LocalDate, thisYear: Int, otherYear: Int): String {
    val date = LocalDate.parse(day)
    val pattern = stringResource(if (date.year == today.year) thisYear else otherYear)
    return date.format(DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0]))
}

/** 목록 날짜 타일 아래 칸: 짧은 요일 "목". */
@Composable
@ReadOnlyComposable
internal fun weekdayShort(day: String): String =
    LocalDate.parse(day).format(
        DateTimeFormatter.ofPattern(stringResource(R.string.day_tile_weekday_pattern), LocalConfiguration.current.locales[0]),
    )

/** 목록 날짜 타일 위 칸: 날짜(일) 숫자. */
internal fun dayOfMonthText(day: String): String = LocalDate.parse(day).dayOfMonth.toString()

/**
 * "주행 2회 · 들른 곳 1곳 · 42.3km · 1시간 20분" (들른 곳이 없으면 그 칸은 뺀다).
 * 긴 날은 두 줄로 넘어가는데, 한글은 글자 사이 어디서나 줄을 바꿀 수 있어 "4시간 19 / 분"처럼 값 가운데서 끊겼다
 * → 값 하나는 글자 사이를 단어 잇기 문자(U+2060)로 묶어 " · " 자리에서만 줄이 바뀌게 한다.
 */
@Composable
@ReadOnlyComposable
internal fun daySummaryLine(s: DaySummary): String = buildList {
    add(stringResource(R.string.timeline_trips, s.tripCount))
    if (s.stayCount > 0) add(stringResource(R.string.timeline_stays, s.stayCount))
    add(distanceText(s.distanceM))
    add(durationText(s.driveMillis))
}.joinToString(SEPARATOR) { part -> part.replace(' ', '\u00A0').toList().joinToString("\u2060") }

/** 목록 행 머문 곳 줄: "경포해변 · 초당순두부마을 · 안목해변" (Rust가 앞 3개까지 골라 준다). 없으면 null. */
internal fun stopNamesText(s: DaySummary): String? =
    s.stopNames.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)

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
internal fun stayDetailText(s: StaySummary, place: PlaceLabel, showDuration: Boolean = true): String? {
    val category = place.category.ifBlank { null }
    return if (s.isOpen || !showDuration) {
        category ?: place.address.ifBlank { null }
    } else {
        listOfNotNull(category, durationText(s.leaveMs - s.arriveMs)).joinToString(SEPARATOR)
    }
}

/** 이름이 차량 내비에서 왔으면 작은 "내비" 표시를 붙인다 (주행은 출발·도착 중 하나라도). */
internal val TripSummary.hasNaviPlace: Boolean
    get() = startPlace?.source == PlaceSource.NAVI || endPlace?.source == PlaceSource.NAVI
