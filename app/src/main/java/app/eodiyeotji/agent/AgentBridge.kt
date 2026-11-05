package app.eodiyeotji.agent

import android.content.Context
import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.CourseLeg
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.rust.AgentContext
import app.eodiyeotji.core.rust.AgentListener
import app.eodiyeotji.core.rust.EodiStore
import app.eodiyeotji.core.rust.GeoPoint
import app.eodiyeotji.core.rust.LabelReport
import app.eodiyeotji.core.rust.LlmConfig
import app.eodiyeotji.core.rust.MemoryAgent
import app.eodiyeotji.pleos.PleosNavi
import app.eodiyeotji.pleos.RouteStop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.eodiyeotji.core.rust.AgentDone as RAgentDone
import app.eodiyeotji.core.rust.CourseRecommendation as RCourseRecommendation
import app.eodiyeotji.core.rust.CourseRoute as RCourseRoute
import app.eodiyeotji.core.rust.MemoryHit as RMemoryHit
import app.eodiyeotji.core.rust.NavAction as RNavAction
import app.eodiyeotji.core.rust.NavProposal as RNavProposal

/**
 * 기억 에이전트 연결부 — 에이전트 자체(빠른 길·LLM 도구 루프·하루 라벨링)는 Rust `core-rs/src/agent`에 있고,
 * 여기는 ① Rust `MemoryAgent`를 백그라운드 스레드에서 부르고 ② 콜백을 [AgentEvent] 흐름으로 바꾸고
 * ③ 운전자가 확인한 [AgentNavAction]을 Pleos 내비로 실행하기만 한다 (계산 없음).
 *
 * 안내는 확인 없이 실행되지 않는다: Rust가 제안만 만들고, [confirm](화면의 "예") 또는 운전자의 다음 말 "응/안내해줘"가 있어야
 * 내비 명령이 나온다. 내비 경유지는 2개까지라 코스는 구간으로 나뉘어 오고, 첫 구간만 요청한 뒤 나머지는 [pendingLegs]에 둔다.
 */
@Singleton
class AgentBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navi: PleosNavi,
) {
    /** Rust 에이전트. DB를 여는 일이라 처음 쓸 때 IO 스레드에서 만든다. */
    private val agent: MemoryAgent by lazy { createAgent() }

    private val _pendingLegs = MutableStateFlow<List<CourseLeg>>(emptyList())

    /** 아직 내비에 요청하지 않은 코스 구간. 내비 도착 이벤트(NaviEvent.DestinationArrived)를 받는 쪽이 [continueCourse]를 부른다. */
    val pendingLegs: StateFlow<List<CourseLeg>> = _pendingLegs.asStateFlow()

    /**
     * 질문 하나. 모으는 동안 Rust `ask`가 IO 스레드에서 돈다. 모으기를 취소하면 Rust 쪽 LLM 스트림도 끊는다.
     * [location]: 지금 차 위치 (코스를 지금 위치에서 가장 짧은 순서로 짠다), [isDriving]: 주행 중이면 TTS용 한 문장.
     */
    fun ask(utterance: String, isDriving: Boolean, location: LatLon? = null): Flow<AgentEvent> = callbackFlow {
        var voiceConfirmed: AgentNavAction? = null
        val listener = object : AgentListener {
            override fun onAnswerDelta(text: String) {
                trySendBlocking(AgentEvent.AnswerDelta(text))
            }

            override fun onRecommendation(hit: RMemoryHit, course: RCourseRoute?) {
                trySendBlocking(AgentEvent.Recommendation(hit.toModel(), course?.toModel()))
            }

            override fun onCourseRecommendations(courses: List<RCourseRecommendation>, focus: UInt) {
                trySendBlocking(AgentEvent.CourseRecommendations(courses.toModel(), focus.toInt()))
            }

            override fun onActionProposal(proposal: RNavProposal) {
                trySendBlocking(AgentEvent.ActionProposal(proposal.toModel()))
            }

            override fun onActionConfirmed(proposalId: String, action: RNavAction) {
                voiceConfirmed = action.toModel() // ask가 끝난 뒤 이 스레드 밖(코루틴)에서 실행한다
            }

            override fun onDone(done: RAgentDone) {
                trySendBlocking(done.toEvent())
            }

            override fun onError(message: String) {
                trySendBlocking(AgentEvent.Error(message))
            }
        }
        val job = launch(Dispatchers.IO) {
            try {
                agent.ask(utterance, agentContext(isDriving, location), listener)
                voiceConfirmed?.let { a -> send(AgentEvent.ActionStarted(a, execute(a))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "에이전트 실패", e)
                send(AgentEvent.Error("기억 찾기에 실패했어요."))
            }
            channel.close()
        }
        awaitClose { if (job.isActive) runCatching { agent.cancel() } }
    }

    /** 화면의 "예": 대기 중인 제안을 Rust에서 내비 명령으로 받아 실행한다. 반환: 내비에 요청이 갔는지. */
    suspend fun confirm(proposal: AgentProposal): Boolean = withContext(Dispatchers.IO) {
        val action = runCatching { agent.confirm(proposal.id) }
            .onFailure { Log.i(TAG, "확인할 제안 없음: ${it.message}") }
            .getOrNull() ?: return@withContext false
        execute(action.toModel())
    }

    /** 화면의 "아니오". */
    suspend fun decline(proposal: AgentProposal) = withContext(Dispatchers.IO) { agent.decline(proposal.id) }

    /** 코스의 다음 구간 요청 (도착했을 때). 남은 구간이 없으면 false. */
    suspend fun continueCourse(): Boolean {
        val legs = _pendingLegs.value
        val leg = legs.firstOrNull() ?: return false
        val ok = requestLeg(leg)
        if (ok) _pendingLegs.value = legs.drop(1)
        return ok
    }

    /** 대화 기억을 지운다 (새 질문 화면 등). */
    suspend fun newSession() = withContext(Dispatchers.IO) { agent.newSession() }

    /** LLM 서버에 고정 프롬프트를 미리 계산시킨다 (첫 질문을 빠르게). 막는 호출이라 IO에서. */
    suspend fun warmUp(): Boolean = withContext(Dispatchers.IO) { runCatching { agent.warmUp() }.getOrDefault(false) }

    /** 라벨이 필요한 날을 [limit]일까지 라벨링 (막는 호출이라 IO에서, 하루 1~2초). */
    suspend fun labelPendingDays(limit: Int): LabelReport = withContext(Dispatchers.IO) {
        agent.labelPendingDays(limit.toUInt(), tzOffsetMinutes(System.currentTimeMillis()))
    }

    private suspend fun execute(a: AgentNavAction): Boolean = when (a) {
        is AgentNavAction.Course -> startLegs(a.legs)
        is AgentNavAction.SharedCourse -> startLegs(a.legs)
        is AgentNavAction.Place -> navi.requestRoute(a.stop.name, a.stop.lat, a.stop.lon, a.stop.address)
        is AgentNavAction.Search -> navi.openPlaceSearch(a.keyword)
    }

    /** 코스: 첫 구간을 요청하고 나머지는 도착할 때마다 [continueCourse]. */
    private suspend fun startLegs(legs: List<CourseLeg>): Boolean {
        val first = legs.firstOrNull()
        val ok = first != null && requestLeg(first)
        _pendingLegs.value = if (ok) legs.drop(1) else emptyList()
        return ok
    }

    private suspend fun requestLeg(leg: CourseLeg): Boolean =
        navi.requestRouteWithWaypoints(leg.destination.toRouteStop(), leg.waypoints.take(2).map { it.toRouteStop() })

    private fun StopFact.toRouteStop() = RouteStop(name, lat, lon, address)

    private fun agentContext(isDriving: Boolean, location: LatLon?): AgentContext {
        val now = System.currentTimeMillis()
        return AgentContext(now, tzOffsetMinutes(now), location?.let { GeoPoint(it.lat, it.lon) }, isDriving)
    }

    private fun tzOffsetMinutes(now: Long) = TimeZone.getDefault().getOffset(now) / 60_000

    /**
     * Rust 에이전트 만들기. LLM 주소가 비어 있으면 Rust가 빠른 길(기기 안 검색)만 쓴다.
     *
     * [임시] EodiCore가 아직 Rust 저장소(EodiStore)를 내주지 않아 같은 DB 파일을 연결 하나 더로 연다 (SQLite WAL이라 읽기·쓰기 모두 안전,
     * Rust가 바쁜 잠금을 기다린다). EodiCore에 저장소 접근자가 생기면 그것으로 바꾼다 — 이 경우 라벨 저장이 EodiCore.changes를 울리지 않는다.
     */
    private fun createAgent(): MemoryAgent {
        val db = File(context.filesDir, "eodi.db").absolutePath
        val store = EodiStore.open(db, TimeZone.getDefault().rawOffset / 60_000)
        val config = LlmConfig(
            baseUrl = BuildConfig.LLM_BASE_URL,
            model = BuildConfig.LLM_MODEL,
            apiKey = BuildConfig.LLM_API_KEY,
            connectTimeoutMs = 2_000u,
            readTimeoutMs = 60_000u,
        )
        if (BuildConfig.LLM_BASE_URL.isBlank()) Log.w(TAG, "LLM_BASE_URL 없음 → 기기 안 검색(빠른 길)만 씀")
        return MemoryAgent(store, config)
    }

    private companion object {
        const val TAG = "EodiAgent"
    }
}
