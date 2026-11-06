package app.eodiyeotji.core

import android.util.Log
import app.eodiyeotji.core.rust.BoardComment as RBoardComment
import app.eodiyeotji.core.rust.BoardPost as RBoardPost
import app.eodiyeotji.core.rust.CommunitySort as RCommunitySort
import app.eodiyeotji.core.rust.CoursePost as RCoursePost
import app.eodiyeotji.core.rust.EpisodeKind as REpisodeKind
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint
import app.eodiyeotji.core.rust.NearFilter as RNearFilter
import app.eodiyeotji.core.rust.PlaceReview as RPlaceReview
import app.eodiyeotji.core.rust.SharePreview as RSharePreview
import app.eodiyeotji.core.rust.StopFact as RStopFact
import app.eodiyeotji.core.rust.parseOpenMeteoWeather
import app.eodiyeotji.place.PlatformHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/*
 * 커뮤니티(게시판 · 드라이브 코스 · 여행 리뷰) 화면용 입구 (core-rs/src/community.rs).
 * 정렬·거르기·하트 집계·공유할 곳 거르기·계절·날씨 해석은 Rust가 하고, 여기서는 옮겨 담기만 한다.
 * 날씨를 받는 네트워크 호출만 Kotlin이 한다 (주소는 Rust가 만들고, 응답 해석도 Rust).
 */

/** 다녀온 날 · 계절 · 그날 날씨 ("2025-11-06", "가을 · 단풍철", "맑음 · 14°C"). */
data class Visit(val day: String, val season: String, val weather: String?)

/** 드라이브 코스 + 다녀온 날 정보. */
data class DriveCourse(val post: CoursePost, val visit: Visit)

/** 여행 리뷰 + 들른 날 정보. */
data class TravelReview(val review: PlaceReview, val visit: Visit)

/** 내 기록(나들이·여행)을 코스로 올리기 전 미리보기. [hiddenCount] = 집·회사 근처라 자동으로 가린 곳 수. */
data class SharePreview(
    val episodeId: Long,
    val day: String,
    val season: String,
    val title: String,
    val region: String,
    val kind: EpisodeKind,
    val stops: List<StopFact>,
    val hiddenCount: Int,
    val unnamedCount: Int,
    val distanceM: Double,
    val shared: Boolean,
    val weatherUrl: String,
)

data class BoardPost(
    val id: String, val title: String, val body: String, val author: String, val mine: Boolean, val region: String?,
    val tags: List<String>, val createdMs: Long, val hearts: Int, val heartedByMe: Boolean, val commentCount: Int,
)

data class BoardComment(
    val id: String, val postId: String, val author: String, val mine: Boolean, val body: String, val createdMs: Long,
    val hearts: Int, val heartedByMe: Boolean,
)

// ---- 드라이브 코스 ----

suspend fun EodiCore.driveCourses(sort: CommunitySort, near: LatLon? = null, radiusKm: Double = 30.0, limit: Int = 100): List<DriveCourse> =
    withContext(Dispatchers.IO) { rust.communityCourses(sort.toR(), near.toNear(radiusKm), limit.toUInt()).map { it.toDrive() } }

/** 코스 지도 (들른 곳 핀, 하루 지도와 같은 GeoJSON 모양). */
suspend fun EodiCore.courseMap(postId: String): DayMapData = withContext(Dispatchers.IO) {
    val m = rust.courseMap(postId)
    DayMapData(
        day = m.day,
        geojson = m.geojson,
        bounds = m.bounds?.let { MapBoundsLatLon(it.minLat, it.minLon, it.maxLat, it.maxLon) },
        entryBounds = m.entries.associate { it.key to MapBoundsLatLon(it.bounds.minLat, it.bounds.minLon, it.bounds.maxLat, it.bounds.maxLon) },
    )
}

/** 내비 구간 (경유지 ≤ 2 + 목적지). */
suspend fun EodiCore.courseLegs(postId: String): List<CourseLeg> = withContext(Dispatchers.IO) {
    rust.courseLegs(postId).map { l -> CourseLeg(l.waypoints.map { it.toModel() }, l.destination.toModel()) }
}

/** 차가 마지막으로 있던 곳 ("내 근처" 기준). */
suspend fun EodiCore.lastKnownPosition(): LatLon? = withContext(Dispatchers.IO) { rust.lastKnownPosition()?.let { LatLon(it.lat, it.lon) } }

// ---- 내 기록에서 올리기 ----

suspend fun EodiCore.shareCandidates(limit: Int = 30): List<SharePreview> =
    withContext(Dispatchers.IO) { rust.shareCandidates(limit.toUInt()).map { it.toModel() } }

/** 그날 코스로 올릴 수 있는 나들이·여행 (타임라인 하루 상세의 "커뮤니티에 올리기"). */
suspend fun EodiCore.shareCandidatesOn(day: String): List<SharePreview> =
    withContext(Dispatchers.IO) { rust.shareCandidatesOn(day).map { it.toModel() } }

suspend fun EodiCore.sharePreview(episodeId: Long): SharePreview = withContext(Dispatchers.IO) { rust.sharePreview(episodeId).toModel() }

/** 코스 올리기. [excludedArriveMs] = 미리보기에서 체크를 푼 곳의 도착 시각. 날씨는 [weatherFor]로 미리 받아 넘긴다. */
suspend fun EodiCore.shareCourseWith(episodeId: Long, title: String, body: String, excludedArriveMs: List<Long>, weather: String?): DriveCourse =
    withContext(Dispatchers.IO) {
        rust.shareCourseWith(episodeId, title, body, excludedArriveMs, weather, System.currentTimeMillis()).toDrive()
    }

/** 미리보기의 그날 날씨 (Open-Meteo, 짧게 기다리고 못 받으면 null — 날씨 없이 올린다). */
suspend fun EodiCore.weatherFor(preview: SharePreview): String? = fetchWeather(preview.weatherUrl, preview.day)

// ---- 여행 리뷰 ----

suspend fun EodiCore.travelReviews(sort: CommunitySort, near: LatLon? = null, radiusKm: Double = 30.0, limit: Int = 100): List<TravelReview> =
    withContext(Dispatchers.IO) { rust.placeReviews(sort.toR(), near.toNear(radiusKm), limit.toUInt()).map { it.toTravel() } }

suspend fun EodiCore.reviewsOfPlace(place: StopFact, sort: CommunitySort, limit: Int = 50): List<TravelReview> =
    withContext(Dispatchers.IO) { rust.reviewsOfPlace(place.toR(), sort.toR(), limit.toUInt()).map { it.toTravel() } }

/** 리뷰 쓸 수 있는 곳: 최근 실제로 머문 곳 (이름 있는 곳, 최신순). */
suspend fun EodiCore.reviewCandidates(limit: Int = 30): List<StopFact> =
    withContext(Dispatchers.IO) { rust.reviewCandidates(limit.toUInt()).map { it.toModel() } }

/** 들른 날 날씨 (못 받으면 null). */
suspend fun EodiCore.visitWeather(place: StopFact): String? {
    val t = if (place.arriveMs > 0) place.arriveMs else System.currentTimeMillis()
    val req = withContext(Dispatchers.IO) { rust.visitWeatherRequest(place.lat, place.lon, t) }
    return fetchWeather(req.url, req.day)
}

suspend fun EodiCore.addPlaceReviewWith(place: StopFact, rating: Int, text: String, weather: String?): TravelReview =
    withContext(Dispatchers.IO) {
        rust.addPlaceReviewWith(place.toR(), rating.coerceIn(1, 5).toUByte(), text, weather, System.currentTimeMillis()).toTravel()
    }

// ---- 게시판 ----

suspend fun EodiCore.boardPosts(sort: CommunitySort, limit: Int = 100): List<BoardPost> =
    withContext(Dispatchers.IO) { rust.boardPosts(sort.toR(), limit.toUInt()).map { it.toModel() } }

suspend fun EodiCore.boardPost(id: String): BoardPost? = withContext(Dispatchers.IO) { rust.boardPost(id)?.toModel() }

suspend fun EodiCore.boardComments(postId: String): List<BoardComment> =
    withContext(Dispatchers.IO) { rust.boardComments(postId).map { it.toModel() } }

suspend fun EodiCore.addBoardPost(title: String, body: String, tags: List<String>): BoardPost =
    withContext(Dispatchers.IO) { rust.addBoardPost(title, body, tags, System.currentTimeMillis()).toModel() }

suspend fun EodiCore.addBoardComment(postId: String, body: String): BoardComment =
    withContext(Dispatchers.IO) { rust.addBoardComment(postId, body, System.currentTimeMillis()).toModel() }

suspend fun EodiCore.toggleBoardPostHeart(id: String): Int = withContext(Dispatchers.IO) { rust.toggleBoardPostHeart(id).toInt() }

suspend fun EodiCore.toggleBoardCommentHeart(id: String): Int = withContext(Dispatchers.IO) { rust.toggleBoardCommentHeart(id).toInt() }

// ---- 내부 ----

private const val WEATHER_TAG = "EodiWeather"
private const val WEATHER_WAIT_MS = 4_000L

/** Open-Meteo 일별 날씨 하나 받기. 주소가 비었거나, 실패하거나, [WEATHER_WAIT_MS]를 넘기면 null. */
private suspend fun fetchWeather(url: String, day: String): String? {
    if (url.isBlank()) return null
    return withTimeoutOrNull(WEATHER_WAIT_MS) {
        withContext(Dispatchers.IO) {
            runCatching { parseOpenMeteoWeather(PlatformHttp.get(url, emptyMap()), day) }
                .onFailure { Log.w(WEATHER_TAG, "날씨 못 받음: ${it.message}") }
                .getOrNull()
        }
    }
}

private fun CommunitySort.toR() = when (this) {
    CommunitySort.POPULAR -> RCommunitySort.POPULAR
    CommunitySort.RECENT -> RCommunitySort.RECENT
}

private fun LatLon?.toNear(radiusKm: Double) = this?.let { RNearFilter(RGeoPoint(it.lat, it.lon), radiusKm) }

private fun StopFact.toR() = RStopFact(name, category, address, arriveMs, leaveMs, lat, lon, firstVisit)

internal fun RCoursePost.toDrive() = DriveCourse(toModel(), Visit(visitedDay, season, weather))

private fun RPlaceReview.toTravel() = TravelReview(
    PlaceReview(id, place.toModel(), author, mine, rating.toInt(), text, createdMs, hearts.toInt(), heartedByMe),
    Visit(visitedDay, season, weather),
)

private fun REpisodeKind.toKind() = when (this) {
    REpisodeKind.COMMUTE_TO_WORK -> EpisodeKind.COMMUTE_TO_WORK
    REpisodeKind.COMMUTE_HOME -> EpisodeKind.COMMUTE_HOME
    REpisodeKind.ERRAND -> EpisodeKind.ERRAND
    REpisodeKind.OUTING -> EpisodeKind.OUTING
    REpisodeKind.TRIP -> EpisodeKind.TRIP
}

private fun RSharePreview.toModel() = SharePreview(
    episodeId, day, season, title, region, kind.toKind(), stops.map { it.toModel() }, hiddenCount.toInt(), unnamedCount.toInt(),
    distanceM, shared, weatherUrl,
)

internal fun RBoardPost.toModel() =
    BoardPost(id, title, body, author, mine, region, tags, createdMs, hearts.toInt(), heartedByMe, commentCount.toInt())

private fun RBoardComment.toModel() = BoardComment(id, postId, author, mine, body, createdMs, hearts.toInt(), heartedByMe)
