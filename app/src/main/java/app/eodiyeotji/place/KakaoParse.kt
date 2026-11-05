package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceSource
import org.json.JSONObject

/** 카카오 응답 해석과 이름 고르기 (순수 함수, JVM 테스트 대상). 형식: developers.kakao.com 로컬 API 문서. */
object KakaoParse {
    /** 근처 가게·시설 반경 안에서 이 거리 안의 목적지형 가게는 넓은 명소보다 우선 (해변 앞 카페 등) */
    const val STRONG_POI_M = 25.0

    /** 전통시장 이름 핀이 이 거리 안이면 시장 안이다 → 시장 이름 (시장 안 가게 이름보다 우선) */
    const val MARKET_RADIUS_M = 150.0

    /** 넓은 명소(고궁·해변·공원 등) 이름 핀이 이 거리 안이면 그 명소 안이다 */
    const val AREA_RADIUS_M = 300.0

    /** 목적지형 분류 (음식점·카페·관광명소·문화시설·숙박·대형마트) */
    val PRIMARY_CODES = setOf("FD6", "CE7", "AT4", "CT1", "AD5", "MT1")

    /** 넓은 명소 분류 낱말 (카카오 category_name의 칸) — 이름 핀 하나가 넓은 땅을 대표하는 곳 */
    private val AREA_TOKENS = setOf("고궁", "궁", "문화유적", "해수욕장", "해변", "테마파크", "유원지", "수목원", "동물원", "식물원", "산", "호수")

    /** 길 이름일 뿐인 관광 코스 → 이름으로 쓰지 않는다 */
    private val ROUTE_TOKENS = setOf("도보여행", "자전거여행")

    /** 카테고리 검색 후보. [score] = 거리 × 분류 가중치 (작을수록 좋음). [groupCode]는 카카오 분류 코드 (예: FD6). */
    data class Candidate(
        val label: PlaceLabel,
        val distanceM: Double,
        val score: Double,
        val groupCode: String,
        /** category_name을 '>'·','로 나눈 낱말 (예: "여행 > 관광,명소 > 고궁,궁" → 여행·관광·명소·고궁·궁) */
        val tokens: Set<String>,
    )

    /** 좌표→주소 결과. [isBuilding]이면 이름이 건물 이름, 아니면 "구 동". */
    data class Address(val label: PlaceLabel, val isBuilding: Boolean)

    /** 주차장·주유소·편의점·은행은 목적지 옆에 서거나 지나치는 곳이라 가중치를 크게 둔다 */
    fun weight(groupCode: String): Double = when (groupCode) {
        "PK6" -> 4.0
        "OL7" -> 2.0
        "CS2", "BK9" -> 1.5
        else -> 1.0
    }

    /** 카테고리 검색 응답(sort=distance)에서 가장 알맞은 곳 (점수가 가장 작은 곳, ATM 제외). 없으면 null. */
    fun nearestPlace(json: String): Candidate? =
        places(json).filterNot { "ATM" in it.tokens }.minByOrNull { it.score }

    /** 응답의 모든 장소 (거리가 없는 항목은 뺀다). */
    fun places(json: String): List<Candidate> {
        val docs = JSONObject(json).optJSONArray("documents") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i -> docs.optJSONObject(i)?.let(::candidate) }
    }

    private fun candidate(d: JSONObject): Candidate? {
        val name = d.str("place_name") ?: return null
        val dist = d.str("distance")?.toDoubleOrNull() ?: return null
        val categoryName = d.str("category_name")
        val label = PlaceLabel(
            name = name,
            category = shortCategory(categoryName, d.str("category_group_name")),
            address = d.str("road_address_name") ?: d.str("address_name").orEmpty(),
            source = PlaceSource.KAKAO,
        )
        val code = d.str("category_group_code").orEmpty()
        val tokens = categoryName.orEmpty().split('>', ',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return Candidate(label, dist, dist * weight(code), code, tokens)
    }

    /**
     * "음식점 > 한식 > 순두부" → "순두부", "음식점 > 카페 > 커피전문점 > 스타벅스" → "커피전문점"(셋째 칸, 넷째는 상호라서),
     * "음식점 > 한식" → "한식". 쉼표는 가운뎃점으로 ("전,빈대떡" → "전·빈대떡").
     */
    fun shortCategory(categoryName: String?, groupName: String?): String {
        val parts = categoryName?.split('>')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val pick = parts.getOrNull(2) ?: parts.lastOrNull() ?: groupName.orEmpty()
        return pick.replace(',', '·')
    }

    fun isMarket(c: Candidate) = "시장" in c.tokens || "전통시장" in c.tokens

    fun isArea(c: Candidate) = c.tokens.any { it in AREA_TOKENS || it.endsWith("공원") }

    fun isRoute(c: Candidate) = c.tokens.any { it in ROUTE_TOKENS }

    /**
     * 이름 고르기. 순서:
     * 1. 전통시장 핀이 [MARKET_RADIUS_M] 안 → 시장 이름 (시장 안 가게·은행보다 우선)
     * 2. 지금 선 건물의 이름(좌표→주소)과 이름이 같은 가게·시설이 80 m 안 → 그곳 (서울시청 앞 → "서울특별시청")
     * 3. 넓은 명소(고궁·해변·공원…) 핀이 [AREA_RADIUS_M] 안 → 명소 이름. 단 [STRONG_POI_M] 안에 목적지형 가게가 있으면 4로.
     *    "경복궁 연생전"처럼 큰 명소의 한 부분이면 큰 명소("경복궁") 이름
     * 4. 반경 80 m 가게·시설 중 "거리 × 가중치"가 가장 작은 곳
     * 5. 반경 500 m 관광명소 중 가장 가까운 곳 (길 코스 제외)
     * 6. 좌표→주소: 건물 이름, 없으면 "구 동"
     *
     * @param pois 분류별 [nearestPlace] 결과, @param markets 전통시장 검색 결과, @param landmarks 반경 500 m 관광명소 검색 결과
     */
    fun choose(pois: List<Candidate>, markets: List<Candidate>, landmarks: List<Candidate>, addr: Address?): PlaceLabel? {
        markets.filter { isMarket(it) && it.distanceM <= MARKET_RADIUS_M }.minByOrNull { it.distanceM }
            ?.let { return it.label }
        if (addr?.isBuilding == true) {
            pois.filter { it.label.name == addr.label.name }.minByOrNull { it.distanceM }?.let { return it.label }
        }
        val areas = landmarks.filter { isArea(it) && it.distanceM <= AREA_RADIUS_M }
        val area = areas.minByOrNull { it.distanceM }?.let { a -> areas.firstOrNull { a.label.name.startsWith(it.label.name + " ") } ?: a }
        val strongPoi = pois.any { it.groupCode in PRIMARY_CODES && it.distanceM <= STRONG_POI_M }
        if (area != null && !strongPoi) return area.label
        pois.minByOrNull { it.score }?.let { return it.label }
        landmarks.filterNot { isRoute(it) }.minByOrNull { it.distanceM }?.let { return it.label }
        return addr?.label
    }

    /** 좌표→주소 응답 → 건물 이름(있으면) 또는 "구 동". 결과 없으면 null (바다·모래사장 등). */
    fun coord2address(json: String): Address? {
        val doc = JSONObject(json).optJSONArray("documents")?.optJSONObject(0) ?: return null
        val road = doc.optJSONObject("road_address")
        val jibun = doc.optJSONObject("address")
        val address = road?.str("address_name") ?: jibun?.str("address_name").orEmpty()
        road?.str("building_name")?.let {
            return Address(PlaceLabel(name = it, category = "", address = address, source = PlaceSource.KAKAO), true)
        }
        // 도로명 주소의 region_3depth_name은 비어 있는 경우가 많아 지번 주소의 동을 쓴다
        val areaName = listOfNotNull(
            jibun?.str("region_2depth_name") ?: road?.str("region_2depth_name"),
            jibun?.str("region_3depth_name") ?: road?.str("region_3depth_name"),
        ).joinToString(" ").takeIf { it.isNotEmpty() } ?: return null
        return Address(PlaceLabel(name = areaName, category = "", address = address, source = PlaceSource.KAKAO), false)
    }
}
