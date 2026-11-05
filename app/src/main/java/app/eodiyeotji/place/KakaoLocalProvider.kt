package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceQuery
import app.eodiyeotji.core.PlaceSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 카카오 로컬 API — 가게·건물 실제 이름을 찾는 기본(주) 출처. 출처 표시는 [PlaceSource.KAKAO].
 *
 * ## 키 넣는 곳
 * 저장소 루트 `local.properties`(git에 올라가지 않음)에 한 줄:
 * ```
 * KAKAO_REST_API_KEY=카카오디벨로퍼스_앱의_REST_API_키
 * ```
 * 또는 빌드하는 셸의 환경 변수 `KAKAO_REST_API_KEY`. `app/build.gradle.kts`가 `BuildConfig.KAKAO_REST_API_KEY`로 넣는다.
 * 키가 비어 있으면 이 출처는 만들어지지 않고(시작할 때 경고 로그 한 줄), 가게 이름 대신 OSM Nominatim 결과만 쓴다.
 * 키가 틀리면(401·403) 그 실행 동안 이 출처를 끄고 OSM으로 넘어간다.
 * 키는 APK 안에 들어간다(클라이언트 호출이라 피할 수 없음) — 카카오 디벨로퍼스에서 사용량 한도를 걸어 둔다.
 *
 * ## 찾는 방법
 * - 머문 곳(`isStay`): 반경 [RADIUS_M] m 카테고리 검색(`/v2/local/search/category.json`, sort=distance).
 *   1차로 목적지형 분류([PRIMARY_CODES]: 음식점·카페·관광명소·문화시설·숙박·대형마트)를 동시에 묻고,
 *   [EARLY_EXIT_M] m 안에 있으면 거기서 끝낸다. 아니면 나머지 분류([SECONDARY_CODES])를 묻는다.
 *   후보는 "거리 × 분류 가중치"가 가장 작은 곳을 고른다. 차는 목적지 옆 주차장·주유소에 서므로
 *   주차장(×4)·주유소(×2)·편의점(×1.5)은 근처에 다른 곳이 없을 때만 이름이 된다.
 *   아무것도 없으면 좌표→주소(`/v2/local/geo/coord2address.json`)의 건물 이름, 그것도 없으면
 *   반경 [LANDMARK_RADIUS_M] m 관광명소(해변·공원처럼 넓은 곳), 그것도 없으면 "구 동".
 * - 주행 출발·도착 지점: 좌표→주소만 (건물 이름, 없으면 "구 동").
 *
 * 요청 수: 머문 곳 하나에 6번(1차에서 끝날 때) ~ 18번, 출발·도착 지점 하나에 1번.
 * 같은 지점은 Rust가 이름을 저장해 다시 묻지 않는다.
 *
 * 요청 간격은 [gate]가 지킨다 (기본 60 ms, 무료 한도 하루 10만 건 대비 넉넉함).
 */
class KakaoLocalProvider(
    private val restKey: String,
    private val http: HttpGetter = PlatformHttp,
    private val gate: RateGate = RateGate(60),
) : PlaceProvider {
    override val name = "kakao"

    private val headers = mapOf("Authorization" to "KakaoAK $restKey")

    override suspend fun lookup(q: PlaceQuery): Lookup {
        if (q.isStay) {
            val first = searchCategories(q, PRIMARY_CODES)
            val best = if (first != null && first.distanceM <= EARLY_EXIT_M) {
                first
            } else {
                listOfNotNull(first, searchCategories(q, SECONDARY_CODES)).minByOrNull { it.score }
            }
            if (best != null) return Lookup.Found(best.label)
        }
        val addr = KakaoParse.coord2address(get(coord2addressUrl(q)))
        if (addr?.isBuilding == true) return Lookup.Found(addr.label)
        if (q.isStay) {
            // 해변·공원처럼 넓은 명소는 이름 핀이 가운데 하나뿐이라 80 m 안에 안 잡힌다
            KakaoParse.nearestLandmark(get(landmarkUrl(q)))?.let { return Lookup.Found(it.label) }
        }
        return addr?.let { Lookup.Found(it.label) } ?: Lookup.NotFound
    }

    /** 분류들을 동시에 묻고(간격은 gate가 지킴) 가장 알맞은 후보 하나. */
    private suspend fun searchCategories(q: PlaceQuery, codes: List<String>): KakaoParse.Candidate? = coroutineScope {
        codes.map { code -> async { KakaoParse.nearestPlace(get(categoryUrl(q, code))) } }
            .awaitAll()
            .filterNotNull()
            .minByOrNull { it.score }
    }

    private suspend fun get(url: String): String {
        gate.acquire()
        return withContext(Dispatchers.IO) { http.get(url, headers) }
    }

    companion object {
        const val BASE = "https://dapi.kakao.com"
        const val RADIUS_M = 80
        const val EARLY_EXIT_M = 20.0
        const val LANDMARK_RADIUS_M = 500

        /** 사람이 찾아가는 목적지형 분류 — 먼저 묻는다 */
        val PRIMARY_CODES = listOf("FD6", "CE7", "AT4", "CT1", "AD5", "MT1")

        /** 나머지 방문 분류 (어린이집 PS3·중개업소 AG2는 목적지로 쓸 일이 드물어 뺀다) */
        val SECONDARY_CODES = listOf("HP8", "PM9", "BK9", "PO3", "SC4", "AC5", "SW8", "CS2", "OL7", "PK6")

        fun categoryUrl(q: PlaceQuery, code: String) =
            "$BASE/v2/local/search/category.json?category_group_code=$code" +
                "&x=${coord(q.lon)}&y=${coord(q.lat)}&radius=$RADIUS_M&sort=distance&size=3"

        /** 넓은 명소(관광명소 AT4) 찾기 — 가까운 가게·건물 이름이 하나도 없을 때만 */
        fun landmarkUrl(q: PlaceQuery) =
            "$BASE/v2/local/search/category.json?category_group_code=AT4" +
                "&x=${coord(q.lon)}&y=${coord(q.lat)}&radius=$LANDMARK_RADIUS_M&sort=distance&size=5"

        fun coord2addressUrl(q: PlaceQuery) =
            "$BASE/v2/local/geo/coord2address.json?x=${coord(q.lon)}&y=${coord(q.lat)}"

        /** 기기 언어가 쉼표 소수점이어도 "37.566500"으로 */
        private fun coord(v: Double) = String.format(Locale.ROOT, "%.6f", v)
    }
}

/** 카카오 응답 해석 (순수 함수, JVM 테스트 대상). 형식: developers.kakao.com 로컬 API 문서. */
object KakaoParse {
    /** 카테고리 검색 후보. [score] = 거리 × 분류 가중치 (작을수록 좋음). */
    data class Candidate(val label: PlaceLabel, val distanceM: Double, val score: Double)

    /** 주차장·주유소·편의점은 목적지 옆에 서는 곳이라 가중치를 크게 둔다 */
    fun weight(groupCode: String): Double = when (groupCode) {
        "PK6" -> 4.0
        "OL7" -> 2.0
        "CS2" -> 1.5
        else -> 1.0
    }

    /** 카테고리 검색 응답(sort=distance)에서 가장 가까운 곳. 없으면 null. */
    fun nearestPlace(json: String): Candidate? = candidates(json).minByOrNull { it.score }

    private fun candidates(json: String): List<Candidate> {
        val docs = JSONObject(json).optJSONArray("documents") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i -> docs.optJSONObject(i)?.let(::candidate) }
    }

    private fun candidate(d: JSONObject): Candidate? {
        val name = d.str("place_name") ?: return null
        val dist = d.str("distance")?.toDoubleOrNull() ?: return null
        val label = PlaceLabel(
            name = name,
            category = shortCategory(d.str("category_name"), d.str("category_group_name")),
            address = d.str("road_address_name") ?: d.str("address_name").orEmpty(),
            source = PlaceSource.KAKAO,
        )
        return Candidate(label, dist, dist * weight(d.str("category_group_code").orEmpty()))
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

    /** 넓은 명소 검색(반경 500 m)에서 가장 가까운 곳. 자전거길 인증센터 같은 작은 표지점은 뺀다. */
    fun nearestLandmark(json: String): Candidate? =
        candidates(json).filterNot { it.label.category == "자전거여행" }.minByOrNull { it.distanceM }

    /** 좌표→주소 결과. [isBuilding]이면 이름이 건물 이름, 아니면 "구 동". */
    data class Address(val label: PlaceLabel, val isBuilding: Boolean)

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
