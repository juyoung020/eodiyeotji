package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceQuery
import app.eodiyeotji.core.PlaceSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * OpenStreetMap Nominatim 역지오코딩 — 키 없이 쓰는 예비 출처 (카카오 키가 없거나 카카오가 못 찾을 때). 출처 [PlaceSource.OSM].
 *
 * 사용 정책(operations.osmfoundation.org/policies/nominatim)을 지킨다:
 * - 1초에 1번 이하 ([gate], 1.1초 간격)
 * - 같은 지점을 다시 묻지 않는다 (받은 이름은 Rust가 저장, 못 찾은 지점도 Rust가 재시도 시각까지 빼 준다)
 * - 앱을 밝히는 User-Agent (개인 연락처는 넣지 않는다)
 */
class NominatimProvider(
    private val userAgent: String,
    private val http: HttpGetter = PlatformHttp,
    private val gate: RateGate = RateGate(1_100),
) : PlaceProvider {
    override val name = "osm"

    override suspend fun lookup(q: PlaceQuery): Lookup {
        gate.acquire()
        val body = withContext(Dispatchers.IO) { http.get(url(q), mapOf("User-Agent" to userAgent)) }
        return NominatimParse.reverse(body)?.let { Lookup.Found(it) } ?: Lookup.NotFound
    }

    companion object {
        fun url(q: PlaceQuery) =
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                "&lat=${String.format(Locale.ROOT, "%.6f", q.lat)}&lon=${String.format(Locale.ROOT, "%.6f", q.lon)}" +
                "&zoom=18&addressdetails=1&namedetails=1&accept-language=ko"
    }
}

/** Nominatim jsonv2 reverse 응답 해석 (순수 함수, JVM 테스트 대상). */
object NominatimParse {
    /** 이름이 있어도 가게·건물이 아닌 결과 (도로·행정구역) → "구 동"으로 보여 준다 */
    private val AREA_CATEGORIES = setOf("highway", "place", "boundary", "landuse", "railway_line", "waterway")

    fun reverse(json: String): PlaceLabel? {
        val o = JSONObject(json)
        if (o.has("error")) return null
        val addr = o.optJSONObject("address") ?: JSONObject()
        val category = o.str("category").orEmpty()
        val type = o.str("type").orEmpty()

        val nd = o.optJSONObject("namedetails")
        val poiName = (nd?.str("name:ko") ?: nd?.str("name") ?: o.str("name"))
            ?.takeIf { category !in AREA_CATEGORIES }
        val name = poiName ?: areaName(addr) ?: addr.str("road") ?: return null

        return PlaceLabel(
            name = name,
            category = if (poiName != null) koreanCategory(category, type) else "",
            address = address(addr),
            source = PlaceSource.OSM,
        )
    }

    /** "중구 태평로1가", "강릉시 경포동" (카카오 좌표→주소의 "구 동"과 같은 모양). */
    fun areaName(addr: JSONObject): String? {
        val upper = addr.str("borough") ?: addr.str("county") ?: addr.str("city") ?: addr.str("town")
        val dong = addr.str("quarter") ?: addr.str("suburb") ?: addr.str("city_district")
            ?: addr.str("neighbourhood") ?: addr.str("village") ?: addr.str("hamlet")
        return listOfNotNull(upper, dong).distinct().joinToString(" ").takeIf { it.isNotEmpty() }
    }

    /** "서울특별시 중구 세종대로 110" 처럼 한국식 순서 한 줄. */
    fun address(addr: JSONObject): String = listOfNotNull(
        addr.str("province") ?: addr.str("state"),
        addr.str("city"),
        addr.str("borough") ?: addr.str("county"),
        addr.str("road"),
        addr.str("house_number")?.trimEnd(',', ' ')?.takeIf { it.isNotEmpty() },
    ).distinct().joinToString(" ")

    /** OSM 태그 → 짧은 한국어 분류. 모르면 빈 문자열. */
    fun koreanCategory(category: String, type: String): String = when (category) {
        "amenity" -> when (type) {
            "restaurant", "fast_food", "food_court" -> "음식점"
            "cafe" -> "카페"
            "bar", "pub" -> "술집"
            "parking", "parking_space" -> "주차장"
            "fuel", "charging_station" -> "주유소"
            "hospital", "clinic", "doctors", "dentist" -> "병원"
            "pharmacy" -> "약국"
            "bank" -> "은행"
            "school", "kindergarten" -> "학교"
            "university", "college" -> "대학교"
            "library" -> "도서관"
            "townhall", "police", "post_office", "fire_station" -> "공공기관"
            "marketplace" -> "시장"
            "cinema", "theatre", "arts_centre" -> "문화시설"
            "place_of_worship" -> "종교시설"
            else -> ""
        }
        "shop" -> when (type) {
            "convenience" -> "편의점"
            "supermarket", "mall", "department_store" -> "대형마트"
            else -> "상점"
        }
        "tourism" -> when (type) {
            "hotel", "motel", "guest_house", "hostel", "apartment" -> "숙박"
            "museum", "gallery" -> "문화시설"
            else -> "관광명소"
        }
        "historic" -> "관광명소"
        "leisure" -> when (type) {
            "park", "garden" -> "공원"
            "beach_resort" -> "해변"
            else -> ""
        }
        "natural" -> if (type == "beach") "해변" else ""
        "railway", "public_transport" -> if (type == "station") "역" else ""
        else -> ""
    }
}
