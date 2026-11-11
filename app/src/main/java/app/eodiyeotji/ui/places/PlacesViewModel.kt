package app.eodiyeotji.ui.places

import app.eodiyeotji.core.OfferPlace
import app.eodiyeotji.core.PartnerOffer
import app.eodiyeotji.core.offersForPlaces
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
import app.eodiyeotji.pleos.CourseGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class PlacesTab { SAVED, FREQUENT, COUPONS, SETTINGS }

@Immutable
data class PlacesUiState(
    val tab: PlacesTab = PlacesTab.SAVED,
    val loaded: Boolean = false,
    val saved: List<PlaceCard> = emptyList(),
    val frequent: List<PlaceCard> = emptyList(),
    val homeOffice: HomeOffice = HomeOffice(null, null),
    val hidden: List<HiddenRecord> = emptyList(),
    /** 장소 카드의 제휴 쿠폰 (장소 번호 → 쿠폰, 쿠폰 없는 곳은 빠짐) */
    val offers: Map<Long, List<PartnerOffer>> = emptyMap(),
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
    private val guide: CourseGuide,
) : ViewModel() {
    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()
    private var noticeJob: Job? = null

    /** 화면이 안 보이는 동안 기록이 바뀌었는지 */
    private var stale = false

    init {
        reload()
        // 이 VM은 화면(액티비티) 내내 살아 있다. 주행 중엔 10초마다 기록이 바뀌므로, 내 장소 화면이 안 보일 때는
        // 다시 읽지 않고 "낡음"만 표시했다가 화면이 다시 보일 때 한 번 읽는다.
        viewModelScope.launch {
            core.changes.debounce(RELOAD_DEBOUNCE_MS).collectLatest {
                if (_state.subscriptionCount.value > 0) load() else stale = true
            }
        }
        viewModelScope.launch {
            _state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collect { visible ->
                if (visible && stale) {
                    stale = false
                    load()
                }
            }
        }
    }


    /** 사용자가 탭을 직접 골랐는지 (고른 뒤에는 첫 탭을 대신 정하지 않는다) */
    private var tabPicked = false

    fun setTab(tab: PlacesTab) {
        tabPicked = true
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
            val ok = runCatching { guide.startPlace(l?.name.orEmpty(), card.lat, card.lon, l?.address.orEmpty()) }.getOrDefault(false)
            show(if (ok) R.string.notice_navi_ok else R.string.notice_navi_fail)
        }
    }

    fun restore(r: HiddenRecord) = act(R.string.notice_restored) { core.restoreHidden(r.id) }

    /** 사용자가 정한 집 · 회사를 지운다 (내비 · 기록 추정으로 돌아간다). */
    fun resetAnchor(role: AnchorRole) = act(R.string.notice_restored) { core.setMyAnchor(role, null) }

    private suspend fun load() {
        try {
            // 서로 상관없는 네 조회는 Rust 읽기 연결에서 동시에 돈다
            val (saved, frequent, ho, hidden) = coroutineScope {
                val saved = async { core.savedPlaces() }
                val frequent = async { core.frequentPlaces() }
                val ho = async { core.homeOffice() }
                val hidden = async { core.hiddenRecords() }
                Loaded4(saved.await(), frequent.await(), ho.await(), hidden.await())
            }
            val offers = loadOffers(saved + frequent)
            _state.update {
                // 저장한 곳이 비어 있으면 빈 화면 대신 자주 가는 곳을 먼저 연다 (사용자가 탭을 고르기 전까지만)
                val tab = if (!tabPicked && it.tab == PlacesTab.SAVED && saved.isEmpty() && frequent.isNotEmpty()) PlacesTab.FREQUENT else it.tab
                it.copy(tab = tab, loaded = true, saved = saved, frequent = frequent, homeOffice = ho, hidden = hidden, offers = offers)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "내 장소 읽기 실패", e)
            _state.update { it.copy(loaded = true) }
        }
    }

    /** 이름 있는 장소 카드의 제휴 쿠폰 (한 번에 Rust로). 못 읽으면 빈 것 → 배지만 안 보인다. */
    private suspend fun loadOffers(cards: List<PlaceCard>): Map<Long, List<PartnerOffer>> {
        val named = cards.distinctBy { it.id }.filter { it.label?.name?.isNotBlank() == true }
        val found = runCatching { core.offersForPlaces(named.map { OfferPlace(it.label!!.name, it.lat, it.lon) }) }.getOrNull() ?: return emptyMap()
        return named.zip(found).filter { it.second.isNotEmpty() }.associate { (c, o) -> c.id to o }
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

private data class Loaded4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
