package app.eodiyeotji.ui.review

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.CommunitySort
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.PopularPlace
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.ReviewKeywordOptions
import app.eodiyeotji.core.WithWhom
import app.eodiyeotji.core.reviewKeywordOptions
import app.eodiyeotji.core.lastKnownPosition
import app.eodiyeotji.core.reviewCandidates
import app.eodiyeotji.core.toggleReviewHeart
import app.eodiyeotji.core.travelFeed
import app.eodiyeotji.core.toggleCourseHeart
import app.eodiyeotji.core.TravelFeedItem
import androidx.annotation.StringRes
import app.eodiyeotji.R
import app.eodiyeotji.core.visitWeather
import app.eodiyeotji.core.writePlaceReview
import app.eodiyeotji.ui.community.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 열어 달라는 장소 (화면이 앱 공통 장소 상세 창으로 넘기고 바로 비운다). */
@Immutable
data class PlaceReviews(val place: PopularPlace)

/**
 * 리뷰 쓰기: 들른 곳 고르기 → 별점 + 키워드(여러 개, [keywordLimit]개까지) + 누구와 + 후기.
 * [keywordOptions]는 Rust가 정한 키워드 목록 (`review_keyword_options`). 다녀온 때 · 머문 시간 · 몇 번째 방문은 Rust가 기록에서 채운다.
 */
@Immutable
data class ReviewDraft(
    val candidates: Load<StopFact> = Load.Loading,
    val place: StopFact? = null,
    val rating: Int = 5,
    val text: String = "",
    val keywordOptions: List<String> = emptyList(),
    val keywordLimit: Int = 5,
    val keywords: List<String> = emptyList(),
    val withWhom: WithWhom? = null,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    /** 키워드 누르기: 고른 것이면 빼고, 아니면 [keywordLimit]개까지 더한다. */
    fun toggleKeyword(k: String): ReviewDraft = when {
        k in keywords -> copy(keywords = keywords - k)
        keywords.size >= keywordLimit -> this
        else -> copy(keywords = keywords + k)
    }

    /** 누구와: 같은 것을 다시 누르면 고르지 않은 상태로. */
    fun pickWith(w: WithWhom): ReviewDraft = copy(withWhom = if (withWhom == w) null else w)

    fun withOptions(o: ReviewKeywordOptions): ReviewDraft = copy(keywordOptions = o.keywords, keywordLimit = o.limit)
}

@Immutable
data class ReviewUiState(
    val sort: CommunitySort = CommunitySort.POPULAR,
    val nearOn: Boolean = false,
    val nearKnown: Boolean = true,
    /** 장소 리뷰와 여행 코스 후기가 섞인 한 목록 (순서는 Rust) */
    val feed: Load<TravelFeedItem> = Load.Loading,
    val placeReviews: PlaceReviews? = null,
    val draft: ReviewDraft? = null,
    /** 화면 아래 알림 한 줄 (리뷰를 올림 · 코스를 올림) */
    @StringRes val notice: Int? = null,
)

/**
 * 커뮤니티 > 여행 리뷰: 장소 리뷰와 여행 코스 후기를 한 피드로 (인기순·최신순·내 근처) / 내가 들른 곳 리뷰 쓰기.
 * 섞는 순서·근처 거르기는 Rust (`travel_feed`). 코스 후기를 누르면 상세(지도·들른 곳·안내)는 [app.eodiyeotji.ui.community.CourseViewModel]이 맡는다. 리뷰를 쓸 때 그날 날씨를 한 번 받아 같이 저장한다(못 받으면 날씨 없이).
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val core: EodiCore,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
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
            try {
                val near: LatLon? = if (s.nearOn) core.lastKnownPosition() else null
                val f = core.travelFeed(s.sort, near, NEAR_KM)
                _state.update { it.copy(feed = Load.Loaded(f), nearKnown = !s.nearOn || near != null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                _state.update { it.copy(feed = Load.Failed(msg)) }
            }
        }
    }

    /** 장소 열기 → 화면이 장소 상세 창(ui/place/PlaceDetailSheet)을 연다. 검색 결과의 리뷰 줄이 여기로 온다. */
    fun openPlace(p: PopularPlace) = _state.update { it.copy(placeReviews = PlaceReviews(p)) }

    fun closePlace() = _state.update { it.copy(placeReviews = null) }

    /** 리뷰 하트 ([trip] = 여행 코스 후기). 누르자마자 화면에 반영하고, 실패하면 되돌린다. */
    fun toggleHeart(id: String, trip: Boolean = false) {
        _state.update { it.flipHeart(id, trip) }
        viewModelScope.launch {
            try {
                if (trip) core.toggleCourseHeart(id) else core.toggleReviewHeart(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "하트 실패 $id", e)
                _state.update { it.flipHeart(id, trip) }
            }
        }
    }

    /** 내 기록에서 여행 코스를 올린 뒤: 최신순으로 바꿔 내 글이 맨 위에 보이게. */
    fun onTripShared() {
        _state.update { it.copy(sort = CommunitySort.RECENT, nearOn = false, notice = R.string.course_shared_done) }
        reload()
    }

    // ---- 리뷰 쓰기 ----

    fun startDraft() {
        _state.update { it.copy(draft = ReviewDraft()) }
        viewModelScope.launch {
            val options = runCatching { core.reviewKeywordOptions() }.getOrNull()
            val r = try {
                Load.Loaded(core.reviewCandidates())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { s -> s.copy(draft = s.draft?.copy(candidates = r)?.let { d -> options?.let(d::withOptions) ?: d }) }
        }
    }

    fun closeDraft() = _state.update { it.copy(draft = null) }
    fun pickPlace(p: StopFact?) = _state.update { s -> s.copy(draft = s.draft?.copy(place = p, error = null)) }
    fun setRating(r: Int) = _state.update { s -> s.copy(draft = s.draft?.copy(rating = r.coerceIn(1, 5))) }
    fun setText(t: String) = _state.update { s -> s.copy(draft = s.draft?.copy(text = t.take(500))) }
    fun toggleKeyword(k: String) = _state.update { s -> s.copy(draft = s.draft?.toggleKeyword(k)) }
    fun pickWith(w: WithWhom) = _state.update { s -> s.copy(draft = s.draft?.pickWith(w)) }

    fun submitDraft() {
        val d = _state.value.draft ?: return
        val place = d.place ?: return
        if (d.submitting) return
        _state.update { s -> s.copy(draft = d.copy(submitting = true, error = null)) }
        viewModelScope.launch {
            try {
                val weather = core.visitWeather(place)
                core.writePlaceReview(place, d.rating, d.text, d.keywords, d.withWhom, weather)
                _state.update { it.copy(draft = null, sort = CommunitySort.RECENT, notice = R.string.review_posted) }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(draft = s.draft?.copy(submitting = false, error = e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private companion object {
        const val TAG = "EodiReviews"
        const val NEAR_KM = 30.0
    }
}

private fun TravelFeedItem.flip(id: String, trip: Boolean): TravelFeedItem = when (this) {
    is TravelFeedItem.Review -> if (trip || review.review.id != id) this else {
        val r = review.review
        copy(review = review.copy(review = r.copy(heartedByMe = !r.heartedByMe, hearts = r.hearts + if (r.heartedByMe) -1 else 1)))
    }
    is TravelFeedItem.Trip -> if (!trip || course.post.id != id) this else {
        val p = course.post
        copy(course = course.copy(post = p.copy(heartedByMe = !p.heartedByMe, hearts = p.hearts + if (p.heartedByMe) -1 else 1)))
    }
}

private fun ReviewUiState.flipHeart(id: String, trip: Boolean): ReviewUiState {
    val f = feed
    return if (f is Load.Loaded) copy(feed = Load.Loaded(f.items.map { it.flip(id, trip) })) else this
}
