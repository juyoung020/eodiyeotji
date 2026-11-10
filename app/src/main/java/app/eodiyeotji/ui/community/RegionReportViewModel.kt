package app.eodiyeotji.ui.community

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.rust.RegionActivity
import app.eodiyeotji.core.rust.TourismReport
import app.eodiyeotji.ui.recap.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RegionReportUiState(
    val regions: Load<List<RegionActivity>> = Load.Loading,
    val selected: String? = null,
    val report: Load<TourismReport> = Load.Loading,
)

/** 지역 관광 리포트 (커뮤니티 "지역 리포트" 탭). 계산은 모두 Rust(`tourism.rs`). */
@HiltViewModel
class RegionReportViewModel @Inject constructor(private val core: EodiCore) : ViewModel() {
    private val _state = MutableStateFlow(RegionReportUiState())
    val state: StateFlow<RegionReportUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val regions = try {
                core.tourismRegions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(regions = regions?.let { r -> Load.Ready(r) } ?: Load.Failed) }
            regions?.firstOrNull()?.let { select(it.region) }
        }
    }

    fun select(region: String) {
        _state.update { it.copy(selected = region, report = Load.Loading) }
        job?.cancel()
        job = viewModelScope.launch {
            val r = try {
                Load.Ready(core.tourismReport(region))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed
            }
            _state.update { if (it.selected == region) it.copy(report = r) else it }
        }
    }
}
