package app.eodiyeotji.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.eodiyeotji.core.CoreStatus
import app.eodiyeotji.core.DayEntry
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.StaySummary
import app.eodiyeotji.core.TripSummary
import app.eodiyeotji.ui.home.CoreStatusText
import app.eodiyeotji.ui.home.CoreUiState
import app.eodiyeotji.ui.theme.EodiTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// 미리보기 전용 예시 데이터. 크기는 Pleos 앱 영역 실측값(넓게 1682×1210, 나눈 창 약 841×1210)에 맞춘다.

private val PreviewToday: LocalDate = LocalDate.of(2026, 11, 7)

private fun ms(day: LocalDate, h: Int, m: Int): Long =
    LocalDateTime.of(day, LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private val Home = LatLon(37.56650, 126.97800)
private val Cafe = LatLon(37.49790, 127.02760)
private val Park = LatLon(37.52190, 126.92440)

private val SampleDays = listOf(
    DaySummary("2026-11-07", 2, 1, 42_300.0, 80 * 60_000L, ms(PreviewToday, 9, 10), ms(PreviewToday, 11, 53)),
    DaySummary("2026-11-06", 1, 0, 12_400.0, 32 * 60_000L, ms(PreviewToday.minusDays(1), 18, 5), ms(PreviewToday.minusDays(1), 18, 37)),
    DaySummary("2026-11-04", 3, 2, 87_900.0, 155 * 60_000L, ms(PreviewToday.minusDays(3), 7, 40), ms(PreviewToday.minusDays(3), 21, 12)),
    DaySummary("2026-11-01", 1, 1, 640.0, 4 * 60_000L, ms(PreviewToday.minusDays(6), 14, 0), ms(PreviewToday.minusDays(6), 15, 30)),
    DaySummary("2025-12-24", 2, 1, 120_000.0, 120 * 60_000L, ms(LocalDate.of(2025, 12, 24), 10, 0), ms(LocalDate.of(2025, 12, 24), 19, 0)),
)

private val SampleEntries = listOf(
    DayEntry.Trip(TripSummary(ms(PreviewToday, 9, 10), ms(PreviewToday, 9, 42), 12_400.0, Home, Cafe, listOf(Home, Cafe))),
    DayEntry.Stay(StaySummary(Cafe, ms(PreviewToday, 9, 42), ms(PreviewToday, 11, 5))),
    DayEntry.Trip(TripSummary(ms(PreviewToday, 11, 5), ms(PreviewToday, 11, 53), 29_900.0, Cafe, Park, listOf(Cafe, Park))),
    DayEntry.Stay(StaySummary(Park, ms(PreviewToday, 11, 53), ms(PreviewToday, 11, 53))),
)

private val SampleStatus = CoreUiState.Ready(CoreStatus(version = "0.1.0", pointsIn = 10_000, pointsOut = 412, elapsedMicros = 1_840))

private val LoadedDays = DaysState.Loaded(SampleDays, PreviewToday)
private val LoadedDetail = DetailState.Loaded(SampleDays.first(), SampleEntries)

@Composable
private fun PreviewScreen(state: TimelineUiState) {
    EodiTheme {
        TimelineScreen(
            state = state,
            onSelectDay = {},
            onBack = {},
            onRetry = {},
            onWideChange = {},
            status = { CoreStatusText(SampleStatus) },
        )
    }
}

@Preview(name = "목록 (나눈 창)", widthDp = 841, heightDp = 1210)
@Composable
private fun ListNarrowPreview() = PreviewScreen(TimelineUiState(days = LoadedDays))

@Preview(name = "기록 없음", widthDp = 841, heightDp = 1210)
@Composable
private fun EmptyPreview() = PreviewScreen(TimelineUiState(days = DaysState.Loaded(emptyList(), PreviewToday)))

@Preview(name = "하루 상세 (나눈 창)", widthDp = 841, heightDp = 1210)
@Composable
private fun DetailNarrowPreview() =
    PreviewScreen(TimelineUiState(days = LoadedDays, selectedDay = "2026-11-07", detail = LoadedDetail))

@Preview(name = "목록 + 상세 (넓은 창)", widthDp = 1682, heightDp = 1210)
@Composable
private fun WidePreview() = PreviewScreen(TimelineUiState(days = LoadedDays, detail = LoadedDetail))

@Preview(name = "불러오는 중", widthDp = 841, heightDp = 1210)
@Composable
private fun LoadingPreview() = PreviewScreen(TimelineUiState())
