package app.eodiyeotji.ui.timeline

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.DayMapData
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.DayEntry
import app.eodiyeotji.core.shareCandidatesOn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/** 날짜 목록 상태. 목록은 Rust가 최신순으로 정렬해서 준 그대로다. */
@Immutable
sealed interface DaysState {
    data object Loading : DaysState
    data class Failed(val message: String) : DaysState
    /** [today]는 "오늘/어제" 표시용. 다시 읽을 때마다 새로 잡아서 자정을 넘겨도 맞는다. */
    data class Loaded(val days: List<DaySummary>, val today: LocalDate) : DaysState
}

/** LazyColumn 키 (주행은 시작 시각, 머문 곳은 도착 시각). */
internal val DayEntry.key: String
    get() = when (this) {
        is DayEntry.Trip -> "t${trip.startMs}"
        is DayEntry.Stay -> "s${stay.arriveMs}"
    }

/** 오른쪽(넓은 창) 또는 단독(좁은 창)으로 보이는 하루 상세. */
@Immutable
sealed interface DetailState {
    data object None : DetailState
    data class Loading(val summary: DaySummary) : DetailState
    /**
     * [map]은 그날 지도 데이터. 지도만 못 읽었으면 null이고 목록은 그대로 보인다.
     * [shareEpisode]: 그날 커뮤니티에 코스로 올릴 수 있는 기록(나들이·여행)이 있으면 그 id → 상세에 "커뮤니티에 올리기".
     */
    data class Loaded(
        val summary: DaySummary,
        val entries: List<DayEntry>,
        val map: DayMapData? = null,
        val shareEpisode: Long? = null,
    ) : DetailState
    data class Failed(val summary: DaySummary, val message: String) : DetailState
}

/** 지금 상세에 보이는 날의 요약, 없으면 null. */
val DetailState.shownSummary: DaySummary?
    get() = when (this) {
        DetailState.None -> null
        is DetailState.Loading -> summary
        is DetailState.Loaded -> summary
        is DetailState.Failed -> summary
    }

@Immutable
data class TimelineUiState(
    val days: DaysState = DaysState.Loading,
    /** 사용자가 고른 날. 좁은 창에서는 이 값이 있으면 상세 화면을 보여 준다. */
    val selectedDay: String? = null,
    val detail: DetailState = DetailState.None,
    /** 상세에서 고른 항목 키([DayEntry.key]). 목록과 지도가 같이 강조한다. 날이 바뀌면 지운다. */
    val selectedEntry: String? = null,
)

/**
 * 타임라인 화면 상태.
 *
 * - 처음 열 때, 그리고 기록이 바뀔 때(EodiCore.changes, 잠깐 모아서) 다시 읽는다.
 * - 넓은 창에서는 고른 날이 없으면 가장 최근 날을 상세에 보여 준다.
 * - Rust 호출은 EodiCore가 IO 스레드로 넘긴다 → UI 스레드를 막지 않는다.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val core: EodiCore,
) : ViewModel() {

    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val selectedDay = MutableStateFlow<String?>(null)
    private val wide = MutableStateFlow(false)
    private val selectedEntry = MutableStateFlow<String?>(null)

    /** 다시 읽을 때: 구독 시작 1번 + 수동 재시도 + 기록 변경(위치 묶음이 연달아 들어오므로 잠깐 모은다). */
    private val refresh: Flow<Unit> = merge(
        flowOf(Unit),
        reloadRequests,
        core.changes.debounce(REFRESH_DEBOUNCE_MS),
    )

    private val days: StateFlow<DaysState> = refresh
        .mapLatest { loadDays() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DaysState.Loading)

    /** 상세에 보일 날. 목록이 다시 읽히면(요약 값이 바뀌면) 상세도 다시 읽는다. */
    private val target: Flow<DaySummary?> = combine(days, selectedDay, wide) { d, sel, w ->
        val list = (d as? DaysState.Loaded)?.days.orEmpty()
        when {
            sel != null -> list.firstOrNull { it.day == sel }
            w -> list.firstOrNull()
            else -> null
        }
    }.distinctUntilChanged()

    /** 마지막으로 상세를 보여 준 날. 같은 날을 다시 읽을 때는 로딩 표시 없이 내용만 바꾼다(깜빡임 방지). */
    private var shownDay: String? = null

    /** 검색 결과 "지도"로 연 날: 그날 상세가 뜨면 고를 항목 (날, 항목 키). */
    @Volatile private var pendingEntry: Pair<String, String>? = null

    private val detail: StateFlow<DetailState> = target
        .transformLatest { s ->
            if (s == null) {
                shownDay = null
                selectedEntry.value = null
                emit(DetailState.None)
                return@transformLatest
            }
            if (shownDay != s.day) {
                selectedEntry.value = null
                emit(DetailState.Loading(s))
            }
            shownDay = s.day
            emit(loadDetail(s))
            pendingEntry?.takeIf { it.first == s.day }?.let {
                selectedEntry.value = it.second
                pendingEntry = null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailState.None)

    val state: StateFlow<TimelineUiState> = combine(days, selectedDay, detail, selectedEntry, ::TimelineUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimelineUiState())

    fun select(day: String) {
        selectedDay.value = day
    }

    /** 검색 결과에서 그날 열기. [entryKey]가 있으면 그날 목록 · 지도에서 그 항목을 고른다 ([DayEntry.key]). */
    fun openDay(day: String, entryKey: String?) {
        if (shownDay == day) {
            pendingEntry = null
            selectedEntry.value = entryKey
        } else {
            pendingEntry = entryKey?.let { day to it }
        }
        selectedDay.value = day
    }

    fun clearSelection() {
        selectedDay.value = null
    }

    /** 상세 목록에서 항목을 누를 때: 고르고, 이미 고른 항목을 다시 누르면 해제. */
    fun toggleEntry(key: String) {
        selectedEntry.value = if (selectedEntry.value == key) null else key
    }

    /** 지도에서 경로·핀을 누를 때 (빈 곳이면 null → 해제). */
    fun selectEntry(key: String?) {
        selectedEntry.value = key
    }

    /** 화면이 창 폭이 바뀔 때마다 알려 준다 (Pleos는 실행 중에 창 크기가 바뀐다). */
    fun setWide(value: Boolean) {
        wide.value = value
    }

    fun reload() {
        reloadRequests.tryEmit(Unit)
    }

    private suspend fun loadDays(): DaysState = try {
        DaysState.Loaded(core.daySummaries(), LocalDate.now())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DaysState.Failed(e.message ?: e.javaClass.simpleName)
    }

    private suspend fun loadDetail(s: DaySummary): DetailState = try {
        // 주행·머문 곳은 Rust가 시간순으로 섞어서 준다 (Kotlin에서 정렬하지 않는다).
        val d = core.dayDetail(s.day)
        DetailState.Loaded(d.summary, d.entries, loadMap(s.day), loadShareEpisode(s.day))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DetailState.Failed(s, e.message ?: e.javaClass.simpleName)
    }

    /** 지도 데이터는 목록과 따로 실패할 수 있다 → 실패하면 지도만 비우고 목록은 보여 준다. */
    private suspend fun loadMap(day: String): DayMapData? = try {
        core.dayMap(day)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "지도 데이터 불러오기 실패 ($day)", e)
        null
    }

    /** 그날 올릴 수 있는 기록 (없거나 못 읽으면 null → 버튼을 숨긴다). */
    private suspend fun loadShareEpisode(day: String): Long? = try {
        core.shareCandidatesOn(day).firstOrNull()?.episodeId
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "올릴 기록 찾기 실패 ($day)", e)
        null
    }

    private companion object {
        const val TAG = "EodiTimeline"
        const val REFRESH_DEBOUNCE_MS = 300L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
