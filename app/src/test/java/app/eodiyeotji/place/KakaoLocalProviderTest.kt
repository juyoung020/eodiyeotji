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
 * 카카오 응답 해석·후보 고르기.
 * - `place/real/kakao_*.json`: 2026-11-06 시연 지점에서 실제로 받은 응답
 * - `place/crafted/kakao_*.json`: 고르기 규칙(주차장 vs 음식점 등)만 확인하려고 공식 응답 형식대로 만든 예시
 */
class KakaoLocalProviderTest {
    private val gwangjang = PlaceQuery(id = 1, lat = 37.5700, lon = 126.9995, isStay = true)
    private val chodang = PlaceQuery(id = 2, lat = 37.7905, lon = 128.9150, isStay = true)
    private val gyeongpo = PlaceQuery(id = 3, lat = 37.8055, lon = 128.9087, isStay = true)
    private val cityhall = PlaceQuery(id = 4, lat = 37.5665, lon = 126.9780, isStay = false)

    /**
     * URL → 표본 파일. 분류 검색은 category_group_code로, 반경 500 m 명소 검색은 [landmark]로, 좌표→주소는 [c2a]로.
     * 표에 없는 분류는 빈 결과. 요청한 URL을 모두 적어 둔다.
     */
    private class FakeKakao(
        private val categories: Map<String, String> = emptyMap(),
        private val c2a: String = "crafted/kakao_c2a_road_null.json",
        private val landmark: String = "crafted/kakao_cat_empty.json",
    ) : HttpGetter {
        val urls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun get(url: String, headers: Map<String, String>): String {
            urls += url
            assertEquals("KakaoAK test-key", headers["Authorization"])
            if ("coord2address" in url) return fixture(c2a)
            if ("radius=${KakaoLocalProvider.LANDMARK_RADIUS_M}" in url) return fixture(landmark)
            val code = Regex("category_group_code=(\\w+)").find(url)!!.groupValues[1]
            return fixture(categories[code] ?: "crafted/kakao_cat_empty.json")
        }
        fun categoryCalls() = urls.count { "category.json" in it }
    }

    private fun provider(fake: FakeKakao) = KakaoLocalProvider("test-key", fake, RateGate(0))

    private fun found(r: Lookup): PlaceLabel = (r as Lookup.Found).label

    // ---- 실제 응답 ----

    @Test
    fun `광장시장 — 바로 옆 음식점이 이름, 1차에서 끝`() = runBlocking {
        val fake = FakeKakao(
            mapOf("FD6" to "real/kakao_cat_gwangjang_FD6.json", "MT1" to "real/kakao_cat_gwangjang_MT1.json", "PK6" to "real/kakao_cat_gwangjang_PK6.json"),
        )
        val label = found(provider(fake).lookup(gwangjang))
        assertEquals("황씨네모듬회", label.name)
        assertEquals("해물·생선", label.category)
        assertEquals("서울 종로구 창경궁로 88", label.address)
        assertEquals(PlaceSource.KAKAO, label.source)
        assertEquals(KakaoLocalProvider.PRIMARY_CODES.size, fake.urls.size) // 8 m라서 2차·주소는 묻지 않음
    }

    @Test
    fun `초당 — 순두부집이 같은 이름 주차장보다 우선`() = runBlocking {
        val fake = FakeKakao(mapOf("FD6" to "real/kakao_cat_chodang_FD6.json", "PK6" to "real/kakao_cat_chodang_PK6.json"))
        val label = found(provider(fake).lookup(chodang))
        assertEquals("원조초당순두부", label.name)
        assertEquals("두부전문점", label.category)
    }

    @Test
    fun `경포해변 — 80 m 안에 아무것도 없고 주소도 없으면 넓은 명소`() = runBlocking {
        val fake = FakeKakao(
            mapOf("AT4" to "real/kakao_cat_gyeongpo_AT4.json", "PK6" to "real/kakao_cat_gyeongpo_PK6.json"),
            c2a = "real/kakao_c2a_gyeongpo.json",
            landmark = "real/kakao_cat_gyeongpo_AT4_wide.json",
        )
        val label = found(provider(fake).lookup(gyeongpo))
        assertEquals("경포해수욕장", label.name) // 128 m의 '경포해변인증센터'(자전거여행)는 건너뜀
        assertEquals("해수욕장·해변", label.category)
        assertEquals(18, fake.urls.size) // 1차 6 + 2차 10 + 주소 1 + 명소 1
    }

    @Test
    fun `주행 도착 지점 — 좌표→주소의 건물 이름만`() = runBlocking {
        val fake = FakeKakao(c2a = "real/kakao_c2a_cityhall.json")
        val label = found(provider(fake).lookup(cityhall))
        assertEquals("서울특별시청", label.name)
        assertEquals("", label.category)
        assertEquals("서울특별시 중구 세종대로 110", label.address)
        assertEquals(1, fake.urls.size)
        assertTrue("x=126.978000&y=37.566500" in fake.urls.single())
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
        assertEquals(16, fake.categoryCalls()) // 1차에서 20 m 안이 없으니 2차까지
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
        val r = provider(FakeKakao(c2a = "real/kakao_c2a_gyeongpo.json")).lookup(cityhall)
        assertEquals(Lookup.NotFound, r)
    }

    @Test
    fun `요청 URL 형식`() {
        assertEquals(
            "https://dapi.kakao.com/v2/local/search/category.json?category_group_code=FD6" +
                "&x=126.999500&y=37.570000&radius=80&sort=distance&size=3",
            KakaoLocalProvider.categoryUrl(gwangjang, "FD6"),
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
