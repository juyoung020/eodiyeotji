package app.eodiyeotji.convoy

import app.eodiyeotji.BuildConfig
import app.eodiyeotji.place.PlatformHttp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import org.json.JSONObject

/** 같이 가기의 목적지 (서버와 주고받는 값 그대로). */
data class ConvoyPlace(val name: String, val lat: Double, val lon: Double)

/** 내가 들어간 모임. */
data class ConvoySession(val id: String, val code: String, val memberId: String, val dest: ConvoyPlace?, val expiresAtMs: Long)

/** 서버가 준 차 한 대 (나 포함). */
data class ConvoyMemberDto(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val heading: Double,
    val speedMps: Double,
    val etaS: Long,
    val tsMs: Long,
    val ev: Boolean = false,
    /** 남은 % · 주행 가능 km (모르면 음수) */
    val energyPct: Double = -1.0,
    val rangeKm: Double = -1.0,
)

/** [waypoint]: 모두 함께 들를 곳 (충전소 · 주유소), 없으면 null. */
data class ConvoyGroupDto(val dest: ConvoyPlace?, val members: List<ConvoyMemberDto>, val nowMs: Long, val waypoint: ConvoyPlace? = null)

/** 내 위치 한 번 보내기. */
data class ConvoyPosition(
    val lat: Double,
    val lon: Double,
    val heading: Double,
    val speedMps: Double,
    val etaS: Long,
    val tsMs: Long,
    val ev: Boolean = false,
    val energyPct: Double = -1.0,
    val rangeKm: Double = -1.0,
)

/** 모임 서버. 모든 함수는 막는(blocking) 호출 → IO 스레드에서, 짧은 타임아웃으로 부른다. 실패하면 예외. */
interface ConvoyBackend {
    /** 중계 서버 없이 이 기기 안에서만 도는지 (디버그 시연) */
    val local: Boolean
    fun create(name: String, dest: ConvoyPlace?): ConvoySession
    fun join(code: String, name: String): ConvoySession
    fun post(s: ConvoySession, p: ConvoyPosition): ConvoyGroupDto
    fun state(s: ConvoySession): ConvoyGroupDto
    fun setDest(s: ConvoySession, dest: ConvoyPlace?): ConvoyGroupDto
    /** 모두 함께 들를 곳 (null이면 뺀다) */
    fun setWaypoint(s: ConvoySession, stop: ConvoyPlace?): ConvoyGroupDto
    fun leave(s: ConvoySession)
}

/** 중계 서버 `server/relay`의 `/convoy` (src/convoy.js). */
class RelayConvoyBackend(private val base: String, private val token: String) : ConvoyBackend {
    override val local = false
    private val headers get() = mapOf("X-Eodi-App" to token)

    private fun post(path: String, body: JSONObject): JSONObject = JSONObject(PlatformHttp.postJson("$base/convoy$path", body.toString(), headers))

    private fun session(o: JSONObject) = ConvoySession(
        id = o.getString("id"),
        code = o.getString("code"),
        memberId = o.getString("member_id"),
        dest = o.optJSONObject("dest")?.toPlace(),
        expiresAtMs = o.optLong("expires_at"),
    )

    override fun create(name: String, dest: ConvoyPlace?) =
        session(post("", JSONObject().put("name", name).put("dest", dest?.toJson() ?: JSONObject.NULL)))

    override fun join(code: String, name: String) = session(post("/join", JSONObject().put("code", code).put("name", name)))

    override fun post(s: ConvoySession, p: ConvoyPosition) = group(
        post(
            "/${s.id}/pos",
            JSONObject().put("member_id", s.memberId).put("lat", p.lat).put("lng", p.lon).put("heading", p.heading)
                .put("speed", p.speedMps).put("eta_s", p.etaS)
                .put("ev", p.ev).put("energy_pct", p.energyPct).put("range_km", p.rangeKm),
        ),
    )

    override fun state(s: ConvoySession) = group(JSONObject(PlatformHttp.get("$base/convoy/${s.id}?member=${s.memberId}", headers)))

    override fun setDest(s: ConvoySession, dest: ConvoyPlace?) =
        group(post("/${s.id}/dest", JSONObject().put("member_id", s.memberId).put("dest", dest?.toJson() ?: JSONObject.NULL)))

    override fun setWaypoint(s: ConvoySession, stop: ConvoyPlace?) =
        group(post("/${s.id}/dest", JSONObject().put("member_id", s.memberId).put("waypoint", stop?.toJson() ?: JSONObject.NULL)))

    override fun leave(s: ConvoySession) {
        post("/${s.id}/leave", JSONObject().put("member_id", s.memberId))
    }

    private fun group(o: JSONObject): ConvoyGroupDto {
        val arr = o.optJSONArray("members")
        val members = (0 until (arr?.length() ?: 0)).map { i ->
            val m = arr!!.getJSONObject(i)
            ConvoyMemberDto(
                id = m.getString("id"),
                name = m.optString("name"),
                lat = m.getDouble("lat"),
                lon = m.getDouble("lng"),
                heading = m.optDouble("heading", 0.0),
                speedMps = m.optDouble("speed", 0.0),
                etaS = m.optLong("eta_s", -1),
                tsMs = m.optLong("ts"),
                ev = m.optBoolean("ev"),
                energyPct = m.optDouble("energy_pct", -1.0),
                rangeKm = m.optDouble("range_km", -1.0),
            )
        }
        return ConvoyGroupDto(o.optJSONObject("dest")?.toPlace(), members, o.optLong("now", System.currentTimeMillis()), o.optJSONObject("waypoint")?.toPlace())
    }

    private fun ConvoyPlace.toJson() = JSONObject().put("name", name).put("lat", lat).put("lng", lon)
    private fun JSONObject.toPlace() = ConvoyPlace(optString("name"), getDouble("lat"), getDouble("lng"))
}

/**
 * 중계 서버 주소(`RELAY_URL`)가 없을 때: 이 기기 안에서만 모임을 흉내 낸다 (에뮬레이터 한 대 시연).
 * 디버그 빌드에서 [addFake]로 가짜 차를 넣으면 내 위치 근처에서 목적지 쪽으로 움직인다 (ConvoyRepository의 디버그 브로드캐스트).
 */
class LocalConvoyBackend : ConvoyBackend {
    override val local = true
    private var dest: ConvoyPlace? = null
    private var waypoint: ConvoyPlace? = null
    private var me: ConvoyPosition? = null

    /** 가짜 차: 출발점 → 목적지로 [speedMps]로 곧게 간다. */
    private class Fake(
        val id: String,
        val name: String,
        val startLat: Double,
        val startLon: Double,
        val speedMps: Double,
        val startMs: Long,
        val ev: Boolean,
        val pct: Double,
        val rangeKm: Double,
    )
    private val fakes = ConcurrentHashMap<String, Fake>()

    override fun create(name: String, dest: ConvoyPlace?): ConvoySession {
        this.dest = dest
        val code = (100000..999999).random().toString()
        return ConvoySession("local-$code", code, "me", dest, System.currentTimeMillis() + 12 * 3600_000L)
    }

    override fun join(code: String, name: String) = create(name, dest)

    override fun post(s: ConvoySession, p: ConvoyPosition): ConvoyGroupDto {
        me = p
        return state(s)
    }

    override fun state(s: ConvoySession): ConvoyGroupDto {
        val now = System.currentTimeMillis()
        val members = fakes.values.map { f -> f.at(now) }
        return ConvoyGroupDto(dest, members, now, waypoint)
    }

    override fun setDest(s: ConvoySession, dest: ConvoyPlace?): ConvoyGroupDto {
        this.dest = dest
        return state(s)
    }

    override fun setWaypoint(s: ConvoySession, stop: ConvoyPlace?): ConvoyGroupDto {
        waypoint = stop
        return state(s)
    }

    override fun leave(s: ConvoySession) {
        fakes.clear()
        dest = null
        waypoint = null
    }

    /**
     * 가짜 차 하나 ([behindKm]: 내 위치에서 목적지 반대쪽으로 이만큼 뒤에서 출발. 음수면 앞).
     * 내 위치를 아직 모르면 목적지 근처(없으면 서울 시청)에서 출발한다.
     */
    fun addFake(name: String, behindKm: Double, speedKmh: Double, ev: Boolean = true, pct: Double = -1.0, rangeKm: Double = -1.0) {
        if (!BuildConfig.DEBUG) return
        val d = dest
        val base = me?.let { it.lat to it.lon } ?: d?.let { it.lat + 0.05 to it.lon } ?: (37.5665 to 126.9780)
        // 목적지에서 나로 향하는 방향(위도 · 경도 단위)으로 behindKm만큼 더 간 자리. 목적지가 없으면 북쪽.
        val (dLat, dLon) = if (d != null) (base.first - d.lat) to (base.second - d.lon) else 1.0 to 0.0
        val norm = kotlin.math.hypot(dLat * 111.0, dLon * 111.0 * cos(Math.toRadians(base.first))).coerceAtLeast(1e-6)
        val lat = base.first + dLat / norm * behindKm
        val lon = base.second + dLon / norm * behindKm
        val id = "fake-${fakes.size + 1}"
        fakes[id] = Fake(id, name, lat, lon, speedKmh / 3.6, System.currentTimeMillis(), ev, pct, rangeKm)
    }

    private fun Fake.at(now: Long): ConvoyMemberDto {
        val d = dest
        val t = (now - startMs) / 1000.0
        // 시연용 움직임: 목적지 쪽으로 곧게 (좌표만 옮긴다, 길 모양은 없다)
        val (lat, lon) = if (d == null) {
            startLat to startLon + t * speedMps / 88_000.0
        } else {
            val totalLat = d.lat - startLat
            val totalLon = d.lon - startLon
            val totalM = kotlin.math.hypot(totalLat * 111_000.0, totalLon * 111_000.0 * cos(Math.toRadians(startLat))).coerceAtLeast(1.0)
            val f = (t * speedMps / totalM).coerceAtMost(1.0)
            (startLat + totalLat * f) to (startLon + totalLon * f)
        }
        return ConvoyMemberDto(id, name, lat, lon, -1.0, speedMps, -1, now, ev, pct, rangeKm)
    }
}
