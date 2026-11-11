package app.eodiyeotji.ui.agent

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.CourseRoute
import app.eodiyeotji.core.DayMapData
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.MemoryHit
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.agent.RecommendedCourse
import app.eodiyeotji.core.lastKnownLocation
import app.eodiyeotji.platform.SpeechInput
import app.eodiyeotji.platform.SpeechOutput
import app.eodiyeotji.platform.SpeechResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 화면 상태 전체. [turn]이 null이면 아직 묻지 않은 처음 화면(예시 질문)이다.
 * [history]: 앞서 주고받은 대화 (오래된 것부터). 정차 화면은 [history] 아래에 [turn]을 이어 쌓고, 주행 화면은 [turn] 하나만 보인다.
 */
@Immutable
data class AgentUiState(
    val isDriving: Boolean = false,
    val turn: AgentTurn? = null,
    val voice: VoiceUi = VoiceUi(),
    val history: List<AgentTurn> = emptyList(),
)

/** 말로 묻기·답 읽기 상태. */
@Immutable
data class VoiceUi(
    /** 마이크가 듣는 중 */
    val listening: Boolean = false,
    /** 지금까지 알아들은 말 (듣는 중에 보인다) */
    val partial: String = "",
    /** 목소리 크기 0~1 (듣는 표시가 출렁인다) */
    val level: Float = 0f,
    /** 답을 읽는 중 */
    val speaking: Boolean = false,
    /** 답 뒤에 이어서 듣는 중 ("응", "2번으로 가자" 같은 한마디) */
    val followUp: Boolean = false,
    /** 이어 듣기가 추천 코스 고르기다 ("2번으로 가자") — 아니면 안내 제안 ("응") */
    val followUpCourses: Boolean = false,
    /** 잠깐 보이는 안내 ("말씀을 듣지 못했어요" 등) */
    val note: String? = null,
    /** 검색 칸에 받아쓰는 중 ([AgentViewModel.dictate]) — 에이전트 화면의 듣기 표시는 띄우지 않는다 */
    val dictating: Boolean = false,
)

/**
 * 질문 하나와 그 답. 새 질문을 하거나 후보를 고르면 지금 것은 [AgentUiState.history]로 넘어가고 새 것이 아래에 붙는다.
 * [pickedRank]가 있으면 화면에서 후보를 눌러 생긴 차례다 ([question]은 "2번 볼게요" 같은 내 말풍선 글).
 */
@Immutable
data class AgentTurn(
    val id: Long,
    val question: String,
    val pickedRank: Int? = null,
    val answer: String = "",
    val phase: AnswerPhase = AnswerPhase.Thinking,
    val recommendations: List<RecommendationUi> = emptyList(),
    val proposals: List<AgentEvent.ActionProposal> = emptyList(),
    /** 기억 후보 (흐릿한 질문의 답, 번호 = 자리 + 1). 둘 이상이면 후보 칸에 나란히 보이고 [recommendations]는 고른 후보 하나다. */
    val candidates: List<MemoryHit> = emptyList(),
    /** 지금 자세히 보이는 후보 자리 ([candidates] 안). */
    val candidateFocus: Int = 0,
    /** 첫 추천 카드에서 고른 곳 ("여기만 가기" 대상). */
    val selectedStop: Int = 0,
    val action: ActionState = ActionState.Idle,
    val error: String? = null,
    /** 답 아래 흐린 안내 한 줄 (연결 문제 등 — 오류가 아니다, 읽지 않는다). */
    val notice: String? = null,
    /** 근처 추천 코스 목록 (순위순). 비어 있지 않으면 기억 카드 대신 코스 카드를 보인다. */
    val courses: List<RecommendedCourse> = emptyList(),
    /** 지금 안내를 제안한 코스의 자리 ([courses] 안). */
    val courseFocus: Int = 0,
    /** 이번 질문에서 코스 목록을 받았나. 앞 질문에서 넘겨받은 목록은 답이 끝날 때까지 안 오면 지운다. */
    val coursesFresh: Boolean = false,
)

enum class AnswerPhase {
    /** 아직 답 글자가 오지 않음 (도구로 찾는 중). */
    Thinking,
    Streaming,
    Done,
    Failed,
}

/**
 * 추천 카드 하나. [map]은 첫 카드만 불러온다 ([mapLoaded] 전에는 자리만).
 * 지도 핀 번호는 [stops](사진 줄) 순서로 매긴다 — [mapStops]는 지금 [map]이 번호를 맞춘 들른 곳 (코스가 늦게 와 순서가 바뀌면 다시 맞춘다).
 */
@Immutable
data class RecommendationUi(
    val hit: MemoryHit,
    val course: CourseRoute?,
    val map: DayMapData? = null,
    val mapLoaded: Boolean = false,
    val mapStops: List<StopFact>? = null,
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
 * - 말로 묻기: 마이크 → [SpeechInput]이 듣고 알아들은 말을 [ask]에 넘긴다. 운전 중에는 답을 [SpeechOutput]으로 읽고,
 *   안내 제안·추천 코스가 있으면 이어서 한 번 더 듣는다 ("응" 한마디로 안내 시작, "2번으로 가자").
 *   새 질문·마이크·닫기를 하면 읽던 답을 멈춘다.
 */
@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agent: AgentPort,
    private val driving: DrivingStatePort,
    private val core: EodiCore,
    private val speech: SpeechInput,
    private val tts: SpeechOutput,
) : ViewModel() {

    private val turn = MutableStateFlow<AgentTurn?>(null)
    private val history = MutableStateFlow<List<AgentTurn>>(emptyList())
    private val note = MutableStateFlow<String?>(null)
    private val followUp = MutableStateFlow(false)
    private val followUpCourses = MutableStateFlow(false)
    /** 검색 칸 받아쓰기 중 (알아들은 말을 묻지 않고 [dictated]로 넘긴다) */
    private val dictating = MutableStateFlow(false)
    private val _dictated = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 검색 칸 마이크로 알아들은 말 (검색 칸에 넣는다). */
    val dictated: SharedFlow<String> = _dictated.asSharedFlow()

    private val voice = combine(speech.state, tts.speaking, combine(followUp, followUpCourses, dictating, ::Triple), note) { l, s, (f, fc, d), n ->
        VoiceUi(
            listening = l.listening,
            partial = l.partial,
            level = l.level,
            speaking = s,
            followUp = f && l.listening,
            followUpCourses = fc,
            note = n,
            dictating = d && l.listening,
        )
    }

    val state: StateFlow<AgentUiState> = combine(turn, history, driving.isDriving, voice) { t, h, d, v -> AgentUiState(isDriving = d, turn = t, voice = v, history = h) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentUiState(isDriving = driving.isDriving.value))

    /** 주행 중에 주행 화면만 보일지 (차 true, 폰 false). */
    val drivingLocksScreen: Boolean get() = driving.locksScreen

    private var askJob: Job? = null
    private var actionJob: Job? = null
    private var noteJob: Job? = null
    private var lastId = 0L
    /** 불러오는 중인 지도 (차례 id, 번호를 맞출 들른 곳) — 같은 지도를 두 번 부르지 않게. */
    private var mapKey: Pair<Long, List<StopFact>>? = null

    init {
        viewModelScope.launch { speech.results.collect(::onSpeech) }
    }

    /** 마이크 버튼: 듣는 중이면 "다 말했다"(지금까지 들은 말로 묻기), 아니면 듣기 시작. 읽던 답은 멈춘다. */
    fun voice() {
        if (speech.state.value.listening) {
            speech.stop()
        } else {
            dictating.value = false
            listen(followUp = false)
        }
    }

    /**
     * 검색 칸 마이크: 듣고 알아들은 말을 묻지 않고 [dictated]로 넘긴다 (검색 칸에 들어간다).
     * 듣는 중에 다시 누르면 "다 말했다".
     */
    fun dictate() {
        if (speech.state.value.listening) {
            speech.stop()
            return
        }
        tts.stop()
        note.value = null
        followUp.value = false
        dictating.value = true
        speech.start()
    }

    /** 듣기 취소 (× 버튼). */
    fun cancelVoice() {
        followUp.value = false
        dictating.value = false
        speech.cancel()
    }

    /** Android 인식기로 대신 들어야 하는데 마이크 권한이 없으면 true (화면이 권한을 물은 뒤 [voice]). */
    fun needsMicPermission(): Boolean = speech.needsMicPermission()

    /** 마이크 권한을 거절당함. */
    fun micPermissionDenied() = showNote(MSG_NO_MIC)

    /** 지금 답을 다시 읽는다 (정차 중 스피커 버튼). */
    fun speakAnswer() {
        turn.value?.takeIf { it.phase == AnswerPhase.Done || it.phase == AnswerPhase.Failed }?.let { t ->
            tts.speak((t.error ?: t.answer).replace("♥", "하트 "))
        }
    }

    fun ask(utterance: String) = ask(utterance, replace = false)

    /** [replace]: 지금 차례를 대화에 남기지 않고 바꾼다 (실패한 질문 다시 묻기). */
    private fun ask(utterance: String, replace: Boolean) {
        val question = utterance.trim()
        if (question.isEmpty()) return
        tts.stop()
        speech.cancel()
        followUp.value = false
        dictating.value = false
        note.value = null
        askJob?.cancel()
        actionJob?.cancel()
        val id = ++lastId
        // 추천 코스 이야기 중이면 후속 질문("2번으로 가자") 동안 목록을 그대로 둔다 (새 목록이 오면 바뀐다)
        val prev = turn.value
        if (!replace) archive()
        turn.value = AgentTurn(id = id, question = question, courses = prev?.courses.orEmpty(), courseFocus = prev?.courseFocus ?: 0)
        val isDriving = driving.isDriving.value
        askJob = viewModelScope.launch(Dispatchers.Default) {
            // 지금 위치 = 마지막으로 저장된 위치 (근처 추천 코스·지금 위치에서 가장 짧은 순서). DB 한 줄이라 1ms 안쪽.
            val here = try {
                core.lastKnownLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val context = AgentContext(nowMs = System.currentTimeMillis(), currentLocation = here, isDriving = isDriving)
            agent.ask(question, context)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "에이전트 질문 실패", e)
                    emit(AgentEvent.Error(MSG_FAILED))
                }
                .collect { onEvent(id, it) }
        }
    }

    /** 처음 화면으로 (운전 중 "취소" 포함). 진행 중인 질문·행동도 멈춘다. */
    fun clear() {
        askJob?.cancel()
        actionJob?.cancel()
        tts.stop()
        speech.cancel()
        followUp.value = false
        turn.value = null
        history.value = emptyList()
    }

    /**
     * 지금 차례를 대화 기록으로 넘긴다 (새 질문·후보 고르기 직전). 다 오지 않은 답은 온 데까지 남긴다.
     * 기록에서는 버튼을 누를 수 없으니 안내 제안은 빼고, 지도(GeoJSON)도 들고 있지 않는다 (작은 카드로만 보인다).
     */
    private fun archive() {
        val t = turn.value ?: return
        val frozen = t.copy(
            phase = if (t.phase == AnswerPhase.Failed) AnswerPhase.Failed else AnswerPhase.Done,
            proposals = emptyList(),
            action = if (t.action is ActionState.Running) ActionState.Idle else t.action,
            recommendations = t.recommendations.take(1).map { it.copy(map = null, mapLoaded = false, mapStops = null) },
        )
        history.update { (it + frozen).takeLast(MAX_HISTORY) }
    }

    override fun onCleared() {
        tts.stop()
        speech.cancel()
    }

    private fun listen(followUp: Boolean, courses: Boolean = false) {
        tts.stop()
        note.value = null
        dictating.value = false
        this.followUp.value = followUp
        followUpCourses.value = courses
        // 이어 듣기는 한마디("응")라 짧게 기다린다
        if (followUp) speech.start(FOLLOW_UP_LISTEN_MS) else speech.start()
    }

    private fun onSpeech(r: SpeechResult) {
        if (dictating.value) {
            dictating.value = false
            when (r) {
                is SpeechResult.Final -> _dictated.tryEmit(r.text)
                SpeechResult.NoSpeech -> showNote(MSG_NO_SPEECH)
                is SpeechResult.Failed -> showNote(r.message)
            }
            return
        }
        val wasFollowUp = followUp.value
        followUp.value = false
        when (r) {
            is SpeechResult.Final -> ask(r.text)
            // 답 뒤에 이어 듣다가 아무 말이 없으면 조용히 끝낸다 (운전자가 화면 버튼을 누를 수도 있다)
            SpeechResult.NoSpeech -> if (!wasFollowUp) showNote(MSG_NO_SPEECH)
            is SpeechResult.Failed -> if (!wasFollowUp) {
                showNote(r.message)
                if (driving.isDriving.value) tts.speak(r.message)
            }
        }
    }

    private fun showNote(text: String) {
        note.value = text
        noteJob?.cancel()
        noteJob = viewModelScope.launch {
            delay(NOTE_MS)
            note.value = null
        }
    }

    /**
     * 답이 끝났다: 운전 중이면 짧은 답을 읽고, 이어서 고를 것(안내 제안·추천 코스)이 있으면 한 번 더 듣는다
     * ("응" → 안내 시작, "2번으로 가자"). 정차 중에는 읽지 않는다 (화면 스피커 버튼으로 [speakAnswer]).
     */
    private fun speakIfDriving(id: Long) {
        if (!driving.isDriving.value) return
        val t = turn.value?.takeIf { it.id == id } ?: return
        // 소리로 읽을 글: 화면 기호는 말로 ("♥ 513" → "하트 513")
        val text = (t.error ?: t.answer).replace("♥", "하트 ")
        if (text.isBlank()) return
        val choice = t.error == null && t.action is ActionState.Idle && (t.proposals.isNotEmpty() || t.courses.isNotEmpty())
        val courses = t.courses.size > 1
        tts.speak(text) {
            // 다 읽은 뒤에도 같은 질문 화면이고 운전 중일 때만
            if (choice && turn.value?.id == id && driving.isDriving.value && !speech.needsMicPermission()) listen(followUp = true, courses = courses)
        }
    }

    /** 답을 못 받은 질문을 다시 묻는다 (실패한 차례는 대화에 남기지 않고 새 답으로 바꾼다). */
    fun retry() {
        val t = turn.value ?: return
        if (t.pickedRank == null) ask(t.question, replace = true)
    }

    fun selectStop(index: Int) {
        val id = turn.value?.id ?: return
        updateTurn(id) { it.copy(selectedStop = index) }
    }

    /**
     * 후보 칸에서 후보를 눌렀다: 새 질문 없이 그 자리에서 자세한 카드를 바꾸고, Rust에 알려 안내 버튼을 그 후보로 바꾼다.
     * 이어서 말하는 "다른 거"·"응"도 이 후보 기준이 된다.
     */
    fun selectCandidate(index: Int, bubble: String) {
        val t = turn.value ?: return
        val hit = t.candidates.getOrNull(index) ?: return
        if (index == t.candidateFocus || t.action is ActionState.Running) return
        val rec = RecommendationUi(hit, null)
        val stop = rec.stops.indexOfFirst { s -> s.name == hit.bestStop?.name }.coerceAtLeast(0)
        // 답이 다 왔으면 "2번 볼게요" 말풍선과 새 답을 아래에 붙인다 (앞 답은 대화에 남는다). 오는 중이면 그 자리에서 바꾼다.
        val finished = t.phase == AnswerPhase.Done || t.phase == AnswerPhase.Failed
        val id = if (finished) {
            archive()
            val next = ++lastId
            turn.value = t.copy(
                id = next,
                question = bubble,
                pickedRank = index + 1,
                answer = "",
                phase = AnswerPhase.Thinking,
                error = null,
                notice = null,
                candidateFocus = index,
                recommendations = listOf(rec),
                proposals = emptyList(),
                action = ActionState.Idle,
                selectedStop = stop,
                courses = emptyList(),
                courseFocus = 0,
            )
            next
        } else {
            updateTurn(t.id) {
                it.copy(candidateFocus = index, recommendations = listOf(rec), proposals = emptyList(), action = ActionState.Idle, selectedStop = stop)
            }
            t.id
        }
        ensureMap(id)
        val fallback = hit.episode.title ?: hit.dayTitle ?: hit.episode.facts.ruleLabel
        viewModelScope.launch(Dispatchers.Default) {
            val here = try {
                core.lastKnownLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val picked = agent.selectCandidate(hit.episode.facts.id, AgentContext(System.currentTimeMillis(), here, driving.isDriving.value))
            updateTurn(id) { cur ->
                if (cur.candidateFocus != index) return@updateTurn cur
                val phase = if (finished) AnswerPhase.Done else cur.phase
                if (picked == null) return@updateTurn if (finished) cur.copy(answer = fallback, phase = phase) else cur
                val recs = cur.recommendations.map { r -> if (r.hit.episode.facts.id == hit.episode.facts.id && picked.course != null) r.copy(course = picked.course) else r }
                cur.copy(
                    answer = picked.line,
                    phase = phase,
                    proposals = listOfNotNull(picked.proposal),
                    recommendations = recs,
                    selectedStop = sameStop(cur.recommendations.firstOrNull(), recs.firstOrNull(), cur.selectedStop),
                )
            }
            // 코스가 오면 사진 줄 순서가 바뀔 수 있다 → 지도 번호를 다시 맞춘다
            ensureMap(id)
        }
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
            is AgentEvent.CourseRecommendations -> updateTurn(id) {
                it.copy(courses = event.courses, courseFocus = event.focus.coerceIn(0, (event.courses.size - 1).coerceAtLeast(0)), coursesFresh = true)
            }
            is AgentEvent.MemoryCandidates -> {
                val focusId = event.hits.getOrNull(event.focus)?.episode?.facts?.id
                updateTurn(id) { t ->
                    // 후보 칸이 목록을 맡고, 자세한 카드는 고른 후보 하나만
                    t.copy(candidates = event.hits, candidateFocus = event.focus, recommendations = t.recommendations.filter { it.hit.episode.facts.id == focusId })
                }
            }
            is AgentEvent.Recommendation -> {
                val cur = turn.value?.takeIf { it.id == id }
                if (cur != null && cur.candidates.size > 1) {
                    // 후보 모드: 자세한 카드를 이 후보로 바꿔 끼운다 (같은 후보면 지도는 그대로)
                    val old = cur.recommendations.firstOrNull()
                    val same = old?.hit?.episode?.facts?.id == event.hit.episode.facts.id
                    val rec = RecommendationUi(event.hit, event.course).let { if (same && old != null) it.copy(map = old.map, mapLoaded = old.mapLoaded, mapStops = old.mapStops) else it }
                    updateTurn(id) { t ->
                        val selected = if (same) sameStop(old, rec, t.selectedStop) else rec.stops.indexOfFirst { it.name == event.hit.bestStop?.name }.coerceAtLeast(0)
                        t.copy(recommendations = listOf(rec), selectedStop = selected, courses = emptyList(), courseFocus = 0)
                    }
                    ensureMap(id)
                    return
                }
                val first = cur?.recommendations?.isEmpty() == true
                updateTurn(id) { it.copy(courses = emptyList(), courseFocus = 0) }
                updateTurn(id) { t ->
                    val rec = RecommendationUi(event.hit, event.course)
                    // 같은 기억이 다시 오면(빠른 길 → LLM 확인) 새 것으로 바꿔 끼운다 — "다른 날"에 같은 날이 겹치지 않게
                    val epId = event.hit.episode.facts.id
                    val at = t.recommendations.indexOfFirst { it.hit.episode.facts.id == epId }
                    val selected = when {
                        first -> rec.stops.indexOfFirst { it.name == event.hit.bestStop?.name }.coerceAtLeast(0)
                        at == 0 -> sameStop(t.recommendations[0], rec, t.selectedStop)
                        else -> t.selectedStop
                    }
                    val recs = if (at < 0) {
                        t.recommendations + rec
                    } else {
                        val old = t.recommendations[at]
                        t.recommendations.toMutableList().also { it[at] = rec.copy(map = old.map, mapLoaded = old.mapLoaded, mapStops = old.mapStops) }
                    }
                    t.copy(recommendations = recs, selectedStop = selected)
                }
                ensureMap(id)
            }
            is AgentEvent.ActionProposal -> updateTurn(id) { it.copy(proposals = it.proposals + event) }
            is AgentEvent.Notice -> updateTurn(id) { it.copy(notice = event.text) }
            AgentEvent.Done -> {
                updateTurn(id) {
                    val t = if (it.coursesFresh) it else it.copy(courses = emptyList(), courseFocus = 0)
                    if (t.phase == AnswerPhase.Failed) t else t.copy(phase = AnswerPhase.Done)
                }
                speakIfDriving(id)
            }
            is AgentEvent.Error -> {
                updateTurn(id) { it.copy(phase = AnswerPhase.Failed, error = event.message) }
                speakIfDriving(id)
            }
        }
    }

    /**
     * 첫 추천 카드의 하루 지도 (Rust가 만든 GeoJSON, 핀 번호는 카드 사진 줄 순서로 맞춘 것). 이미 맞는 지도가 있거나
     * 같은 지도를 부르는 중이면 아무것도 안 한다. 없거나 실패하면 카드가 지도 대신 사진을 보인다.
     */
    private fun ensureMap(id: Long) {
        val head = turn.value?.takeIf { it.id == id }?.recommendations?.firstOrNull() ?: return
        val stops = head.stops
        if (head.mapLoaded && head.mapStops == stops) return
        val key = id to stops
        if (mapKey == key) return
        mapKey = key
        val epId = head.hit.episode.facts.id
        val day = head.hit.episode.facts.day
        viewModelScope.launch {
            val map = try {
                core.dayMap(day).let { it.copy(geojson = agent.numberMapStops(it.geojson, stops)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (mapKey == key) mapKey = null
            updateTurn(id) { t ->
                val h = t.recommendations.firstOrNull() ?: return@updateTurn t
                // 그새 다른 후보·순서로 바뀌었으면 버린다 (바뀐 쪽 지도를 따로 부른다)
                if (h.hit.episode.facts.id != epId || h.stops != stops) return@updateTurn t
                t.copy(recommendations = listOf(h.copy(map = map, mapLoaded = true, mapStops = stops)) + t.recommendations.drop(1))
            }
        }
    }

    private inline fun updateTurn(id: Long, crossinline f: (AgentTurn) -> AgentTurn) {
        turn.update { t -> if (t != null && t.id == id) f(t) else t }
    }

    private companion object {
        const val TAG = "EodiAgentVm"
        const val NOTE_MS = 4_000L
        const val FOLLOW_UP_LISTEN_MS = 7_000L
        /** 대화 기록은 최근 이만큼만 (오래된 것부터 지운다). */
        const val MAX_HISTORY = 30
        const val MSG_FAILED = "기억을 찾다가 문제가 생겼어요. 다시 물어봐 주세요."
        const val MSG_NO_SPEECH = "말씀을 듣지 못했어요. 마이크를 누르고 다시 말씀해 주세요."
        const val MSG_NO_MIC = "마이크 권한이 있어야 말로 물을 수 있어요."
    }
}

/**
 * 추천 카드가 바뀌어도(코스가 뒤늦게 와 들른 곳 순서가 바뀜) 고른 곳은 그대로 둔다: [old]에서 [index]번째였던 곳을 [new]에서 이름으로 다시 찾는다.
 * 순서 자리만 들고 있으면 "1번은 경포해변" 답 아래 "여기로 안내"가 순서가 바뀐 자리의 다른 곳(초당할머니순두부)을 가리켰다.
 */
private fun sameStop(old: RecommendationUi?, new: RecommendationUi?, index: Int): Int {
    val name = old?.stops?.getOrNull(index)?.name ?: return index
    return new?.stops?.indexOfFirst { it.name == name }?.takeIf { it >= 0 } ?: index
}
