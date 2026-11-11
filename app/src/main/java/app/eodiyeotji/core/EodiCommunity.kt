package app.eodiyeotji.core

import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.rust.BoardComment as RBoardComment
import app.eodiyeotji.core.rust.BoardPost as RBoardPost
import app.eodiyeotji.core.rust.CommunitySort as RCommunitySort
import app.eodiyeotji.core.rust.CoursePost as RCoursePost
import app.eodiyeotji.core.rust.DayMap as RDayMap
import app.eodiyeotji.core.rust.EpisodeKind as REpisodeKind
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint
import app.eodiyeotji.core.rust.NearFilter as RNearFilter
import app.eodiyeotji.core.rust.PlaceReview as RPlaceReview
import app.eodiyeotji.core.rust.SharePreview as RSharePreview
import app.eodiyeotji.core.rust.StopFact as RStopFact
import app.eodiyeotji.core.rust.parseOpenMeteoWeather
import app.eodiyeotji.core.rust.reviewKeywordLimit as rustReviewKeywordLimit
import app.eodiyeotji.core.rust.reviewKeywordOptions as rustReviewKeywordOptions
import app.eodiyeotji.place.PlatformHttp
import app.eodiyeotji.place.blockingWithTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/*
 * 커뮤니티(게시판 · 여행 리뷰 · 여행 코스 후기) 화면용 입구 (core-rs/src/community.rs). 드라이브 코스(경치 길)는 EodiDriveRoutes.kt.
 * 정렬·거르기·하트 집계·공유할 곳 거르기·계절·날씨 해석은 Rust가 하고, 여기서는 옮겨 담기만 한다.
 * 날씨를 받는 네트워크 호출만 Kotlin이 한다 (주소는 Rust가 만들고, 응답 해석도 Rust).
 */

/** 다녀온 날 · 계절 · 그날 날씨 ("2025-11-06", "가을 · 단풍철", "맑음 · 14°C"). */
data class Visit(val day: String, val season: String, val weather: String?)

/** 여행 코스 후기(들른 곳을 차례로 이은 코스) + 다녀온 날 정보 + 들른 곳의 제휴 쿠폰. */
data class DriveCourse(val post: CoursePost, val visit: Visit, val offers: CourseOffers = CourseOffers.NONE)

/** 여행 리뷰 + 들른 날 정보 + 그 장소의 제휴 쿠폰. */
data class TravelReview(val review: PlaceReview, val visit: Visit, val offers: List<PartnerOffer> = emptyList())

/** 여행 코스 후기의 들른 곳 하나의 리뷰 요약: 평균 별점 · 리뷰 수 · 대표 리뷰([byAuthor] = 코스 글쓴이가 쓴 것). */
data class StopReviews(val avgRating: Float, val reviewCount: Int, val pick: TravelReview?, val byAuthor: Boolean)

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

// ---- 여행 코스 후기 ----

suspend fun EodiCore.driveCourses(sort: CommunitySort, near: LatLon? = null, radiusKm: Double = 30.0, limit: Int = 100): List<DriveCourse> =
    coreCall("driveCourses") { rust.communityCourses(sort.toR(), near.toNear(radiusKm), limit.toUInt()).map { it.toDrive() }.withOffers() }

/** 코스 지도 (들른 곳 핀 + 곳 사이 길, 하루 지도와 같은 GeoJSON 모양). 받아 둔 도로 경로가 있으면 길은 도로를 따른다. */
suspend fun EodiCore.courseMap(postId: String): DayMapData = coreCall("courseMap") { rust.courseMap(postId).toData() }

/**
 * 코스 곳 사이 도로 경로 받기 (카카오모빌리티 길찾기, 폰 길 안내와 같은 키·주소). 받으면 Rust가 줄여 기기에 두고
 * 도로를 따른 새 지도를 돌려준다. 이미 받아 뒀거나, 키가 없거나, 하나라도 못 받으면 null (지도는 직선 그대로).
 * 요청들은 동시에 보내고 [ROAD_WAIT_MS]까지만 기다린다.
 */
suspend fun EodiCore.courseRoadMap(postId: String): DayMapData? {
    val key = BuildConfig.KAKAO_REST_API_KEY
    if (key.isBlank()) return null
    val urls = coreCall("courseRoadRequests") { rust.courseRoadRequests(postId) }
    if (urls.isEmpty()) return null
    val headers = mapOf("Authorization" to "KakaoAK $key")
    val bodies = coroutineScope {
        urls.map { u ->
            async {
                blockingWithTimeout(ROAD_WAIT_MS) {
                    runCatching { PlatformHttp.get(u, headers) }.onFailure { Log.w(ROAD_TAG, "코스 길 못 받음: ${it.message}") }.getOrNull()
                }
            }
        }.awaitAll()
    }
    if (bodies.any { it == null }) return null
    return coreCall("saveCourseRoad") { rust.saveCourseRoad(postId, bodies.map { it.orEmpty() }).toData() }
}

private fun RDayMap.toData() = DayMapData(
    day = day,
    geojson = geojson,
    bounds = bounds?.let { MapBoundsLatLon(it.minLat, it.minLon, it.maxLat, it.maxLon) },
    entryBounds = entries.associate { it.key to MapBoundsLatLon(it.bounds.minLat, it.bounds.minLon, it.bounds.maxLat, it.bounds.maxLon) },
)

/** 내비 구간 (경유지 ≤ 2 + 목적지). */
suspend fun EodiCore.courseLegs(postId: String): List<CourseLeg> = coreCall("courseLegs") {
    rust.courseLegs(postId).map { l -> CourseLeg(l.waypoints.map { it.toModel() }, l.destination.toModel()) }
}

/** 차가 마지막으로 있던 곳 ("내 근처" 기준). */
suspend fun EodiCore.lastKnownPosition(): LatLon? = coreCall("lastKnownPosition") { rust.lastKnownPosition()?.let { LatLon(it.lat, it.lon) } }

// ---- 내 기록에서 올리기 ----

suspend fun EodiCore.shareCandidates(limit: Int = 30): List<SharePreview> =
    coreCall("shareCandidates") { rust.shareCandidates(limit.toUInt()).map { it.toModel() } }

/** 그날 코스로 올릴 수 있는 나들이·여행 (타임라인 하루 상세의 "커뮤니티에 올리기"). */
suspend fun EodiCore.shareCandidatesOn(day: String): List<SharePreview> =
    coreCall("shareCandidatesOn") { rust.shareCandidatesOn(day).map { it.toModel() } }

suspend fun EodiCore.sharePreview(episodeId: Long): SharePreview = coreCall("sharePreview") { rust.sharePreview(episodeId).toModel() }

/** 코스 올리기. [excludedArriveMs] = 미리보기에서 체크를 푼 곳의 도착 시각. 날씨는 [weatherFor]로 미리 받아 넘긴다. */
suspend fun EodiCore.shareCourseWith(episodeId: Long, title: String, body: String, excludedArriveMs: List<Long>, weather: String?): DriveCourse =
    coreCall("shareCourseWith") {
        rust.shareCourseWith(episodeId, title, body, excludedArriveMs, weather, System.currentTimeMillis()).toDrive()
    }

/** 미리보기의 그날 날씨 (Open-Meteo, 짧게 기다리고 못 받으면 null — 날씨 없이 올린다). */
suspend fun EodiCore.weatherFor(preview: SharePreview): String? = fetchWeather(preview.weatherUrl, preview.day)

/** 드라이브 코스로 올릴 주행의 그날 날씨 (못 받으면 null). */
suspend fun EodiCore.weatherFor(trip: DriveTripCandidate): String? = fetchWeather(trip.weatherUrl, trip.day)

// ---- 여행 리뷰 ----

suspend fun EodiCore.travelReviews(sort: CommunitySort, near: LatLon? = null, radiusKm: Double = 30.0, limit: Int = 100): List<TravelReview> =
    coreCall("travelReviews") { rust.placeReviews(sort.toR(), near.toNear(radiusKm), limit.toUInt()).map { it.toTravel() }.withOffers() }

/** 여행 코스 후기의 들른 곳마다 리뷰 요약 (들른 곳 순서대로, 고르기는 Rust `course_stop_reviews`). */
suspend fun EodiCore.courseStopReviews(postId: String): List<StopReviews> = coreCall("courseStopReviews") {
    rust.courseStopReviews(postId).map { StopReviews(it.avgRating, it.reviewCount.toInt(), it.pick?.toTravel(), it.byAuthor) }
}

suspend fun EodiCore.reviewsOfPlace(place: StopFact, sort: CommunitySort, limit: Int = 50): List<TravelReview> =
    coreCall("reviewsOfPlace") { rust.reviewsOfPlace(place.toR(), sort.toR(), limit.toUInt()).map { it.toTravel() }.withOffers() }

/** 리뷰 쓸 수 있는 곳: 최근 실제로 머문 곳 (이름 있는 곳, 최신순). */
suspend fun EodiCore.reviewCandidates(limit: Int = 30): List<StopFact> =
    coreCall("reviewCandidates") { rust.reviewCandidates(limit.toUInt()).map { it.toModel() } }

/** 들른 날 날씨 (못 받으면 null). */
suspend fun EodiCore.visitWeather(place: StopFact): String? {
    val t = if (place.arriveMs > 0) place.arriveMs else System.currentTimeMillis()
    val req = coreCall("visitWeather") { rust.visitWeatherRequest(place.lat, place.lon, t) }
    return fetchWeather(req.url, req.day)
}

/**
 * 리뷰 쓰기. [keywords]는 고른 키워드 (정해진 것만 · 5개까지 Rust가 거른다), [withWhom]은 누구와 (안 고르면 null).
 * 다녀온 때 · 머문 시간 · 몇 번째 방문은 Rust가 [place](내 기록의 실제 방문)에서 채운다.
 */
suspend fun EodiCore.writePlaceReview(
    place: StopFact,
    rating: Int,
    text: String,
    keywords: List<String>,
    withWhom: WithWhom?,
    weather: String?,
): TravelReview = coreCall("writePlaceReview") {
    rust.writePlaceReview(
        place.toR(), rating.coerceIn(1, 5).toUByte(), text, keywords, withWhom?.toR(), weather, System.currentTimeMillis(),
    ).toTravel()
}

/** 리뷰 쓰기에서 고를 수 있는 키워드(정해진 순서)와 한 리뷰에 고를 수 있는 수. */
data class ReviewKeywordOptions(val keywords: List<String>, val limit: Int)

suspend fun EodiCore.reviewKeywordOptions(): ReviewKeywordOptions = coreCall("reviewKeywordOptions") {
    ReviewKeywordOptions(rustReviewKeywordOptions(), rustReviewKeywordLimit().toInt())
}

// ---- 게시판 ----

suspend fun EodiCore.boardPosts(sort: CommunitySort, limit: Int = 100): List<BoardPost> =
    coreCall("boardPosts") { rust.boardPosts(sort.toR(), limit.toUInt()).map { it.toModel() } }

suspend fun EodiCore.boardPost(id: String): BoardPost? = coreCall("boardPost") { rust.boardPost(id)?.toModel() }

suspend fun EodiCore.boardComments(postId: String): List<BoardComment> =
    coreCall("boardComments") { rust.boardComments(postId).map { it.toModel() } }

suspend fun EodiCore.addBoardPost(title: String, body: String, tags: List<String>): BoardPost =
    coreCall("addBoardPost") { rust.addBoardPost(title, body, tags, System.currentTimeMillis()).toModel() }

suspend fun EodiCore.addBoardComment(postId: String, body: String): BoardComment =
    coreCall("addBoardComment") { rust.addBoardComment(postId, body, System.currentTimeMillis()).toModel() }

suspend fun EodiCore.toggleBoardPostHeart(id: String): Int = coreCall("toggleBoardPostHeart") { rust.toggleBoardPostHeart(id).toInt() }

suspend fun EodiCore.toggleBoardCommentHeart(id: String): Int = coreCall("toggleBoardCommentHeart") { rust.toggleBoardCommentHeart(id).toInt() }

// ---- 내부 ----

private const val ROAD_TAG = "EodiCourseRoad"
private const val ROAD_WAIT_MS = 5_000L
private const val WEATHER_TAG = "EodiWeather"
private const val WEATHER_WAIT_MS = 4_000L

/** Open-Meteo 일별 날씨 하나 받기. 주소가 비었거나, 실패하거나, [WEATHER_WAIT_MS]를 넘기면 null. */
private suspend fun fetchWeather(url: String, day: String): String? {
    if (url.isBlank()) return null
    // 막는 HTTP를 떼어 놓고 기다림만 끊는다 (오프라인 DNS 대기까지 기다리지 않게)
    return blockingWithTimeout(WEATHER_WAIT_MS) {
        runCatching { parseOpenMeteoWeather(PlatformHttp.get(url, emptyMap()), day) }
            .onFailure { Log.w(WEATHER_TAG, "날씨 못 받음: ${it.message}") }
            .getOrNull()
    }
}

private fun CommunitySort.toR() = when (this) {
    CommunitySort.POPULAR -> RCommunitySort.POPULAR
    CommunitySort.RECENT -> RCommunitySort.RECENT
}

private fun LatLon?.toNear(radiusKm: Double) = this?.let { RNearFilter(RGeoPoint(it.lat, it.lon), radiusKm) }

private fun StopFact.toR() = RStopFact(name, category, address, arriveMs, leaveMs, lat, lon, firstVisit)

/** 코스 목록에 제휴 쿠폰을 붙인다 (목록 전체를 Rust에 한 번에, offers.rs). */
@JvmName("courseWithOffers")
internal fun List<DriveCourse>.withOffers(): List<DriveCourse> {
    if (isEmpty()) return this
    val offers = runCatching { courseOffersNow(map { it.post.stops }) }.getOrNull() ?: return this
    return mapIndexed { i, c -> c.copy(offers = offers.getOrNull(i) ?: CourseOffers.NONE) }
}

/** 리뷰 목록에 그 장소의 제휴 쿠폰을 붙인다 (목록 전체를 한 번에). */
@JvmName("reviewWithOffers")
internal fun List<TravelReview>.withOffers(): List<TravelReview> {
    if (isEmpty()) return this
    val offers = runCatching { offersForPlacesNow(map { OfferPlace(it.review.place.name, it.review.place.lat, it.review.place.lon) }) }.getOrNull() ?: return this
    return mapIndexed { i, r -> r.copy(offers = offers.getOrNull(i).orEmpty()) }
}

internal fun RCoursePost.toDrive() = DriveCourse(toModel(), Visit(visitedDay, season, weather))

internal fun RPlaceReview.toTravel() = TravelReview(toModel(), Visit(visitedDay, season, weather))

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
