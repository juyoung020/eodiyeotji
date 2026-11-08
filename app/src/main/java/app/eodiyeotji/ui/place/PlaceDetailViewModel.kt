package app.eodiyeotji.ui.place

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.PlaceDetail
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.WithWhom
import app.eodiyeotji.core.reviewKeywordOptions
import app.eodiyeotji.core.writePlaceReview
import app.eodiyeotji.core.placeDetail
import app.eodiyeotji.core.setPlaceSaved
import app.eodiyeotji.core.toggleReviewHeart
import app.eodiyeotji.core.visitWeather
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.community.Load
import app.eodiyeotji.ui.review.ReviewDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 장소 상세 창 상태. [place] = 누른 곳 (null이면 창이 닫혀 있다). [detail]은 Rust가 계산해 오기 전까지 null.
 * [navi]: "안내"를 누른 결과 (null = 안 누름 / 알림 사라짐). [draft]: 이 창에서 연 리뷰 쓰기.
 */
@Immutable
data class PlaceDetailUiState(
    val place: StopFact? = null,
    val detail: PlaceDetail? = null,
    val error: String? = null,
    val navi: Boolean? = null,
    val draft: ReviewDraft? = null,
    val justPosted: Boolean = false,
)

/**
 * 장소 상세 창 (네이버 지도 장소 창처럼): 앱 어디서든 [open]으로 연다 ([PlaceDetailScope]가 앱 전체에 하나 둔다).
 * 별점 요약 · 리뷰 · 내 방문 · 쿠폰은 Rust `place_detail` 한 번으로 받고, 저장 · 하트 · 리뷰 쓰기도 Rust에 맡긴다.
 * 차는 운전 중에 열지 않고, 열려 있다가 운전을 시작하면 닫는다 (리뷰 · 긴 목록은 정차 중에만).
 * 폰은 화면을 막지 않는다 (동승자일 수 있다, [DrivingStatePort.locksScreen]).
 */
@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    private val core: EodiCore,
    private val navi: Navigator,
    private val driving: DrivingStatePort,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaceDetailUiState())
    val state: StateFlow<PlaceDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var naviJob: Job? = null

    init {
        if (driving.locksScreen) viewModelScope.launch { driving.isDriving.collect { if (it) close() } }
    }

    fun open(place: StopFact) {
        if (driving.locksScreen && driving.isDriving.value) return
        _state.value = PlaceDetailUiState(place = place)
        load(place)
    }

    fun close() {
        loadJob?.cancel()
        naviJob?.cancel()
        _state.value = PlaceDetailUiState()
    }

    fun retry() {
        _state.value.place?.let { p ->
            _state.update { it.copy(error = null) }
            load(p)
        }
    }

    private fun load(place: StopFact) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val d = core.placeDetail(place)
                _state.update { s -> if (s.place == place) s.copy(detail = d, error = null) else s }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "장소 상세 읽기 실패: ${place.name}", e)
                _state.update { s -> if (s.place == place) s.copy(error = e.message ?: e.javaClass.simpleName) else s }
            }
        }
    }

    /** "안내": 이곳을 목적지로 내비에 보낸다 (경유지 없이). 결과는 잠깐 보였다 사라진다. */
    fun navigate() {
        val s = _state.value
        val p = s.detail?.place ?: s.place ?: return
        naviJob?.cancel()
        naviJob = viewModelScope.launch {
            val ok = runCatching { navi.requestRoute(p.name, p.lat, p.lon, p.address) }.getOrDefault(false)
            _state.update { it.copy(navi = ok) }
            delay(NOTICE_MS)
            _state.update { it.copy(navi = null) }
        }
    }

    /** 저장(♥) 누르기: 바로 바뀐 것처럼 보이고, 저장은 Rust (내 장소 화면은 EodiCore.changes로 다시 읽는다). */
    fun toggleSaved() {
        val d = _state.value.detail ?: return
        val id = d.placeId ?: return
        val now = d.saved
        _state.update { s -> s.copy(detail = s.detail?.copy(saved = !now)) }
        viewModelScope.launch {
            val ok = runCatching { core.setPlaceSaved(id, !now) }.isSuccess
            if (!ok) _state.update { s -> s.copy(detail = s.detail?.copy(saved = now)) }
        }
    }

    fun toggleHeart(reviewId: String) {
        _state.update { it.flipHeart(reviewId) }
        viewModelScope.launch {
            try {
                core.toggleReviewHeart(reviewId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "하트 실패 $reviewId", e)
                _state.update { it.flipHeart(reviewId) }
            }
        }
    }

    // ---- 리뷰 쓰기 (내가 실제로 다녀온 곳만: 마지막 방문의 날짜 · 날씨로 쓴다) ----

    /** 리뷰 쓰는 곳 → 미리 받는 그날 날씨 */
    private var weather: Pair<StopFact, Deferred<String?>>? = null

    fun startReview() {
        val visit = _state.value.detail?.lastVisit ?: return
        _state.update { it.copy(draft = ReviewDraft(candidates = Load.Loaded(listOf(visit)), place = visit)) }
        // 그날 날씨(인터넷)는 글을 쓰는 동안 미리 받아 둔다 → [올리기]가 날씨를 기다리지 않는다
        weather = visit to viewModelScope.async { runCatching { core.visitWeather(visit) }.getOrNull() }
        viewModelScope.launch {
            val options = runCatching { core.reviewKeywordOptions() }.getOrNull() ?: return@launch
            _state.update { s -> s.copy(draft = s.draft?.withOptions(options)) }
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
        val visit = d.place ?: return
        if (d.submitting) return
        _state.update { it.copy(draft = d.copy(submitting = true, error = null)) }
        viewModelScope.launch {
            try {
                val pre = weather?.takeIf { it.first == visit }?.second
                val w = if (pre != null) pre.await() else core.visitWeather(visit)
                core.writePlaceReview(visit, d.rating, d.text, d.keywords, d.withWhom, w)
                _state.update { it.copy(draft = null, justPosted = true) }
                _state.value.place?.let { load(it) }
                delay(NOTICE_MS)
                _state.update { it.copy(justPosted = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(draft = s.draft?.copy(submitting = false, error = e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    private companion object {
        const val TAG = "EodiPlaceDetail"
        const val NOTICE_MS = 2_500L
    }
}

private fun PlaceDetailUiState.flipHeart(id: String): PlaceDetailUiState {
    val d = detail ?: return this
    val r = d.reviews.firstOrNull { it.review.id == id }?.review ?: return this
    val delta = if (r.heartedByMe) -1 else 1
    val reviews = d.reviews.map {
        if (it.review.id != id) it else it.copy(review = it.review.copy(heartedByMe = !r.heartedByMe, hearts = r.hearts + delta))
    }
    return copy(detail = d.copy(reviews = reviews, hearts = (d.hearts + delta).coerceAtLeast(0)))
}
