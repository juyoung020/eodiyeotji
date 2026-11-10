package app.eodiyeotji.ui.biz

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.EodiCore
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

    fun close() = _state.update { it.copy(open = false) }

    fun shift(by: Int) {
        val next = _state.value.ym.plusMonths(by.toLong())
        if (next > YearMonth.now()) return
        _state.update { it.copy(ym = next) }
        load()
    }

    /** "출장 아님": 그날을 자동 판정에서 뺀다 (Rust `set_biz_mark`). */
    fun markNotBiz(day: String) {
        viewModelScope.launch {
            runCatching { core.setBizMark(day, false) }
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
            _state.update { if (it.ym == ym) it.copy(month = m) else it }
        }
    }
}
