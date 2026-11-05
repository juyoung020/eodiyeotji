package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceQuery
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * 실제 카카오 API로 확인 (평소에는 건너뛴다). 요청 약 250번.
 * 돌리려면: `KAKAO_LIVE=1 ./gradlew testDebugUnitTest --tests '*KakaoLiveTest*'`
 * (키는 환경 변수 KAKAO_REST_API_KEY, 없으면 저장소 루트 local.properties)
 *
 * 응답 시간 테스트가 먼저 돌아야 "연결 없는 첫 요청"을 잴 수 있어서 이름 순서로 돌린다.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KakaoLiveTest {
    private val demo = listOf(
        "서울시청" to (37.5665 to 126.9780),
        "광장시장" to (37.5700 to 126.9995),
        "경복궁 주차장" to (37.5758 to 126.9777),
        "경포해변" to (37.8055 to 128.9087),
        "초당순두부마을" to (37.7905 to 128.9150),
    )

    /**
     * 시연 지점 5곳 응답 시간 (지점마다 1.5초 띄움): 첫 요청(DNS·TLS 포함) → 머문 곳 1바퀴 → 출발·도착 지점 1바퀴 →
     * 연결이 식은 뒤(6초 쉼) 미리 열기 없이 / 미리 열기(warmUp) 후.
     */
    @Test
    fun `1 실제 API — 응답 시간 p50·max`() = runBlocking {
        val p = KakaoLocalProvider(liveKey())
        var id = 100L
        suspend fun time(name: String, lat: Double, lon: Double, stay: Boolean, tag: String): Long {
            Thread.sleep(1_500) // 실제처럼 띄엄띄엄 (카카오 짧은 시간 한도에 걸리지 않게, 잰 시간에는 넣지 않음)
            val t0 = System.nanoTime()
            val r = p.lookup(PlaceQuery(id++, lat, lon, stay))
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("$tag $name: $ms ms → ${(r as? Lookup.Found)?.label?.name}")
            return ms
        }

        // KAKAO_LIVE_WARM_FIRST=1: 첫 요청 전에 미리 열기 (주행 중 미리 열어 둔 상황) — 따로 돌려 비교한다
        if (System.getenv("KAKAO_LIVE_WARM_FIRST") == "1") {
            val tw = System.nanoTime()
            p.warmUp()
            println("미리 열기(첫 연결): ${(System.nanoTime() - tw) / 1_000_000} ms")
        }
        val (n0, ll0) = demo[0]
        val coldMs = time(n0, ll0.first, ll0.second, true, "첫 요청 머문 곳")

        val stayMs = mutableListOf<Long>()
        for ((name, ll) in demo) stayMs += time(name, ll.first, ll.second, true, "머문 곳")
        val endMs = demo.map { (name, ll) -> time(name, ll.first, ll.second, false, "출발·도착") }

        Thread.sleep(5_000) // JVM은 쉬는 연결을 5초 뒤 닫는다 (+ time()의 1.5초)
        val idleMs = time(demo[1].first, demo[1].second.first, demo[1].second.second, true, "6초 쉰 뒤, 미리 열기 없이")
        Thread.sleep(5_000)
        val tw = System.nanoTime()
        p.warmUp()
        val warmUpMs = (System.nanoTime() - tw) / 1_000_000
        val warmedMs = time(demo[1].first, demo[1].second.first, demo[1].second.second, true, "6초 쉰 뒤, 미리 열기($warmUpMs ms) 후")

        println("== 첫 요청 $coldMs ms")
        println("== 머문 곳 p50=${p50(stayMs)} ms max=${stayMs.max()} ms (n=${stayMs.size})")
        println("== 출발·도착 p50=${p50(endMs)} ms max=${endMs.max()} ms (n=${endMs.size})")
        println("== 연결이 식은 뒤: 미리 열기 없이 $idleMs ms / 미리 열기 후 $warmedMs ms")
    }

    @Test
    fun `2 실제 API — 시장·고궁·음식점·시청 이름`() = runBlocking {
        val p = KakaoLocalProvider(liveKey())
        suspend fun name(lat: Double, lon: Double, stay: Boolean): app.eodiyeotji.core.PlaceLabel {
            Thread.sleep(1_500)
            return (p.lookup(PlaceQuery(1, lat, lon, stay)) as Lookup.Found).label.also { println("($lat, $lon, stay=$stay) → $it") }
        }

        assertEquals("광장시장", name(37.5700, 126.9995, stay = false).name)
        assertEquals("경복궁", name(37.5758, 126.9777, stay = false).name)
        assertEquals("두부전문점", name(37.7905, 128.9150, stay = true).category)
        assertEquals("서울특별시청", name(37.5665, 126.9780, stay = false).name)
    }

    private fun p50(xs: List<Long>) = xs.sorted()[xs.size / 2]

    private fun liveKey(): String {
        assumeTrue(System.getenv("KAKAO_LIVE") == "1")
        val key = System.getenv("KAKAO_REST_API_KEY")?.takeIf { it.isNotBlank() } ?: localPropertiesKey()
        assumeTrue(key.isNotBlank())
        return key
    }

    /** 단위 테스트의 작업 폴더는 app/ → 저장소 루트의 local.properties */
    private fun localPropertiesKey(): String {
        val f = File("../local.properties").takeIf { it.exists() } ?: return ""
        return Properties().apply { f.inputStream().use { load(it) } }.getProperty("KAKAO_REST_API_KEY").orEmpty().trim()
    }
}
