package app.eodiyeotji.handoff

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import app.eodiyeotji.core.rust.HandoffPairing
import app.eodiyeotji.core.rust.ShareCard as RShareCard
import app.eodiyeotji.core.rust.handoffNewPairing
import app.eodiyeotji.core.rust.handoffOpen
import app.eodiyeotji.core.rust.handoffOpenCard
import app.eodiyeotji.core.rust.handoffOpenPairing
import app.eodiyeotji.core.rust.handoffPairTopic
import app.eodiyeotji.core.rust.handoffSeal
import app.eodiyeotji.core.rust.handoffSealCard
import app.eodiyeotji.core.rust.handoffSealPairing
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * 차 ↔ 폰 연결 (core-rs/src/handoff.rs 설명 참고).
 * - 전달: 공개 알림 중계 ntfy.sh의 주제 하나. 글은 모두 페어링 키로 잠겨 있어 중계 서버는 읽지 못한다.
 * - 차: [startPairing] → 코드를 화면에 띄우고 [awaitPhone]으로 폰 인사를 기다린다 → 공유 때 [sendCard].
 * - 폰: [pairWithCode] → 연결 → [pollCards]로 차가 보낸 카드를 받는다.
 * 네트워크는 모두 IO 스레드, 짧은 타임아웃.
 */
@Singleton
class HandoffRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("handoff", Context.MODE_PRIVATE)
    private val _link = MutableStateFlow(readLink())

    /** 연결된 상대 (없으면 null). */
    val link: StateFlow<HandoffLink?> = _link.asStateFlow()

    private fun readLink(): HandoffLink? {
        val topic = prefs.getString(K_TOPIC, null) ?: return null
        val key = prefs.getString(K_KEY, null) ?: return null
        return HandoffLink(topic, key, prefs.getString(K_PEER, "").orEmpty())
    }

    private fun save(topic: String, key: String, peer: String) {
        prefs.edit().putString(K_TOPIC, topic).putString(K_KEY, key).putString(K_PEER, peer).apply()
        _link.value = HandoffLink(topic, key, peer)
    }

    /** 연결 끊기 (양쪽이 각자 지운다). */
    fun unlink() {
        prefs.edit().clear().apply()
        _link.value = null
    }

    // ---------- 차 ----------

    /** 새 코드를 만들고 페어링 글을 중계에 올린다. */
    suspend fun startPairing(): HandoffPairing = withContext(Dispatchers.IO) {
        val p = handoffNewPairing()
        Ntfy.publish(handoffPairTopic(p.code), handoffSealPairing(p))
        p
    }

    /** 폰의 "연결됨" 인사를 [timeoutMs] 동안 기다린다. 오면 저장하고 true. */
    suspend fun awaitPhone(p: HandoffPairing, timeoutMs: Long = 5 * 60_000L): Boolean = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() / 1000 - 5
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val hello = runCatching { Ntfy.poll(p.topic, since.toString()) }.getOrDefault(emptyList())
                    .firstNotNullOfOrNull { m -> runCatching { handoffOpen(p.key, m.body()) }.getOrNull()?.takeIf { it.startsWith(HELLO) } }
                if (hello != null) {
                    save(p.topic, p.key, hello.removePrefix(HELLO))
                    return@withTimeoutOrNull true
                }
                delay(POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
    }

    /** 공유 카드를 폰으로 보낸다. 연결이 없거나 실패하면 false. */
    suspend fun sendCard(card: RShareCard): Boolean = withContext(Dispatchers.IO) {
        val l = _link.value ?: return@withContext false
        runCatching { Ntfy.publish(l.topic, handoffSealCard(l.key, card), title = "어디였지", tags = "car") }
            .onFailure { Log.w(TAG, "폰으로 보내기 실패", it) }
            .isSuccess
    }

    // ---------- 폰 ----------

    /** 입력한 코드로 차와 연결한다. 성공하면 true. */
    suspend fun pairWithCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val msgs = runCatching { Ntfy.poll(handoffPairTopic(code), "15m") }.getOrDefault(emptyList())
        val p = msgs.asReversed().firstNotNullOfOrNull { handoffOpenPairing(code, it.body()) } ?: return@withContext false
        val name = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        runCatching { Ntfy.publish(p.topic, handoffSeal(p.key, HELLO + name)) }.getOrElse { return@withContext false }
        save(p.topic, p.key, "")
        prefs.edit().putString(K_SINCE, (System.currentTimeMillis() / 1000).toString()).apply()
        true
    }

    /** 지난번 이후 차가 보낸 카드들 (인사 같은 다른 글은 건너뜀). */
    suspend fun pollCards(): List<RShareCard> = withContext(Dispatchers.IO) {
        val l = _link.value ?: return@withContext emptyList()
        val since = prefs.getString(K_SINCE, null) ?: "1h"
        val msgs = runCatching { Ntfy.poll(l.topic, since) }.getOrElse { return@withContext emptyList() }
        if (msgs.isNotEmpty()) prefs.edit().putString(K_SINCE, msgs.last().id).apply()
        msgs.mapNotNull { m -> runCatching { handoffOpenCard(l.key, m.body()) }.getOrNull() }
    }

    private companion object {
        const val TAG = "Handoff"
        const val K_TOPIC = "topic"
        const val K_KEY = "key"
        const val K_PEER = "peer"
        const val K_SINCE = "since"
        const val HELLO = "hello:"
        const val POLL_MS = 2_000L
    }
}

@Immutable
data class HandoffLink(val topic: String, val key: String, val peer: String)

/** ntfy.sh 짧은 HTTP 호출. 4KB 넘는 글은 ntfy가 첨부 파일로 바꾸므로 받을 때 첨부를 따라 받는다. */
internal object Ntfy {
    private const val BASE = "https://ntfy.sh/"
    private const val TIMEOUT_MS = 6_000

    data class Msg(val id: String, val message: String, val attachment: String?) {
        fun body(): String = attachment?.let { get(it) } ?: message
    }

    fun publish(topic: String, body: String, title: String? = null, tags: String? = null) {
        val c = open(BASE + topic, "POST")
        try {
            c.doOutput = true
            title?.let { c.setRequestProperty("Title", it) }
            tags?.let { c.setRequestProperty("Tags", it) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) throw IOException("ntfy ${c.responseCode}")
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /** [since] = 메시지 id, 유닉스 초, 또는 "15m" 같은 기간. */
    fun poll(topic: String, since: String): List<Msg> {
        val text = get("$BASE$topic/json?poll=1&since=$since")
        return text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val o = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
            if (o.optString("event") != "message") return@mapNotNull null
            Msg(o.optString("id"), o.optString("message"), o.optJSONObject("attachment")?.optString("url")?.takeIf { it.isNotBlank() })
        }.toList()
    }

    fun get(url: String): String {
        val c = open(url, "GET")
        try {
            if (c.responseCode !in 200..299) throw IOException("ntfy ${c.responseCode}")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String, method: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }
}
