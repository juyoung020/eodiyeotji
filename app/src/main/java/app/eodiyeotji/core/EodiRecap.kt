package app.eodiyeotji.core

import androidx.compose.runtime.Immutable
import app.eodiyeotji.core.rust.EpisodeKind as REpisodeKind
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint
import app.eodiyeotji.core.rust.MonthRecap as RMonthRecap
import app.eodiyeotji.core.rust.OnThisDay as ROnThisDay
import app.eodiyeotji.core.rust.Recall as RRecall
import app.eodiyeotji.core.rust.RecapDay as RRecapDay
import app.eodiyeotji.core.rust.RecapPlace as RRecapPlace
import app.eodiyeotji.core.rust.RecapRegion as RRecapRegion
import app.eodiyeotji.core.rust.RecapTotals as RRecapTotals
import app.eodiyeotji.core.rust.RoutineNudge as RRoutineNudge
import app.eodiyeotji.core.rust.YearRecap as RYearRecap

/*
 * 회상 · 요약 입구 (core-rs/src/recap.rs). 고르기 · 횟수 제한 · 숫자 · 안내 문장은 Rust가 하고, 여기서는 옮겨 담기만 한다.
 */

/** "N년 전 오늘" (첫 화면 카드 · 운전 중 음성 한 줄). */
@Immutable
data class OnThisDay(
    val yearsAgo: Int,
    val day: String,
    /** 하루 제목 (AI, 없으면 규칙 제목) */
    val title: String,
    val summary: String,
    val episodeId: Long,
    val kind: EpisodeKind,
    /** 규칙 이름 ("강릉 여행") */
    val label: String,
    val region: String,
    val distanceM: Double,
    val stops: List<StopFact>,
    /** 사진으로 보일 곳 (최대 3곳) */
    val photos: List<StopFact>,
    /** 읽어 줄 한 줄 */
    val line: String,
    val dismissed: Boolean,
    val spoken: Boolean,
    /** 그날로 돌아가기로 다시 볼 것이 있다 */
    val replayable: Boolean = true,
)

/** "늘 가던 날" (첫 화면 카드, core-rs/src/routine.rs). 정해진 요일마다 가던 곳의 그날이 다가왔다. */
@Immutable
data class RoutineNudge(
    /** 같은 곳 표시. 닫을 때 그대로 넘긴다 */
    val key: String,
    val place: StopFact,
    /** "매달 첫째 · 셋째 수요일" */
    val pattern: String,
    /** 돌아오는 그날 "YYYY-MM-DD" */
    val day: String,
    /** "오늘" · "내일" · "다음 주 수요일" */
    val whenText: String,
    /** "최근 6개월 동안 11번 · 보통 오전 9시 반쯤 도착" */
    val detail: String,
    /** 카드 한 줄 */
    val line: String,
)

/** 지나갈 때 회상 하나. */
@Immutable
data class Recall(
    val key: String,
    val place: StopFact,
    val distanceM: Double,
    val day: String,
    val dayTitle: String,
    val episodeId: Long,
    val yearsAgo: Int,
    /** 도장 글 ("1년 전 가을") */
    val whenText: String,
    /** 읽어 줄 한 줄 ("1년 전 가을에 들렀던 초당할머니순두부가 근처에 있어요") */
    val line: String,
)

@Immutable data class RecapPlace(val place: StopFact, val days: Int)
@Immutable data class RecapRegion(val region: String, val days: Int)

@Immutable
data class RecapDay(
    val day: String,
    val spanDays: Int,
    val title: String,
    val episodeId: Long,
    val kind: EpisodeKind,
    val region: String,
    val distanceM: Double,
    val photo: StopFact?,
)

@Immutable
data class RecapTotals(
    val distanceM: Double,
    val driveMillis: Long,
    val driveDays: Int,
    val outings: Int,
    val trips: Int,
    val newPlaces: Int,
)

@Immutable
data class MonthRecap(
    val year: Int,
    val month: Int,
    val inProgress: Boolean,
    val recordDays: Int,
    val totals: RecapTotals,
    val topPlaces: List<RecapPlace>,
    val regions: List<RecapRegion>,
    val bestDay: RecapDay?,
    val vsPrevDistanceM: Double,
    val vsPrevDriveMillis: Long,
    val vsPrevOutings: Int,
    /** 나들이 · 여행 횟수 (위 칸 나들이 + 여행과 같은 셈) */
    val outingCount: Int,
    /** 지난달(진행 중이면 같은 날짜까지) 나들이 · 여행 횟수 */
    val prevOutingCount: Int,
    val hasPrevRecords: Boolean,
    val hasPrev: Boolean,
    val hasNext: Boolean,
)

@Immutable
data class SeasonRecap(val name: String, val distanceM: Double, val outings: Int, val bestDay: RecapDay?)

@Immutable
data class YearRecap(
    val year: Int,
    val inProgress: Boolean,
    /** "2026년 1월 1일 ~ 11월 11일" (Rust가 만든 기간 글) */
    val periodLabel: String,
    val recordDays: Int,
    val totals: RecapTotals,
    val topPlaces: List<RecapPlace>,
    val regions: List<RecapRegion>,
    val seasons: List<SeasonRecap>,
    val longestTrip: RecapDay?,
    val bestDay: RecapDay?,
    /** 1월 ~ 12월 거리 (m) */
    val monthDistanceM: List<Double>,
    val hasPrev: Boolean,
    val hasNext: Boolean,
)

/** "N년 전 오늘" (1~10년 전 같은 날에 나들이 · 여행이 없으면 null). */
suspend fun EodiCore.onThisDay(nowMs: Long = System.currentTimeMillis()): OnThisDay? = coreCall("onThisDay") {
    rust.onThisDay(nowMs)?.toModel()
}

/** 오늘은 "N년 전 오늘" 카드를 닫는다. */
suspend fun EodiCore.dismissOnThisDay(nowMs: Long = System.currentTimeMillis()) = coreCall("dismissOnThisDay") { rust.dismissOnThisDay(nowMs) }

/** 늘 가던 날이 7일 안에 돌아오는 곳 하나 (없으면 null, 고르기 · 문장은 Rust). */
suspend fun EodiCore.routineNudge(nowMs: Long = System.currentTimeMillis()): RoutineNudge? = coreCall("routineNudge") {
    rust.routineNudge(nowMs)?.toModel()
}

/** 이번에 돌아오는 날 것은 닫는다 (다음에 돌아오는 날 다시 나온다). */
suspend fun EodiCore.dismissRoutineNudge(nudge: RoutineNudge) = coreCall("dismissRoutineNudge") { rust.dismissRoutineNudge(nudge.key, nudge.day) }

/** 오늘 "N년 전 오늘"을 읽었다. */
/** 첫 화면 기억 지도 소개 카드를 띄울까 (기록 30일 이상 · 아직 안 닫음, Rust가 정한다). */
suspend fun EodiCore.memoryMapIntro(nowMs: Long = System.currentTimeMillis()): Boolean = coreCall("memoryMapIntro") { rust.memoryMapIntro(nowMs) }

/** 기억 지도 소개 카드를 닫는다 (다시 나오지 않는다). */
suspend fun EodiCore.dismissMemoryMapIntro() = coreCall("dismissMemoryMapIntro") { rust.dismissMemoryMapIntro() }

suspend fun EodiCore.markOnThisDaySpoken(nowMs: Long = System.currentTimeMillis()) = coreCall("markOnThisDaySpoken") { rust.markOnThisDaySpoken(nowMs) }

/** 위치 묶음 근처의 회상 하나 (횟수 제한 포함, 세지는 않는다). */
suspend fun EodiCore.recallNear(points: List<LatLon>, nowMs: Long = System.currentTimeMillis()): Recall? = coreCall("recallNear") {
    rust.recallNear(points.map { RGeoPoint(it.lat, it.lon) }, nowMs)?.toModel()
}

/** 회상을 보여 줬다 (횟수에 센다). */
suspend fun EodiCore.markRecallShown(key: String, nowMs: Long = System.currentTimeMillis()) = coreCall("markRecallShown") { rust.markRecallShown(key, nowMs) }

suspend fun EodiCore.monthRecap(year: Int, month: Int, nowMs: Long = System.currentTimeMillis()): MonthRecap = coreCall("monthRecap") {
    rust.monthRecap(year, month.toUInt(), nowMs).toModel()
}

suspend fun EodiCore.yearRecap(year: Int, nowMs: Long = System.currentTimeMillis()): YearRecap = coreCall("yearRecap") {
    rust.yearRecap(year, nowMs).toModel()
}

// ---- 옮겨 담기 ----

private fun REpisodeKind.recapKind() = when (this) {
    REpisodeKind.COMMUTE_TO_WORK -> EpisodeKind.COMMUTE_TO_WORK
    REpisodeKind.COMMUTE_HOME -> EpisodeKind.COMMUTE_HOME
    REpisodeKind.ERRAND -> EpisodeKind.ERRAND
    REpisodeKind.OUTING -> EpisodeKind.OUTING
    REpisodeKind.TRIP -> EpisodeKind.TRIP
}

private fun ROnThisDay.toModel() = OnThisDay(
    yearsAgo.toInt(), day, title, summary, episodeId, kind.recapKind(), label, region, distanceM,
    stops.map { it.toModel() }, photos.map { it.toModel() }, line, dismissed, spoken, replayable,
)

private fun RRoutineNudge.toModel() = RoutineNudge(key, place.toModel(), pattern, day, whenText, detail, line)

private fun RRecall.toModel() = Recall(key, place.toModel(), distanceM, day, dayTitle, episodeId, yearsAgo.toInt(), whenText, line)

private fun RRecapPlace.toModel() = RecapPlace(place.toModel(), days.toInt())
private fun RRecapRegion.toModel() = RecapRegion(region, days.toInt())
private fun RRecapDay.toModel() = RecapDay(day, spanDays.toInt(), title, episodeId, kind.recapKind(), region, distanceM, photo?.toModel())
private fun RRecapTotals.toModel() = RecapTotals(distanceM, driveMillis, driveDays.toInt(), outings.toInt(), trips.toInt(), newPlaces.toInt())

private fun RMonthRecap.toModel() = MonthRecap(
    year, month.toInt(), inProgress, recordDays.toInt(), totals.toModel(), topPlaces.map { it.toModel() }, regions.map { it.toModel() },
    bestDay?.toModel(), vsPrevDistanceM, vsPrevDriveMillis, vsPrevOutings, outingCount.toInt(), prevOutingCount.toInt(),
    hasPrevRecords, hasPrev, hasNext,
)

private fun RYearRecap.toModel() = YearRecap(
    year, inProgress, periodLabel, recordDays.toInt(), totals.toModel(), topPlaces.map { it.toModel() }, regions.map { it.toModel() },
    seasons.map { SeasonRecap(it.name, it.distanceM, it.outings.toInt(), it.bestDay?.toModel()) },
    longestTrip?.toModel(), bestDay?.toModel(), monthDistanceM, hasPrev, hasNext,
)
