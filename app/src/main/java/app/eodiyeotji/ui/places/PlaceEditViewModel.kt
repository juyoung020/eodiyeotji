package app.eodiyeotji.ui.places

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.AnchorRole
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.PlaceCard
import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceSource
import app.eodiyeotji.core.PlaceSpot
import app.eodiyeotji.core.StaySummary
import app.eodiyeotji.core.TripSummary
import app.eodiyeotji.core.deleteDay
import app.eodiyeotji.core.deleteStay
import app.eodiyeotji.core.deleteTrip
import app.eodiyeotji.core.hideDay
import app.eodiyeotji.core.hideStay
import app.eodiyeotji.core.hideTrip
import app.eodiyeotji.core.placeCard
import app.eodiyeotji.core.renamePlace
import app.eodiyeotji.core.setDayTitle
import app.eodiyeotji.core.setMyAnchor
import app.eodiyeotji.core.setPlaceSaved
import app.eodiyeotji.place.PlaceCandidates
import app.eodiyeotji.pleos.CourseGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 메뉴를 연 대상: 주행 · 머문 곳 · 하루. */
@Immutable
sealed interface EditTarget {
    data class Trip(val trip: TripSummary) : EditTarget
    data class Stay(val stay: StaySummary) : EditTarget
    data class Day(val summary: DaySummary) : EditTarget
}

/** 이름 고치기 시트. [candidates]가 null이면 아직 찾는 중. */
@Immutable
data class RenameState(
    val card: PlaceCard,
    val candidates: List<PlaceLabel>? = null,
)

@Immutable
data class PlaceEditUiState(
    /** ⋯ 메뉴가 열린 대상 */
    val menu: EditTarget? = null,
    /** 머문 곳 메뉴의 장소 카드 (♥ · 집 · 회사 표시). 읽는 중이면 null. */
    val card: PlaceCard? = null,
    val rename: RenameState? = null,
    /** 하루 제목 고치기 창이 열린 날 */
    val dayTitle: DaySummary? = null,
    /** 지우기 확인 창 */
    val confirmDelete: EditTarget? = null,
    /** 잠깐 보이는 알림 한 줄 */
    @StringRes val notice: Int? = null,
    val noticeArg: String? = null,
)

/**
 * 타임라인 항목 · 하루의 ⋯ 메뉴 (숨기기 · 지우기 · 이름 고치기 · 집 · 회사 · ♥ · 안내).
 * 모든 계산 · 저장은 Rust(EodiCore 확장 함수)가 IO 스레드에서 하고, 끝나면 EodiCore가 화면들에 다시 읽으라고 알린다.
 */
@HiltViewModel
class PlaceEditViewModel @Inject constructor(
    private val core: EodiCore,
    private val guide: CourseGuide,
    private val candidates: PlaceCandidates,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaceEditUiState())
    val state: StateFlow<PlaceEditUiState> = _state.asStateFlow()
    private var noticeJob: Job? = null

    fun open(target: EditTarget) {
        _state.update { it.copy(menu = target, card = null) }
        if (target is EditTarget.Stay) {
            viewModelScope.launch {
                val card = runCatching { core.placeCard(PlaceSpot.stay(target.stay)) }.getOrNull()
                _state.update { if (it.menu == target) it.copy(card = card) else it }
            }
        }
    }

    fun close() {
        _state.update { it.copy(menu = null, card = null) }
    }

    fun toggleSave(card: PlaceCard) = act(if (card.saved) R.string.notice_unsaved else R.string.notice_saved, keepMenu = true) {
        core.setPlaceSaved(card.id, !card.saved)
        _state.update { s -> s.copy(card = s.card?.takeIf { it.id == card.id }?.copy(saved = !card.saved) ?: s.card) }
    }

    /** 이름 고치기 시트를 연다 (머문 곳 · 주행 출발 · 도착). 근처 후보는 뒤에서 채운다. */
    fun startRename(spot: PlaceSpot) {
        viewModelScope.launch {
            val card = runCatching { core.placeCard(spot) }.getOrNull()
            if (card == null) {
                show(R.string.notice_no_place)
                return@launch
            }
            _state.update { it.copy(menu = null, rename = RenameState(card)) }
            val near = runCatching { candidates.near(card.lat, card.lon) }.getOrDefault(emptyList())
            // 내가 정한 집 · 회사 자리면 "집" · "회사"를 맨 앞 후보로 (카카오 후보에는 없는 이름 — 기억 요약이 쓰는 이름과 같다)
            val mine = when (card.role) {
                AnchorRole.HOME -> ANCHOR_HOME
                AnchorRole.OFFICE -> ANCHOR_OFFICE
                else -> null
            }?.takeIf { card.label?.name != it }
            val list = if (mine != null) listOf(PlaceLabel(mine, "", card.label?.address.orEmpty(), PlaceSource.USER)) + near else near
            _state.update { s -> s.copy(rename = s.rename?.takeIf { it.card.id == card.id }?.copy(candidates = list) ?: s.rename) }
        }
    }

    fun cancelRename() {
        _state.update { it.copy(rename = null) }
    }

    /** [picked]가 있으면 그 후보의 분류 · 주소도 같이 쓴다. */
    fun rename(name: String, picked: PlaceLabel? = null) {
        val r = _state.value.rename ?: return
        if (name.isBlank()) return
        _state.update { it.copy(rename = null) }
        act(R.string.notice_renamed) { core.renamePlace(r.card.id, name.trim(), picked?.category.orEmpty(), picked?.address.orEmpty()) }
    }

    /** 하루 제목 고치기 창을 연다. */
    fun startDayTitle(day: DaySummary) {
        _state.update { it.copy(menu = null, dayTitle = day) }
    }

    fun cancelDayTitle() {
        _state.update { it.copy(dayTitle = null) }
    }

    /** 빈 글이면 직접 쓴 제목을 지우고 자동 제목으로 돌아간다. */
    fun saveDayTitle(title: String) {
        val day = _state.value.dayTitle ?: return
        _state.update { it.copy(dayTitle = null) }
        act(R.string.notice_day_titled) { core.setDayTitle(day.day, title.trim()) }
    }

    fun setAnchor(role: AnchorRole, card: PlaceCard) =
        act(if (role == AnchorRole.HOME) R.string.notice_home else R.string.notice_office) {
            core.setMyAnchor(role, LatLon(card.lat, card.lon))
        }

    fun hide(target: EditTarget) = act(R.string.notice_hidden) {
        when (target) {
            is EditTarget.Trip -> core.hideTrip(target.trip.startMs)
            is EditTarget.Stay -> core.hideStay(target.stay.arriveMs)
            is EditTarget.Day -> core.hideDay(target.summary.day)
        }
    }

    fun askDelete(target: EditTarget) {
        _state.update { it.copy(menu = null, confirmDelete = target) }
    }

    fun cancelDelete() {
        _state.update { it.copy(confirmDelete = null) }
    }

    fun confirmDelete() {
        val target = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        act(R.string.notice_deleted) {
            when (target) {
                is EditTarget.Trip -> core.deleteTrip(target.trip.startMs)
                is EditTarget.Stay -> core.deleteStay(target.stay.arriveMs)
                is EditTarget.Day -> core.deleteDay(target.summary.day)
            }
        }
    }

    /** 지우기 대신 숨기기 (확인 창에서). */
    fun hideInstead() {
        val target = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        hide(target)
    }

    fun navigate(card: PlaceCard) {
        viewModelScope.launch {
            val l = card.label
            val ok = runCatching { guide.startPlace(l?.name.orEmpty(), card.lat, card.lon, l?.address.orEmpty()) }.getOrDefault(false)
            close()
            show(if (ok) R.string.notice_navi_ok else R.string.notice_navi_fail)
        }
    }

    private fun act(@StringRes done: Int, keepMenu: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!keepMenu) close()
            try {
                block()
                show(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "기록 고치기 실패", e)
                show(R.string.notice_failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun show(@StringRes res: Int, arg: String? = null) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = res, noticeArg = arg) }
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS)
            _state.update { it.copy(notice = null, noticeArg = null) }
        }
    }

    private companion object {
        const val TAG = "EodiEdit"
        const val NOTICE_MS = 3_000L
        const val ANCHOR_HOME = "집"
        const val ANCHOR_OFFICE = "회사"
    }
}
