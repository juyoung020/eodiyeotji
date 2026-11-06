package app.eodiyeotji.ui.review

import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.DriveCourse
import app.eodiyeotji.core.TravelFeedItem
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.WithWhom
import app.eodiyeotji.ui.community.CenterMessage
import app.eodiyeotji.ui.community.CourseViewModel
import app.eodiyeotji.ui.community.CourseDetailScreen
import app.eodiyeotji.ui.community.CompactToolbar
import app.eodiyeotji.ui.community.ShareCourseSheet
import app.eodiyeotji.ui.community.ShareEpisodePicker
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.Load
import app.eodiyeotji.ui.community.LoadingBox
import app.eodiyeotji.ui.community.Notice
import app.eodiyeotji.ui.community.OverlayCard
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.community.SortChoice
import app.eodiyeotji.ui.community.StopPhoto
import app.eodiyeotji.ui.community.TogglePill
import app.eodiyeotji.ui.community.dateText
import app.eodiyeotji.ui.community.localDate
import app.eodiyeotji.ui.place.LocalOpenPlace
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import kotlinx.coroutines.delay

/**
 * 커뮤니티 > 여행 리뷰: 장소 리뷰와 여행 코스 후기(여러 곳을 차례로 다녀온 글)를 한 피드에 같은 카드 모양으로.
 * 도구 줄: 인기순·최신순 + 내 근처 + 여행 코스 올리기(내 기록에서) + 리뷰 쓰기.
 * 장소 리뷰의 큰 사진 → 장소 창, 여행 코스 후기 카드 → 코스 상세(지도 · 들른 곳 · 이 코스로 안내, [CourseViewModel]).
 */
@Composable
fun ReviewPane(viewModel: ReviewViewModel = hiltViewModel(), courses: CourseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val course by courses.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var shareEpisode by rememberSaveable { mutableStateOf<Long?>(null) }
    val openPlace: (StopFact) -> Unit = LocalOpenPlace.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp)) {
                val compact = maxWidth < CompactToolbar
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 좁은 창: 왼쪽 선택들은 옆으로 밀어 볼 수 있고, 버튼은 항상 다 보인다
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SortChoice(state.sort, viewModel::setSort)
                        TogglePill(stringResource(R.string.community_near_me, 30), state.nearOn, viewModel::toggleNear)
                    }
                    SecondaryPill(
                        stringResource(if (compact) R.string.review_share_trip_short else R.string.review_share_trip),
                        CommunityIcons.Upload,
                        { picking = true },
                    )
                    PrimaryPill(stringResource(R.string.review_write), CommunityIcons.Pen, { viewModel.startDraft() })
                }
            }
            if (state.nearOn && !state.nearKnown) {
                Text(
                    stringResource(R.string.community_near_unknown),
                    style = EodiTheme.type.caption,
                    color = EodiTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
                )
            }
            LoadList(state.feed, viewModel::reload) { items ->
                FeedGrid(items, onHeart = viewModel::toggleHeart, onOpenTrip = courses::open)
            }
        }
        // 여행 코스 후기 상세 (지도 · 들른 곳 · 하트 · 이 코스로 안내). 닫으면 피드를 다시 읽어 하트 수를 맞춘다
        course.detail?.let { d ->
            val close = {
                courses.closeDetail()
                viewModel.reload()
            }
            BackHandler(onBack = close)
            val progress by courses.progress.collectAsStateWithLifecycle()
            CourseDetailScreen(
                d,
                progress = progress?.takeIf { it.courseId == d.course.post.id && !it.finished },
                onBack = close,
                onHeart = { courses.toggleHeart(d.course.post.id) },
                onNavigate = courses::navigate,
                onOpenPlace = openPlace,
            )
        }
        // 검색 결과의 리뷰 줄을 누르면 앱 공통 장소 상세 창으로 연다 (별점 · 리뷰 · 안내 · 리뷰 쓰기)
        state.placeReviews?.let { pr ->
            LaunchedEffect(pr) {
                openPlace(pr.place.place)
                viewModel.closePlace()
            }
        }
        state.draft?.let { d ->
            BackHandler(onBack = viewModel::closeDraft)
            WriteReviewSheet(
                d,
                onPick = viewModel::pickPlace,
                onRating = viewModel::setRating,
                onText = viewModel::setText,
                onKeyword = viewModel::toggleKeyword,
                onWith = viewModel::pickWith,
                onSubmit = viewModel::submitDraft,
                onClose = viewModel::closeDraft,
            )
        }
        if (picking) {
            BackHandler { picking = false }
            ShareEpisodePicker(onPick = { picking = false; shareEpisode = it }, onDismiss = { picking = false })
        }
        shareEpisode?.let { id ->
            BackHandler { shareEpisode = null }
            ShareCourseSheet(episodeId = id, onDone = { shared ->
                shareEpisode = null
                if (shared != null) viewModel.onTripShared()
            })
        }
        state.notice?.let { res ->
            LaunchedEffect(res) {
                delay(2_500)
                viewModel.dismissNotice()
            }
            Notice(stringResource(res), Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun <T> LoadList(load: Load<T>, onRetry: () -> Unit, content: @Composable (List<T>) -> Unit) {
    when (load) {
        Load.Loading -> LoadingBox()
        is Load.Failed -> CenterMessage(stringResource(R.string.community_failed, load.message)) {
            SecondaryPill(stringResource(R.string.community_retry), EodiIcons.Refresh, onRetry)
        }
        is Load.Loaded -> if (load.items.isEmpty()) CenterMessage(stringResource(R.string.review_empty)) else content(load.items)
    }
}

// ---------------------------------------------------------------- 피드

@Composable
private fun FeedGrid(items: List<TravelFeedItem>, onHeart: (String, Boolean) -> Unit, onOpenTrip: (DriveCourse) -> Unit) {
    val state = rememberLazyStaggeredGridState()
    // 정렬을 바꾸거나 새 글을 올려 맨 위가 바뀌면 맨 위로 (rememberTopGridState와 같은 이유)
    val firstKey = items.firstOrNull()?.key
    LaunchedEffect(firstKey) { state.scrollToItem(0) }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 680.dp),
        state = state,
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalItemSpacing = 16.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        staggeredItems(items, key = { it.key }, contentType = { if (it is TravelFeedItem.Trip) "trip" else "review" }) { item ->
            when (item) {
                is TravelFeedItem.Review -> RichReviewCard(item.review, onHeart = { onHeart(item.review.review.id, false) })
                is TravelFeedItem.Trip -> TripReviewCard(item.course, onOpen = { onOpenTrip(item.course) }, onHeart = { onHeart(item.course.post.id, true) })
            }
        }
    }
}

// ---------------------------------------------------------------- 리뷰 쓰기

/**
 * 1단계: 내가 실제로 들른 곳(최근 머문 곳, 사진) 고르기 → 2단계: 별점 + 키워드(5개까지) + 누구와 + 후기 → 등록.
 * 다녀온 때 · 머문 시간 · 몇 번째 방문은 Rust가 고른 방문 기록에서 채운다.
 */
@Composable
internal fun WriteReviewSheet(
    d: ReviewDraft,
    onPick: (StopFact?) -> Unit,
    onRating: (Int) -> Unit,
    onText: (String) -> Unit,
    onKeyword: (String) -> Unit,
    onWith: (WithWhom) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val c = EodiTheme.colors
    OverlayCard(onDismiss = { if (!d.submitting) onClose() }, maxWidth = 1100.dp) {
        val place = d.place
        Column(Modifier.padding(32.dp).heightIn(max = 1050.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (place != null) {
                    RoundIconButton(EodiIcons.Back, stringResource(R.string.community_back), { onPick(null) })
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    if (place == null) stringResource(R.string.review_pick_place) else place.name,
                    style = EodiTheme.type.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(CommunityIcons.Close, stringResource(R.string.community_close), onClose)
            }
            Spacer(Modifier.height(20.dp))
            if (place == null) {
                when (val l = d.candidates) {
                    Load.Loading -> Box(Modifier.fillMaxWidth().height(240.dp)) { LoadingBox() }
                    is Load.Failed -> Text(stringResource(R.string.community_failed, l.message), color = c.error)
                    is Load.Loaded ->
                        if (l.items.isEmpty()) {
                            Text(stringResource(R.string.review_pick_empty), style = EodiTheme.type.body, color = c.textSecondary)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(l.items, key = { i, s -> "$i-${s.name}-${s.arriveMs}" }) { _, s -> CandidatePlace(s) { onPick(s) } }
                            }
                        }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StopPhoto(place.name, place.address, place.lat, place.lon, order = null, size = 140.dp)
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text(listOf(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · "), style = EodiTheme.type.meta, color = c.textTertiary)
                            Text(dateText(localDate(place.arriveMs)), style = EodiTheme.type.label, color = c.textSecondary)
                        }
                    }
                    StarPicker(d.rating, onRating)
                    KeywordPicker(d.keywordOptions, d.keywords, d.keywordLimit, onKeyword)
                    WithWhomPicker(d.withWhom, onWith)
                    OutlinedTextField(
                        value = d.text,
                        onValueChange = onText,
                        placeholder = { Text(stringResource(R.string.review_text_hint), style = EodiTheme.type.body) },
                        textStyle = EodiTheme.type.body,
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    d.error?.let { Text(stringResource(R.string.community_failed, it), style = EodiTheme.type.caption, color = c.error) }
                    PrimaryPill(
                        stringResource(if (d.submitting) R.string.share_submitting else R.string.review_submit),
                        CommunityIcons.Check,
                        onSubmit,
                        Modifier.fillMaxWidth(),
                        enabled = !d.submitting,
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidatePlace(s: StopFact, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Surface(onClick = onClick, color = c.surfaceHigh, shape = RoundedCornerShape(EodiTheme.space.radiusM), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StopPhoto(s.name, s.address, s.lat, s.lon, order = null, size = 96.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = EodiTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(dateText(localDate(s.arriveMs)), s.category).filter { it.isNotBlank() }.joinToString(" · "),
                    style = EodiTheme.type.meta,
                    color = c.textTertiary,
                    maxLines = 1,
                )
            }
            Icon(EodiIcons.Chevron, null, tint = c.textTertiary, modifier = Modifier.size(28.dp))
        }
    }
}

/** 큰 별 5개 (하나 76dp). */
@Composable
private fun StarPicker(rating: Int, onRating: (Int) -> Unit) {
    val c = EodiTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 1..5) {
            val desc = stringResource(R.string.review_rating_description, i)
            Box(
                Modifier
                    .size(76.dp)
                    .background(if (i <= rating) c.star.copy(alpha = 0.16f) else c.surfaceHigh, CircleShape)
                    .clickable(role = Role.RadioButton) { onRating(i) }
                    .semantics { contentDescription = desc },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (i <= rating) CommunityIcons.Star else CommunityIcons.StarOutline,
                    null,
                    tint = if (i <= rating) c.star else c.textTertiary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
