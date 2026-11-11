package app.eodiyeotji.ui.agent

import android.content.Context
import app.eodiyeotji.R
import app.eodiyeotji.agent.AgentBridge
import app.eodiyeotji.agent.AgentNavAction
import app.eodiyeotji.agent.AgentProposal
import app.eodiyeotji.core.CourseRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import app.eodiyeotji.agent.AgentEvent as BridgeEvent

/**
 * 화면([AgentPort])과 실제 기억 에이전트(Rust `MemoryAgent`, [AgentBridge])를 잇는다.
 * 사건을 화면 모양으로 옮겨 담고, 버튼 글자를 붙이고, 확인 대기 중인 제안을 id로 기억해 [confirm]에 넘기기만 한다.
 * 버튼·안내 글은 문자열 자원에서 읽는다 (폰 판은 "내비" 대신 "지도 앱"으로 바꿔 쓴다, src/phone/res).
 */
@Singleton
class MemoryAgentPort @Inject constructor(
    private val bridge: AgentBridge,
    @ApplicationContext private val context: Context,
) : AgentPort {

    private val proposals = ConcurrentHashMap<String, AgentProposal>()

    override fun ask(utterance: String, context: AgentContext): Flow<AgentEvent> =
        bridge.ask(utterance, context.isDriving, context.currentLocation).transform { e ->
            when (e) {
                is BridgeEvent.AnswerDelta -> emit(AgentEvent.AnswerDelta(e.text))
                is BridgeEvent.Recommendation -> emit(AgentEvent.Recommendation(e.hit, e.course))
                is BridgeEvent.CourseRecommendations -> emit(AgentEvent.CourseRecommendations(e.courses, e.focus))
                is BridgeEvent.MemoryCandidates -> emit(AgentEvent.MemoryCandidates(e.hits, e.focus))
                is BridgeEvent.ActionProposal -> emit(button(e.proposal))
                // 운전자가 말로 "응"이라고 해 이미 안내를 보냈다 → 답 글에 한 줄 덧붙인다
                is BridgeEvent.ActionStarted ->
                    emit(AgentEvent.AnswerDelta(text(if (e.ok) R.string.agent_started_line else R.string.agent_start_failed_line)))
                is BridgeEvent.Done -> {
                    e.notice?.let { emit(AgentEvent.Notice(it)) }
                    emit(AgentEvent.Done)
                }
                is BridgeEvent.Error -> emit(AgentEvent.Error(e.message))
            }
        }

    override suspend fun selectCandidate(episodeId: Long, context: AgentContext): CandidatePick? =
        bridge.selectCandidate(episodeId, context.isDriving, context.currentLocation)?.let { c -> CandidatePick(c.proposal?.let(::button), c.course, c.line) }

    /** 제안을 기억해 두고 화면 버튼 모양으로. */
    private fun button(p: AgentProposal): AgentEvent.ActionProposal {
        proposals[p.id] = p
        return AgentEvent.ActionProposal(p.id, buttonTitle(p.action), buttonDetail(p))
    }

    override suspend fun confirm(proposal: AgentEvent.ActionProposal) {
        val p = proposals.remove(proposal.id) ?: error("이미 끝난 제안이에요.")
        check(bridge.confirm(p)) { text(R.string.agent_send_failed) }
    }

    private fun buttonTitle(action: AgentNavAction) = when (action) {
        is AgentNavAction.Course -> text(R.string.agent_proposal_course)
        is AgentNavAction.SharedCourse -> text(R.string.agent_proposal_shared, action.title)
        is AgentNavAction.Place -> text(R.string.agent_proposal_place, action.stop.name)
        is AgentNavAction.Search -> text(R.string.agent_proposal_search)
    }

    /** 버튼 아래 작은 글: 제목을 되풀이하지 않고 들르는 순서(코스)·주소(장소)만. 에이전트 대화 기록에는 summary 전체가 남는다. */
    private fun buttonDetail(p: AgentProposal) = when (val a = p.action) {
        is AgentNavAction.Course, is AgentNavAction.SharedCourse -> p.summary.substringAfter(": ", p.summary)
        is AgentNavAction.Place -> a.stop.address
        is AgentNavAction.Search -> p.summary
    }

    private fun text(id: Int, vararg args: Any): String = this.context.getString(id, *args)
}
