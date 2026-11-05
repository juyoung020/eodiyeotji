package app.eodiyeotji.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.eodiyeotji.core.CoreStatus
import app.eodiyeotji.core.DayEntry
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceSource
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

private val CityHall = LatLon(37.56650, 126.97800)
private val Market = LatLon(37.57010, 126.99960)
private val Palace = LatLon(37.57960, 126.97700)
private val Bukchon = LatLon(37.58260, 126.98360)
private val Cafe = LatLon(37.49790, 127.02760)

private val CityHallPlace = PlaceLabel("서울시청", "공공기관", "서울 중구 세종대로 110", PlaceSource.KAKAO)
private val MarketPlace = PlaceLabel("광장시장", "전통시장", "서울 종로구 창경궁로 88", PlaceSource.NAVI)
private val PalacePlace = PlaceLabel("경복궁", "", "서울 종로구 사직로 161", PlaceSource.OSM)
private val BukchonPlace = PlaceLabel("북촌한옥마을", "관광명소", "", PlaceSource.KAKAO)

private val SampleDays = listOf(
    DaySummary("2026-11-07", 4, 3, 42_300.0, 80 * 60_000L, ms(PreviewToday, 9, 10), ms(PreviewToday, 15, 20)),
    DaySummary("2026-11-06", 1, 0, 12_400.0, 32 * 60_000L, ms(PreviewToday.minusDays(1), 18, 5), ms(PreviewToday.minusDays(1), 18, 37)),
    DaySummary("2026-11-04", 3, 2, 87_900.0, 155 * 60_000L, ms(PreviewToday.minusDays(3), 7, 40), ms(PreviewToday.minusDays(3), 21, 12)),
    DaySummary("2026-11-01", 1, 1, 640.0, 4 * 60_000L, ms(PreviewToday.minusDays(6), 14, 0), ms(PreviewToday.minusDays(6), 15, 30)),
    DaySummary("2025-12-24", 2, 1, 120_000.0, 120 * 60_000L, ms(LocalDate.of(2025, 12, 24), 10, 0), ms(LocalDate.of(2025, 12, 24), 19, 0)),
)

private fun trip(h0: Int, m0: Int, h1: Int, m1: Int, km: Double, a: LatLon, b: LatLon, from: PlaceLabel?, to: PlaceLabel?) =
    DayEntry.Trip(TripSummary(ms(PreviewToday, h0, m0), ms(PreviewToday, h1, m1), km * 1000, a, b, listOf(a, b), from, to))

private fun stay(h0: Int, m0: Int, h1: Int, m1: Int, at: LatLon, place: PlaceLabel?) =
    DayEntry.Stay(StaySummary(at, ms(PreviewToday, h0, m0), ms(PreviewToday, h1, m1), place))

/**
 * 장소 이름이 붙은 하루: 출발·도착 다 앎(내비 이름 포함) · 도착만 모름 · 출발만 모름 · 둘 다 모름,
 * 머문 곳은 이름+분류 · 이름 모름 · 분류 없는 이름, 마지막은 아직 머무는 중(이름 앎).
 */
private val NamedEntries = listOf(
    trip(9, 10, 9, 32, 4.1, CityHall, Market, CityHallPlace, MarketPlace),
    stay(9, 32, 11, 5, Market, MarketPlace),
    trip(11, 5, 11, 20, 2.6, Market, Cafe, MarketPlace, null),
    stay(11, 20, 12, 40, Cafe, null),
    trip(12, 40, 13, 5, 30.2, Cafe, Palace, null, PalacePlace),
    stay(13, 5, 14, 50, Palace, PalacePlace),
    trip(14, 50, 15, 20, 5.4, Palace, Bukchon, null, null),
    stay(15, 20, 15, 20, Bukchon, BukchonPlace),
)

/** 이름을 아직 하나도 모르는 하루 (기존 화면과 같은 모양, 둘째 줄에 "장소 이름 찾는 중…"). */
private val UnnamedEntries = listOf(
    trip(9, 10, 9, 42, 12.4, CityHall, Cafe, null, null),
    stay(9, 42, 11, 5, Cafe, null),
    trip(11, 5, 11, 53, 29.9, Cafe, Palace, null, null),
    stay(11, 53, 11, 53, Palace, null),
)

private val SampleStatus = CoreUiState.Ready(CoreStatus(version = "0.1.0", pointsIn = 10_000, pointsOut = 412, elapsedMicros = 1_840))

private val LoadedDays = DaysState.Loaded(SampleDays, PreviewToday)
private val LoadedDetail = DetailState.Loaded(SampleDays.first(), NamedEntries)
private val UnnamedDetail = DetailState.Loaded(SampleDays.first(), UnnamedEntries)

@Composable
private fun PreviewScreen(state: TimelineUiState) {
    EodiTheme {
        TimelineScreen(
            state = state,
            onSelectDay = {},
            onBack = {},
            onRetry = {},
            onWideChange = {},
            onEntryClick = {},
            onMapSelect = {},
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

@Preview(name = "하루 상세 · 이름 모름 (나눈 창)", widthDp = 841, heightDp = 1210)
@Composable
private fun DetailUnnamedPreview() =
    PreviewScreen(TimelineUiState(days = LoadedDays, selectedDay = "2026-11-07", detail = UnnamedDetail))

@Preview(name = "목록 + 상세 (넓은 창)", widthDp = 1682, heightDp = 1210)
@Composable
private fun WidePreview() = PreviewScreen(TimelineUiState(days = LoadedDays, detail = LoadedDetail))

@Preview(name = "불러오는 중", widthDp = 841, heightDp = 1210)
@Composable
private fun LoadingPreview() = PreviewScreen(TimelineUiState())
