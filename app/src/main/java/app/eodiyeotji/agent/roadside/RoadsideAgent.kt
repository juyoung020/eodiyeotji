package app.eodiyeotji.agent.roadside

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import app.eodiyeotji.R
import app.eodiyeotji.agent.ProactiveCenter
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.MemoryQuery
import app.eodiyeotji.core.rust.GeoPoint
import app.eodiyeotji.core.rust.NavSession
import app.eodiyeotji.core.rust.RoadsideCall
import app.eodiyeotji.core.rust.RoadsideCategory
import app.eodiyeotji.core.rust.RoadsideHit
import app.eodiyeotji.core.rust.RoadsidePlace
import app.eodiyeotji.core.rust.navDirectionsUrl
import app.eodiyeotji.core.rust.roadsideBundled
import app.eodiyeotji.core.rust.roadsideHitsJson
import app.eodiyeotji.core.rust.roadsideKakaoQuery
import app.eodiyeotji.core.rust.roadsideNeed
import app.eodiyeotji.core.rust.roadsideParseCall
import app.eodiyeotji.core.rust.roadsideParseJsonCall
import app.eodiyeotji.core.rust.roadsideParseKakao
import app.eodiyeotji.core.rust.roadsidePick
import app.eodiyeotji.core.rust.roadsideProbePoints
import app.eodiyeotji.core.rust.roadsideRank
import app.eodiyeotji.core.rust.roadsideSystemPrompt
import app.eodiyeotji.core.rust.roadsideToolSpecs
import app.eodiyeotji.place.ApiAccess
import app.eodiyeotji.place.PlatformHttp
import app.eodiyeotji.place.blockingWithTimeout
import app.eodiyeotji.platform.EnergySource
import app.eodiyeotji.platform.GaugeSource
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RouteStop
import app.eodiyeotji.platform.SpeechOutput
import app.eodiyeotji.ui.agent.AgentEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** 길 위 카드의 선택지 하나. */
@Immutable
data class RoadsideOption(
    val name: String,
    /** "휴게소" · "졸음쉼터" */
    val kind: String,
    /** "12km · 8분 앞" */
    val ahead: String,
    /** 공공데이터포털 기기 목록에서 온 곳 (카드에 출처 표시) */
    val publicData: Boolean = false,
)

/** Rust `roadside::BUNDLED_SOURCE`와 같은 값: 공공데이터포털 휴게소 · 졸음쉼터 표준데이터에서 온 곳. */
const val ROADSIDE_PUBLIC_SOURCE = "공공데이터"

/** 길 위 카드: 1순위 + 다른 선택 (주행 중엔 1·2번만, 정차 중엔 3개까지). */
@Immutable
data class RoadsideCard(
    val options: List<RoadsideOption>,
    val addsWaypoint: Boolean,
    /** 정차 중 목록에서 고른 곳 (제안 버튼이 이곳을 가리킨다). 처음엔 1순위. */
    val selected: Int = 0,
)

/**
 * 길 위의 필요("나 똥마려" · "졸려" · "배고파")를 스스로 처리하는 도구 에이전트.
 *
 * - **LLM 길 (기본):** [LlmChat]에 도구 정의(Rust `roadside_tool_specs`)를 주고 최대 [MAX_ROUNDS]번 도구를 부르게 한다.
 *   모델은 해석 · 계획만 하고, 도구는 여기서 실행한다: 주행 상황, 경로 앞쪽 찾기(Rust `roadside_rank`), 기억 찾기, 경유지 · 안내 **제안**.
 *   네이티브 `tool_calls`가 없으면 글 속 `{"tool":..,"args":..}`를 Rust가 찾아 같은 길로 (Qwen 예비).
 * - **빠른 길:** 흔한 말은 Rust 규칙(`roadside_need`)이 바로 알아듣고, 그 검색을 LLM과 **동시에** 미리 돌린다.
 *   LLM이 없거나 늦거나 실패하면 그 결과로 규칙 답(`roadside_pick`)을 낸다.
 * - 실행(경유지 넣기 · 안내)은 운전자의 "응" / 버튼 뒤 [confirm]으로만. 경유지는 내비 제한대로 기존 것 하나 + 새 것 = 2개까지.
 * - 네트워크(카카오 · LLM)는 짧은 타임아웃, 끊기면 기기 안 휴게소 · 졸음쉼터 목록으로.
 */
@Singleton
class RoadsideAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
    private val navi: Navigator,
    private val proactive: ProactiveCenter,
    private val energy: EnergySource,
    private val gauges: GaugeSource,
    private val tts: SpeechOutput,
) {
    private val llm: LlmChat = OpenAiCompatChat()
    private val tools: String by lazy { roadsideToolSpecs() }

    /** 이번 대화에서 찾은 곳 (id → 곳). 제안 · 확인에 쓴다. */
    private val places = ConcurrentHashMap<String, Found>()
    private val proposals = ConcurrentHashMap<String, Proposal>()
    private val seq = AtomicLong()

    /** 마지막 카드의 선택지 (카드 순서 그대로) · 그때 상황. 정차 중 목록에서 다른 곳을 누르면 이걸로 제안을 다시 만든다. */
    @Volatile private var lastOptions: List<Found> = emptyList()
    @Volatile private var lastDrive: DriveContext? = null

    /** 경로 캐시: 같은 목적지면 [ROUTE_TTL_MS] 동안 다시 받지 않는다. */
    @Volatile private var routeCache: Pair<String, CachedRoute>? = null

    /** 이 말을 길 위 에이전트가 맡을지 (규칙으로 필요를 알아들었을 때). */
    fun handles(text: String): Boolean = runCatching { roadsideNeed(text) != null }.getOrDefault(false)

    /** 화면 버튼 id가 이 에이전트 것인가. */
    fun owns(proposalId: String) = proposalId.startsWith(ID_PREFIX)

    fun ask(text: String, isDriving: Boolean): Flow<AgentEvent> = channelFlow {
        val t0 = SystemClock.elapsedRealtime()
        val trace = StringBuilder()
        fun mark(s: String) = trace.append("[+${SystemClock.elapsedRealtime() - t0}ms] ").append(s).append('\n')
        places.clear()
        proposals.clear()
        val need = runCatching { roadsideNeed(text) }.getOrNull()
        val drive = context(isDriving)
        mark("상황 ${drive.summary()}")
        val searches = Searches(drive)
        // 빠른 길: 규칙으로 알아들은 종류는 LLM을 기다리지 않고 미리 찾는다 (LLM이 같은 종류를 부르면 이 결과를 그대로 쓴다)
        need?.categories?.forEach { searches.start(it) }
        // 1.5초가 넘으면 "찾아볼게요" 한마디 (운전 중)
        val filler = launch {
            delay(FILLER_MS)
            if (isDriving) tts.speak(context.getString(R.string.roadside_filler))
        }

        var answer: String? = null
        var proposed: Found? = null
        var usedLlm = false
        if (llm.available) {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", roadsideSystemPrompt(drive.json(), isDriving, false)))
                .put(JSONObject().put("role", "user").put("content", text))
            val deadline = t0 + LLM_BUDGET_MS
            for (round in 0 until MAX_ROUNDS + 1) {
                val left = deadline - SystemClock.elapsedRealtime()
                if (left < 400) break
                // 마지막 차례는 도구 없이 답만
                val reply = llm.chat(messages, if (round < MAX_ROUNDS) tools else null, if (isDriving) 160 else 260, left)
                if (reply == null) {
                    mark("LLM 실패/시간 초과 (round $round)")
                    break
                }
                usedLlm = true
                var calls = reply.toolCalls
                if (calls.isEmpty()) {
                    // 네이티브 tool_calls 없이 글로 흘린 호출 (Qwen 예비 규칙)
                    runCatching { roadsideParseJsonCall(reply.content) }.getOrNull()?.let { calls = listOf(LlmToolCall("json_$round", it[0], it[1])) }
                }
                mark("LLM round $round ${reply.ms}ms → " + if (calls.isEmpty()) "답 \"${reply.content.take(80)}\"" else calls.joinToString { "${it.name}(${it.arguments})" })
                if (calls.isEmpty()) {
                    // 화면 · 소리용: 마크다운 강조 기호는 뺀다
                    answer = reply.content.replace("**", "").replace(Regex("\\n{2,}"), "\n").trim().takeIf { it.isNotEmpty() }
                    break
                }
                val assistant = JSONObject().put("role", "assistant").put("content", reply.content)
                assistant.put("tool_calls", JSONArray(calls.map { c ->
                    JSONObject().put("id", c.id).put("type", "function").put("function", JSONObject().put("name", c.name).put("arguments", c.arguments))
                }))
                messages.put(assistant)
                for (c in calls.take(3)) {
                    val ts = SystemClock.elapsedRealtime()
                    val (result, prop) = runTool(c, drive, searches)
                    if (prop != null) proposed = prop
                    mark("  도구 ${c.name} ${SystemClock.elapsedRealtime() - ts}ms → ${result.take(160)}")
                    messages.put(JSONObject().put("role", "tool").put("tool_call_id", c.id).put("name", c.name).put("content", result))
                }
            }
        } else {
            mark("LLM 설정 없음 → 규칙 길")
        }

        // 찾은 것 정리: 미리 찾은 것 + LLM이 찾은 것. 1순위 = LLM이 제안한 곳, 아니면 규칙 순서의 가장 가까운 곳.
        val order = need?.categories ?: searches.started()
        if (order.isEmpty() && proposed == null && answer == null) {
            // LLM도 규칙도 모르는 말 → 상황만 보고 휴게소
            searches.start(RoadsideCategory.REST_AREA)
        }
        val hits = searches.all()
        val pickOrder = (proposed?.hit?.place?.category?.let { listOf(it) }.orEmpty() + order.ifEmpty { listOf(RoadsideCategory.REST_AREA) }).distinct()
        val canAdd = drive.destination != null
        val pick = runCatching { roadsidePick(pickOrder, hits, canAdd) }.getOrNull()
        val primary = proposed ?: pick?.primary?.let { remember(it) }
        val second = (listOfNotNull(pick?.second) + hits).map { remember(it) }.firstOrNull { it.id != primary?.id && it.hit.place.name != primary?.hit?.place?.name }
        val third = hits.map { remember(it) }.firstOrNull { it.id != primary?.id && it.id != second?.id && it.hit.place.name != primary?.hit?.place?.name && it.hit.place.name != second?.hit?.place?.name }
        // 답 문장: LLM 글이 1순위 이름을 말하고 너무 길지 않으면 그대로, 아니면 규칙 문장 (숫자를 지어내지 않게)
        // 운전 중엔 짧고 묻는 말로 끝나야 쓴다 (읽고 바로 "응"을 듣는다)
        val llmOk = answer != null && (primary == null || answer.contains(primary.hit.place.name.substringBefore(' '))) &&
            (if (isDriving) answer.length <= 80 && answer.trimEnd().endsWith("?") else answer.length <= 220)
        val line = if (llmOk) answer!! else pick?.line ?: context.getString(R.string.roadside_none)
        filler.cancel()
        if (primary != null) {
            val found = listOfNotNull(primary, second, if (isDriving) null else third)
            lastOptions = found
            lastDrive = drive
            send(AgentEvent.Roadside(RoadsideCard(found.map { it.option() }, canAdd)))
        }
        send(AgentEvent.AnswerDelta(line))
        if (primary != null) {
            val id = ID_PREFIX + seq.incrementAndGet()
            proposals[id] = Proposal(primary, canAdd, drive)
            val title = context.getString(if (canAdd) R.string.roadside_add_waypoint else R.string.roadside_navigate, primary.hit.place.name)
            send(AgentEvent.ActionProposal(id, title, primary.hit.place.address))
        }
        if (usedLlm.not() && llm.available) send(AgentEvent.Notice(context.getString(R.string.roadside_offline_notice)))
        mark("끝 (${if (llmOk) "LLM 답" else "규칙 답"}) 1순위=${primary?.hit?.place?.name} 2순위=${second?.hit?.place?.name}")
        Log.i(TAG, "길 위 에이전트 \"$text\" ${SystemClock.elapsedRealtime() - t0}ms\n$trace")
        send(AgentEvent.Done)
    }.flowOn(Dispatchers.Default)

    /**
     * 정차 중 목록에서 [index]번 곳을 눌렀다: 그곳으로 제안("○○ 경유지로" / "○○ 안내")을 새로 만든다.
     * 앞 제안은 버린다 (한 번에 하나만 실행된다). 없는 번호면 null.
     */
    fun proposeOption(index: Int): AgentEvent.ActionProposal? {
        val f = lastOptions.getOrNull(index) ?: return null
        val drive = lastDrive ?: return null
        val canAdd = drive.destination != null
        proposals.clear()
        val id = ID_PREFIX + seq.incrementAndGet()
        proposals[id] = Proposal(f, canAdd, drive)
        val title = context.getString(if (canAdd) R.string.roadside_add_waypoint else R.string.roadside_navigate, f.hit.place.name)
        return AgentEvent.ActionProposal(id, title, f.hit.place.address)
    }

    /** 운전자가 "응" / 버튼: 경유지로 넣거나 그곳으로 안내. */
    suspend fun confirm(proposal: AgentEvent.ActionProposal): String {
        val p = proposals.remove(proposal.id) ?: error(context.getString(R.string.agent_send_failed))
        val place = p.found.hit.place
        val stop = RouteStop(place.name, place.lat, place.lon, place.address)
        val dest = p.drive.destination
        val ok = if (p.addWaypoint && dest != null) {
            // 이미 있던 경유지(좌표를 아는 것) 하나는 남긴다 — 내비 경유지는 2개까지
            val keep = proactive.naviWaypoints.mapNotNull { w -> if (w.lat != null && w.lon != null) RouteStop(w.name, w.lat, w.lon, w.address) else null }.take(1)
            navi.requestRouteWithWaypoints(RouteStop(dest.name, dest.lat, dest.lon), listOf(stop) + keep)
        } else {
            navi.requestRoute(place.name, place.lat, place.lon, place.address)
        }
        check(ok) { context.getString(R.string.agent_send_failed) }
        Log.i(TAG, "길 위 제안 실행: ${if (p.addWaypoint && dest != null) "경유지" else "안내"} ${place.name}")
        return context.getString(if (p.addWaypoint && dest != null) R.string.proactive_done_waypoint else R.string.roadside_done_navigate)
    }

    // ------------------------------------------------------------------------------------------
    // 도구
    // ------------------------------------------------------------------------------------------

    /** 도구 하나 실행 → (모델에 돌려줄 JSON, 제안했으면 그곳). */
    private suspend fun runTool(c: LlmToolCall, drive: DriveContext, searches: Searches): Pair<String, Found?> =
        when (val call = runCatching { roadsideParseCall(c.name, c.arguments) }.getOrElse { RoadsideCall.Invalid("인자 해석 실패") }) {
            RoadsideCall.DriveContext -> drive.json() to null
            is RoadsideCall.Search -> {
                val hits = searches.get(call.category, call.maxDetourKm * 1000.0, call.limit.toInt())
                val found = hits.map { remember(it) }
                roadsideHitsJson(hits, found.map { it.id }) to null
            }
            is RoadsideCall.Memory -> memory(call.query, drive) to null
            is RoadsideCall.Waypoint -> propose(call.placeId, waypoint = true, drive)
            is RoadsideCall.Navigate -> propose(call.placeId, waypoint = false, drive)
            is RoadsideCall.Invalid -> JSONObject().put("ok", false).put("error", call.error).toString() to null
        }

    private fun propose(id: String, waypoint: Boolean, drive: DriveContext): Pair<String, Found?> {
        val f = places[id] ?: return JSONObject().put("ok", false).put("error", "없는 place_id: $id (search_along_route 결과의 id)").toString() to null
        if (waypoint && drive.destination == null) {
            return JSONObject().put("ok", true).put("note", "목적지가 없어 바로 안내로 제안함. 운전자에게 안내할지 물어라.").toString() to f
        }
        return JSONObject().put("ok", true).put("note", "운전자에게 '${if (waypoint) "경유지로 넣을까요?" else "안내할까요?"}'라고 물어라. 실행은 운전자가 응하면 앱이 한다.").toString() to f
    }

    /** 기억 찾기: 들른 곳 중 이름 · 꼬리표가 맞는 곳 → 경로 앞쪽 순으로. */
    private suspend fun memory(query: String, drive: DriveContext): String {
        val hits = runCatching { core.searchMemories(MemoryQuery(terms = query.split(' ').filter { it.isNotBlank() }.take(3), limit = 10)) }.getOrDefault(emptyList())
        val stops = hits.mapNotNull { it.bestStop }.distinctBy { it.name }
        val cands = stops.map { s ->
            RoadsidePlace(s.name, if (s.name.contains("휴게소")) RoadsideCategory.REST_AREA else RoadsideCategory.FOOD, s.lat, s.lon, s.address, false, "내 기록", "")
        }
        val here = drive.here ?: return JSONObject().put("ok", true).put("places", JSONArray(stops.take(3).map { it.name })).toString()
        val ranked = roadsideRank(drive.route, here, drive.headingDeg, drive.speedKmh, cands, 10_000.0, 3u)
        val found = ranked.map { remember(it) }
        return roadsideHitsJson(ranked, found.map { it.id })
    }

    private fun remember(h: RoadsideHit): Found {
        places.values.firstOrNull { it.hit.place.name == h.place.name && it.hit.place.category == h.place.category }?.let { return it }
        val f = Found("p${places.size + 1}", h)
        places[f.id] = f
        return f
    }

    /** 이번 질문의 찾기: 종류마다 한 번만 (동시에 돌리고, 같은 종류를 다시 부르면 그 결과). */
    private inner class Searches(private val drive: DriveContext) {
        private val jobs = ConcurrentHashMap<RoadsideCategory, Deferred<List<RoadsideHit>>>()
        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

        fun start(c: RoadsideCategory): Deferred<List<RoadsideHit>> = jobs.getOrPut(c) { scope.async { search(c, drive) } }

        fun started(): List<RoadsideCategory> = jobs.keys.toList()

        suspend fun get(c: RoadsideCategory, maxDetourM: Double, limit: Int): List<RoadsideHit> =
            start(c).await().filter { it.detourM <= maxDetourM }.take(limit)

        suspend fun all(): List<RoadsideHit> = jobs.values.toList().awaitAll().flatten()
    }

    /** 한 종류 찾기: 기기 목록(휴게소 · 졸음쉼터) + 카카오(경로 앞 지점들 주변) → Rust가 경로 앞쪽 순으로. */
    private suspend fun search(c: RoadsideCategory, drive: DriveContext): List<RoadsideHit> {
        val here = drive.here ?: return emptyList()
        val local = runCatching { roadsideBundled(listOf(c)) }.getOrDefault(emptyList())
        val online = if (ApiAccess.kakaoAvailable) kakao(c, drive, here) else emptyList()
        return roadsideRank(drive.route, here, drive.headingDeg, drive.speedKmh, local + online, MAX_DETOUR_M, 5u)
    }

    private suspend fun kakao(c: RoadsideCategory, drive: DriveContext, here: GeoPoint): List<RoadsidePlace> = coroutineScope {
        val q = roadsideKakaoQuery(c)
        val probes = roadsideProbePoints(drive.route, here, drive.headingDeg, PROBE_STEP_M, PROBE_COUNT)
        probes.map { p ->
            async {
                val url = if (q.groupCode.isNotEmpty()) {
                    "${ApiAccess.kakaoBase}/v2/local/search/category.json?category_group_code=${q.groupCode}&x=${coord(p.lon)}&y=${coord(p.lat)}&radius=${q.radiusM}&sort=distance&size=15"
                } else {
                    "${ApiAccess.kakaoBase}/v2/local/search/keyword.json?query=${URLEncoder.encode(q.keyword, "UTF-8")}&x=${coord(p.lon)}&y=${coord(p.lat)}&radius=${q.radiusM}&sort=distance&size=15"
                }
                runCatching { blockingWithTimeout(KAKAO_TIMEOUT_MS) { PlatformHttp.get(url, ApiAccess.kakaoHeaders) } }.getOrNull()
                    ?.let { runCatching { roadsideParseKakao(it, c) }.getOrNull() }.orEmpty()
            }
        }.awaitAll().flatten()
    }

    // ------------------------------------------------------------------------------------------
    // 주행 상황
    // ------------------------------------------------------------------------------------------

    private suspend fun context(isDriving: Boolean): DriveContext = coroutineScope {
        val sim = RoadsideDebug.sim
        val now = System.currentTimeMillis()
        val motion = runCatching { core.rust.roadsideMotion(now) }.getOrNull()
        val here = sim?.here ?: motion?.here
        val dest = sim?.destination ?: proactive.naviDestination?.let { p -> if (p.lat != null && p.lon != null) Dest(p.name, p.lat, p.lon) else null }
        val energyJob = async { runCatching { energy.level() }.getOrNull() }
        val rangeJob = async { runCatching { gauges.rangeKm() }.getOrNull() }
        val route = if (here != null && dest != null) route(here, dest) else null
        val progress = route?.let { r -> runCatching { r.session.update(here!!.lat, here.lon) }.getOrNull() }
        val level = energyJob.await()
        DriveContext(
            driving = isDriving || sim != null,
            here = here,
            headingDeg = sim?.headingDeg ?: motion?.headingDeg ?: progress?.bearingDeg,
            speedKmh = sim?.speedKmh ?: motion?.speedKmh,
            drivingMin = sim?.drivingMin ?: motion?.drivingMin?.toInt(),
            destination = dest,
            remainingM = progress?.remainingM,
            remainingS = progress?.remainingS,
            route = route?.points.orEmpty(),
            waypoints = proactive.naviWaypoints.size,
            energyPct = level?.pct,
            ev = level?.ev,
            rangeKm = rangeJob.await(),
        )
    }

    /** 목적지까지 경로 (카카오모빌리티 길찾기, 중계). 실패하면 null → 진행 방향 직선으로. */
    private suspend fun route(here: GeoPoint, dest: Dest): CachedRoute? {
        val key = "${dest.name}|${dest.lat}|${dest.lon}"
        routeCache?.takeIf { it.first == key && SystemClock.elapsedRealtime() - it.second.atMs < ROUTE_TTL_MS }?.let { return it.second }
        if (!ApiAccess.kakaoAvailable) return null
        val url = ApiAccess.kakaoNavi(navDirectionsUrl(here, listOf(GeoPoint(dest.lat, dest.lon))))
        val json = runCatching { blockingWithTimeout(ROUTE_TIMEOUT_MS) { PlatformHttp.get(url, ApiAccess.kakaoHeaders) } }.getOrNull() ?: return null
        val session = runCatching { NavSession(json) }.getOrNull() ?: return null
        val r = CachedRoute(session, session.route().points, SystemClock.elapsedRealtime())
        routeCache = key to r
        return r
    }

    private fun Found.option(): RoadsideOption {
        val km = hit.alongM / 1000.0
        val dist = when {
            hit.alongM < 1_000 -> "${(Math.round(hit.alongM / 100.0) * 100).coerceAtLeast(100)}m"
            km < 10 -> String.format(Locale.ROOT, "%.1fkm", km).replace(".0km", "km")
            else -> "${Math.round(km)}km"
        }
        return RoadsideOption(hit.place.name, kindWord(hit.place.category), context.getString(R.string.roadside_ahead, dist, hit.etaMin.toInt()), hit.place.source == ROADSIDE_PUBLIC_SOURCE)
    }

    private fun kindWord(c: RoadsideCategory) = when (c) {
        RoadsideCategory.REST_AREA -> "휴게소"
        RoadsideCategory.DROWSY_SHELTER -> "졸음쉼터"
        RoadsideCategory.TOILET -> "화장실"
        RoadsideCategory.GAS -> "주유소"
        RoadsideCategory.EV_CHARGER -> "충전소"
        RoadsideCategory.CAFE -> "카페"
        RoadsideCategory.FOOD -> "식당"
        RoadsideCategory.CONVENIENCE -> "편의점"
    }

    private data class Found(val id: String, val hit: RoadsideHit)
    private data class Proposal(val found: Found, val addWaypoint: Boolean, val drive: DriveContext)
    private class CachedRoute(val session: NavSession, val points: List<GeoPoint>, val atMs: Long)

    data class Dest(val name: String, val lat: Double, val lon: Double)

    /** 모델에 보여 줄 · 도구가 쓰는 지금 상황. */
    private data class DriveContext(
        val driving: Boolean,
        val here: GeoPoint?,
        val headingDeg: Double?,
        val speedKmh: Double?,
        val drivingMin: Int?,
        val destination: Dest?,
        val remainingM: Double?,
        val remainingS: Double?,
        val route: List<GeoPoint>,
        val waypoints: Int,
        val energyPct: Double?,
        val ev: Boolean?,
        val rangeKm: Double?,
    ) {
        fun json(): String = JSONObject().apply {
            put("driving", driving)
            speedKmh?.let { put("speed_kmh", Math.round(it)) }
            here?.let { put("here", JSONObject().put("lat", it.lat).put("lon", it.lon)) }
            headingDeg?.let { put("heading_deg", Math.round(it)) }
            destination?.let { d ->
                put("destination", JSONObject().put("name", d.name).apply {
                    remainingM?.let { put("remaining_km", Math.round(it / 100.0) / 10.0) }
                    remainingS?.let { put("remaining_min", Math.round(it / 60.0)) }
                })
            } ?: put("destination", JSONObject.NULL)
            put("route_known", route.size >= 2)
            put("waypoints", waypoints)
            drivingMin?.let { put("driving_min_since_last_stop", it) }
            energyPct?.let { put(if (ev == true) "battery_pct" else "fuel_pct", Math.round(it)) }
            rangeKm?.let { put("range_km", Math.round(it)) }
        }.toString()

        fun summary() = "here=${here?.let { "%.4f,%.4f".format(Locale.ROOT, it.lat, it.lon) }} heading=${headingDeg?.let { Math.round(it) }} " +
            "speed=${speedKmh?.let { Math.round(it) }} dest=${destination?.name} route=${route.size}점 남은=${remainingM?.let { Math.round(it / 1000) }}km"
    }

    companion object {
        const val ID_PREFIX = "rs-"
        private const val TAG = "EodiRoadside"
        private const val MAX_ROUNDS = 3
        private const val LLM_BUDGET_MS = 7_000L
        private const val FILLER_MS = 1_500L
        private const val MAX_DETOUR_M = 3_000.0
        private const val PROBE_STEP_M = 6_000.0
        private const val PROBE_COUNT = 4u
        private const val KAKAO_TIMEOUT_MS = 2_500L
        private const val ROUTE_TIMEOUT_MS = 2_500L
        private const val ROUTE_TTL_MS = 5 * 60_000L

        private fun coord(v: Double) = String.format(Locale.ROOT, "%.6f", v)
    }
}

/**
 * 시연용으로 바꿔 끼운 주행 상황 (디버그 수신기 `RoadsideDebugReceiver`). null이면 실제 값.
 * 에뮬레이터는 내비 경로 · 진행 방향을 마음대로 만들 수 없어서 쓴다.
 */
object RoadsideDebug {
    data class Sim(
        val here: GeoPoint,
        val headingDeg: Double?,
        val speedKmh: Double?,
        val destination: RoadsideAgent.Dest?,
        val drivingMin: Int? = null,
    )

    @Volatile var sim: Sim? = null
}

/** 에이전트에 말을 넣는 곳 (디버그 수신기 · 시연). 에이전트 화면이 받아 묻는다. */
object AgentUtterances {
    val texts = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
}
