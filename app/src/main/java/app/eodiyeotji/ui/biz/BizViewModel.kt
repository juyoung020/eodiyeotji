package app.eodiyeotji.ui.biz

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.rust.BizClient
import app.eodiyeotji.core.rust.BizMonth
import app.eodiyeotji.ui.recap.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class BizUiState(
    val open: Boolean = false,
    val ym: YearMonth = YearMonth.now(),
    val month: Load<BizMonth> = Load.Loading,
    /** 거래처 목록 · 거래처로 권할 곳 (Rust `bizclient.rs`) */
    val clients: List<BizClient> = emptyList(),
    val suggestions: List<BizClient> = emptyList(),
    /** 열어 둔 출장 (경비 내역 창, `BizTrip.episodeId`) */
    val selected: Long? = null,
    /** 방금 "출장 아님"으로 뺀 날 (되돌리기 줄) */
    val removedDay: String? = null,
)

/** 출장 관리. 계산은 모두 Rust(`biztrip.rs`): 여기서는 부르고 상태에 담기만 한다. */
@HiltViewModel
class BizViewModel @Inject constructor(private val core: EodiCore) : ViewModel() {
    private val _state = MutableStateFlow(BizUiState())
    val state: StateFlow<BizUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun open() {
        _state.update { it.copy(open = true, ym = YearMonth.now()) }
        load()
    }

    fun close() = _state.update { it.copy(open = false, selected = null, removedDay = null) }

    /** 출장 한 건의 경비 내역을 연다 (같은 것을 다시 누르면 닫는다). */
    fun select(episodeId: Long?) = _state.update { it.copy(selected = if (it.selected == episodeId) null else episodeId) }

    fun shift(by: Int) {
        val next = _state.value.ym.plusMonths(by.toLong())
        if (next > YearMonth.now()) return
        _state.update { it.copy(ym = next, selected = null, removedDay = null) }
        load()
    }

    /** "출장 아님": 그날을 자동 판정에서 뺀다 (Rust `set_biz_mark`). */
    fun markNotBiz(day: String) {
        _state.update { it.copy(selected = null, removedDay = day) }
        viewModelScope.launch {
            runCatching { core.setBizMark(day, false) }
            load(keep = true)
        }
    }

    /** "출장 아님" 되돌리기: 자동 판정으로 돌린다. */
    fun undoNotBiz() {
        val day = _state.value.removedDay ?: return
        _state.update { it.copy(removedDay = null) }
        viewModelScope.launch {
            runCatching { core.setBizMark(day, null) }
            load(keep = true)
        }
    }

    /** 거래처로 정하거나([on]) 뺀다. 출장 목록이 바로 달라지므로 다시 읽는다. */
    fun setClient(c: BizClient, on: Boolean) {
        viewModelScope.launch {
            runCatching { core.setBizClient(c, on) }
            load(keep = true)
        }
    }

    fun csv(m: BizMonth): String = core.bizReportCsv(m)

    private fun load(keep: Boolean = false) {
        val ym = _state.value.ym
        if (!keep) _state.update { it.copy(month = Load.Loading) }
        job?.cancel()
        job = viewModelScope.launch {
            val m = try {
                Load.Ready(core.bizTrips(ym.toString()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed
            }
            val clients = runCatching { core.bizClients() }.getOrDefault(emptyList())
            val suggestions = runCatching { core.bizClientSuggestions() }.getOrDefault(emptyList())
            _state.update { if (it.ym == ym) it.copy(month = m, clients = clients, suggestions = suggestions) else it }
        }
    }
}
