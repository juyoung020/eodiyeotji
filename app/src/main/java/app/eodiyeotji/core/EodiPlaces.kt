package app.eodiyeotji.core

import app.eodiyeotji.core.rust.AnchorRole as RAnchorRole
import app.eodiyeotji.core.rust.AnchorSource as RAnchorSource
import app.eodiyeotji.core.rust.AnchorInfo as RAnchorInfo
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint
import app.eodiyeotji.core.rust.HiddenKind as RHiddenKind
import app.eodiyeotji.core.rust.PlaceCandidate as RPlaceCandidate
import app.eodiyeotji.core.rust.PlaceCard as RPlaceCard
import app.eodiyeotji.core.rust.PlaceSpot as RPlaceSpot
import app.eodiyeotji.core.rust.SpotKind as RSpotKind
import app.eodiyeotji.core.rust.rankPlaceCandidates

/*
 * 기록 고치기 · 장소 저장 입구 (core-rs/src/edits.rs, saved.rs). 무엇을 숨기고 지우고 다시 계산할지,
 * 자주 가는 곳 순위는 모두 Rust가 정한다. 여기서는 옮겨 담고, 바뀌면 화면들이 다시 읽게 알리기만 한다.
 */

/** 화면에서 고른 장소 자리. 주행 출발 · 도착은 시각([tMs] = start/end), 머문 곳은 도착 시각, [POINT]는 좌표만. */
data class PlaceSpot(val kind: Kind, val tMs: Long, val lat: Double, val lon: Double) {
    enum class Kind { TRIP_START, TRIP_END, STAY, POINT }

    companion object {
        fun tripStart(t: TripSummary) = PlaceSpot(Kind.TRIP_START, t.startMs, t.start.lat, t.start.lon)
        fun tripEnd(t: TripSummary) = PlaceSpot(Kind.TRIP_END, t.endMs, t.end.lat, t.end.lon)
        fun stay(s: StaySummary) = PlaceSpot(Kind.STAY, s.arriveMs, s.center.lat, s.center.lon)
        fun point(lat: Double, lon: Double) = PlaceSpot(Kind.POINT, 0, lat, lon)
    }
}

enum class AnchorRole { HOME, OFFICE }
enum class AnchorSource { USER, NAVI, GUESSED }

/** 장소 카드. [label]은 이름을 아직 모르면 null, [role]은 지금 쓰는 집 · 회사면 그것. */
data class PlaceCard(
    val id: Long,
    val label: PlaceLabel?,
    val lat: Double,
    val lon: Double,
    val saved: Boolean,
    val visitDays: Int,
    val lastVisitDay: String?,
    val role: AnchorRole?,
)

/** 지금 쓰는 집 또는 회사 ([name]은 근처 장소 이름, 모르면 빈 문자열). */
data class AnchorInfo(val role: AnchorRole, val lat: Double, val lon: Double, val name: String, val source: AnchorSource)

data class HomeOffice(val home: AnchorInfo?, val office: AnchorInfo?)

enum class HiddenKind { TRIP, STAY, DAY }

/** "숨긴 기록" 한 줄. [label]은 숨길 때의 이름 (모르면 빈 문자열). */
data class HiddenRecord(
    val id: Long,
    val kind: HiddenKind,
    val day: String,
    val fromMs: Long,
    val toMs: Long,
    val label: String,
    val hiddenMs: Long,
)

/** 이름 후보 (카카오 근처 검색 한 건과 거리). */
data class PlaceCandidate(val label: PlaceLabel, val distanceM: Double)

suspend fun EodiCore.placeCard(spot: PlaceSpot): PlaceCard? = coreCall("placeCard") { rust.placeCard(spot.toR())?.toModel() }

suspend fun EodiCore.setPlaceSaved(id: Long, saved: Boolean) = edit { rust.setPlaceSaved(id, saved, System.currentTimeMillis()) }

suspend fun EodiCore.savedPlaces(): List<PlaceCard> = coreCall("savedPlaces") { rust.savedPlaces().map { it.toModel() } }

suspend fun EodiCore.frequentPlaces(limit: Int = 30): List<PlaceCard> =
    coreCall("frequentPlaces") { rust.frequentPlaces(limit.toUInt(), System.currentTimeMillis()).map { it.toModel() } }

/** 좌표마다 저장한 곳인지 (검색 결과의 ♥). */
suspend fun EodiCore.placesSavedAt(points: List<LatLon>): List<Boolean> =
    coreCall("placesSavedAt") { rust.placesSavedAt(points.map { RGeoPoint(it.lat, it.lon) }) }

/** 이름 고치기 (사용자 이름이 모든 출처보다 우선, 그 장소의 모든 방문에 보인다). 분류 · 주소가 비면 원래 값. */
suspend fun EodiCore.renamePlace(id: Long, name: String, category: String = "", address: String = "") =
    edit { rust.renamePlace(id, name, category, address) }

suspend fun EodiCore.setMyAnchor(role: AnchorRole, at: LatLon?) =
    edit { rust.setMyAnchor(role.toR(), at?.let { RGeoPoint(it.lat, it.lon) }) }

suspend fun EodiCore.homeOffice(): HomeOffice = coreCall("homeOffice") {
    val h = rust.homeOffice()
    HomeOffice(h.home?.toModel(), h.office?.toModel())
}

suspend fun EodiCore.hideTrip(startMs: Long) = edit { rust.hideTrip(startMs, System.currentTimeMillis()) }
/** 주행을 지하철 이동으로 바꾸거나([transit] = true) 차 주행으로 되돌린다. */
suspend fun EodiCore.setTripTransit(startMs: Long, transit: Boolean) = edit { rust.setTripTransit(startMs, transit, System.currentTimeMillis()) }

/** 끝난 주행 중 지하철 이동을 가려낸다 (폰 판, Rust `transit`). 바뀐 것이 있을 때만 화면에 알린다. */
suspend fun EodiCore.classifyTransit(): Int = coreCall("classifyTransit") {
    rust.classifyTransit(System.currentTimeMillis()).toInt().also { if (it > 0) notifyChanged() }
}

suspend fun EodiCore.hideStay(arriveMs: Long) = edit { rust.hideStay(arriveMs, System.currentTimeMillis()) }
suspend fun EodiCore.hideDay(day: String) = edit { rust.hideDay(day, System.currentTimeMillis()) }
suspend fun EodiCore.deleteTrip(startMs: Long) = edit { rust.deleteTrip(startMs) }
suspend fun EodiCore.deleteStay(arriveMs: Long) = edit { rust.deleteStay(arriveMs, System.currentTimeMillis()) }
/** 하루 제목을 직접 고친다. 빈 글이면 사용자 제목을 지워 AI · 규칙 제목으로 돌아간다. */
suspend fun EodiCore.setDayTitle(day: String, title: String) = edit { rust.setDayTitle(day, title.takeIf { it.isNotBlank() }) }
/** "이 요약이 이상해요": 그날 AI 요약을 숨기고 규칙 제목 · 요약으로 돌아간다 (기기 안에만 남는다). */
suspend fun EodiCore.hideDayStory(day: String) = edit { rust.hideDayStory(day, System.currentTimeMillis()) }
suspend fun EodiCore.deleteDay(day: String) = edit { rust.deleteDay(day, System.currentTimeMillis()) }
suspend fun EodiCore.restoreHidden(id: Long) = edit { rust.restoreHidden(id) }

suspend fun EodiCore.hiddenRecords(): List<HiddenRecord> = coreCall("hiddenRecords") {
    rust.hiddenRecords().map {
        HiddenRecord(
            it.id,
            when (it.kind) {
                RHiddenKind.TRIP -> HiddenKind.TRIP
                RHiddenKind.STAY -> HiddenKind.STAY
                RHiddenKind.DAY -> HiddenKind.DAY
            },
            it.day, it.fromMs, it.toMs, it.label, it.hiddenMs,
        )
    }
}

/** 이름 후보를 거리순으로 (같은 이름은 하나). 순위는 Rust. */
suspend fun rankCandidates(cands: List<PlaceCandidate>, limit: Int): List<PlaceLabel> = coreCall("rankCandidates") {
    rankPlaceCandidates(cands.map { RPlaceCandidate(it.label.toRust(), it.distanceM) }, limit.toUInt()).map { it.toModel() }
}

/** 기록을 바꾸는 호출: IO 스레드에서 하고, 타임라인 · 검색이 다시 읽게 알린다. */
private suspend fun EodiCore.edit(block: () -> Unit) = coreCall("edit") {
    block()
    notifyChanged()
}

private fun PlaceSpot.toR() = RPlaceSpot(
    when (kind) {
        PlaceSpot.Kind.TRIP_START -> RSpotKind.TRIP_START
        PlaceSpot.Kind.TRIP_END -> RSpotKind.TRIP_END
        PlaceSpot.Kind.STAY -> RSpotKind.STAY
        PlaceSpot.Kind.POINT -> RSpotKind.POINT
    },
    tMs, lat, lon,
)

private fun AnchorRole.toR() = when (this) {
    AnchorRole.HOME -> RAnchorRole.HOME
    AnchorRole.OFFICE -> RAnchorRole.OFFICE
}

private fun RAnchorRole.toModel() = when (this) {
    RAnchorRole.HOME -> AnchorRole.HOME
    RAnchorRole.OFFICE -> AnchorRole.OFFICE
}

private fun RPlaceCard.toModel() =
    PlaceCard(id, label?.toModel(), lat, lon, saved, visitDays.toInt(), lastVisitDay, role?.toModel())

private fun RAnchorInfo.toModel() = AnchorInfo(
    role.toModel(), lat, lon, name,
    when (source) {
        RAnchorSource.USER -> AnchorSource.USER
        RAnchorSource.NAVI -> AnchorSource.NAVI
        RAnchorSource.GUESSED -> AnchorSource.GUESSED
    },
)
