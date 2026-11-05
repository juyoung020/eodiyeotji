package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceQuery
import app.eodiyeotji.core.PlaceSource
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 카카오 응답 해석·이름 고르기.
 * - `place/real/kakao_*.json`: 2026-11-06 시연 지점에서 실제로 받은 응답
 * - `place/crafted/kakao_*.json`: 고르기 규칙(주차장 vs 음식점 등)만 확인하려고 공식 응답 형식대로 만든 예시
 */
class KakaoLocalProviderTest {
    private val gwangjang = PlaceQuery(id = 1, lat = 37.5700, lon = 126.9995, isStay = true)
    private val chodang = PlaceQuery(id = 2, lat = 37.7905, lon = 128.9150, isStay = true)
    private val gyeongpo = PlaceQuery(id = 3, lat = 37.8055, lon = 128.9087, isStay = true)
    private val cityhall = PlaceQuery(id = 4, lat = 37.5665, lon = 126.9780, isStay = false)
    private val gyeongbok = PlaceQuery(id = 5, lat = 37.5758, lon = 126.9777, isStay = false)

    private val empty = "crafted/kakao_cat_empty.json"

    /**
     * URL → 표본 파일. 분류 검색은 category_group_code로, 전통시장 검색은 [market], 반경 500 m 명소는 [landmark],
     * 좌표→주소는 [c2a]. 표에 없는 분류는 빈 결과. 요청한 URL을 모두 적어 둔다.
     */
    private inner class FakeKakao(
        private val categories: Map<String, String> = emptyMap(),
        private val c2a: String = "crafted/kakao_c2a_road_null.json",
        private val landmark: String = empty,
        private val market: String = empty,
        /** 이 키(분류 코드, "c2a"·"landmark"·"market")는 [slowMs]만큼 늦게 답한다 */
        private val slow: Set<String> = emptySet(),
        private val slowMs: Long = 1_500,
        /** 이 키는 HTTP 오류로 답한다 */
        private val failCode: Pair<String, Int>? = null,
    ) : HttpGetter {
        val urls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun get(url: String, headers: Map<String, String>): String {
            urls += url
            assertEquals("KakaoAK test-key", headers["Authorization"])
            val key = when {
                "coord2address" in url -> "c2a"
                "keyword.json" in url -> "market"
                "radius=${KakaoLocalProvider.LANDMARK_RADIUS_M}" in url -> "landmark"
                else -> Regex("category_group_code=(\\w+)").find(url)!!.groupValues[1]
            }
            if (key in slow) Thread.sleep(slowMs)
            if (failCode?.first == key) throw HttpStatusException(failCode.second, "test")
            return fixture(
                when (key) {
                    "c2a" -> c2a
                    "landmark" -> landmark
                    "market" -> market
                    else -> categories[key] ?: empty
                }
            )
        }
    }

    private fun provider(fake: FakeKakao) = KakaoLocalProvider("test-key", fake)

    private fun found(r: Lookup): PlaceLabel = (r as Lookup.Found).label

    private val gwangjangReal
        get() = FakeKakao(
            mapOf(
                "FD6" to "real/kakao_cat_gwangjang_FD6.json", "MT1" to "real/kakao_cat_gwangjang_MT1.json",
                "PK6" to "real/kakao_cat_gwangjang_PK6.json", "BK9" to "real/kakao_cat_gwangjang_BK9.json",
            ),
            c2a = "real/kakao_c2a_gwangjang.json",
            landmark = "real/kakao_cat_gwangjang_AT4_wide.json",
            market = "real/kakao_kw_market_gwangjang.json",
        )

    // ---- 실제 응답 ----

    @Test
    fun `광장시장 — 시장 안 가게·은행보다 시장 이름`() = runBlocking {
        val fake = gwangjangReal
        val label = found(provider(fake).lookup(gwangjang))
        assertEquals("광장시장", label.name)
        assertEquals("시장", label.category)
        assertEquals(PlaceSource.KAKAO, label.source)
        assertEquals(19, fake.urls.size) // 분류 16 + 시장 1 + 명소 1 + 주소 1
    }

    @Test
    fun `광장시장 — 도착 지점도 같은 방법`() = runBlocking {
        assertEquals("광장시장", found(provider(gwangjangReal).lookup(gwangjang.copy(isStay = false))).name)
    }

    @Test
    fun `경복궁 — 80 m 안에 가게가 없고 주소는 구·동뿐이면 넓은 명소(고궁)`() = runBlocking {
        val fake = FakeKakao(c2a = "real/kakao_c2a_gyeongbok.json", landmark = "real/kakao_cat_gyeongbok_AT4_wide.json")
        val label = found(provider(fake).lookup(gyeongbok))
        assertEquals("경복궁", label.name)
        assertEquals("문화유적", label.category) // "여행 > 관광,명소 > 문화유적 > 고궁,궁"의 셋째 칸
    }

    @Test
    fun `초당 — 순두부집이 같은 이름 주차장보다 우선`() = runBlocking {
        val fake = FakeKakao(mapOf("FD6" to "real/kakao_cat_chodang_FD6.json", "PK6" to "real/kakao_cat_chodang_PK6.json"))
        val label = found(provider(fake).lookup(chodang))
        assertEquals("원조초당순두부", label.name)
        assertEquals("두부전문점", label.category)
    }

    @Test
    fun `넓은 명소 안이라도 25 m 안의 음식점이면 음식점`() = runBlocking {
        val fake = FakeKakao(mapOf("FD6" to "real/kakao_cat_chodang_FD6.json"), landmark = "real/kakao_cat_gyeongbok_AT4_wide.json")
        assertEquals("원조초당순두부", found(provider(fake).lookup(chodang)).name)
    }

    @Test
    fun `경포해변 — 자전거 인증센터는 건너뛰고 해수욕장`() = runBlocking {
        val fake = FakeKakao(
            mapOf("AT4" to "real/kakao_cat_gyeongpo_AT4.json", "PK6" to "real/kakao_cat_gyeongpo_PK6.json"),
            c2a = "real/kakao_c2a_gyeongpo.json",
            landmark = "real/kakao_cat_gyeongpo_AT4_wide.json",
        )
        val label = found(provider(fake).lookup(gyeongpo))
        assertEquals("경포해수욕장", label.name)
        assertEquals("해수욕장·해변", label.category)
    }

    @Test
    fun `서울시청 — 건물 이름과 같은 시설이 은행·전망대·근처 고궁보다 우선`() = runBlocking {
        val fake = FakeKakao(
            mapOf(
                "PO3" to "real/kakao_cat_cityhall_PO3.json", "AT4" to "real/kakao_cat_cityhall_AT4.json",
                "BK9" to "real/kakao_cat_cityhall_BK9.json", "CT1" to "real/kakao_cat_cityhall_CT1.json",
            ),
            c2a = "real/kakao_c2a_cityhall.json",
            landmark = "real/kakao_cat_cityhall_AT4_wide.json",
        )
        val label = found(provider(fake).lookup(cityhall))
        assertEquals("서울특별시청", label.name)
        assertEquals("시청", label.category)
    }

    @Test
    fun `큰 명소의 한 부분이면 큰 명소 이름`() {
        fun lm(name: String, d: Double) = KakaoParse.Candidate(
            PlaceLabel(name, "문화유적", "", PlaceSource.KAKAO), d, d, "AT4", setOf("여행", "관광", "명소", "문화유적", "고궁", "궁"),
        )
        val label = KakaoParse.choose(emptyList(), emptyList(), listOf(lm("경복궁 연생전", 120.0), lm("경복궁", 280.0)), null)
        assertEquals("경복궁", label!!.name)
    }

    @Test
    fun `서울시청 — 다른 이름이 없으면 마지막으로 건물 이름`() = runBlocking {
        val fake = FakeKakao(c2a = "real/kakao_c2a_cityhall.json")
        val label = found(provider(fake).lookup(cityhall))
        assertEquals("서울특별시청", label.name)
        assertEquals("", label.category)
        assertEquals("서울특별시 중구 세종대로 110", label.address)
        assertTrue(fake.urls.any { "coord2address.json?x=126.978000&y=37.566500" in it })
    }

    @Test
    fun `은행 분류 — ATM은 빼고 지점`() {
        assertEquals("KB국민은행 청계", KakaoParse.nearestPlace(fixture("real/kakao_cat_gwangjang_BK9.json"))!!.label.name)
    }

    @Test
    fun `건물 이름이 없는 주소 — 지번 주소의 구·동`() {
        val a = KakaoParse.coord2address(fixture("real/kakao_c2a_gwangjang.json"))!!
        assertFalse(a.isBuilding)
        assertEquals("종로구 예지동", a.label.name) // 도로명 주소의 region_3depth_name은 비어 있다
        assertEquals("서울특별시 종로구 창경궁로 88", a.label.address)
        assertNull(KakaoParse.coord2address(fixture("real/kakao_c2a_gyeongpo.json")))
    }

    // ---- 고르기 규칙 (만든 예시) ----

    @Test
    fun `12 m 주차장보다 45 m 음식점`() = runBlocking {
        val fake = FakeKakao(mapOf("FD6" to "crafted/kakao_cat_FD6_45m.json", "PK6" to "crafted/kakao_cat_PK6_12m.json"))
        val label = found(provider(fake).lookup(gwangjang))
        assertEquals("예시식당", label.name)
        assertEquals("전·빈대떡", label.category)
    }

    @Test
    fun `근처에 주차장뿐이면 주차장 이름`() = runBlocking {
        val fake = FakeKakao(mapOf("PK6" to "crafted/kakao_cat_PK6_12m.json"))
        val label = found(provider(fake).lookup(gwangjang))
        assertEquals("예시 공영주차장", label.name)
        assertEquals("주차장", label.category)
        assertEquals("서울 종로구 예지동 2", label.address) // 도로명이 비면 지번
    }

    @Test
    fun `아무것도 없으면 구·동 (도로명 주소 null)`() = runBlocking {
        val label = found(provider(FakeKakao()).lookup(gwangjang))
        assertEquals("종로구 관훈동", label.name)
        assertEquals("서울 종로구 관훈동 155", label.address)
    }

    @Test
    fun `주소까지 없으면 결과 없음`() = runBlocking {
        assertEquals(Lookup.NotFound, provider(FakeKakao(c2a = "real/kakao_c2a_gyeongpo.json")).lookup(cityhall))
    }

    // ---- 빠르기 ----

    @Test
    fun `요청을 한꺼번에 보낸다 — 응답마다 0_3초 걸려도 전체는 1초 안`() = runBlocking {
        val fake = FakeKakao(
            mapOf("FD6" to "crafted/kakao_cat_FD6_45m.json"),
            slow = KakaoLocalProvider.ALL_CODES.toSet() + "c2a" + "landmark" + "market",
            slowMs = 300,
        )
        val t0 = System.nanoTime()
        assertEquals("예시식당", found(provider(fake).lookup(gwangjang)).name)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("차례로 보내면 19 × 300 ms: $ms ms", ms < 1_000)
    }

    @Test
    fun `늦는 응답은 정해진 시간까지만 기다린다`() = runBlocking {
        val fake = FakeKakao(mapOf("FD6" to "crafted/kakao_cat_FD6_45m.json"), slow = setOf("c2a", "PK6"), slowMs = 2_500)
        val t0 = System.nanoTime()
        assertEquals("예시식당", found(provider(fake).lookup(gwangjang)).name)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("늦는 응답(2.5초)을 기다림: $ms ms", ms < KakaoLocalProvider.DECIDE_AFTER_MS + 400)
    }

    @Test(expected = HttpStatusException::class)
    fun `요청 하나라도 429면 그대로 알린다 (Resolver가 쉼)`() {
        runBlocking { provider(FakeKakao(failCode = "CS2" to 429)).lookup(gwangjang) }
    }

    // ---- 형식 ----

    @Test
    fun `요청 URL 형식`() {
        assertEquals(
            "https://dapi.kakao.com/v2/local/search/category.json?category_group_code=FD6" +
                "&x=126.999500&y=37.570000&radius=80&sort=distance&size=5",
            KakaoLocalProvider.categoryUrl(gwangjang, "FD6"),
        )
        assertEquals(
            "https://dapi.kakao.com/v2/local/search/keyword.json?query=%EC%A0%84%ED%86%B5%EC%8B%9C%EC%9E%A5" +
                "&x=126.999500&y=37.570000&radius=150&sort=distance&size=5",
            KakaoLocalProvider.marketUrl(gwangjang),
        )
    }

    @Test
    fun `분류 짧게`() {
        assertEquals("순두부", KakaoParse.shortCategory("음식점 > 한식 > 순두부", "음식점"))
        assertEquals("커피전문점", KakaoParse.shortCategory("음식점 > 카페 > 커피전문점 > 스타벅스", "카페"))
        assertEquals("한식", KakaoParse.shortCategory("음식점 > 한식", "음식점"))
        assertEquals("카페", KakaoParse.shortCategory(null, "카페"))
        assertEquals("", KakaoParse.shortCategory(null, null))
    }
}

internal fun fixture(name: String): String =
    requireNotNull(KakaoLocalProviderTest::class.java.classLoader!!.getResourceAsStream("place/$name")) { "표본 없음: $name" }
        .use { it.readBytes().toString(Charsets.UTF_8) }
