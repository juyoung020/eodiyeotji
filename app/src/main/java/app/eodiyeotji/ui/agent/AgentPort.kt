package app.eodiyeotji.ui.agent

import app.eodiyeotji.core.CourseRoute
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.MemoryHit
import kotlinx.coroutines.flow.Flow

/**
 * "어디였지? 물어보세요" 화면이 쓰는 기억 에이전트 입구 (docs/70_기억_검색_설계.md §4).
 *
 * 화면은 이 인터페이스만 안다. 실제 구현(LLM + 도구, `agent/MemoryAgent`)은 통합할 때 여기에 맞춘다.
 * - [ask]는 답을 조각([AgentEvent.AnswerDelta])으로 흘려보내고, 찾은 기억([AgentEvent.Recommendation])과
 *   운전자 확인이 필요한 행동([AgentEvent.ActionProposal])을 사이사이에 보낸 뒤 [AgentEvent.Done]으로 끝낸다.
 * - 화면은 새 질문이 오면 이전 Flow 수집을 취소한다 → 구현은 취소에 협조해야 한다(일반 suspend 호출이면 된다).
 * - 안내 시작 같은 행동은 운전자가 버튼(또는 "응")으로 고른 뒤 [confirm]으로만 실행한다.
 */
interface AgentPort {
    fun ask(utterance: String, context: AgentContext): Flow<AgentEvent>

    /** 운전자가 고른 행동을 실행한다 (예: 코스로 내비 안내 시작). 실패하면 예외를 던진다. */
    suspend fun confirm(proposal: AgentEvent.ActionProposal)
}

/**
 * 질문할 때의 상황. "작년 여름" 같은 날짜 표현은 [nowMs](기기 시각) 기준으로 해석한다.
 * [isDriving]이면 답을 더 짧게(TTS로 읽을 한두 문장) 만든다.
 */
data class AgentContext(val nowMs: Long, val currentLocation: LatLon?, val isDriving: Boolean)

sealed interface AgentEvent {
    /** 답 글자 조각. 화면은 받은 순서대로 이어 붙인다. */
    data class AnswerDelta(val text: String) : AgentEvent

    /** 찾은 기억 한 건 + (있으면) 다시 달릴 코스. 첫 번째가 가장 그럴듯한 답이다. */
    data class Recommendation(val hit: MemoryHit, val course: CourseRoute?) : AgentEvent

    /**
     * 운전자 확인이 필요한 행동. [title]은 버튼에 그대로 쓰는 짧은 말("이 코스로 안내"),
     * [description]은 버튼 아래 한 줄 설명("경포해변 → 초당할머니순두부 → 안목해변").
     * 여러 개면 첫 번째가 기본 행동이다(운전 중에는 첫 번째만 보인다).
     */
    data class ActionProposal(val id: String, val title: String, val description: String) : AgentEvent

    data object Done : AgentEvent

    data class Error(val message: String) : AgentEvent
}
