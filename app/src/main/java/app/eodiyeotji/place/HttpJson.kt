package app.eodiyeotji.place

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 플랫폼 [HttpURLConnection]만 쓰는 GET (추가 HTTP 라이브러리 없음). 반드시 IO 스레드에서 부른다.
 * 2xx가 아니면 [HttpStatusException], 연결·읽기 실패는 [IOException].
 *
 * 빠르게: 짧은 타임아웃, 연결 재사용(keep-alive — 본문을 끝까지 읽고 닫으면 연결이 풀로 돌아간다,
 * 성공 때는 disconnect()를 부르지 않는다), 풀에 남겨 둘 연결 수를 늘림(머문 곳 하나에 요청 18개를 동시에 보내므로).
 */
object PlatformHttp : HttpGetter {
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 3_000

    init {
        // 기본 5개 → 동시 요청이 끝난 뒤에도 연결을 버리지 않게. 다른 코드가 이미 정했으면 건드리지 않는다.
        if (System.getProperty("http.maxConnections") == null) System.setProperty("http.maxConnections", "24")
    }

    override fun get(url: String, headers: Map<String, String>): String {
        val conn = open(url, "GET")
        try {
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = runCatching { conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                throw HttpStatusException(code, body?.take(200) ?: conn.responseMessage.orEmpty())
            }
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            conn.disconnect()
            throw e
        } catch (e: RuntimeException) {
            // 잘못된 응답 헤더 등 플랫폼이 RuntimeException으로 던지는 경우도 네트워크 오류로 다룬다
            conn.disconnect()
            throw IOException(e)
        }
    }

    /** JSON 본문 POST (구글 Places Text Search 등). 2xx가 아니면 [HttpStatusException]. IO 스레드에서. */
    fun postJson(url: String, body: String, headers: Map<String, String>): String {
        val conn = open(url, "POST")
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                throw HttpStatusException(code, err?.take(200) ?: conn.responseMessage.orEmpty())
            }
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            conn.disconnect()
            throw e
        } catch (e: RuntimeException) {
            conn.disconnect()
            throw IOException(e)
        }
    }

    override fun head(url: String) {
        val conn = open(url, "HEAD")
        try {
            conn.responseCode
            conn.inputStream.close()
        } catch (e: IOException) {
            // 4xx여도 연결(DNS·TLS)은 열렸다 → 오류 본문을 비워 연결을 풀로 돌린다
            runCatching { conn.errorStream?.close() }
        } catch (e: RuntimeException) {
            conn.disconnect()
            throw IOException(e)
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
        }
}

/** 막는(blocking) HTTP 호출을 기다리는 쪽에서 떼어 내는 범위. 기다림을 그만둬도 요청은 여기서 조용히 끝난다. */
private val detachedIo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * [block](막는 HTTP 호출)을 IO 스레드에서 돌리되 [ms] 안에 안 끝나면 바로 null.
 * `withTimeoutOrNull { withContext(IO) { http.get() } }`는 막힌 스레드를 깨우지 못해 DNS · 연결 · 읽기 시간을
 * 다 기다린 뒤에야 돌아온다(오프라인이면 수 초~십수 초). 여기서는 요청을 떼어 놓고 기다림만 끊는다.
 * [block]이 던진 예외는 그대로 다시 던진다.
 */
internal suspend fun <T> blockingWithTimeout(ms: Long, block: () -> T): T? {
    val job = detachedIo.async { block() }
    return withTimeoutOrNull(ms) { job.await() }
}
