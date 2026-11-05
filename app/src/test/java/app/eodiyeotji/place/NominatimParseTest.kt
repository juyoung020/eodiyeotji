package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceQuery
import app.eodiyeotji.core.PlaceSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nominatim 응답 해석. 표본(src/test/resources/place/real/nominatim_*.json)은 2026-11-06 실제 응답을 한 번 받아 저장한 것. */
class NominatimParseTest {

    @Test
    fun `광장시장 — 가게 이름과 분류, 주소의 이상한 쉼표 정리`() {
        val l = NominatimParse.reverse(fixture("real/nominatim_gwangjang.json"))!!
        assertEquals("CU 광장시장점", l.name)
        assertEquals("편의점", l.category)
        assertEquals("서울특별시 종로구 창경궁로 88", l.address)
        assertEquals(PlaceSource.OSM, l.source)
    }

    @Test
    fun `초당순두부마을 — 음식점`() {
        val l = NominatimParse.reverse(fixture("real/nominatim_chodang.json"))!!
        assertEquals("초당할머니순두부", l.name)
        assertEquals("음식점", l.category)
        assertEquals("강원특별자치도 강릉시 초당순두부길", l.address)
    }

    @Test
    fun `서울시청 — name ko가 없으면 name 계열`() {
        val l = NominatimParse.reverse(fixture("real/nominatim_cityhall.json"))!!
        assertEquals("Happy Plus Cafe", l.name)
        assertEquals("카페", l.category)
        assertEquals("서울특별시 중구 세종대로 110", l.address)
    }

    @Test
    fun `경포해변 — 도로가 잡히면 구·동 이름`() {
        val l = NominatimParse.reverse(fixture("real/nominatim_gyeongpo.json"))!!
        assertEquals("강릉시 경포동", l.name)
        assertEquals("", l.category)
        assertEquals("강원특별자치도 강릉시 창해로", l.address)
    }

    @Test
    fun `바다 한가운데 등 결과 없음`() {
        assertNull(NominatimParse.reverse("""{"error":"Unable to geocode"}"""))
    }

    @Test
    fun `name ko를 name보다 먼저`() {
        val json = """{"category":"tourism","type":"attraction","name":"Gyeongbokgung",
            "namedetails":{"name":"Gyeongbokgung","name:ko":"경복궁"},"address":{"borough":"종로구","city":"서울특별시"}}"""
        val l = NominatimParse.reverse(json)!!
        assertEquals("경복궁", l.name)
        assertEquals("관광명소", l.category)
    }

    @Test
    fun `정책 지키기 — User-Agent 헤더와 요청 URL`() = runBlocking {
        var seenUa: String? = null
        var seenUrl = ""
        val p = NominatimProvider("eodiyeotji/test", { url, headers ->
            seenUrl = url
            seenUa = headers["User-Agent"]
            fixture("real/nominatim_chodang.json")
        }, RateGate(0))
        val r = p.lookup(PlaceQuery(1, 37.7905, 128.9150, true))
        assertTrue(r is Lookup.Found)
        assertEquals("eodiyeotji/test", seenUa)
        assertEquals(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=37.790500&lon=128.915000" +
                "&zoom=18&addressdetails=1&namedetails=1&accept-language=ko",
            seenUrl,
        )
    }
}
