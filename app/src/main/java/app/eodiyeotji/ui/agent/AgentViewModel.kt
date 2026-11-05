package app.eodiyeotji.ui.agent

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.CourseRoute
import app.eodiyeotji.core.DayMapData
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.MemoryHit
import app.eodiyeotji.core.StopFact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 화면 상태 전체. [turn]이 null이면 아직 묻지 않은 처음 화면(예시 질문)이다. */
@Immutable
data class AgentUiState(
    val isDriving: Boolean = false,
    val turn: AgentTurn? = null,
)

/** 질문 하나와 그 답. 새 질문을 하면 통째로 바뀐다. */
@Immutable
data class AgentTurn(
    val id: Long,
    val question: String,
    val answer: String = "",
    val phase: AnswerPhase = AnswerPhase.Thinking,
    val recommendations: List<RecommendationUi> = emptyList(),
    val proposals: List<AgentEvent.ActionProposal> = emptyList(),
    /** 첫 추천 카드에서 고른 곳 ("여기만 가기" 대상). */
    val selectedStop: Int = 0,
    val action: ActionState = ActionState.Idle,
    val error: String? = null,
)

enum class AnswerPhase {
    /** 아직 답 글자가 오지 않음 (도구로 찾는 중). */
    Thinking,
    Streaming,
    Done,
    Failed,
}

/** 추천 카드 하나. [map]은 첫 카드만 불러온다 ([mapLoaded] 전에는 자리만). */
@Immutable
data class RecommendationUi(
    val hit: MemoryHit,
    val course: CourseRoute?,
    val map: DayMapData? = null,
    val mapLoaded: Boolean = false,
) {
    /** 카드에 늘어놓을 곳: 코스가 있으면 코스 순서, 없으면 그날 들른 순서. */
    val stops: List<StopFact> get() = course?.stops ?: hit.episode.facts.stops
}

@Immutable
sealed interface ActionState {
    data object Idle : ActionState
    data class Running(val proposalId: String) : ActionState
    data class Done(val proposalId: String) : ActionState
    data class Failed(val proposalId: String, val message: String) : ActionState
}

/**
 * 기억 에이전트 대화 상태.
 * - 에이전트 Flow는 Dispatchers.Default에서 모은다 (UI 스레드는 상태만 받아 그린다).
 * - 새 질문을 하면 이전 질문 수집·행동을 취소한다. 늦게 온 이전 질문 이벤트는 [AgentTurn.id]로 걸러 버린다.
 */
@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agent: AgentPort,
    private val driving: DrivingStatePort,
    private val core: EodiCore,
) : ViewModel() {

    private val turn = MutableStateFlow<AgentTurn?>(null)

    val state: StateFlow<AgentUiState> = combine(turn, driving.isDriving) { t, d -> AgentUiState(isDriving = d, turn = t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentUiState(isDriving = driving.isDriving.value))

    private var askJob: Job? = null
    private var actionJob: Job? = null
    private var lastId = 0L

    fun ask(utterance: String) {
        val question = utterance.trim()
        if (question.isEmpty()) return
        askJob?.cancel()
        actionJob?.cancel()
        val id = ++lastId
        turn.value = AgentTurn(id = id, question = question)
        // TODO(통합): 현재 위치(FusedLocation 마지막 위치)를 넣으면 "가장 빠른 순서"가 지금 위치 기준이 된다
        val context = AgentContext(nowMs = System.currentTimeMillis(), currentLocation = null, isDriving = driving.isDriving.value)
        askJob = viewModelScope.launch(Dispatchers.Default) {
            agent.ask(question, context)
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(AgentEvent.Error(e.message ?: e.javaClass.simpleName))
                }
                .collect { onEvent(id, it) }
        }
    }

    /** 처음 화면으로 (운전 중 "취소" 포함). 진행 중인 질문·행동도 멈춘다. */
    fun clear() {
        askJob?.cancel()
        actionJob?.cancel()
        turn.value = null
    }

    fun retry() {
        turn.value?.question?.let(::ask)
    }

    fun selectStop(index: Int) {
        val id = turn.value?.id ?: return
        updateTurn(id) { it.copy(selectedStop = index) }
    }

    /** 운전자가 고른 행동 실행 (예: 이 코스로 안내). 같은 질문 안에서 한 번에 하나만. */
    fun confirm(proposal: AgentEvent.ActionProposal) {
        val id = turn.value?.id ?: return
        if (turn.value?.action is ActionState.Running) return
        actionJob = viewModelScope.launch(Dispatchers.Default) {
            updateTurn(id) { it.copy(action = ActionState.Running(proposal.id)) }
            val result = try {
                agent.confirm(proposal)
                ActionState.Done(proposal.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ActionState.Failed(proposal.id, e.message ?: e.javaClass.simpleName)
            }
            updateTurn(id) { it.copy(action = result) }
        }
    }

    private fun onEvent(id: Long, event: AgentEvent) {
        when (event) {
            is AgentEvent.AnswerDelta -> updateTurn(id) {
                it.copy(answer = it.answer + event.text, phase = AnswerPhase.Streaming)
            }
            is AgentEvent.Recommendation -> {
                val first = turn.value?.takeIf { it.id == id }?.recommendations?.isEmpty() == true
                updateTurn(id) { t ->
                    val rec = RecommendationUi(event.hit, event.course)
                    val selected = if (first) rec.stops.indexOfFirst { it.name == event.hit.bestStop?.name }.coerceAtLeast(0) else t.selectedStop
                    t.copy(recommendations = t.recommendations + rec, selectedStop = selected)
                }
                if (first) loadMap(id, event.hit.episode.facts.day)
            }
            is AgentEvent.ActionProposal -> updateTurn(id) { it.copy(proposals = it.proposals + event) }
            AgentEvent.Done -> updateTurn(id) { if (it.phase == AnswerPhase.Failed) it else it.copy(phase = AnswerPhase.Done) }
            is AgentEvent.Error -> updateTurn(id) { it.copy(phase = AnswerPhase.Failed, error = event.message) }
        }
    }

    /** 첫 추천 카드의 하루 지도 (Rust가 만든 GeoJSON). 없거나 실패하면 카드가 지도 대신 코스 그림을 보인다. */
    private fun loadMap(id: Long, day: String) {
        viewModelScope.launch {
            val map = try {
                core.dayMap(day)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            updateTurn(id) { t ->
                val head = t.recommendations.firstOrNull() ?: return@updateTurn t
                t.copy(recommendations = listOf(head.copy(map = map, mapLoaded = true)) + t.recommendations.drop(1))
            }
        }
    }

    private inline fun updateTurn(id: Long, crossinline f: (AgentTurn) -> AgentTurn) {
        turn.update { t -> if (t != null && t.id == id) f(t) else t }
    }
}
