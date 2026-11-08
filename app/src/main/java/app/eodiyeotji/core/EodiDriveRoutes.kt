package app.eodiyeotji.core

import androidx.compose.runtime.Immutable
import app.eodiyeotji.core.rust.BestTime as RBestTime
import app.eodiyeotji.core.rust.DriveRoutePost as RDriveRoutePost
import app.eodiyeotji.core.rust.DriveRouteRecommendation as RDriveRouteRecommendation
import app.eodiyeotji.core.rust.DriveRouteSort as RDriveRouteSort
import app.eodiyeotji.core.rust.DriveTripCandidate as RDriveTripCandidate
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint
import app.eodiyeotji.core.rust.RouteTheme as RRouteTheme
import app.eodiyeotji.core.rust.driveBestTimeOptions

/*
 * 커뮤니티 > 드라이브 코스 = 차에서 즐기는 경치 길 하나 (들를 곳 없음, core-rs/src/community/drive.rs).
 * 순위(인기·최신·길 위 가장 가까운 점까지 거리)·지도 데이터·카메라·미리보기 비행·안내 구간·집 근처 지우기는 Rust가 하고,
 * 여기서는 옮겨 담기만 한다.
 */

/** 언제 가면 가장 좋은지. 3D 미리보기 색([RouteTheme])이 여기서 정해진다 (Rust가 정해 준다). */
enum class BestTime { NIGHT, SUNSET, MORNING, DAY, FOLIAGE, BLOSSOM }

/** 3D 미리보기 색: 야경 → 밤, 노을 → 해 질 녘, 나머지 → 낮. */
enum class RouteTheme { DAY, SUNSET, NIGHT }

enum class DriveRouteSort { POPULAR, RECENT, NEAREST }

/** 가는 길에 보이는 풍경 (지도 글자, 들르는 곳이 아니다). */
data class RouteHighlight(val name: String, val lat: Double, val lon: Double)

/** 드라이브 코스(경치 길) 글 하나. 선은 [driveRouteMap]으로 따로 받는다. */
@Immutable
data class DriveRoute(
    val id: String,
    val title: String,
    val body: String,
    val author: String,
    val mine: Boolean,
    val region: String,
    val startName: String,
    val endName: String,
    val start: LatLon,
    val end: LatLon,
    val bestTime: BestTime,
    val theme: RouteTheme,
    val highlights: List<RouteHighlight>,
    val distanceM: Double,
    val durationMs: Long,
    val createdMs: Long,
    val hearts: Int,
    val heartedByMe: Boolean,
    val visit: Visit,
)

/** 목록 한 줄: 길 + 지금 위치에서 길 위 가장 가까운 점까지 거리 (모르면 null). */
@Immutable
data class DriveRouteItem(val route: DriveRoute, val distanceKm: Double?)

/** 3D 미리보기 지도 데이터 (Rust GeoJSON 그대로: 선·시작·끝·풍경 글자). */
@Immutable
data class DriveRouteMapData(val routeId: String, val geojson: String, val bounds: MapBoundsLatLon)

/** 지도 카메라 (Rust가 길 방향·화면 크기에 맞춰 계산한 값). */
@Immutable
data class RouteCameraSpec(val lat: Double, val lon: Double, val zoom: Double, val bearing: Double, val pitch: Double)

/** 미리보기 비행 장면 (길을 따라 같은 간격). */
@Immutable
data class FlightFrame(val lat: Double, val lon: Double, val bearing: Double, val zoom: Double)

/** 드라이브 코스로 올릴 수 있는 내 주행 하나. */
@Immutable
data class DriveTripCandidate(
    val tripStartMs: Long,
    val day: String,
    val startMs: Long,
    val endMs: Long,
    val fromName: String,
    val toName: String,
    val defaultTitle: String,
    val distanceM: Double,
    val durationMs: Long,
    val trimmed: Boolean,
    val shared: Boolean,
    val weatherUrl: String,
)

/** 3D 미리보기 기울기(도) — Rust `PREVIEW_PITCH`와 같다. */
const val ROUTE_PREVIEW_PITCH = 60.0

suspend fun EodiCore.driveRoutes(sort: DriveRouteSort, from: LatLon?, limit: Int = 100): List<DriveRouteItem> = coreCall("driveRoutes") {
    rust.driveRoutes(sort.toR(), from?.let { RGeoPoint(it.lat, it.lon) }, limit.toUInt()).map { it.toItem() }
}

suspend fun EodiCore.driveRoute(id: String): DriveRoute = coreCall("driveRoute") { rust.driveRoute(id).toModel() }

suspend fun EodiCore.toggleDriveRouteHeart(id: String): Int = coreCall("toggleDriveRouteHeart") { rust.toggleDriveRouteHeart(id).toInt() }

suspend fun EodiCore.driveRouteMap(id: String): DriveRouteMapData = coreCall("driveRouteMap") {
    val m = rust.driveRouteMap(id)
    DriveRouteMapData(m.routeId, m.geojson, MapBoundsLatLon(m.bounds.minLat, m.bounds.minLon, m.bounds.maxLat, m.bounds.maxLon))
}

/** 길 방향을 따라 보는 카메라. 크기는 지도 칸의 논리 픽셀(dp). */
suspend fun EodiCore.driveRouteCamera(id: String, widthDp: Float, heightDp: Float, paddingDp: Float, pitch: Double = ROUTE_PREVIEW_PITCH): RouteCameraSpec =
    coreCall("driveRouteCamera") {
        val c = rust.driveRouteCamera(id, widthDp.toDouble(), heightDp.toDouble(), paddingDp.toDouble(), pitch)
        RouteCameraSpec(c.lat, c.lon, c.zoom, c.bearing, c.pitch)
    }

suspend fun EodiCore.driveRouteFlight(id: String, frames: Int = 0): List<FlightFrame> = coreCall("driveRouteFlight") {
    rust.driveRouteFlight(id, frames.toUInt()).map { FlightFrame(it.lat, it.lon, it.bearing, it.zoom) }
}

/** 안내 구간: ① 길 시작점까지 ② 도착하면 끝점까지 (둘 다 경유지 없음). */
suspend fun EodiCore.driveRouteLegs(id: String): List<CourseLeg> = coreCall("driveRouteLegs") {
    rust.driveRouteLegs(id).map { l -> CourseLeg(l.waypoints.map { it.toModel() }, l.destination.toModel()) }
}

suspend fun EodiCore.driveShareCandidates(days: Int = 30): List<DriveTripCandidate> = coreCall("driveShareCandidates") {
    rust.driveShareCandidates(days.toUInt()).map { it.toModel() }
}

suspend fun EodiCore.shareDriveRoute(tripStartMs: Long, title: String, body: String, bestTime: BestTime, weather: String?): DriveRoute =
    coreCall("shareDriveRoute") {
        rust.shareDriveRoute(tripStartMs, title, body, bestTime.toR(), weather, System.currentTimeMillis()).toModel()
    }

/** 드라이브 코스를 올릴 때 고를 수 있는 "언제" (Rust가 정한 순서). */
suspend fun bestTimeOptions(): List<BestTime> = coreCall("bestTimeOptions") { driveBestTimeOptions().map { it.toModel() } }

// ---- 옮겨 담기 ----

internal fun RDriveRouteRecommendation.toItem() = DriveRouteItem(route.toModel(), distanceKm.takeIf { it >= 0.0 })

internal fun RDriveRoutePost.toModel() = DriveRoute(
    id = id,
    title = title,
    body = body,
    author = author,
    mine = mine,
    region = region,
    startName = startName,
    endName = endName,
    start = LatLon(start.lat, start.lon),
    end = LatLon(end.lat, end.lon),
    bestTime = bestTime.toModel(),
    theme = when (theme) {
        RRouteTheme.DAY -> RouteTheme.DAY
        RRouteTheme.SUNSET -> RouteTheme.SUNSET
        RRouteTheme.NIGHT -> RouteTheme.NIGHT
    },
    highlights = highlights.map { RouteHighlight(it.name, it.lat, it.lon) },
    distanceM = distanceM,
    durationMs = durationMs,
    createdMs = createdMs,
    hearts = hearts.toInt(),
    heartedByMe = heartedByMe,
    visit = Visit(visitedDay, season, weather),
)

private fun RDriveTripCandidate.toModel() =
    DriveTripCandidate(tripStartMs, day, startMs, endMs, fromName, toName, defaultTitle, distanceM, durationMs, trimmed, shared, weatherUrl)

private fun RBestTime.toModel() = when (this) {
    RBestTime.NIGHT -> BestTime.NIGHT
    RBestTime.SUNSET -> BestTime.SUNSET
    RBestTime.MORNING -> BestTime.MORNING
    RBestTime.DAY -> BestTime.DAY
    RBestTime.FOLIAGE -> BestTime.FOLIAGE
    RBestTime.BLOSSOM -> BestTime.BLOSSOM
}

private fun BestTime.toR() = when (this) {
    BestTime.NIGHT -> RBestTime.NIGHT
    BestTime.SUNSET -> RBestTime.SUNSET
    BestTime.MORNING -> RBestTime.MORNING
    BestTime.DAY -> RBestTime.DAY
    BestTime.FOLIAGE -> RBestTime.FOLIAGE
    BestTime.BLOSSOM -> RBestTime.BLOSSOM
}

private fun DriveRouteSort.toR() = when (this) {
    DriveRouteSort.POPULAR -> RDriveRouteSort.POPULAR
    DriveRouteSort.RECENT -> RDriveRouteSort.RECENT
    DriveRouteSort.NEAREST -> RDriveRouteSort.NEAREST
}
