package app.eodiyeotji.ui.recap

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.MonthRecap
import app.eodiyeotji.core.OnThisDay
import app.eodiyeotji.core.YearRecap
import app.eodiyeotji.core.dismissOnThisDay
import app.eodiyeotji.core.monthRecap
import app.eodiyeotji.core.onThisDay
import app.eodiyeotji.core.yearRecap
import app.eodiyeotji.pleos.CourseGuide
import app.eodiyeotji.ui.agent.DrivingStatePort
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecapMode { MONTH, YEAR }

/** 불러오기 상태. */
@Immutable
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data object Failed : Load<Nothing>
}

/**
 * 첫 화면 위 "N년 전 오늘" 카드 · 요약 입구와 요약 화면 상태.
 * [onThisDay]는 오늘 닫았으면 null. [driving]이면 카드를 숨긴다 (운전 중에는 음성 한 줄만, `RecallCenter`).
 */
@Immutable
data class RecapUiState(
    val today: LocalDate = LocalDate.now(),
    val onThisDay: OnThisDay? = null,
    val driving: Boolean = false,
    val open: Boolean = false,
    val mode: RecapMode = RecapMode.MONTH,
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val monthRecap: Load<MonthRecap> = Load.Loading,
    val yearRecap: Load<YearRecap> = Load.Loading,
    @StringRes val note: Int? = null,
)

/** 계산은 모두 Rust(`recap.rs`): 여기서는 부르고 상태에 담기만 한다. */
@OptIn(FlowPreview::class)
@HiltViewModel
class RecapViewModel @Inject constructor(
    private val core: EodiCore,
    private val guide: CourseGuide,
    driving: DrivingStatePort,
) : ViewModel() {

    private val _state = MutableStateFlow(RecapUiState())
    val state: StateFlow<RecapUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var noteJob: Job? = null

    init {
        loadOnThisDay()
        viewModelScope.launch { driving.isDriving.collect { d -> _state.update { it.copy(driving = d) } } }
        // 기록이 바뀌면(시연 데이터 · 라벨링) 잠시 모았다가 다시 읽는다. 주행 중(10초마다 위치 저장)에는 카드를 안 보이므로 읽지 않는다.
        viewModelScope.launch {
            core.changes.debounce(CHANGE_DEBOUNCE_MS).collect { if (!_state.value.driving) loadOnThisDay() }
        }
    }

    private fun loadOnThisDay() {
        viewModelScope.launch {
            val otd = runCatching { core.onThisDay() }.onFailure { if (it is CancellationException) throw it; Log.w(TAG, "N년 전 오늘 실패", it) }
                .getOrNull()
            _state.update { it.copy(today = LocalDate.now(), onThisDay = otd?.takeUnless { o -> o.dismissed }) }
        }
    }

    fun dismissOnThisDay() {
        _state.update { it.copy(onThisDay = null) }
        viewModelScope.launch { runCatching { core.dismissOnThisDay() }.onFailure { Log.w(TAG, "닫기 저장 실패", it) } }
    }

    /** "그 코스로 안내": 그날 들른 순서대로 내비 구간을 나눠(Rust) 코스 안내로 넘긴다 (도착하면 다음 구간은 [CourseGuide]가 잇는다). */
    fun navigateOnThisDay() {
        val otd = _state.value.onThisDay ?: return
        viewModelScope.launch {
            val ok = try {
                val route = core.courseRoute(otd.episodeId, from = null, optimize = false)
                route.legs.isNotEmpty() && guide.startShared("recap-${otd.episodeId}", otd.title, route.legs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "코스 안내 실패", e)
                false
            }
            showNote(if (ok) R.string.recap_navi_started else R.string.recap_navi_failed)
        }
    }

    fun openRecap(mode: RecapMode) {
        val now = LocalDate.now()
        _state.update { it.copy(open = true, mode = mode, year = now.year, month = now.monthValue) }
        load()
    }

    fun closeRecap() {
        _state.update { it.copy(open = false) }
    }

    fun setMode(mode: RecapMode) {
        _state.update { it.copy(mode = mode) }
        load()
    }

    /** 달 · 해 넘기기 ([step] = -1 이전, +1 다음). */
    fun shift(step: Int) {
        _state.update {
            when (it.mode) {
                RecapMode.MONTH -> {
                    val d = LocalDate.of(it.year, it.month, 1).plusMonths(step.toLong())
                    it.copy(year = d.year, month = d.monthValue)
                }
                RecapMode.YEAR -> it.copy(year = it.year + step)
            }
        }
        load()
    }

    private fun load() {
        val s = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                when (s.mode) {
                    RecapMode.MONTH -> {
                        _state.update { it.copy(monthRecap = Load.Loading) }
                        val r = core.monthRecap(s.year, s.month)
                        _state.update { it.copy(monthRecap = Load.Ready(r)) }
                    }
                    RecapMode.YEAR -> {
                        _state.update { it.copy(yearRecap = Load.Loading) }
                        val r = core.yearRecap(s.year)
                        _state.update { it.copy(yearRecap = Load.Ready(r)) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "요약 실패", e)
                _state.update { if (s.mode == RecapMode.MONTH) it.copy(monthRecap = Load.Failed) else it.copy(yearRecap = Load.Failed) }
            }
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
        const val TAG = "EodiRecap"
        const val CHANGE_DEBOUNCE_MS = 3_000L
        const val NOTE_MS = 2_500L
    }
}
