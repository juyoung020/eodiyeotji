package app.eodiyeotji.ui.replay

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.DayReplay
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.dayReplay
import app.eodiyeotji.pleos.CourseGuide
import app.eodiyeotji.ui.agent.DrivingStatePort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 그날로 돌아가기 화면 상태. [day]가 null이면 닫힘, [replay]가 null이면 불러오는 중. */
@Immutable
data class ReplayUiState(
    val day: String? = null,
    val replay: DayReplay? = null,
    val failed: Boolean = false,
    val driving: Boolean = false,
    @StringRes val note: Int? = null,
)

/**
 * 그날로 돌아가기 (정차 중에만). 재료는 모두 Rust(`replay.rs`)가 만들고 여기서는 불러 담기만 한다.
 * 주행이 시작되면 저절로 닫는다 (운전 중에는 큰 사진 · 움직이는 화면을 보이지 않는다).
 */
@HiltViewModel
class ReplayViewModel @Inject constructor(
    private val core: EodiCore,
    private val guide: CourseGuide,
    driving: DrivingStatePort,
) : ViewModel() {

    private val _state = MutableStateFlow(ReplayUiState())
    val state: StateFlow<ReplayUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var noteJob: Job? = null

    init {
        viewModelScope.launch {
            driving.isDriving.collect { d ->
                _state.update { it.copy(driving = d) }
                if (d) close()
            }
        }
    }

    fun open(day: String) {
        if (_state.value.driving) return
        _state.update { ReplayUiState(day = day, driving = it.driving) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val r = core.dayReplay(day)
                _state.update { if (it.day == day) it.copy(replay = r) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "그날 다시 보기 실패 ($day)", e)
                _state.update { if (it.day == day) it.copy(failed = true) else it }
            }
        }
    }

    fun close() {
        loadJob?.cancel()
        _state.update { ReplayUiState(driving = it.driving) }
    }

    /** "그 코스로 안내": 그날 대표 에피소드를 내비 구간으로 나눠(Rust) 코스 안내로 넘긴다. */
    fun navigate() {
        val r = _state.value.replay ?: return
        val id = r.episodeId ?: return
        viewModelScope.launch {
            val ok = try {
                val route = core.courseRoute(id, from = null, optimize = false)
                route.legs.isNotEmpty() && guide.startShared("replay-$id", r.title, route.legs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "코스 안내 실패", e)
                false
            }
            if (ok) close() else showNote(R.string.recap_navi_failed)
        }
    }

    private fun showNote(@StringRes res: Int) {
        noteJob?.cancel()
        _state.update { it.copy(note = res) }
        noteJob = viewModelScope.launch {
            delay(NOTE_MS)
            _state.update { it.copy(note = null) }
        }
    }

    private companion object {
        const val TAG = "EodiReplay"
        const val NOTE_MS = 2_500L
    }
}
