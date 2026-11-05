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
import app.eodiyeotji.core.TravelReview
import app.eodiyeotji.core.addPlaceReviewWith
import app.eodiyeotji.core.lastKnownPosition
import app.eodiyeotji.core.popularPlaces
import app.eodiyeotji.core.reviewCandidates
import app.eodiyeotji.core.reviewsOfPlace
import app.eodiyeotji.core.toggleReviewHeart
import app.eodiyeotji.core.travelReviews
import app.eodiyeotji.core.visitWeather
import app.eodiyeotji.ui.community.Load
import app.eodiyeotji.pleos.PleosNavi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReviewMode { PLACES, FEED }

/** 장소 하나의 리뷰 모아 보기. */
@Immutable
/** [navi]: "여기로 안내"를 누른 결과 (null = 아직 안 누름, true = 내비에 보냄, false = 실패). */
data class PlaceReviews(val place: PopularPlace, val reviews: Load<TravelReview> = Load.Loading, val navi: Boolean? = null)

/** 리뷰 쓰기: 들른 곳 고르기 → 별점 + 한 줄. */
@Immutable
data class ReviewDraft(
    val candidates: Load<StopFact> = Load.Loading,
    val place: StopFact? = null,
    val rating: Int = 5,
    val text: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

@Immutable
data class ReviewUiState(
    val mode: ReviewMode = ReviewMode.PLACES,
    val sort: CommunitySort = CommunitySort.POPULAR,
    val nearOn: Boolean = false,
    val nearKnown: Boolean = true,
    val places: Load<PopularPlace> = Load.Loading,
    val feed: Load<TravelReview> = Load.Loading,
    val placeReviews: PlaceReviews? = null,
    val draft: ReviewDraft? = null,
    val justPosted: Boolean = false,
)

/**
 * 커뮤니티 > 여행 리뷰: 인기 장소 순위(하트 합·별점·리뷰 수) / 리뷰 피드(인기순·최신순) / 내가 들른 곳 리뷰 쓰기.
 * 순위·장소 묶기·근처 거르기는 Rust. 리뷰를 쓸 때 그날 날씨를 한 번 받아 같이 저장한다(못 받으면 날씨 없이).
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val core: EodiCore,
    private val navi: PleosNavi,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        reload()
    }

    fun setMode(m: ReviewMode) = _state.update { it.copy(mode = m) }

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
                val places = async { core.popularPlaces(near, NEAR_KM, limit = 100) }
                val feed = async { core.travelReviews(s.sort, near, NEAR_KM) }
                val (p, f) = places.await() to feed.await()
                _state.update { it.copy(places = Load.Loaded(p), feed = Load.Loaded(f), nearKnown = !s.nearOn || near != null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                _state.update { it.copy(places = Load.Failed(msg), feed = Load.Failed(msg)) }
            }
        }
    }

    fun openPlace(p: PopularPlace) {
        _state.update { it.copy(placeReviews = PlaceReviews(p)) }
        viewModelScope.launch {
            val r = try {
                Load.Loaded(core.reviewsOfPlace(p.place, CommunitySort.POPULAR))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { s -> s.placeReviews?.takeIf { it.place == p }?.let { s.copy(placeReviews = it.copy(reviews = r)) } ?: s }
        }
    }

    fun closePlace() = _state.update { it.copy(placeReviews = null) }

    /** 리뷰를 보던 곳으로 바로 안내 (경유지 없이 목적지 하나). */
    fun navigateToPlace() {
        val p = _state.value.placeReviews?.place ?: return
        viewModelScope.launch {
            val s = p.place
            val ok = runCatching { navi.requestRoute(s.name, s.lat, s.lon, s.address) }.getOrDefault(false)
            _state.update { st -> st.placeReviews?.takeIf { it.place == p }?.let { st.copy(placeReviews = it.copy(navi = ok)) } ?: st }
        }
    }

    /**
     * 리뷰를 보던 곳의 리뷰 쓰기. 리뷰는 내가 실제로 들른 곳에만 쓰므로, 내 들른 곳 목록에 그곳이 있으면
     * 그 방문(날짜·날씨)으로 곳 고르기 단계를 건너뛰고, 없으면 평소처럼 들른 곳 목록을 보여 준다.
     */
    fun writeForPlace() {
        val p = _state.value.placeReviews?.place?.place ?: return
        _state.update { it.copy(placeReviews = null) }
        startDraft(preferName = p.name)
    }

    fun toggleHeart(id: String) {
        _state.update { it.flipReview(id) }
        viewModelScope.launch {
            try {
                core.toggleReviewHeart(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "하트 실패 $id", e)
                _state.update { it.flipReview(id) }
            }
        }
    }

    // ---- 리뷰 쓰기 ----

    fun startDraft(preferName: String? = null) {
        _state.update { it.copy(draft = ReviewDraft()) }
        viewModelScope.launch {
            val r = try {
                Load.Loaded(core.reviewCandidates())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.message ?: e.javaClass.simpleName)
            }
            val visited = preferName?.let { name -> (r as? Load.Loaded)?.items?.firstOrNull { it.name == name } }
            _state.update { s -> s.copy(draft = s.draft?.copy(candidates = r, place = s.draft.place ?: visited)) }
        }
    }

    fun closeDraft() = _state.update { it.copy(draft = null) }
    fun pickPlace(p: StopFact?) = _state.update { s -> s.copy(draft = s.draft?.copy(place = p, error = null)) }
    fun setRating(r: Int) = _state.update { s -> s.copy(draft = s.draft?.copy(rating = r.coerceIn(1, 5))) }
    fun setText(t: String) = _state.update { s -> s.copy(draft = s.draft?.copy(text = t.take(500))) }

    fun submitDraft() {
        val d = _state.value.draft ?: return
        val place = d.place ?: return
        if (d.submitting) return
        _state.update { s -> s.copy(draft = d.copy(submitting = true, error = null)) }
        viewModelScope.launch {
            try {
                val weather = core.visitWeather(place)
                core.addPlaceReviewWith(place, d.rating, d.text, weather)
                _state.update { it.copy(draft = null, mode = ReviewMode.FEED, sort = CommunitySort.RECENT, justPosted = true) }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(draft = s.draft?.copy(submitting = false, error = e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    fun dismissPosted() = _state.update { it.copy(justPosted = false) }

    private companion object {
        const val TAG = "EodiReviews"
        const val NEAR_KM = 30.0
    }
}

private fun TravelReview.flip(id: String): TravelReview =
    if (review.id != id) this else copy(review = review.copy(heartedByMe = !review.heartedByMe, hearts = review.hearts + if (review.heartedByMe) -1 else 1))

private fun Load<TravelReview>.flip(id: String): Load<TravelReview> = if (this is Load.Loaded) Load.Loaded(items.map { it.flip(id) }) else this

private fun ReviewUiState.flipReview(id: String) = copy(
    feed = feed.flip(id),
    placeReviews = placeReviews?.let { it.copy(reviews = it.reviews.flip(id)) },
)
