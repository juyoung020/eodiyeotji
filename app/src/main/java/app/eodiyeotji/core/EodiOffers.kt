package app.eodiyeotji.core

import app.eodiyeotji.core.rust.ClaimedCoupon as RClaimedCoupon
import app.eodiyeotji.core.rust.CourseOffers as RCourseOffers
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint
import app.eodiyeotji.core.rust.OfferCategory as ROfferCategory
import app.eodiyeotji.core.rust.OfferPlace as ROfferPlace
import app.eodiyeotji.core.rust.PartnerOffer as RPartnerOffer
import app.eodiyeotji.core.rust.StopFact as RStopFact
import app.eodiyeotji.core.rust.courseOffers as rustCourseOffers
import app.eodiyeotji.core.rust.offersForPlaces as rustOffersForPlaces

/*
 * 제휴 혜택 · 할인 쿠폰 입구 (core-rs/src/offers.rs). 어느 장소 · 코스에 어떤 쿠폰이 붙는지(이름 · 거리 맞추기)는 Rust가 정한다.
 * 쿠폰은 공모전 시연용 예시 데이터다 (실제 제휴 아님, docs/80_사업모델_수익성.md).
 * 화면 한 장의 장소 · 코스를 한 번에 넘긴다 (JNI 한 번).
 */

enum class OfferCategory { RESTAURANT, CAFE, FUEL, REST_AREA, ATTRACTION, MARKET, SHOPPING }

/** 제휴 쿠폰 하나. [badge]는 짧은 배지("10% 할인"), [validText]는 "12월 31일까지". */
data class PartnerOffer(
    val id: String,
    val placeName: String,
    val partnerName: String,
    val badge: String,
    val title: String,
    val conditions: String,
    val validUntil: String,
    val validText: String,
    val category: OfferCategory,
    val categoryLabel: String,
    val lat: Double,
    val lon: Double,
)

/** 쿠폰을 찾을 장소 (좌표를 모르면 null). */
data class OfferPlace(val name: String, val lat: Double? = null, val lon: Double? = null)

/** 코스 하나의 쿠폰. [placeCount] = 쿠폰이 있는 들른 곳 수 ("쿠폰 2곳"). */
data class CourseOffers(val placeCount: Int, val offers: List<PartnerOffer>) {
    companion object {
        val NONE = CourseOffers(0, emptyList())
    }
}

/** 받은 쿠폰. [code]는 매장에서 보여 줄 번호 ("EODI-7K3Q"). */
data class ClaimedCoupon(val offer: PartnerOffer, val code: String, val claimedMs: Long)

/** 장소마다 붙는 쿠폰 (넘긴 순서대로). */
suspend fun EodiCore.offersForPlaces(places: List<OfferPlace>): List<List<PartnerOffer>> =
    if (places.isEmpty()) emptyList() else coreCall("offersForPlaces") { offersForPlacesNow(places) }

/** 코스마다 들른 곳의 쿠폰 (넘긴 순서대로). */
suspend fun EodiCore.courseOffers(courses: List<List<StopFact>>): List<CourseOffers> =
    if (courses.isEmpty()) emptyList() else coreCall("courseOffers") { courseOffersNow(courses) }

/** 쿠폰 받기 (이미 받았으면 처음 받은 것 그대로). */
suspend fun EodiCore.claimOffer(offerId: String): ClaimedCoupon =
    coreCall("claimOffer") { rust.claimOffer(offerId, System.currentTimeMillis()).toModel() }

/** 쿠폰함: 받은 쿠폰 (최근에 받은 것부터). */
suspend fun EodiCore.claimedCoupons(): List<ClaimedCoupon> = coreCall("claimedCoupons") { rust.claimedCoupons().map { it.toModel() } }

/**
 * 이미 UI 스레드 밖(Rust 에이전트 콜백 · Default)에 있을 때 쓰는 묶음 호출. 계산 없이 바로 끝난다 (시연 쿠폰 수십 개).
 */
internal fun courseOffersNow(courses: List<List<StopFact>>): List<CourseOffers> =
    rustCourseOffers(courses.map { s -> s.map { it.toOfferR() } }).map { it.toModel() }

internal fun offersForPlacesNow(places: List<OfferPlace>): List<List<PartnerOffer>> =
    rustOffersForPlaces(places.map { p -> ROfferPlace(p.name, if (p.lat != null && p.lon != null) RGeoPoint(p.lat, p.lon) else null) })
        .map { l -> l.map { it.toModel() } }

private fun StopFact.toOfferR() = RStopFact(name, category, address, arriveMs, leaveMs, lat, lon, firstVisit)

private fun RCourseOffers.toModel() = CourseOffers(placeCount.toInt(), offers.map { it.toModel() })

private fun RClaimedCoupon.toModel() = ClaimedCoupon(offer.toModel(), code, claimedMs)

internal fun RPartnerOffer.toModel() = PartnerOffer(
    id, placeName, partnerName, badge, title, conditions, validUntil, validText, category.toModel(), categoryLabel, lat, lon,
)

private fun ROfferCategory.toModel() = when (this) {
    ROfferCategory.RESTAURANT -> OfferCategory.RESTAURANT
    ROfferCategory.CAFE -> OfferCategory.CAFE
    ROfferCategory.FUEL -> OfferCategory.FUEL
    ROfferCategory.REST_AREA -> OfferCategory.REST_AREA
    ROfferCategory.ATTRACTION -> OfferCategory.ATTRACTION
    ROfferCategory.MARKET -> OfferCategory.MARKET
    ROfferCategory.SHOPPING -> OfferCategory.SHOPPING
}
