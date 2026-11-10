package app.eodiyeotji.agent

import app.eodiyeotji.core.CourseLeg
import app.eodiyeotji.core.CourseRoute
import app.eodiyeotji.core.DriveRoute
import app.eodiyeotji.core.Episode
import app.eodiyeotji.core.EpisodeFacts
import app.eodiyeotji.core.EpisodeKind
import app.eodiyeotji.core.MemoryHit
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.toModel as toCoreModel
import app.eodiyeotji.core.rust.AgentDone as RAgentDone
import app.eodiyeotji.core.rust.DriveRouteRecommendation as RDriveRouteRecommendation
import app.eodiyeotji.core.rust.AnswerSource as RAnswerSource
import app.eodiyeotji.core.rust.CourseLeg as RCourseLeg
import app.eodiyeotji.core.rust.CourseRoute as RCourseRoute
import app.eodiyeotji.core.rust.Episode as REpisode
import app.eodiyeotji.core.rust.EpisodeKind as REpisodeKind
import app.eodiyeotji.core.rust.MemoryHit as RMemoryHit
import app.eodiyeotji.core.rust.NavAction as RNavAction
import app.eodiyeotji.core.rust.NavProposal as RNavProposal
import app.eodiyeotji.core.rust.StopFact as RStopFact

/**
 * 기억 에이전트가 화면에 내보내는 사건 ([AgentBridge.ask]).
 * 순서: Recommendation(빠른 길이면 수 ms 안) → AnswerDelta… → ActionProposal → Done (또는 Error).
 */
sealed interface AgentEvent {
    /** 답 글 조각. 이어 붙이면 답 전체. 운전 중이면 [Done.answer]를 TTS로 읽는다. */
    data class AnswerDelta(val text: String) : AgentEvent

    /** 추천 카드 (같은 질문에서 여러 번 오면 마지막이 최신). 코스를 짰으면 [course] 포함. */
    data class Recommendation(val hit: MemoryHit, val course: CourseRoute?) : AgentEvent

    /**
     * 근처 추천 드라이브 길 목록 (순서 = 순위). [focus]: 지금 이야기하는(안내를 제안한) 길의 자리.
     * 후속 말("2번으로 가자", "바다 쪽은?")마다 다시 온다 — 마지막이 최신.
     */
    data class CourseRecommendations(val courses: List<RecommendedCourse>, val focus: Int) : AgentEvent

    /** "그 코스로 안내할까요?" — 확인 카드에 [AgentProposal.summary]를 보여 주고, "예"면 [AgentBridge.confirm]. */
    data class ActionProposal(val proposal: AgentProposal) : AgentEvent

    /** 내비 요청을 보냈다 (운전자가 말로 "응/안내해줘"라고 했거나 confirm 했을 때). [ok] = 내비에 요청이 갔는지. */
    data class ActionStarted(val action: AgentNavAction, val ok: Boolean) : AgentEvent

    /** 끝. [source]: 빠른 길(LLM 없음)·LLM·대체(서버 없음)·확인. */
    data class Done(val answer: String, val source: AnswerSource, val timing: Timing) : AgentEvent

    /** 실패 (화면·TTS에 그대로 보여 줄 수 있는 한국어). */
    data class Error(val message: String) : AgentEvent
}

enum class AnswerSource { FAST_PATH, LLM, FALLBACK, CONFIRM }

/** 지연 (ms, 질문 시작부터). */
data class Timing(val toRecommendationMs: Long?, val firstTextMs: Long?, val totalMs: Long, val llmCalls: Int, val llmMs: Long)

/** 추천 드라이브 길 하나 (들를 곳 없는 경치 길). [rank]는 1부터, [distanceKm]은 지금 위치~길 위 가장 가까운 점 (위치를 모르면 null). */
data class RecommendedCourse(
    val rank: Int,
    val route: DriveRoute,
    val distanceKm: Double?,
    val score: Double,
)

/** 운전자 확인을 기다리는 안내 제안. */
data class AgentProposal(val id: String, val summary: String, val action: AgentNavAction)

/** 실행할 내비 명령 (Rust가 만든 그대로, 실행만 Kotlin). */
sealed interface AgentNavAction {
    /** 코스: 구간마다 경유지 ≤ 2 + 목적지. 첫 구간을 요청하고 나머지는 도착하면 [AgentBridge.continueCourse]. */
    data class Course(val episodeId: Long, val title: String, val legs: List<CourseLeg>) : AgentNavAction
    /** 커뮤니티 드라이브 길: 시작점까지 → 끝점까지 (경유지 없는 두 구간). 구간 규칙은 [Course]와 같다. */
    data class SharedCourse(val postId: String, val title: String, val legs: List<CourseLeg>) : AgentNavAction
    data class Place(val stop: StopFact) : AgentNavAction
    data class Search(val keyword: String) : AgentNavAction
}

// ---- Rust 바인딩 값 → 화면용 불변 값 (옮겨 담기만, core/EodiCore.kt와 같은 규칙) ----

internal fun RStopFact.toModel() = StopFact(name, category, address, arriveMs, leaveMs, lat, lon, firstVisit)

internal fun RCourseRoute.toModel() = CourseRoute(stops.map { it.toModel() }, legs.map { it.toModel() }, distanceM)

private fun RCourseLeg.toModel() = CourseLeg(waypoints.map { it.toModel() }, destination.toModel())

private fun REpisodeKind.toModel() = when (this) {
    REpisodeKind.COMMUTE_TO_WORK -> EpisodeKind.COMMUTE_TO_WORK
    REpisodeKind.COMMUTE_HOME -> EpisodeKind.COMMUTE_HOME
    REpisodeKind.ERRAND -> EpisodeKind.ERRAND
    REpisodeKind.OUTING -> EpisodeKind.OUTING
    REpisodeKind.TRIP -> EpisodeKind.TRIP
}

private fun REpisode.toModel() = Episode(
    EpisodeFacts(
        facts.id, facts.day, facts.kind.toModel(), facts.ruleLabel, facts.startMs, facts.endMs, facts.distanceM,
        facts.fromName, facts.toName, facts.stops.map { it.toModel() }, facts.region,
    ),
    title, tags,
)

internal fun RMemoryHit.toModel() = MemoryHit(episode.toModel(), dayTitle, score, bestStop?.toModel(), matchedTags)

internal fun RNavAction.toModel(): AgentNavAction = when (this) {
    is RNavAction.Course -> AgentNavAction.Course(episodeId, title, legs.map { it.toModel() })
    is RNavAction.SharedCourse -> AgentNavAction.SharedCourse(postId, title, legs.map { it.toModel() })
    is RNavAction.Place -> AgentNavAction.Place(stop.toModel())
    is RNavAction.Search -> AgentNavAction.Search(keyword)
}

/** 에이전트 콜백 스레드(UI 밖)에서 부른다. */
internal fun List<RDriveRouteRecommendation>.toModel(): List<RecommendedCourse> =
    mapIndexed { i, r -> RecommendedCourse(i + 1, r.route.toCoreModel(), r.distanceKm.takeIf { it >= 0.0 }, r.score) }

internal fun RNavProposal.toModel() = AgentProposal(id, summary, action.toModel())

internal fun RAgentDone.toEvent() = AgentEvent.Done(
    answer,
    when (source) {
        RAnswerSource.FAST_PATH -> AnswerSource.FAST_PATH
        RAnswerSource.LLM -> AnswerSource.LLM
        RAnswerSource.FALLBACK -> AnswerSource.FALLBACK
        RAnswerSource.CONFIRM -> AnswerSource.CONFIRM
    },
    Timing(
        timing.toRecommendationMs?.toLong(), timing.firstTextMs?.toLong(), timing.totalMs.toLong(),
        timing.llmCalls.toInt(), timing.llmMs.toLong(),
    ),
)
