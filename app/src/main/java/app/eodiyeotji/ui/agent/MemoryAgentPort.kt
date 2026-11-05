package app.eodiyeotji.ui.agent

import app.eodiyeotji.agent.AgentBridge
import app.eodiyeotji.agent.AgentNavAction
import app.eodiyeotji.agent.AgentProposal
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import app.eodiyeotji.agent.AgentEvent as BridgeEvent

/**
 * 화면([AgentPort])과 실제 기억 에이전트(Rust `MemoryAgent`, [AgentBridge])를 잇는다.
 * 사건을 화면 모양으로 옮겨 담고, 버튼 글자를 붙이고, 확인 대기 중인 제안을 id로 기억해 [confirm]에 넘기기만 한다.
 */
@Singleton
class MemoryAgentPort @Inject constructor(private val bridge: AgentBridge) : AgentPort {

    private val proposals = ConcurrentHashMap<String, AgentProposal>()

    override fun ask(utterance: String, context: AgentContext): Flow<AgentEvent> =
        bridge.ask(utterance, context.isDriving, context.currentLocation).transform { e ->
            when (e) {
                is BridgeEvent.AnswerDelta -> emit(AgentEvent.AnswerDelta(e.text))
                is BridgeEvent.Recommendation -> emit(AgentEvent.Recommendation(e.hit, e.course))
                is BridgeEvent.CourseRecommendations -> emit(AgentEvent.CourseRecommendations(e.courses, e.focus))
                is BridgeEvent.ActionProposal -> {
                    proposals[e.proposal.id] = e.proposal
                    emit(AgentEvent.ActionProposal(e.proposal.id, buttonTitle(e.proposal.action), e.proposal.summary))
                }
                // 운전자가 말로 "응"이라고 해 이미 안내를 보냈다 → 답 글에 한 줄 덧붙인다
                is BridgeEvent.ActionStarted ->
                    emit(AgentEvent.AnswerDelta(if (e.ok) "\n내비 안내를 시작했어요." else "\n내비에 안내를 보내지 못했어요."))
                is BridgeEvent.Done -> emit(AgentEvent.Done)
                is BridgeEvent.Error -> emit(AgentEvent.Error(e.message))
            }
        }

    override suspend fun confirm(proposal: AgentEvent.ActionProposal) {
        val p = proposals.remove(proposal.id) ?: error("이미 끝난 제안이에요.")
        check(bridge.confirm(p)) { "내비에 안내를 보내지 못했어요." }
    }

    private fun buttonTitle(action: AgentNavAction) = when (action) {
        is AgentNavAction.Course -> "이 코스로 안내"
        is AgentNavAction.SharedCourse -> "'${action.title}' 코스로 안내"
        is AgentNavAction.Place -> "${action.stop.name}로 안내"
        is AgentNavAction.Search -> "내비에서 찾기"
    }
}
