package app.eodiyeotji.place

import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceQuery
import java.io.IOException
import org.json.JSONObject

/**
 * 좌표 → 장소 이름을 찾는 온라인 지도 API 하나 (카카오 로컬, OSM Nominatim).
 *
 * [PlaceResolver]가 IO 스레드에서 부른다. 구현은 네트워크 호출과 응답 해석만 한다
 * (어느 지점을 물을지·출처 우선순위는 Rust `core-rs/src/places.rs`가 정한다).
 *
 * 실패 규칙:
 * - 결과 없음 → [Lookup.NotFound] (다음 출처로 넘어간다)
 * - 네트워크 오류 → [IOException] (다음 출처로 넘어가지 않고 나중에 다시 묻는다)
 * - HTTP 오류 → [HttpStatusException] (401·403이면 이 출처를 끈다, 429·503이면 잠시 쉰다)
 */
interface PlaceProvider {
    /** 로그에 쓰는 이름 */
    val name: String

    suspend fun lookup(q: PlaceQuery): Lookup

    /** 연결을 미리 열어 둔다 (DNS·TLS). 실패해도 조용히 넘어간다. */
    suspend fun warmUp() {}
}

sealed interface Lookup {
    data class Found(val label: PlaceLabel) : Lookup
    data object NotFound : Lookup
}

/** 2xx가 아닌 HTTP 응답. [body]는 오류 본문 앞부분. */
class HttpStatusException(val code: Int, val body: String) : IOException("HTTP $code: $body") {
    /** 키가 없거나 틀림 → 이 출처는 앱이 다시 켜질 때까지 쓰지 않는다 */
    val isAuthError: Boolean get() = code == 401 || code == 403

    /** 요청이 너무 많음·서버 과부하 → 잠시 쉬고 다시. 카카오는 한도 초과를 400 + `"code":-10`으로 알린다 */
    val isRateLimited: Boolean get() = code == 429 || code == 503 || (code == 400 && KAKAO_LIMIT.containsMatchIn(body))

    private companion object {
        val KAKAO_LIMIT = Regex("\"code\"\\s*:\\s*-10\\b")
    }
}

/** 블로킹 HTTP GET (응답 본문 문자열). 테스트에서는 저장해 둔 응답 파일로 바꿔 끼운다. */
fun interface HttpGetter {
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String>): String

    /** 연결만 열어 두는 가벼운 HEAD (연결 미리 열기용). 기본은 아무것도 안 함. */
    @Throws(IOException::class)
    fun head(url: String) {}
}

// ---- org.json 도우미 (Android 판은 null 값을 "null" 문자열로 돌려주므로 직접 거른다) ----

/** 값이 없거나 null이거나 빈 문자열이면 null. */
internal fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
