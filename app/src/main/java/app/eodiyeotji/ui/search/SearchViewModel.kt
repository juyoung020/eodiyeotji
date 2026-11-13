package app.eodiyeotji.ui.search

import app.eodiyeotji.core.CourseOffers
import app.eodiyeotji.core.OfferPlace
import app.eodiyeotji.core.PartnerOffer
import app.eodiyeotji.core.courseOffers
import app.eodiyeotji.core.offersForPlaces
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.EpisodeKind
import app.eodiyeotji.core.SearchAnswer
import app.eodiyeotji.core.SearchFilter
import app.eodiyeotji.core.SearchPeriod
import app.eodiyeotji.core.SearchScope
import app.eodiyeotji.core.SearchSuggestions
import app.eodiyeotji.core.clearRecentSearches
import app.eodiyeotji.core.forgetSearch
import app.eodiyeotji.core.rememberSearch
import app.eodiyeotji.core.searchSuggestions
import app.eodiyeotji.core.searchTyped
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.PlaceSpot
import app.eodiyeotji.core.SearchRow
import app.eodiyeotji.core.SearchRowKind
import app.eodiyeotji.core.placeCard
import app.eodiyeotji.core.placesSavedAt
import app.eodiyeotji.core.setPlaceSaved
import app.eodiyeotji.pleos.CourseGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 검색 화면 상태. [active]면 목록 대신 검색 결과(또는 처음 화면)가 보인다. */
@Immutable
data class SearchUiState(
    val active: Boolean = false,
    val query: String = "",
    val scope: SearchScope = SearchScope.RECORDS,
    val filter: SearchFilter = SearchFilter(),
    /** null = 아직 찾지 않음 (말 · 칩이 없음 → 최근 검색어 · 추천 칩) */
    val answer: SearchAnswer? = null,
    val suggestions: SearchSuggestions = SearchSuggestions(),
    /** "여기로 안내" 결과 (true = 내비에 넘김, false = 못 넘김), 잠깐 보인다 */
    val navi: Boolean? = null,
    /** 장소 줄([SearchRowKind.PLACE])의 저장(♥) 여부 (줄 키 → 저장됨). 없는 키는 아직 모름. */
    val saved: Map<String, Boolean> = emptyMap(),
    /** 장소 · 리뷰 줄의 제휴 쿠폰 (줄 키 → 쿠폰, 쿠폰 없는 줄은 빠짐) */
    val offers: Map<String, List<PartnerOffer>> = emptyMap(),
    /** 코스 줄의 제휴 쿠폰 (줄 키 → 코스 쿠폰) */
    val courseOffers: Map<String, CourseOffers> = emptyMap(),
) {
    /** 말도 칩도 없으면 처음 화면 */
    val idle: Boolean get() = query.isBlank() && !filter.isSet
}

/**
 * 손으로 치는 검색 (내 기록 / 커뮤니티 하위 탭). 칠 때마다 [DEBOUNCE_MS] 기다렸다가 Rust `search_typed`를 IO 스레드에서 부른다
 * (1년치 수 ms). 새 글자가 오면 앞 검색은 버린다(collectLatest). 계산은 모두 Rust — 여기서는 상태만 옮긴다.
 * 화면마다 따로 쓴다 (`hiltViewModel(key = "search-records")` 등) → 탭을 오가도 각자 말이 남는다.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val core: EodiCore,
    private val guide: CourseGuide,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** (말, 범위, 칩) 바뀜 → 찾기. 칩 · 범위는 바로, 말은 잠깐 모아서. */
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            requests.debounce(DEBOUNCE_MS).collectLatest { runSearch() }
        }
        if (BuildConfig.DEBUG) {
            // 디버그: 에뮬레이터에서 한글을 칠 수 없을 때 adb 브로드캐스트로 넣은 말 (src/debug의 SearchDebugReceiver)
            viewModelScope.launch {
                SearchDebugInput.texts.collect { t -> if (_state.value.active) setQuery(t) }
            }
        }
    }

    /** 검색 칸을 눌렀다 (처음 화면 · 결과가 보인다). */
    fun open() {
        if (!_state.value.active) _state.update { it.copy(active = true) }
        loadSuggestions()
    }

    /** 결과를 열었다: 결과 자리를 접는다 (말 · 결과는 남아 칸을 다시 누르면 그대로 보인다). */
    fun hide() {
        _state.update { it.copy(active = false) }
    }

    /** 닫기 (뒤로 · 닫기 버튼): 말 · 칩 · 결과를 비운다. */
    fun close() {
        _state.update { SearchUiState(scope = it.scope, suggestions = it.suggestions) }
    }

    fun setScope(scope: SearchScope) {
        if (_state.value.scope == scope) return
        // 커뮤니티 하위 탭이 바뀌면 그 탭에서 같은 말로 다시 찾는다 (칩은 내 기록 전용이라 비운다)
        _state.update { it.copy(scope = scope, filter = SearchFilter(), answer = null) }
        requestNow()
    }

    fun setQuery(text: String) {
        if (text == _state.value.query) return
        _state.update { it.copy(query = text, active = true) }
        if (_state.value.idle) {
            _state.update { it.copy(answer = null) }
            loadSuggestions()
        }
        requests.tryEmit(Unit)
    }

    /** 키보드 "검색" · 칩 누름: 바로 찾고 최근 검색어에 넣는다. */
    fun submit(text: String = _state.value.query) {
        _state.update { it.copy(query = text, active = true) }
        remember(text)
        requestNow()
    }

    /** 결과를 열거나 AI에게 넘길 때 말을 최근 검색어에 남긴다. */
    fun remember(text: String = _state.value.query) {
        if (text.isBlank()) return
        viewModelScope.launch { runCatching { core.rememberSearch(text) } }
    }

    fun forget(text: String) {
        viewModelScope.launch {
            runCatching { core.forgetSearch(text) }
            loadSuggestionsNow()
        }
    }

    fun clearRecent() {
        viewModelScope.launch {
            runCatching { core.clearRecentSearches() }
            loadSuggestionsNow()
        }
    }

    fun setPeriod(period: SearchPeriod, from: LocalDate? = null, to: LocalDate? = null) {
        _state.update { it.copy(filter = it.filter.copy(period = period, from = from, to = to)) }
        requestNow()
    }

    fun toggleKind(kind: EpisodeKind) {
        _state.update { s ->
            val k = s.filter.kinds
            s.copy(filter = s.filter.copy(kinds = if (kind in k) k - kind else k + kind))
        }
        requestNow()
    }

    fun toggleFirstVisit() {
        _state.update { it.copy(filter = it.filter.copy(firstVisitOnly = !it.filter.firstVisitOnly)) }
        requestNow()
    }

    /** 종류 하나만 모아 보기 (첫 화면의 "여행 모음" 입구): 말 없이 그 종류 칩만 켠 채로 연다. */
    fun openKind(kind: EpisodeKind) {
        _state.update { it.copy(active = true, query = "", filter = SearchFilter(kinds = setOf(kind))) }
        requestNow()
    }

    fun clearFilter() {
        _state.update { it.copy(filter = SearchFilter()) }
        requestNow()
    }

    /** "여기로 안내" (목적지 하나). */
    fun navigate(name: String, lat: Double, lon: Double, address: String) {
        viewModelScope.launch {
            val ok = runCatching { guide.startPlace(name, lat, lon, address) }.getOrDefault(false)
            _state.update { it.copy(navi = ok) }
            kotlinx.coroutines.delay(NAVI_NOTE_MS)
            _state.update { it.copy(navi = null) }
        }
    }

    private fun requestNow() {
        viewModelScope.launch { runSearch() }
    }

    private suspend fun runSearch() {
        val s = _state.value
        if (s.idle) {
            _state.update { it.copy(answer = null) }
            return
        }
        val answer = try {
            core.searchTyped(s.query, s.scope, s.filter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "검색 실패 (${s.query.length}자)", e)
            return
        }
        if (BuildConfig.DEBUG) Log.i(TAG, "검색 \"${s.query}\" ${s.scope}: ${answer.total}건, Rust ${answer.elapsedMicros}µs")
        // 그사이 말 · 범위 · 칩이 바뀌었으면 버린다 (다음 검색이 곧 온다)
        _state.update { cur -> if (cur.query == s.query && cur.scope == s.scope && cur.filter == s.filter) cur.copy(answer = answer) else cur }
        // ♥ · 쿠폰은 서로 기다릴 필요가 없다 → 동시에 묻고, 결과 목록은 한 번만 다시 그린다
        val (saved, offers) = coroutineScope {
            val saved = async { savedFlags(answer) }
            val offers = async { offerMaps(answer) }
            saved.await() to offers.await()
        }
        // 줄 키는 검색마다 다시 쓰이므로 지난 검색의 값은 버리고 이번 결과 것만 둔다 (세션 내내 쌓이지 않게)
        _state.update { cur ->
            if (cur.answer !== answer) cur else cur.copy(saved = saved, offers = offers.first, courseOffers = offers.second)
        }
    }

    /** 장소 · 리뷰 · 코스 줄의 제휴 쿠폰을 한 번에 묻는다 (이름 · 좌표 맞추기는 Rust). */
    private suspend fun offerMaps(answer: SearchAnswer): Pair<Map<String, List<PartnerOffer>>, Map<String, CourseOffers>> {
        val rows = answer.groups.flatMap { it.rows }
        val placeRows = rows.mapNotNull { r ->
            val p = r.place ?: r.reviewPlace?.place
            if (p != null && p.name.isNotBlank() && (r.kind == SearchRowKind.PLACE || r.kind == SearchRowKind.REVIEW)) r to OfferPlace(p.name, p.lat, p.lon) else null
        }
        val courseRows = rows.mapNotNull { r -> r.course?.let { r to it.post.stops } }
        val places = runCatching { core.offersForPlaces(placeRows.map { it.second }) }.getOrNull().orEmpty()
        val courses = runCatching { core.courseOffers(courseRows.map { it.second }) }.getOrNull().orEmpty()
        val offers = placeRows.map { it.first.key }.zip(places).filter { it.second.isNotEmpty() }.toMap()
        val courseOffers = courseRows.map { it.first.key }.zip(courses).filter { it.second.placeCount > 0 }.toMap()
        return offers to courseOffers
    }

    /** 장소 줄의 ♥ 상태를 한 번에 묻는다 (좌표 → 그 자리 장소가 저장됐는지, Rust). */
    private suspend fun savedFlags(answer: SearchAnswer): Map<String, Boolean> {
        val rows = answer.groups.flatMap { g -> g.rows.filter { it.kind == SearchRowKind.PLACE && it.place != null } }
        if (rows.isEmpty()) return emptyMap()
        val flags = runCatching { core.placesSavedAt(rows.map { LatLon(it.place!!.lat, it.place.lon) }) }.getOrNull() ?: return emptyMap()
        return rows.map { r -> r.key }.zip(flags).toMap()
    }

    /** 장소 줄의 ♥ 누르기: 그 자리 장소를 저장하거나 푼다. */
    fun toggleSaved(row: SearchRow) {
        val p = row.place ?: return
        val now = _state.value.saved[row.key] == true
        _state.update { it.copy(saved = it.saved + (row.key to !now)) }
        viewModelScope.launch {
            val ok = runCatching {
                val card = core.placeCard(PlaceSpot.point(p.lat, p.lon)) ?: return@runCatching false
                core.setPlaceSaved(card.id, !now)
                true
            }.getOrDefault(false)
            if (!ok) _state.update { it.copy(saved = it.saved + (row.key to now)) }
        }
    }

    private fun loadSuggestions() {
        viewModelScope.launch { loadSuggestionsNow() }
    }

    private suspend fun loadSuggestionsNow() {
        val sg = runCatching { core.searchSuggestions() }.getOrNull() ?: return
        _state.update { it.copy(suggestions = sg) }
    }

    private companion object {
        const val TAG = "EodiSearch"
        const val DEBOUNCE_MS = 150L
        const val NAVI_NOTE_MS = 2_500L
    }
}

/** 디버그 빌드: adb로 검색 칸에 말 넣기 (에뮬레이터 키보드로 한글을 치기 어려울 때). 받는 쪽은 열려 있는 검색 화면. */
object SearchDebugInput {
    val texts = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
