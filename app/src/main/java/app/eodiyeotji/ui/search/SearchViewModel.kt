package app.eodiyeotji.ui.search

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
import app.eodiyeotji.platform.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
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
    private val navi: Navigator,
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

    fun clearFilter() {
        _state.update { it.copy(filter = SearchFilter()) }
        requestNow()
    }

    /** "여기로 안내" (목적지 하나). */
    fun navigate(name: String, lat: Double, lon: Double, address: String) {
        viewModelScope.launch {
            val ok = runCatching { navi.requestRoute(name, lat, lon, address) }.getOrDefault(false)
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
            Log.w(TAG, "검색 실패: ${s.query}", e)
            return
        }
        if (BuildConfig.DEBUG) Log.i(TAG, "검색 \"${s.query}\" ${s.scope}: ${answer.total}건, Rust ${answer.elapsedMicros}µs")
        // 그사이 말 · 범위 · 칩이 바뀌었으면 버린다 (다음 검색이 곧 온다)
        _state.update { cur -> if (cur.query == s.query && cur.scope == s.scope && cur.filter == s.filter) cur.copy(answer = answer) else cur }
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
