package app.eodiyeotji.ui.agent

import android.util.Log
import app.eodiyeotji.core.CourseLeg
import app.eodiyeotji.core.CourseRoute
import app.eodiyeotji.core.Episode
import app.eodiyeotji.core.EpisodeFacts
import app.eodiyeotji.core.EpisodeKind
import app.eodiyeotji.core.MemoryHit
import app.eodiyeotji.core.StopFact
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * 화면 개발·미리보기·시연용 가짜 에이전트. 진짜 LLM·도구 없이 그럴듯한 답을 글자 조각으로 흘려보낸다.
 * 정해 둔 기억 몇 개(강릉 바다 여행, 엄마랑 간 식당, 퇴근길 카페, 다른 바다)를 질문 낱말로 고른다.
 *
 * TODO(통합): MemoryAgent로 교체 (AgentTempModule). 이 클래스는 미리보기·디버그 화면에만 남긴다.
 */
@Singleton
class FakeAgentPort @Inject constructor() : AgentPort {

    override fun ask(utterance: String, context: AgentContext): Flow<AgentEvent> = flow {
        // 도구 호출(기기 안 검색)에 걸리는 시간 흉내
        delay(THINK_MS)
        val q = utterance.replace(" ", "")
        when {
            q.contains("다른날") -> otherBeachDays()
            q.contains("만안내") -> singleStop(utterance.substringBefore("만").trim())
            q.contains("을왕리") -> memory(Eulwangni, "${Eulwangni.dateText}, 을왕리해수욕장에서 노을을 보고 오셨어요. 그 코스로 안내할까요?")
            q.contains("대천") -> memory(Daecheon, "${Daecheon.dateText}, 대천해수욕장에 다녀오셨어요. 그 코스로 안내할까요?")
            q.contains("바다") || q.contains("해변") || q.contains("강릉") -> memory(
                Gangneung,
                "2025년 8월 14일, 가족 네 분이 강릉에 다녀오셨어요. " +
                    "경포해변에서 놀고 점심은 초당할머니순두부, 마지막은 안목해변 카페거리였어요. 그 코스로 안내할까요?",
            )
            q.contains("엄마") || q.contains("식당") -> memory(
                Yangpyeong,
                "10월 3일 개천절에 어머니와 양평 옥천냉면 황해식당에 다녀오셨어요. 돌아오는 길엔 두물머리에 들르셨어요. 다시 가 볼까요?",
            )
            q.contains("카페") || q.contains("퇴근") -> memory(
                Seongsu,
                "지난주 수요일 퇴근길에 성수동 어니언 성수에 20분쯤 들르셨어요. 거기로 안내할까요?",
                single = true,
            )
            else -> {
                say("그런 기억은 아직 찾지 못했어요. 언제쯤, 누구와 갔는지 조금만 더 알려 주실래요?")
            }
        }
        emit(AgentEvent.Done)
    }

    override suspend fun confirm(proposal: AgentEvent.ActionProposal) {
        // 내비에 경로를 넘기는 시간 흉내. 실제로는 NaviHelper requestRouteWithWaypoints.
        delay(CONFIRM_MS)
        Log.i(TAG, "가짜 안내 시작: ${proposal.id}")
    }

    private suspend fun FlowCollector<AgentEvent>.memory(m: FakeMemory, answer: String, single: Boolean = false) {
        // 첫 문장을 말하는 동안 카드가 먼저 뜬다 (도구 결과는 답보다 먼저 와 있다)
        val firstSentence = answer.indexOf(". ").let { if (it < 0) answer.length else it + 2 }
        say(answer.substring(0, firstSentence))
        emit(AgentEvent.Recommendation(m.hit, m.course))
        say(answer.substring(firstSentence))
        if (single) {
            val stop = m.stops.first()
            emit(AgentEvent.ActionProposal("navigate_stop:${stop.name}", "${stop.name}${ro(stop.name)} 안내", stop.address))
        } else {
            emit(AgentEvent.ActionProposal("navigate_course:${m.id}", "이 코스로 안내", m.stops.joinToString(" → ") { it.name }))
            emit(
                AgentEvent.ActionProposal(
                    "navigate_course_optimized:${m.id}",
                    "가장 빠른 순서로 안내",
                    "지금 위치에서 가장 짧은 순서로 다시 짜요",
                ),
            )
        }
    }

    private suspend fun FlowCollector<AgentEvent>.singleStop(name: String) {
        val stop = AllMemories.flatMap { it.stops }.firstOrNull { it.name == name }
        if (stop == null) {
            say("어느 곳으로 갈지 다시 한 번 말씀해 주세요.")
            return
        }
        say("${stop.name} 한 곳으로 바로 안내할까요?")
        emit(AgentEvent.ActionProposal("navigate_stop:${stop.name}", "${stop.name}${ro(stop.name)} 안내", stop.address))
    }

    private suspend fun FlowCollector<AgentEvent>.otherBeachDays() {
        say("바다에 간 날이 두 번 더 있어요. ")
        emit(AgentEvent.Recommendation(Eulwangni.hit, Eulwangni.course))
        emit(AgentEvent.Recommendation(Daecheon.hit, Daecheon.course))
        say("${Eulwangni.dateText} 을왕리, ${Daecheon.dateText} 대천이에요. 어느 날로 볼까요?")
    }

    /** 사람이 말하듯 두세 글자씩 끊어 보낸다 (스트리밍 흉내). */
    private suspend fun FlowCollector<AgentEvent>.say(text: String) {
        var i = 0
        while (i < text.length) {
            val n = CHUNK_SIZES[i % CHUNK_SIZES.size]
            emit(AgentEvent.AnswerDelta(text.substring(i, minOf(text.length, i + n))))
            i += n
            delay(CHUNK_MS)
        }
    }

    private class FakeMemory(
        val id: Long,
        val dateText: String,
        val title: String,
        val dayTitle: String,
        val kind: EpisodeKind,
        val ruleLabel: String,
        val region: String,
        val distanceKm: Double,
        day: LocalDate,
        start: LocalTime,
        end: LocalTime,
        val stops: List<StopFact>,
        val tags: List<String>,
    ) {
        private val zone = ZoneId.of("Asia/Seoul")
        private val startMs = day.atTime(start).atZone(zone).toInstant().toEpochMilli()
        private val endMs = day.atTime(end).atZone(zone).toInstant().toEpochMilli()

        val hit: MemoryHit = MemoryHit(
            episode = Episode(
                facts = EpisodeFacts(
                    id = id, day = day.toString(), kind = kind, ruleLabel = ruleLabel,
                    startMs = startMs, endMs = endMs, distanceM = distanceKm * 1000.0,
                    fromName = "집", toName = "집", stops = stops, region = region,
                ),
                title = title,
                tags = tags,
            ),
            dayTitle = dayTitle,
            score = 0.92,
            bestStop = stops.first(),
        )

        // 내비 경유지는 최대 2개 → 마지막 곳이 목적지, 앞의 두 곳이 경유지 (Rust course::split_legs와 같은 모양)
        val course: CourseRoute = CourseRoute(
            stops = stops,
            legs = listOf(CourseLeg(waypoints = stops.dropLast(1).take(2), destination = stops.last())),
            distanceM = distanceKm * 1000.0,
        )
    }

    private companion object {
        const val TAG = "FakeAgentPort"
        const val THINK_MS = 700L
        const val CHUNK_MS = 45L
        const val CONFIRM_MS = 600L
        val CHUNK_SIZES = intArrayOf(2, 3, 2, 4, 3)

        fun ro(word: String): String = roJosa(word)

        fun stop(name: String, category: String, address: String, day: LocalDate, arrive: String, leave: String, lat: Double, lon: Double, first: Boolean = false): StopFact {
            val zone = ZoneId.of("Asia/Seoul")
            fun ms(t: String) = day.atTime(LocalTime.parse(t)).atZone(zone).toInstant().toEpochMilli()
            return StopFact(name, category, address, ms(arrive), ms(leave), lat, lon, first)
        }

        private val GangneungDay = LocalDate.of(2025, 8, 14)
        val Gangneung = FakeMemory(
            id = 20250814, dateText = "작년 8월 14일", title = "가족과 강릉 바다 여행", dayTitle = "가족 넷이 떠난 강릉 바다 여행",
            kind = EpisodeKind.TRIP, ruleLabel = "강릉 여행", region = "강원 강릉시", distanceKm = 421.0,
            day = GangneungDay, start = LocalTime.of(7, 5), end = LocalTime.of(19, 40),
            stops = listOf(
                stop("경포해변", "해수욕장", "강원 강릉시 강문동", GangneungDay, "10:20", "12:10", 37.8055, 128.9089, first = true),
                stop("초당할머니순두부", "음식점", "강원 강릉시 초당순두부길 77", GangneungDay, "12:25", "13:30", 37.7913, 128.9147, first = true),
                stop("안목해변", "카페거리", "강원 강릉시 창해로14번길", GangneungDay, "13:50", "15:40", 37.7722, 128.9479),
            ),
            tags = listOf("바다", "해변", "가족", "여행", "순두부", "카페"),
        )

        private val EulwangniDay = LocalDate.of(2024, 7, 21)
        val Eulwangni = FakeMemory(
            id = 20240721, dateText = "2024년 7월 21일", title = "을왕리 노을 드라이브", dayTitle = "을왕리 해변 노을",
            kind = EpisodeKind.OUTING, ruleLabel = "을왕리 나들이", region = "인천 중구", distanceKm = 112.0,
            day = EulwangniDay, start = LocalTime.of(15, 10), end = LocalTime.of(22, 5),
            stops = listOf(
                stop("을왕리해수욕장", "해수욕장", "인천 중구 을왕동", EulwangniDay, "16:40", "19:50", 37.4475, 126.3722),
                stop("선녀바위해변", "해변", "인천 중구 을왕동", EulwangniDay, "20:05", "20:40", 37.4434, 126.3807),
            ),
            tags = listOf("바다", "노을", "해변", "드라이브"),
        )

        private val DaecheonDay = LocalDate.of(2025, 6, 7)
        val Daecheon = FakeMemory(
            id = 20250607, dateText = "2025년 6월 7일", title = "대천 바다와 조개구이", dayTitle = "대천해수욕장 당일치기",
            kind = EpisodeKind.TRIP, ruleLabel = "보령 여행", region = "충남 보령시", distanceKm = 318.0,
            day = DaecheonDay, start = LocalTime.of(8, 30), end = LocalTime.of(20, 10),
            stops = listOf(
                stop("대천해수욕장", "해수욕장", "충남 보령시 신흑동", DaecheonDay, "11:10", "14:00", 36.3051, 126.5143),
                stop("대천항 수산시장", "시장", "충남 보령시 대천항로", DaecheonDay, "14:20", "15:30", 36.3297, 126.5130),
            ),
            tags = listOf("바다", "해수욕장", "조개구이"),
        )

        private val YangpyeongDay = LocalDate.of(2026, 10, 3)
        val Yangpyeong = FakeMemory(
            id = 20261003, dateText = "10월 3일", title = "엄마랑 양평 냉면", dayTitle = "개천절, 엄마와 양평",
            kind = EpisodeKind.OUTING, ruleLabel = "양평 나들이", region = "경기 양평군", distanceKm = 96.0,
            day = YangpyeongDay, start = LocalTime.of(10, 40), end = LocalTime.of(17, 20),
            stops = listOf(
                stop("옥천냉면 황해식당", "음식점", "경기 양평군 옥천면 옥천길 92", YangpyeongDay, "12:00", "13:00", 37.5340, 127.4720, first = true),
                stop("두물머리", "관광지", "경기 양평군 양서면 양수리", YangpyeongDay, "13:40", "15:10", 37.5330, 127.3140),
            ),
            tags = listOf("엄마", "식당", "냉면", "양평"),
        )

        private val SeongsuDay = LocalDate.of(2026, 10, 28)
        val Seongsu = FakeMemory(
            id = 20261028, dateText = "10월 28일", title = "퇴근길 · 어니언 성수 들름", dayTitle = "평소 출퇴근",
            kind = EpisodeKind.COMMUTE_HOME, ruleLabel = "퇴근길 · 어니언 성수 들름", region = "서울 성동구", distanceKm = 14.2,
            day = SeongsuDay, start = LocalTime.of(18, 20), end = LocalTime.of(19, 25),
            stops = listOf(
                stop("어니언 성수", "카페", "서울 성동구 아차산로9길 8", SeongsuDay, "18:45", "19:05", 37.5448, 127.0582, first = true),
            ),
            tags = listOf("카페", "퇴근길", "성수"),
        )

        val AllMemories = listOf(Gangneung, Eulwangni, Daecheon, Yangpyeong, Seongsu)
    }
}
