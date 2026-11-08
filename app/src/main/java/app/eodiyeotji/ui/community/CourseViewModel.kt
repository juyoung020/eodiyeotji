package app.eodiyeotji.ui.community

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.CommunitySort
import app.eodiyeotji.core.CourseLeg
import app.eodiyeotji.core.DayMapData
import app.eodiyeotji.core.DriveCourse
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.courseLegs
import app.eodiyeotji.core.courseMap
import app.eodiyeotji.core.courseOffers
import app.eodiyeotji.core.courseStopReviews
import app.eodiyeotji.core.StopReviews
import app.eodiyeotji.core.driveCourses
import app.eodiyeotji.core.lastKnownPosition
import app.eodiyeotji.core.toggleCourseHeart
import app.eodiyeotji.pleos.CourseGuide
import app.eodiyeotji.pleos.CourseProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 목록 불러오기 상태 (게시판·코스·리뷰 공통). */
@Immutable
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Loaded<T>(val items: List<T>) : Load<T>
}

/** 고른 코스의 상세 (지도·내비 구간은 뒤따라 채운다). [naviFailed] = 내비에 보내지 못했음 (안내 한 줄). */
@Immutable
data class CourseDetail(
    val course: DriveCourse,
    val map: DayMapData? = null,
    val legs: List<CourseLeg> = emptyList(),
    /** 들른 곳마다 리뷰 요약 (들른 곳 순서, 뒤따라 채운다) */
    val stopReviews: List<StopReviews> = emptyList(),
    val naviFailed: Boolean = false,
)

@Immutable
data class CourseUiState(
    val sort: CommunitySort = CommunitySort.POPULAR,
    val nearOn: Boolean = false,
    /** 내 근처를 켰는데 차 위치를 모르면 false (전국을 보여 주고 알린다) */
    val nearKnown: Boolean = true,
    val list: Load<DriveCourse> = Load.Loading,
    val detail: CourseDetail? = null,
    /** 방금 코스를 올렸으면 true (알림 한 줄) */
    val justShared: Boolean = false,
)

/**
 * 커뮤니티 > 드라이브 코스. 정렬·근처 거르기·하트 집계는 Rust가 하고, 여기서는 받은 순서대로 둔다.
 * 하트는 누르자마자 화면에 반영하고(낙관적) Rust가 돌려준 수로 맞춘다. 실패하면 되돌린다.
 */
@HiltViewModel
class CourseViewModel @Inject constructor(
    private val core: EodiCore,
    private val guide: CourseGuide,
) : ViewModel() {
    private val _state = MutableStateFlow(CourseUiState())
    val state: StateFlow<CourseUiState> = _state.asStateFlow()

    /** 진행 중인 코스 안내 (이 코스면 상세의 버튼이 "다음 구간 바로 안내"로 바뀐다). */
    val progress: StateFlow<CourseProgress?> = guide.progress
    private var loadJob: Job? = null

    private var started = false

    /** 목록 첫 읽기. 홈이 검색 결과 열기용으로 이 ViewModel을 일찍 만들어도 여행 리뷰 탭이 처음 보일 때만 읽는다. */
    fun start() {
        if (started) return
        started = true
        reload()
    }

    fun setSort(sort: CommunitySort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    fun toggleNear() {
        _state.update { it.copy(nearOn = !it.nearOn) }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            if (s.list !is Load.Loaded) _state.update { it.copy(list = Load.Loading) }
            val result = try {
                val near: LatLon? = if (s.nearOn) core.lastKnownPosition() else null
                _state.update { it.copy(nearKnown = !s.nearOn || near != null) }
                Load.Loaded(core.driveCourses(s.sort, near, NEAR_KM))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { it.copy(list = result) }
        }
    }

    fun open(course: DriveCourse) {
        _state.update { it.copy(detail = CourseDetail(course)) }
        val id = course.post.id
        viewModelScope.launch {
            val map = runCatching { core.courseMap(id) }.onFailure { Log.w(TAG, "코스 지도 실패 $id", it) }.getOrNull()
            val legs = runCatching { core.courseLegs(id) }.getOrDefault(emptyList())
            val reviews = runCatching { core.courseStopReviews(id) }.onFailure { Log.w(TAG, "들른 곳 리뷰 실패 $id", it) }.getOrDefault(emptyList())
            // 검색 결과에서 열면 쿠폰이 아직 안 붙어 있다 → 이 코스만 Rust에 묻는다
            val offers = if (course.offers.placeCount > 0) course.offers else runCatching { core.courseOffers(listOf(course.post.stops)) }.getOrNull()?.firstOrNull()
            _state.update { s ->
                if (s.detail?.course?.post?.id == id) {
                    s.copy(detail = s.detail.copy(map = map, legs = legs, stopReviews = reviews, course = s.detail.course.copy(offers = offers ?: s.detail.course.offers)))
                } else {
                    s
                }
            }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null) }
    }

    /**
     * 이 코스로 안내: 첫 구간(경유지 2개 + 목적지, Rust `course_legs`)을 내비에 보내고, 도착할 때마다 다음 구간은
     * [CourseGuide]가 알아서 이어 보낸다. 이미 이 코스를 안내 중이면 도착을 기다리지 않고 다음 구간으로 넘긴다.
     */
    fun navigate() {
        val d = _state.value.detail ?: return
        if (d.legs.isEmpty()) return
        val id = d.course.post.id
        val active = guide.progress.value
        if (active != null && active.courseId == id && !active.finished) {
            guide.skipToNextLeg()
            return
        }
        viewModelScope.launch {
            val ok = guide.startShared(id, d.course.post.title, d.legs)
            _state.update { s -> if (s.detail?.course?.post?.id == id) s.copy(detail = s.detail.copy(naviFailed = !ok)) else s }
        }
    }

    fun toggleHeart(id: String) {
        _state.update { it.flipHeart(id, null) }
        viewModelScope.launch {
            try {
                val n = core.toggleCourseHeart(id)
                _state.update { it.flipHeart(id, n, apply = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "하트 실패 $id", e)
                _state.update { it.flipHeart(id, null) } // 되돌리기
            }
        }
    }

    /** 코스를 올린 뒤: 최신순으로 바꿔 내 코스가 맨 위에 보이게. */
    fun onShared() {
        _state.update { it.copy(sort = CommunitySort.RECENT, nearOn = false, justShared = true) }
        reload()
    }

    fun dismissShared() {
        _state.update { it.copy(justShared = false) }
    }

    private companion object {
        const val TAG = "EodiCourses"
        const val NEAR_KM = 30.0
    }
}

/** [id] 코스의 하트를 뒤집는다(apply) 또는 Rust가 준 수 [count]로 맞춘다. 목록과 상세를 같이 바꾼다. */
private fun CourseUiState.flipHeart(id: String, count: Int?, apply: Boolean = true): CourseUiState {
    fun DriveCourse.fix(): DriveCourse {
        if (post.id != id) return this
        val p = if (apply) post.copy(heartedByMe = !post.heartedByMe, hearts = post.hearts + if (post.heartedByMe) -1 else 1) else post
        return copy(post = if (count != null) p.copy(hearts = count) else p)
    }
    val l = list
    return copy(
        list = if (l is Load.Loaded) Load.Loaded(l.items.map { it.fix() }) else l,
        detail = detail?.let { it.copy(course = it.course.fix()) },
    )
}
