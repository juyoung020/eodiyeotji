package app.eodiyeotji.core

import app.eodiyeotji.core.rust.SearchChipKind as RSearchChipKind
import app.eodiyeotji.core.rust.SearchFilter as RSearchFilter
import app.eodiyeotji.core.rust.SearchGroupKind as RSearchGroupKind
import app.eodiyeotji.core.rust.SearchPeriod as RSearchPeriod
import app.eodiyeotji.core.rust.SearchRow as RSearchRow
import app.eodiyeotji.core.rust.SearchRowKind as RSearchRowKind
import app.eodiyeotji.core.rust.SearchScope as RSearchScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 검색 화면 입구 (core-rs/src/search_ui.rs). 말 풀기 · 날짜 말 · 묶기 · 순위 · 날짜 글은 Rust가 하고, 여기서는 옮겨 담기만 한다.
 */

/** 어디를 찾나: 내 기록, 또는 커뮤니티의 지금 하위 탭. */
enum class SearchScope { RECORDS, COURSES, REVIEWS, BOARD }

/** 기간 칩. */
enum class SearchPeriod { ANY, THIS_WEEK, THIS_MONTH, THIS_YEAR, CUSTOM }

/** 칩으로 고른 조건 (내 기록 찾기에만). [from]·[to]는 [SearchPeriod.CUSTOM]일 때. */
data class SearchFilter(
    val period: SearchPeriod = SearchPeriod.ANY,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val kinds: Set<EpisodeKind> = emptySet(),
    val firstVisitOnly: Boolean = false,
) {
    val isSet: Boolean get() = period != SearchPeriod.ANY || kinds.isNotEmpty() || firstVisitOnly
}

enum class SearchGroupKind { PLACES, DAYS, COMMUNITY }
enum class SearchRowKind { PLACE, DAY, COURSE, REVIEW, POST }

/** 결과 한 줄 (글은 그대로 그린다). 열기 · 지도 · 안내에 쓸 것만 같이 온다. */
data class SearchRow(
    val key: String,
    val kind: SearchRowKind,
    val title: String,
    val subtitle: String,
    val dateLine: String,
    val day: String?,
    val place: StopFact?,
    val firstVisit: Boolean,
    val episodeId: Long?,
    val course: DriveCourse?,
    val reviewPlace: PopularPlace?,
    val post: BoardPost?,
)

data class SearchGroup(val kind: SearchGroupKind, val title: String, val rows: List<SearchRow>, val more: Int)

/** "AI에게 물어보기" ([primary]면 맨 위에 크게). */
data class AskAi(val text: String, val primary: Boolean)

data class SearchAnswer(
    val text: String,
    val terms: List<String>,
    val dateLabel: String?,
    val askAi: AskAi?,
    val groups: List<SearchGroup>,
    val total: Int,
    val elapsedMicros: Long,
)

enum class SearchChipKind { KEYWORD, DATE, PLACE }
data class SearchChip(val label: String, val text: String, val kind: SearchChipKind)
data class SearchSuggestions(val recent: List<String> = emptyList(), val chips: List<SearchChip> = emptyList())

/** 친 말로 찾기 (1년치도 수 ms). */
suspend fun EodiCore.searchTyped(text: String, scope: SearchScope, filter: SearchFilter): SearchAnswer = withContext(Dispatchers.IO) {
    val a = rust.searchTyped(text, scope.toR(), filter.toR(), System.currentTimeMillis())
    SearchAnswer(
        text = a.text,
        terms = a.terms,
        dateLabel = a.dateLabel,
        askAi = a.askAi?.let { AskAi(it.text, it.primary) },
        groups = a.groups.map { g ->
            SearchGroup(
                kind = when (g.kind) {
                    RSearchGroupKind.PLACES -> SearchGroupKind.PLACES
                    RSearchGroupKind.DAYS -> SearchGroupKind.DAYS
                    RSearchGroupKind.COMMUNITY -> SearchGroupKind.COMMUNITY
                },
                title = g.title,
                rows = g.rows.map { it.toModel() },
                more = g.more.toInt(),
            )
        },
        total = a.total.toInt(),
        elapsedMicros = a.elapsedMicros.toLong(),
    )
}

/** 아무것도 치지 않았을 때 보일 최근 검색어 · 추천 칩. */
suspend fun EodiCore.searchSuggestions(): SearchSuggestions = withContext(Dispatchers.IO) {
    val s = rust.searchSuggestions()
    SearchSuggestions(
        recent = s.recent,
        chips = s.chips.map {
            SearchChip(
                it.label,
                it.text,
                when (it.kind) {
                    RSearchChipKind.KEYWORD -> SearchChipKind.KEYWORD
                    RSearchChipKind.DATE -> SearchChipKind.DATE
                    RSearchChipKind.PLACE -> SearchChipKind.PLACE
                },
            )
        },
    )
}

suspend fun EodiCore.rememberSearch(text: String) = withContext(Dispatchers.IO) { rust.rememberSearch(text, System.currentTimeMillis()) }
suspend fun EodiCore.forgetSearch(text: String) = withContext(Dispatchers.IO) { rust.forgetSearch(text) }
suspend fun EodiCore.clearRecentSearches() = withContext(Dispatchers.IO) { rust.clearRecentSearches() }

private fun SearchScope.toR() = when (this) {
    SearchScope.RECORDS -> RSearchScope.RECORDS
    SearchScope.COURSES -> RSearchScope.COURSES
    SearchScope.REVIEWS -> RSearchScope.REVIEWS
    SearchScope.BOARD -> RSearchScope.BOARD
}

private fun SearchFilter.toR() = RSearchFilter(
    period = when (period) {
        SearchPeriod.ANY -> RSearchPeriod.ANY
        SearchPeriod.THIS_WEEK -> RSearchPeriod.THIS_WEEK
        SearchPeriod.THIS_MONTH -> RSearchPeriod.THIS_MONTH
        SearchPeriod.THIS_YEAR -> RSearchPeriod.THIS_YEAR
        SearchPeriod.CUSTOM -> RSearchPeriod.CUSTOM
    },
    fromDay = from?.toString(),
    toDay = to?.toString(),
    kinds = kinds.map { it.toRust() },
    firstVisitOnly = firstVisitOnly,
)

private fun RSearchRow.toModel() = SearchRow(
    key = key,
    kind = when (kind) {
        RSearchRowKind.PLACE -> SearchRowKind.PLACE
        RSearchRowKind.DAY -> SearchRowKind.DAY
        RSearchRowKind.COURSE -> SearchRowKind.COURSE
        RSearchRowKind.REVIEW -> SearchRowKind.REVIEW
        RSearchRowKind.POST -> SearchRowKind.POST
    },
    title = title,
    subtitle = subtitle,
    dateLine = dateLine,
    day = day,
    place = place?.toModel(),
    firstVisit = firstVisit,
    episodeId = episodeId,
    course = course?.toDrive(),
    reviewPlace = reviewPlace?.let { PopularPlace(it.place.toModel(), it.hearts.toInt(), it.reviewCount.toInt(), it.avgRating, it.topKeyword) },
    post = post?.toModel(),
)
