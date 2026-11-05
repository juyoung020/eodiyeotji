package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceQuery
import app.eodiyeotji.core.PlaceSource
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 카카오 로컬 API — 가게·시장·명소의 실제 이름을 찾는 기본(주) 출처. 출처 표시는 [PlaceSource.KAKAO].
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
 * ## 찾는 방법 (빠르게: 필요한 요청 19개를 한꺼번에 보낸다)
 * 머문 곳·출발·도착 지점 모두 같은 방법이다 (도착 지점도 "광장시장", "경복궁"처럼 목적지 이름이 되게,
 * 그리고 나중에 Rust가 그 지점을 머문 곳으로 바꿔도 이름이 맞게).
 * - 반경 [RADIUS_M] m 분류 검색(`/v2/local/search/category.json`, sort=distance) — [ALL_CODES] 16개 분류
 * - 전통시장 검색(`/v2/local/search/keyword.json?query=전통시장`) — 광장시장처럼 분류 코드가 없는 시장
 * - 반경 [LANDMARK_RADIUS_M] m 관광명소 — 경복궁·경포해수욕장처럼 이름 핀이 가운데 하나뿐인 넓은 곳
 * - 좌표→주소(`/v2/local/geo/coord2address.json`) — 건물 이름이나 "구 동" (마지막 수단)
 *
 * 고르는 규칙은 [KakaoParse.choose]. 응답은 모두 기다린 뒤 고른다 (먼저 온 응답으로 끊으면 도착 순서에 따라 이름이
 * 바뀌었다). 다만 [DECIDE_AFTER_MS] ms가 지나도 늦는 응답이 있으면 그때까지 온 것으로 고른다.
 *
 * 지점 하나의 요청은 기다림 없이 한꺼번에 나간다. 밀린 지점이 많을 때만 토큰 통([BURST]개, 초당 [PER_SECOND]개)으로
 * 늦춘다 (같은 지점은 Rust가 저장해 다시 묻지 않음). 한도 초과(429, 또는 카카오의 400 + code -10)가 오면 [PlaceResolver]가 쉰다.
 */
class KakaoLocalProvider(
    private val restKey: String,
    private val http: HttpGetter = PlatformHttp,
    private val bucket: TokenBucket = TokenBucket(BURST, PER_SECOND),
) : PlaceProvider {
    override val name = "kakao"

    private val headers = mapOf("Authorization" to "KakaoAK $restKey")
    private val inFlight = Semaphore(MAX_IN_FLIGHT)

    /** 요청은 여기서 돈다 → 늦는 응답을 기다리지 않고 돌아갈 수 있다 (남은 것은 취소, 결과는 버림). */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun lookup(q: PlaceQuery): Lookup {
        // 토큰을 먼저 다 받고 한꺼번에 보낸다 (밀린 지점이 많을 때만 여기서 기다림 → 아래 DECIDE_AFTER_MS는 보낸 뒤부터)
        repeat(REQUESTS_PER_POINT) { bucket.acquire() }
        val pois =ALL_CODES.map { code -> fetch(categoryUrl(q, code)) { KakaoParse.nearestPlace(it) } }
        val markets = fetch(marketUrl(q)) { KakaoParse.places(it) }
        val landmarks = fetch(landmarkUrl(q)) { KakaoParse.places(it) }
        val addr = fetch(coord2addressUrl(q)) { KakaoParse.coord2address(it) }
        val all: List<Deferred<*>> = pois + markets + landmarks + addr
        try {
            // 보통 수십 ms 안에 다 온다. 늦는 응답이 있어도 DECIDE_AFTER_MS 뒤에는 온 것으로 고른다
            // (아무것도 안 왔으면 연결·읽기 타임아웃까지 기다린다)
            if (withTimeoutOrNull(DECIDE_AFTER_MS) { all.joinAll() } == null && all.none { it.isCompleted }) all.joinAll()
            val label = KakaoParse.choose(
                pois = pois.mapNotNull { it.done() },
                markets = markets.done().orEmpty(),
                landmarks = landmarks.done().orEmpty(),
                addr = addr.done(),
            )
            return label?.let { Lookup.Found(it) } ?: Lookup.NotFound
        } finally {
            all.forEach { it.cancel() }
        }
    }

    /** 끝난 요청의 결과 (실패했으면 그 오류를 던진다 → 나중에 다시 묻기), 아직이면 null. */
    private suspend fun <T> Deferred<T>.done(): T? = if (isCompleted) await() else null

    /** 연결 미리 열기 (DNS·TLS): 주행 중에 불러 두면 주행이 끝난 뒤 첫 요청이 빨라진다. 결과·오류는 무시. */
    override suspend fun warmUp() {
        (1..WARM_CONNECTIONS).map {
            io.launch { runCatching { http.head(BASE) } }
        }.joinAll()
    }

    private fun <T> fetch(url: String, parse: (String) -> T): Deferred<T> =
        io.async { inFlight.withPermit { parse(http.get(url, headers)) } }

    companion object {
        const val BASE = "https://dapi.kakao.com"
        const val RADIUS_M = 80
        const val LANDMARK_RADIUS_M = 500
        const val PAGE_SIZE = 5
        const val DECIDE_AFTER_MS = 800L

        /** 한꺼번에 나가는 요청 수 상한 (지점 하나 = 19개, 지점 여러 개를 동시에 찾을 때도 이 안에서) */
        const val MAX_IN_FLIGHT = 24

        /** 지점 하나에 보내는 요청 수: 분류 16 + 전통시장 1 + 넓은 명소 1 + 좌표→주소 1 */
        const val REQUESTS_PER_POINT = 19

        /**
         * 카카오 요청 속도: 쉬고 있으면 [BURST]개(지점 2곳)까지 바로, 그 뒤로 1초에 [PER_SECOND]개.
         * 실측(2026-11-06): 1초 남짓에 요청 약 200개를 보내자 "API limit has been exceeded"(400, code -10).
         */
        const val BURST = 40
        const val PER_SECOND = 15.0

        /** 미리 열어 둘 연결 수 */
        private const val WARM_CONNECTIONS = 6

        /** 묻는 분류 전체 (어린이집 PS3·중개업소 AG2는 목적지로 쓸 일이 드물어 뺀다) */
        val ALL_CODES = listOf(
            "FD6", "CE7", "AT4", "CT1", "AD5", "MT1",
            "HP8", "PM9", "BK9", "PO3", "SC4", "AC5", "SW8", "CS2", "OL7", "PK6",
        )

        private val MARKET_QUERY = URLEncoder.encode("전통시장", "UTF-8")

        fun categoryUrl(q: PlaceQuery, code: String) =
            "$BASE/v2/local/search/category.json?category_group_code=$code" +
                "&x=${coord(q.lon)}&y=${coord(q.lat)}&radius=$RADIUS_M&sort=distance&size=$PAGE_SIZE"

        fun marketUrl(q: PlaceQuery) =
            "$BASE/v2/local/search/keyword.json?query=$MARKET_QUERY" +
                "&x=${coord(q.lon)}&y=${coord(q.lat)}&radius=${KakaoParse.MARKET_RADIUS_M.toInt()}&sort=distance&size=$PAGE_SIZE"

        /** 넓은 명소: 반경 안의 관광명소를 넉넉히 받아(10개) 고궁·해변·공원 같은 곳을 찾는다 */
        fun landmarkUrl(q: PlaceQuery) =
            "$BASE/v2/local/search/category.json?category_group_code=AT4" +
                "&x=${coord(q.lon)}&y=${coord(q.lat)}&radius=$LANDMARK_RADIUS_M&sort=distance&size=10"

        fun coord2addressUrl(q: PlaceQuery) =
            "$BASE/v2/local/geo/coord2address.json?x=${coord(q.lon)}&y=${coord(q.lat)}"

        /** 기기 언어가 쉼표 소수점이어도 "37.566500"으로 */
        private fun coord(v: Double) = String.format(Locale.ROOT, "%.6f", v)
    }
}
