package app.eodiyeotji.ui.agent

import app.eodiyeotji.agent.RecommendedCourse
import app.eodiyeotji.core.CourseRoute
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.MemoryHit
import app.eodiyeotji.core.StopFact
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

    /** 후보 칸에서 후보를 골랐다 (말 없이): 그 후보로 갈 안내 버튼 · 코스 · 답 한 줄. 고를 수 없으면 null. */
    suspend fun selectCandidate(episodeId: Long, context: AgentContext): CandidatePick? = null

    /** 후보 카드 지도(하루 지도 GeoJSON)의 핀 번호를 카드 사진 줄 [stops] 순서(1부터)로 맞춘다. 기본은 그대로. */
    suspend fun numberMapStops(geojson: String, stops: List<StopFact>, skipped: List<StopFact> = emptyList()): String = geojson

    /** 한 문장 코스("○○만 빼고") 카드 지도를 처음 비출 범위. 기본은 없음(하루 전체). */
    suspend fun skipFocusBounds(stops: List<StopFact>, skipped: List<StopFact>): app.eodiyeotji.core.MapBoundsLatLon? = null
}

/**
 * 질문할 때의 상황. "작년 여름" 같은 날짜 표현은 [nowMs](기기 시각) 기준으로 해석한다.
 * [isDriving]이면 답을 더 짧게(TTS로 읽을 한두 문장) 만든다.
 */
/** 후보를 고른 결과 (화면용). */
data class CandidatePick(val proposal: AgentEvent.ActionProposal?, val course: CourseRoute?, val line: String)

data class AgentContext(val nowMs: Long, val currentLocation: LatLon?, val isDriving: Boolean)

sealed interface AgentEvent {
    /** 답 글자 조각. 화면은 받은 순서대로 이어 붙인다. */
    data class AnswerDelta(val text: String) : AgentEvent

    /** 찾은 기억 한 건 + (있으면) 다시 달릴 코스. 첫 번째가 가장 그럴듯한 답이다. */
    data class Recommendation(val hit: MemoryHit, val course: CourseRoute?) : AgentEvent

    /** 기억 후보 목록 (번호 = 자리 + 1). [focus]: 지금 자세히 보이는 후보. 후보 칸에 나란히 그린다. */
    data class MemoryCandidates(val hits: List<MemoryHit>, val focus: Int) : AgentEvent

    /**
     * 근처 인기 드라이브 코스 목록 (순서 = 순위, [RecommendedCourse.rank]는 1부터).
     * [focus]: 지금 안내를 제안한 코스의 자리. 후속 말마다 다시 오고 마지막이 최신이다.
     */
    data class CourseRecommendations(val courses: List<RecommendedCourse>, val focus: Int) : AgentEvent

    /**
     * 운전자 확인이 필요한 행동. [title]은 버튼에 그대로 쓰는 짧은 말("이 코스로 안내"),
     * [description]은 버튼 아래 한 줄 설명("경포해변 → 초당할머니순두부 → 안목해변").
     * 여러 개면 첫 번째가 기본 행동이다(운전 중에는 첫 번째만 보인다).
     */
    data class ActionProposal(val id: String, val title: String, val description: String) : AgentEvent

    /** 답과 따로 보일 흐린 안내 한 줄 (예: AI 연결이 끊겨 기기 안 기록으로 답함). 오류가 아니고 소리로 읽지 않는다. [Done] 바로 앞에 온다. */
    data class Notice(val text: String) : AgentEvent

    /** 길 위의 필요(화장실 · 졸음 등)에 찾은 들를 곳 카드 (agent/roadside). 운전 중엔 1·2번만 보인다. */
    data class Roadside(val card: app.eodiyeotji.agent.roadside.RoadsideCard) : AgentEvent

    data object Done : AgentEvent

    data class Error(val message: String) : AgentEvent
}
