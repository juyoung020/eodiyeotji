package app.eodiyeotji.handoff

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import app.eodiyeotji.core.rust.HandoffPairing
import app.eodiyeotji.core.rust.ShareCard as RShareCard
import app.eodiyeotji.core.rust.handoffNewPairing
import app.eodiyeotji.core.rust.handoffOpen
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.rust.handoffOpenBytes
import app.eodiyeotji.core.rust.handoffOpenCard
import app.eodiyeotji.core.rust.handoffOpenPairing
import app.eodiyeotji.core.rust.handoffPairTopic
import app.eodiyeotji.core.rust.handoffSeal
import app.eodiyeotji.core.rust.handoffSealBytes
import app.eodiyeotji.core.rust.handoffSealCard
import app.eodiyeotji.core.rust.handoffSealPairing
import app.eodiyeotji.core.rust.ParkedCar
import app.eodiyeotji.core.rust.parkedFromJson
import app.eodiyeotji.core.rust.handoffSealParked
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
 * - 폰: [pairWithCode] → 연결 → [listen]으로 차가 보낸 기록·카드를 받는다.
 * 네트워크는 모두 IO 스레드, 짧은 타임아웃.
 */
@Singleton
class HandoffRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
) {
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
        // 새 연결이면 기록 동기화도 처음부터
        prefs.edit().clear().putString(K_TOPIC, topic).putString(K_KEY, key).putString(K_PEER, peer).apply()
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

    /** 차: 주차한 자리를 폰으로 보낸다 (P에 들어갈 때). 연결이 없거나 실패하면 false. */
    suspend fun sendParked(car: ParkedCar): Boolean = withContext(Dispatchers.IO) {
        val l = _link.value ?: return@withContext false
        runCatching { Ntfy.publish(l.topic, handoffSealParked(l.key, car), title = "어디였지", tags = "parking") }
            .onFailure { Log.w(TAG, "주차 위치 보내기 실패", it) }
            .isSuccess
    }

    /** 차: 다시 출발했음을 폰에 알린다 (폰은 받아 둔 주차 위치를 지운다). */
    suspend fun sendMoving(tMs: Long): Boolean = withContext(Dispatchers.IO) {
        val l = _link.value ?: return@withContext false
        runCatching { Ntfy.publish(l.topic, handoffSeal(l.key, MOVING + tMs)) }.isSuccess
    }

    /**
     * 차 → 폰 기록 동기화 (core-rs/src/sync.rs). 지난번 보낸 시각 뒤의 원본을 묶음으로 보낸다.
     * 폰이 받으면 "ack:시각"을 돌려준다. 중계 서버는 글을 12시간(첨부는 3시간)만 두므로, 보낸 지 한참 됐는데
     * 폰이 받았다고 하지 않으면 받은 곳부터 다시 보낸다. 반환: 보낸 묶음 수.
     */
    suspend fun syncNow(): Int = withContext(Dispatchers.IO) {
        val l = _link.value ?: return@withContext 0
        // 폰이 받았다고 알린 시각 읽기
        val ackSince = prefs.getString(K_ACK_SINCE, null) ?: "12h"
        val msgs = runCatching { Ntfy.poll(l.topic, ackSince) }.getOrDefault(emptyList())
        if (msgs.isNotEmpty()) prefs.edit().putString(K_ACK_SINCE, msgs.last().id).apply()
        msgs.mapNotNull { m -> runCatching { handoffOpen(l.key, m.message) }.getOrNull()?.takeIf { it.startsWith(ACK) } }
            .mapNotNull { it.removePrefix(ACK).toLongOrNull() }.maxOrNull()
            ?.let { a -> if (a > prefs.getLong(K_SYNC_ACK, 0)) prefs.edit().putLong(K_SYNC_ACK, a).apply() }
        var sent = prefs.getLong(K_SYNC_SENT, 0)
        val acked = prefs.getLong(K_SYNC_ACK, 0)
        if (acked < sent && System.currentTimeMillis() - prefs.getLong(K_SYNC_AT, 0) > RESEND_MS) sent = acked
        var n = 0
        while (n < MAX_CHUNKS_PER_RUN) {
            val chunk = runCatching { core.rust.syncExport(sent, CHUNK_FIXES.toUInt()) }
                .onFailure { Log.w(TAG, "동기화 묶기 실패", it) }.getOrNull() ?: break
            if (chunk.data.isNotEmpty()) {
                // 머리: 표시 · 이 묶음이 이어지는 시각(from) · 담은 마지막 시각(until) → 폰이 빈틈을 알아챈다
                val head = java.nio.ByteBuffer.allocate(17).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .put(SYNC_MARK).putLong(sent).putLong(chunk.untilMs).array()
                val body = handoffSealBytes(l.key, head + chunk.data)
                // 요청 한도(429)에 걸리면 조금 쉬었다 다시 (최대 3번)
                var ok = false
                for (attempt in 0 until 3) {
                    val r = runCatching { Ntfy.publish(l.topic, body) }
                    if (r.isSuccess) { ok = true; break }
                    Log.w(TAG, "동기화 보내기 실패 (${attempt + 1}번째): ${r.exceptionOrNull()?.message}")
                    delay(RATE_WAIT_MS * (attempt + 1))
                }
                if (!ok) break
                n++
                delay(CHUNK_GAP_MS) // 공개 중계 서버 요청 한도를 넘지 않게
            }
            sent = chunk.untilMs
            prefs.edit().putLong(K_SYNC_SENT, sent).putLong(K_SYNC_AT, System.currentTimeMillis()).apply()
            if (chunk.done) break
        }
        if (n > 0) Log.i(TAG, "기록 동기화: 묶음 ${n}개 보냄")
        n
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

    /**
     * 폰: 차가 보내는 것을 열어 둔 연결 하나로 계속 받는다 (연결이 끊기면 돌아온다 → 부르는 쪽이 잠시 뒤 다시 부른다).
     * 기록 동기화 묶음은 바로 저장하고 받았다고 차에 알리며, 공유 카드는 [onCard]로 넘긴다.
     */
    suspend fun listen(
        onCard: (RShareCard) -> Unit,
        onParked: (ParkedCar) -> Unit = {},
        onMoving: (Long) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val l = _link.value ?: return@withContext
        val since = prefs.getString(K_SINCE, null) ?: "12h"
        runCatching {
            Ntfy.stream(l.topic, since) { m ->
                prefs.edit().putString(K_SINCE, m.id).apply()
                handle(l, m, onParked, onMoving)?.let(onCard)
            }
        }.onFailure { Log.i(TAG, "받기 연결 끊김: ${it.message}") }
    }

    private fun handle(l: HandoffLink, m: Ntfy.Msg, onParked: (ParkedCar) -> Unit, onMoving: (Long) -> Unit): RShareCard? {
        val body = runCatching { m.body() }.getOrNull() ?: return null
        val bytes = runCatching { handoffOpenBytes(l.key, body) }.getOrNull() ?: return null
        // 짧은 글: 주차 위치 · 다시 출발 (한 번 연 글을 그대로 본다)
        if (bytes.size < 4096 && bytes.isNotEmpty() && bytes[0] != SYNC_MARK) {
            val text = bytes.toString(Charsets.UTF_8)
            if (text.startsWith(PARK)) {
                parkedFromJson(text.removePrefix(PARK))?.let(onParked)
                return null
            }
            if (text.startsWith(MOVING)) {
                text.removePrefix(MOVING).toLongOrNull()?.let(onMoving)
                return null
            }
        }
        if (bytes.isNotEmpty() && bytes[0] == SYNC_MARK) {
            if (bytes.size < 17) return null
            val bb = java.nio.ByteBuffer.wrap(bytes, 1, 16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val from = bb.long
            val until = bb.long
            runCatching { core.rust.syncImport(bytes.copyOfRange(17, bytes.size)) }
                .onSuccess {
                    Log.i(TAG, "기록 받음: 점 ${it.fixes}개 · 이름 ${it.named}곳")
                    core.notifyChanged()
                    // 빈틈 없이 이어 받은 곳까지만 앞으로 (중간 묶음을 놓쳤으면 차가 그 뒤부터 다시 보낸다)
                    val have = prefs.getLong(K_SYNC_HAVE, 0)
                    val next = if (from <= have) maxOf(have, until) else have
                    prefs.edit().putLong(K_SYNC_HAVE, next).apply()
                    runCatching { Ntfy.publish(l.topic, handoffSeal(l.key, ACK + next)) }
                }
                .onFailure { Log.w(TAG, "기록 받기 실패", it) }
            return null
        }
        return runCatching { handoffOpenCard(l.key, body) }.getOrNull()
    }

    private companion object {
        const val TAG = "Handoff"
        const val K_TOPIC = "topic"
        const val K_KEY = "key"
        const val K_PEER = "peer"
        const val K_SINCE = "since"
        const val HELLO = "hello:"
        const val ACK = "ack:"
        const val MOVING = "moving:"
        const val PARK = "park:"
        const val K_SYNC_SENT = "sync_sent"
        const val K_SYNC_ACK = "sync_ack"
        const val K_SYNC_AT = "sync_at"
        const val K_SYNC_HAVE = "sync_have"
        const val K_ACK_SINCE = "ack_since"
        const val SYNC_MARK: Byte = 'S'.code.toByte()
        const val CHUNK_FIXES = 50_000
        const val MAX_CHUNKS_PER_RUN = 12
        const val RESEND_MS = 10 * 60_000L
        const val CHUNK_GAP_MS = 4_000L
        const val RATE_WAIT_MS = 20_000L
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
            if (c.responseCode !in 200..299) {
                val err = runCatching { c.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                throw IOException("ntfy ${c.responseCode} ${err?.take(160).orEmpty()}")
            }
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

    /** 열어 둔 연결로 새 글을 받을 때마다 [onMsg]. 서버가 45초마다 보내는 keepalive로 연결을 유지한다. 끊기면 예외로 돌아온다. */
    fun stream(topic: String, since: String, onMsg: (Msg) -> Unit) {
        val c = (URL("$BASE$topic/json?since=$since").openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = 120_000
        }
        try {
            if (c.responseCode !in 200..299) throw IOException("ntfy ${c.responseCode}")
            c.inputStream.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    if (o.optString("event") != "message") continue
                    onMsg(Msg(o.optString("id"), o.optString("message"), o.optJSONObject("attachment")?.optString("url")?.takeIf { it.isNotBlank() }))
                }
            }
        } finally {
            c.disconnect()
        }
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
