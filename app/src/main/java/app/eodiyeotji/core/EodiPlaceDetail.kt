package app.eodiyeotji.core

import app.eodiyeotji.core.rust.StopFact as RStopFact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 장소 상세 창 입구 (core-rs/src/place_detail.rs). 같은 곳 고르기 · 리뷰 정렬 · 별점 평균 · 내 방문 · 쿠폰은 모두 Rust가
 * 한 번에 계산해 준다 (JNI 한 번). 여기서는 옮겨 담기만 한다.
 */

/**
 * 장소 상세 창 한 장.
 * [ratingHalves] = 평균 별점을 반 별 단위로 반올림한 값 × 2 (0~10, 별 그리기용).
 * [placeId] = 저장(♥)에 쓸 내 장소 번호 (내 기록에 없는 곳이면 null → 저장할 수 없다).
 * [lastVisit] = 리뷰 쓰기에 넘길 내 마지막 방문 (다녀온 적이 없으면 null).
 */
data class PlaceDetail(
    val place: StopFact,
    val avgRating: Float,
    val ratingHalves: Int,
    val reviewCount: Int,
    val hearts: Int,
    val reviews: List<TravelReview>,
    val visitDays: Int,
    val lastVisitDay: String?,
    val lastVisit: StopFact?,
    val placeId: Long?,
    val saved: Boolean,
    val offers: List<PartnerOffer>,
)

suspend fun EodiCore.placeDetail(place: StopFact): PlaceDetail = withContext(Dispatchers.IO) {
    val d = rust.placeDetail(RStopFact(place.name, place.category, place.address, place.arriveMs, place.leaveMs, place.lat, place.lon, place.firstVisit))
    PlaceDetail(
        place = d.place.toModel(),
        avgRating = d.avgRating,
        ratingHalves = d.ratingHalves.toInt(),
        reviewCount = d.reviewCount.toInt(),
        hearts = d.hearts.toInt(),
        reviews = d.reviews.map { it.toTravel() },
        visitDays = d.visitDays.toInt(),
        lastVisitDay = d.lastVisitDay,
        lastVisit = d.lastVisit?.toModel(),
        placeId = d.placeId,
        saved = d.saved,
        offers = d.offers.map { it.toModel() },
    )
}
