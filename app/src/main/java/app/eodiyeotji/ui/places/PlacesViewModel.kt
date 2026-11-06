package app.eodiyeotji.ui.places

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.AnchorRole
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.HiddenRecord
import app.eodiyeotji.core.HomeOffice
import app.eodiyeotji.core.PlaceCard
import app.eodiyeotji.core.frequentPlaces
import app.eodiyeotji.core.hiddenRecords
import app.eodiyeotji.core.homeOffice
import app.eodiyeotji.core.restoreHidden
import app.eodiyeotji.core.savedPlaces
import app.eodiyeotji.core.setMyAnchor
import app.eodiyeotji.core.setPlaceSaved
import app.eodiyeotji.platform.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlacesTab { SAVED, FREQUENT, SETTINGS }

@Immutable
data class PlacesUiState(
    val tab: PlacesTab = PlacesTab.SAVED,
    val loaded: Boolean = false,
    val saved: List<PlaceCard> = emptyList(),
    val frequent: List<PlaceCard> = emptyList(),
    val homeOffice: HomeOffice = HomeOffice(null, null),
    val hidden: List<HiddenRecord> = emptyList(),
    @StringRes val notice: Int? = null,
)

/**
 * 내 장소: 저장한 곳 · 자주 가는 곳(순위는 Rust) · 집 · 회사 · 숨긴 기록.
 * 처음 열 때와 기록이 바뀔 때(EodiCore.changes) 다시 읽는다. Rust 호출은 IO 스레드에서.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val core: EodiCore,
    private val navi: Navigator,
) : ViewModel() {
    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()
    private var noticeJob: Job? = null

    init {
        reload()
        viewModelScope.launch { core.changes.debounce(RELOAD_DEBOUNCE_MS).collect { load() } }
    }

    fun setTab(tab: PlacesTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun reload() {
        viewModelScope.launch { load() }
    }

    fun toggleSaved(card: PlaceCard) = act(if (card.saved) R.string.notice_unsaved else R.string.notice_saved) {
        // 바로 바뀐 것처럼 보이고(저장한 곳 목록에서는 남겨 두었다가 다시 읽을 때 빠진다), 저장은 Rust
        _state.update { s ->
            fun List<PlaceCard>.flip() = map { if (it.id == card.id) it.copy(saved = !card.saved) else it }
            s.copy(saved = s.saved.flip(), frequent = s.frequent.flip())
        }
        core.setPlaceSaved(card.id, !card.saved)
    }

    fun navigate(card: PlaceCard) {
        viewModelScope.launch {
            val l = card.label
            val ok = runCatching { navi.requestRoute(l?.name.orEmpty(), card.lat, card.lon, l?.address.orEmpty()) }.getOrDefault(false)
            show(if (ok) R.string.notice_navi_ok else R.string.notice_navi_fail)
        }
    }

    fun restore(r: HiddenRecord) = act(R.string.notice_restored) { core.restoreHidden(r.id) }

    /** 사용자가 정한 집 · 회사를 지운다 (내비 · 기록 추정으로 돌아간다). */
    fun resetAnchor(role: AnchorRole) = act(R.string.notice_restored) { core.setMyAnchor(role, null) }

    private suspend fun load() {
        try {
            val saved = core.savedPlaces()
            val frequent = core.frequentPlaces()
            val ho = core.homeOffice()
            val hidden = core.hiddenRecords()
            _state.update { it.copy(loaded = true, saved = saved, frequent = frequent, homeOffice = ho, hidden = hidden) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "내 장소 읽기 실패", e)
            _state.update { it.copy(loaded = true) }
        }
    }

    private fun act(@StringRes done: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                show(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "내 장소 동작 실패", e)
            }
            load()
        }
    }

    private fun show(@StringRes res: Int) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = res) }
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS)
            _state.update { it.copy(notice = null) }
        }
    }

    private companion object {
        const val TAG = "EodiPlaces"
        const val RELOAD_DEBOUNCE_MS = 300L
        const val NOTICE_MS = 2_500L
    }
}
