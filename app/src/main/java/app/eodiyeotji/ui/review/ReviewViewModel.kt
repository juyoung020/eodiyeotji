package app.eodiyeotji.ui.review

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import app.eodiyeotji.core.PopularPlace
import app.eodiyeotji.core.ReviewKeywordOptions
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.WithWhom
import app.eodiyeotji.ui.community.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 열어 달라는 장소 (화면이 앱 공통 장소 상세 창으로 넘기고 바로 비운다). */
@Immutable
data class PlaceReviews(val place: PopularPlace)

/**
 * 리뷰 쓰기 (장소 창의 [리뷰 쓰기], ui/place/PlaceDetailViewModel): 들른 곳 고르기 → 별점 + 키워드(여러 개, [keywordLimit]개까지) + 누구와 + 후기.
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
data class ReviewUiState(val placeReviews: PlaceReviews? = null)

/**
 * 커뮤니티 > 여행 리뷰에서 장소 창 열기 (검색 결과의 리뷰 줄이 여기로 온다).
 * 여행 코스 후기 목록 · 상세 · 하트 · 올리기는 [app.eodiyeotji.ui.community.CourseViewModel]이 맡는다.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** 장소 열기 → 화면이 장소 상세 창(ui/place/PlaceDetailSheet)을 연다. */
    fun openPlace(p: PopularPlace) = _state.update { it.copy(placeReviews = PlaceReviews(p)) }

    fun closePlace() = _state.update { it.copy(placeReviews = null) }
}
