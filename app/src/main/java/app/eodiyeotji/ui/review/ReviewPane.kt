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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.PopularPlace
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.TravelReview
import app.eodiyeotji.ui.community.CenterMessage
import app.eodiyeotji.ui.community.ChoiceRow
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.HeartButton
import app.eodiyeotji.ui.community.Load
import app.eodiyeotji.ui.community.LoadingBox
import app.eodiyeotji.ui.community.MineBadge
import app.eodiyeotji.ui.community.Notice
import app.eodiyeotji.ui.community.OverlayCard
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.RatingStars
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.community.SortChoice
import app.eodiyeotji.ui.community.StopPhoto
import app.eodiyeotji.ui.community.TogglePill
import app.eodiyeotji.ui.community.VisitLine
import app.eodiyeotji.ui.community.agoText
import app.eodiyeotji.ui.community.rememberTopGridState
import app.eodiyeotji.ui.community.dateText
import app.eodiyeotji.ui.community.localDate
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import java.util.Locale
import kotlinx.coroutines.delay

/** 커뮤니티 > 여행 리뷰: [인기 장소 | 리뷰] + (리뷰면) 정렬 + 내 근처 + 리뷰 쓰기. */
@Composable
fun ReviewPane(viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 좁은 창: 왼쪽 선택들은 옆으로 밀어 볼 수 있고, "리뷰 쓰기"는 항상 다 보인다
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ChoiceRow(
                        options = listOf(
                            ReviewMode.PLACES to stringResource(R.string.review_popular_places),
                            ReviewMode.FEED to stringResource(R.string.review_feed),
                        ),
                        selected = state.mode,
                        onSelect = viewModel::setMode,
                    )
                    if (state.mode == ReviewMode.FEED) SortChoice(state.sort, viewModel::setSort)
                    TogglePill(stringResource(R.string.community_near_me, 30), state.nearOn, viewModel::toggleNear)
                }
                PrimaryPill(stringResource(R.string.review_write), CommunityIcons.Pen, { viewModel.startDraft() })
            }
            if (state.nearOn && !state.nearKnown) {
                Text(
                    stringResource(R.string.community_near_unknown),
                    style = EodiTheme.type.caption,
                    color = EodiTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
                )
            }
            when (state.mode) {
                ReviewMode.PLACES -> LoadList(state.places, viewModel::reload) { PlaceRanking(it, viewModel::openPlace) }
                ReviewMode.FEED -> LoadList(state.feed, viewModel::reload) { ReviewGrid(it, viewModel::toggleHeart) }
            }
        }
        state.placeReviews?.let { pr ->
            BackHandler(onBack = viewModel::closePlace)
            PlaceReviewsSheet(
                pr,
                onClose = viewModel::closePlace,
                onHeart = viewModel::toggleHeart,
                onNavigate = viewModel::navigateToPlace,
                onWrite = viewModel::writeForPlace,
            )
        }
        state.draft?.let { d ->
            BackHandler(onBack = viewModel::closeDraft)
            WriteReviewSheet(
                d,
                onPick = viewModel::pickPlace,
                onRating = viewModel::setRating,
                onText = viewModel::setText,
                onSubmit = viewModel::submitDraft,
                onClose = viewModel::closeDraft,
            )
        }
        if (state.justPosted) {
            LaunchedEffect(Unit) {
                delay(2_500)
                viewModel.dismissPosted()
            }
            Notice(stringResource(R.string.review_posted), Modifier.align(Alignment.BottomCenter))
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

// ---------------------------------------------------------------- 인기 장소

@Composable
private fun PlaceRanking(places: List<PopularPlace>, onOpen: (PopularPlace) -> Unit) {
    LazyVerticalGrid(
        state = rememberTopGridState(places.firstOrNull()?.place?.name),
        columns = GridCells.Adaptive(minSize = 680.dp),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(places.size, key = { i -> "${places[i].place.name}@${places[i].place.lat}" }) { i ->
            PlaceRow(i + 1, places[i]) { onOpen(places[i]) }
        }
    }
}

@Composable
private fun PlaceRow(rank: Int, p: PopularPlace, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Surface(onClick = onClick, color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                rank.toString(),
                style = EodiTheme.type.titleSmall,
                color = if (rank <= 3) c.accent else c.textTertiary,
                modifier = Modifier.width(56.dp),
            )
            StopPhoto(p.place.name, p.place.address, p.place.lat, p.place.lon, order = null, size = 120.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.place.name, style = EodiTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(p.place.category, regionOf(p.place.address)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = EodiTheme.type.meta,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(CommunityIcons.HeartFilled, null, tint = c.heart, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(p.hearts.toString(), style = EodiTheme.type.label, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(18.dp))
                    if (p.reviewCount > 0) {
                        Icon(CommunityIcons.Star, null, tint = c.star, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(String.format(Locale.ROOT, "%.1f", p.avgRating), style = EodiTheme.type.label, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(18.dp))
                    }
                    Text(stringResource(R.string.review_count, p.reviewCount), style = EodiTheme.type.label, color = c.textSecondary)
                }
            }
        }
    }
}

/** "강원 강릉시 초당동 309-1" → "강원 강릉시" (보여 주기만). */
private fun regionOf(address: String): String = address.split(' ').take(2).joinToString(" ")

// ---------------------------------------------------------------- 리뷰 피드

@Composable
private fun ReviewGrid(reviews: List<TravelReview>, onHeart: (String) -> Unit) {
    LazyVerticalGrid(
        state = rememberTopGridState(reviews.firstOrNull()?.review?.id),
        columns = GridCells.Adaptive(minSize = 680.dp),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(reviews, key = { it.review.id }, contentType = { "review" }) { r -> ReviewCard(r, onHeart = { onHeart(r.review.id) }) }
    }
}

@Composable
private fun ReviewCard(t: TravelReview, onHeart: () -> Unit, showPlace: Boolean = true) {
    val c = EodiTheme.colors
    val r = t.review
    Surface(
        color = if (showPlace) c.surface else c.surfaceHigh,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp)) {
            // 한 장소의 리뷰 모음에서는 장소 사진·이름이 위에 한 번만 있으면 된다
            if (showPlace) {
                StopPhoto(r.place.name, r.place.address, r.place.lat, r.place.lon, order = null, size = 148.dp)
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showPlace) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.place.name, style = EodiTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (r.mine) MineBadge()
                    }
                    Text(
                        listOf(r.place.category, regionOf(r.place.address)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = EodiTheme.type.caption,
                        color = c.textTertiary,
                        maxLines = 1,
                    )
                    RatingStars(r.rating)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingStars(r.rating)
                        if (r.mine) MineBadge()
                    }
                }
                if (r.text.isNotBlank()) {
                    Text(r.text, style = EodiTheme.type.body, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                VisitLine(t.visit)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.community_byline, r.author, agoText(r.createdMs)),
                        style = EodiTheme.type.caption,
                        color = c.textTertiary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    HeartButton(r.hearts, r.heartedByMe, onHeart, compact = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 장소별 리뷰

@Composable
private fun PlaceReviewsSheet(pr: PlaceReviews, onClose: () -> Unit, onHeart: (String) -> Unit, onNavigate: () -> Unit, onWrite: () -> Unit) {
    val p = pr.place
    OverlayCard(onDismiss = onClose, maxWidth = 1200.dp) {
        Column(Modifier.padding(32.dp).heightIn(max = 1050.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StopPhoto(p.place.name, p.place.address, p.place.lat, p.place.lon, order = null, size = 120.dp)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_place_title, p.place.name), style = EodiTheme.type.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(p.place.address, style = EodiTheme.type.meta, color = EodiTheme.colors.textTertiary, maxLines = 1)
                }
                RoundIconButton(CommunityIcons.Close, stringResource(R.string.community_close), onClose)
            }
            Spacer(Modifier.height(20.dp))
            // 리뷰를 보고 바로 할 일: 그곳으로 안내 · 그곳 리뷰 쓰기
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryPill(stringResource(R.string.review_navigate_here), EodiIcons.Navi, onNavigate)
                SecondaryPill(stringResource(R.string.review_write_here), CommunityIcons.Pen, onWrite)
                pr.navi?.let { ok ->
                    Text(
                        stringResource(if (ok) R.string.review_navi_sent else R.string.course_navi_failed),
                        style = EodiTheme.type.caption,
                        color = if (ok) EodiTheme.colors.textSecondary else EodiTheme.colors.error,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            when (val l = pr.reviews) {
                Load.Loading -> Box(Modifier.fillMaxWidth().height(240.dp)) { LoadingBox() }
                is Load.Failed -> Text(stringResource(R.string.community_failed, l.message), color = EodiTheme.colors.error)
                is Load.Loaded ->
                    if (l.items.isEmpty()) {
                        Text(stringResource(R.string.review_empty), style = EodiTheme.type.body, color = EodiTheme.colors.textSecondary)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(l.items, key = { it.review.id }) { r -> ReviewCard(r, onHeart = { onHeart(r.review.id) }, showPlace = false) }
                        }
                    }
            }
        }
    }
}

// ---------------------------------------------------------------- 리뷰 쓰기

/** 1단계: 내가 실제로 들른 곳(최근 머문 곳, 사진) 고르기 → 2단계: 별점 + 한 줄 → 등록. */
@Composable
private fun WriteReviewSheet(
    d: ReviewDraft,
    onPick: (StopFact?) -> Unit,
    onRating: (Int) -> Unit,
    onText: (String) -> Unit,
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
                    OutlinedTextField(
                        value = d.text,
                        onValueChange = onText,
                        placeholder = { Text(stringResource(R.string.review_text_hint), style = EodiTheme.type.body) },
                        textStyle = EodiTheme.type.body,
                        maxLines = 3,
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
