package app.eodiyeotji.place

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 플랫폼 [HttpURLConnection]만 쓰는 GET (추가 HTTP 라이브러리 없음). 반드시 IO 스레드에서 부른다.
 * 2xx가 아니면 [HttpStatusException], 연결·읽기 실패는 [IOException].
 */
object PlatformHttp : HttpGetter {
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    override fun get(url: String, headers: Map<String, String>): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = runCatching { conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                throw HttpStatusException(code, body?.take(200) ?: conn.responseMessage.orEmpty())
            }
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            // 잘못된 응답 헤더 등 플랫폼이 RuntimeException으로 던지는 경우도 네트워크 오류로 다룬다
            throw IOException(e)
        } finally {
            conn.disconnect()
        }
    }
}
