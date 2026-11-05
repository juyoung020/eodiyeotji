package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceQuery
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 실제 카카오 API로 한 번 확인 (요청 7~8번). 평소에는 건너뛴다.
 * 돌리려면: `KAKAO_LIVE=1 ./gradlew testDebugUnitTest --tests '*KakaoLiveTest*'`
 * (키는 환경 변수 KAKAO_REST_API_KEY, 없으면 저장소 루트 local.properties)
 */
class KakaoLiveTest {
    @Test
    fun `실제 API — 초당 머문 곳과 서울시청 도착 지점`() = runBlocking {
        assumeTrue(System.getenv("KAKAO_LIVE") == "1")
        val key = System.getenv("KAKAO_REST_API_KEY")?.takeIf { it.isNotBlank() } ?: localPropertiesKey()
        assumeTrue(key.isNotBlank())
        val p = KakaoLocalProvider(key)

        val stay = (p.lookup(PlaceQuery(1, 37.7905, 128.9150, isStay = true)) as Lookup.Found).label
        println("초당 머문 곳 → $stay")
        assertEquals("두부전문점", stay.category)

        val end = (p.lookup(PlaceQuery(2, 37.5665, 126.9780, isStay = false)) as Lookup.Found).label
        println("서울시청 도착 → $end")
        assertEquals("서울특별시청", end.name)
    }

    /** 단위 테스트의 작업 폴더는 app/ → 저장소 루트의 local.properties */
    private fun localPropertiesKey(): String {
        val f = File("../local.properties").takeIf { it.exists() } ?: return ""
        return Properties().apply { f.inputStream().use { load(it) } }.getProperty("KAKAO_REST_API_KEY").orEmpty().trim()
    }
}
