package app.eodiyeotji.ui.place

import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.community.LoadingBox
import app.eodiyeotji.ui.community.RetryButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.PlaceDetail
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.LocalOverlayPresenter
import app.eodiyeotji.ui.community.Notice
import app.eodiyeotji.ui.community.OverlayCard
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.dateText
import app.eodiyeotji.ui.offers.PlaceCouponBadge
import app.eodiyeotji.ui.review.LikedKeywords
import app.eodiyeotji.ui.review.RichReviewCard
import app.eodiyeotji.ui.review.WriteReviewSheet
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import java.time.LocalDate
import java.util.Locale

/*
 * 장소 상세 창 (네이버 지도 장소 창처럼): 큰 사진 → 이름 · 분류 · 주소 · 별점 요약 → [안내 · 저장 · 리뷰 쓰기] → 내 방문 →
 * "이런 점이 좋았어요"(키워드 막대) → 자세한 리뷰 카드.
 * 앱 어디서든 장소를 누르면 [LocalOpenPlace]로 연다. 계산(같은 곳 고르기 · 리뷰 정렬 · 별점 · 내 방문)은 Rust `place_detail`.
 * - 차: 화면 오른쪽 큰 창 (폭 약 64%), 바깥을 누르거나 뒤로 가기로 닫는다.
 * - 폰: 아래 시트 ([LocalOverlayPresenter]).
 * - 운전 중에는 열리지 않는다 ([PlaceDetailViewModel]).
 */

/** 장소 상세 창 열기. 앱 전체에서 하나 ([PlaceDetailScope]가 넣는다). 없으면 아무것도 하지 않는다 (미리보기). */
val LocalOpenPlace = staticCompositionLocalOf<(StopFact) -> Unit> { {} }

private val LocalPlaceDetail = staticCompositionLocalOf<PlaceDetailViewModel?> { null }

/** 앱 전체를 감싼다 (MainActivity). 창 그리기는 판마다 [PlaceDetailHost]를 화면 맨 위에 둔다. */
@Composable
fun PlaceDetailScope(viewModel: PlaceDetailViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val open = remember(viewModel) { { p: StopFact -> viewModel.open(p) } }
    CompositionLocalProvider(LocalPlaceDetail provides viewModel, LocalOpenPlace provides open, content = content)
}

/** 장소 상세 창을 그리는 자리 (차: 화면 맨 위, 폰: 시트 자리를 넣는 곳 안). */
@Composable
fun PlaceDetailHost() {
    val vm = LocalPlaceDetail.current ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    val place = s.place ?: return
    val sheet = LocalOverlayPresenter.current != null
    if (sheet) {
        OverlayCard(onDismiss = vm::close) { PlaceDetailContent(place, s, vm, compact = true) }
    } else {
        BackHandler(onBack = vm::close)
        SidePanel(onDismiss = vm::close) { PlaceDetailContent(place, s, vm, compact = false) }
    }
    s.draft?.let { d ->
        if (!sheet) BackHandler(onBack = vm::closeDraft)
        WriteReviewSheet(
            d,
            onPick = vm::pickPlace,
            onRating = vm::setRating,
            onText = vm::setText,
            onKeyword = vm::toggleKeyword,
            onWith = vm::pickWith,
            onSubmit = vm::submitDraft,
            onClose = vm::closeDraft,
        )
    }
}

/** 차 화면: 뒤를 어둡게 하고 오른쪽에서 밀려 들어오는 큰 창. */
@Composable
private fun SidePanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val none = remember { MutableInteractionSource() }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = none, indication = null, onClick = onDismiss)
            .safeDrawingPadding(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 좁은 창(나눈 화면 · 세로)에서는 창 폭 전부: 560dp로 오른쪽에 붙이면 왼쪽에 뒤 목록이 한 줄 남아 어수선했다
        val width: Dp = if (maxWidth < 900.dp) maxWidth else (maxWidth * 0.64f).coerceIn(560.dp, 1200.dp)
        AnimatedVisibility(
            visibleState = shown,
            enter = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(180)),
        ) {
            Surface(
                color = EodiTheme.colors.surface,
                contentColor = EodiTheme.colors.textPrimary,
                shape = RoundedCornerShape(EodiTheme.space.radiusL),
                modifier = Modifier
                    .padding(24.dp)
                    .width(width)
                    .fillMaxHeight()
                    // 창 안을 눌러도 닫히지 않게 클릭을 먹는다
                    .clickable(interactionSource = none, indication = null) {},
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PlaceDetailContent(opened: StopFact, s: PlaceDetailUiState, vm: PlaceDetailViewModel, compact: Boolean) {
    val c = EodiTheme.colors
    val d = s.detail
    // 사진은 누른 곳 이름 · 좌표로 바로 받는다 (Rust 결과를 기다리지 않는다, 같은 사진 저장소 · 같은 크기)
    val place = d?.place ?: opened
    val pad = if (compact) 28.dp else 36.dp
    Box(Modifier.fillMaxWidth()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = pad),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "gallery", contentType = "gallery") {
                Box(Modifier.padding(start = if (compact) 16.dp else 0.dp, end = if (compact) 16.dp else 0.dp).clip(
                    if (compact) RoundedCornerShape(EodiTheme.space.radiusM)
                    else RoundedCornerShape(topStart = EodiTheme.space.radiusL, topEnd = EodiTheme.space.radiusL),
                )) {
                    PlacePhotoGallery(opened.name, opened.address, opened.lat, opened.lon, Modifier.fillMaxWidth().height(if (compact) 280.dp else 380.dp))
                }
            }
            item(key = "title", contentType = "title") {
                TitleBlock(place, d, compact, Modifier.padding(start = pad, end = pad, top = 24.dp))
            }
            item(key = "actions", contentType = "actions") {
                Column(Modifier.padding(start = pad, end = pad, top = 24.dp)) {
                    ActionRow(d, vm)
                    s.navi?.let { ok ->
                        Text(
                            stringResource(if (ok) R.string.place_detail_navi_ok else R.string.place_detail_navi_fail),
                            style = EodiTheme.type.caption,
                            color = if (ok) c.textSecondary else c.error,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    if (d != null && d.lastVisit == null) {
                        Text(
                            stringResource(R.string.place_detail_write_need_visit),
                            style = EodiTheme.type.caption,
                            color = c.textTertiary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            if (d != null) {
                item(key = "visits", contentType = "visits") { VisitsLine(d, Modifier.padding(start = pad, end = pad, top = 24.dp)) }
            }
            item(key = "divider", contentType = "divider") {
                HorizontalDivider(Modifier.padding(start = pad, end = pad, top = 28.dp, bottom = 20.dp), thickness = 2.dp, color = c.outline)
            }
            when {
                s.error != null -> item(key = "error", contentType = "message") {
                    Column(Modifier.padding(horizontal = pad), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.place_detail_failed), style = EodiTheme.type.body, color = c.error)
                        RetryButton(vm::retry)
                    }
                }
                d == null -> item(key = "loading", contentType = "message") {
                    LoadingBox(Modifier.fillMaxWidth().height(200.dp), compact = true)
                }
                else -> {
                    if (d.keywords.isNotEmpty()) {
                        item(key = "liked", contentType = "liked") {
                            LikedKeywords(d.keywords, d.keywordVoters, Modifier.padding(start = pad, end = pad, bottom = 32.dp))
                        }
                    }
                    item(key = "reviews-h", contentType = "header") {
                        Row(Modifier.padding(start = pad, end = pad, bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
                            Text(
                                stringResource(R.string.review_count, d.reviewCount),
                                style = EodiTheme.type.titleSmall,
                                modifier = Modifier.semantics { heading() },
                            )
                            if (d.reviewCount > 1) {
                                Spacer(Modifier.width(14.dp))
                                Text(stringResource(R.string.place_detail_sort_hearts), style = EodiTheme.type.meta, color = c.textTertiary)
                            }
                        }
                    }
                    if (d.reviews.isEmpty()) {
                        item(key = "empty", contentType = "message") {
                            Text(
                                stringResource(R.string.place_detail_empty),
                                style = EodiTheme.type.body,
                                color = c.textSecondary,
                                modifier = Modifier.padding(horizontal = pad, vertical = 12.dp),
                            )
                        }
                    } else {
                        items(d.reviews, key = { "r-${it.review.id}" }, contentType = { "review" }) { r ->
                            Box(Modifier.padding(start = pad, end = pad, bottom = 12.dp)) {
                                RichReviewCard(r, onHeart = { vm.toggleHeart(r.review.id) }, showPlace = false)
                            }
                        }
                    }
                }
            }
        }
        // 닫기는 사진 위에 늘 떠 있다
        RoundIconButton(
            EodiIcons.Close,
            stringResource(R.string.community_close),
            vm::close,
            Modifier.align(Alignment.TopEnd).padding(if (compact) 28.dp else 20.dp),
        )
        if (s.justPosted) {
            Notice(stringResource(R.string.review_posted), Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }
}

/** 이름(크게) · 분류 · 주소 · ★ 4.5 · 리뷰 12 · ♥ 30 · 쿠폰. */
@Composable
private fun TitleBlock(place: StopFact, d: PlaceDetail?, compact: Boolean, modifier: Modifier) {
    val c = EodiTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            place.name,
            style = if (compact) EodiTheme.type.title else EodiTheme.type.hero,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        val sub = listOf(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotEmpty()) {
            Text(sub, style = EodiTheme.type.meta, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // 리뷰도 하트도 없으면 이 줄은 비운다: 아래 "리뷰 0" 머리와 같은 말이 두 번 보였다
        if (d != null && (d.reviewCount > 0 || d.hearts > 0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (d.reviewCount > 0) {
                    HalfStars(d.ratingHalves, size = 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(String.format(Locale.ROOT, "%.1f", d.avgRating), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold)
                    Text(" · ", style = EodiTheme.type.label, color = c.textTertiary)
                }
                Text(stringResource(R.string.review_count, d.reviewCount), style = EodiTheme.type.label, color = c.textSecondary)
                if (d.hearts > 0) {
                    Spacer(Modifier.width(20.dp))
                    Icon(CommunityIcons.HeartFilled, null, tint = c.heart, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(d.hearts.toString(), style = EodiTheme.type.label, fontWeight = FontWeight.Bold)
                }
            }
            if (d.offers.isNotEmpty()) PlaceCouponBadge(d.offers)
        }
    }
}

/** 별 5개를 반 별 단위로 ([halves] = 0~10, Rust가 반올림한 값). */
@Composable
fun HalfStars(halves: Int, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val c = EodiTheme.colors
    val desc = stringResource(R.string.place_detail_rating_description, halves / 2.0)
    Row(modifier.semantics { contentDescription = desc }, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 5) {
            val filled = (halves - i * 2).coerceIn(0, 2)
            Box(Modifier.size(size)) {
                Icon(CommunityIcons.StarOutline, null, Modifier.size(size), tint = c.textTertiary)
                if (filled > 0) {
                    Icon(
                        CommunityIcons.Star,
                        null,
                        tint = c.star,
                        modifier = Modifier.size(size).drawWithContent {
                            if (filled == 2) {
                                drawContent()
                            } else {
                                clipRect(right = this.size.width / 2) { this@drawWithContent.drawContent() }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** [안내] [저장 ♥] [리뷰 쓰기] [같이 가기]: 한 칸 76dp 이상 (Pleos 터치 크기). */
@Composable
private fun ActionRow(d: PlaceDetail?, vm: PlaceDetailViewModel) {
    val c = EodiTheme.colors
    // 좁은 창(세로 · 두 앱)에서는 칸이 약 110dp라 "여기로 안내"가 "여기로 …"로 잘렸다 → 짧은 이름 "안내"
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val narrow = maxWidth < 600.dp
    Row(horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 16.dp), modifier = Modifier.fillMaxWidth()) {
        ActionTile(
            EodiIcons.Navi,
            stringResource(if (narrow) R.string.place_detail_navigate_short else R.string.place_detail_navigate),
            vm::navigate,
            Modifier.weight(1f),
            primary = true,
            loading = vm.state.collectAsStateWithLifecycle().value.naviBusy,
        )
        val saved = d?.saved == true
        ActionTile(
            if (saved) CommunityIcons.HeartFilled else CommunityIcons.Heart,
            stringResource(if (saved) R.string.place_detail_saved else R.string.place_detail_save),
            vm::toggleSaved,
            Modifier.weight(1f),
            enabled = d?.placeId != null,
            tint = if (saved) c.heart else null,
        )
        ActionTile(
            CommunityIcons.Pen,
            stringResource(R.string.place_detail_write),
            vm::startReview,
            Modifier.weight(1f),
            enabled = d?.lastVisit != null,
        )
        // 같이 가기: 여러 차가 이곳으로 함께 (ui/convoy)
        ActionTile(CommunityIcons.People, stringResource(R.string.convoy_title), vm::goTogether, Modifier.weight(1f))
    }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    loading: Boolean = false,
) {
    val c = EodiTheme.colors
    val bg = when {
        primary -> c.accent
        else -> c.surfaceHigh
    }
    val fg = when {
        primary -> c.onAccent
        !enabled -> c.textTertiary
        else -> c.textPrimary
    }
    PressSurface(
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        color = bg,
        contentColor = fg,
        modifier = modifier.heightIn(min = 104.dp).semantics { this.contentDescription = label },
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (loading) app.eodiyeotji.ui.common.EodiLoader(size = 36.dp, dark = primary || c.isDark)
            else Icon(icon, null, Modifier.size(36.dp), tint = if (enabled) tint ?: (if (primary) fg else c.accent) else fg)
            Spacer(Modifier.height(8.dp))
            Text(label, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 내 방문: "3번 다녀옴 · 마지막 2025년 11월 7일" (기록에 없으면 "아직 다녀온 기록이 없어요"). */
@Composable
private fun VisitsLine(d: PlaceDetail, modifier: Modifier) {
    val c = EodiTheme.colors
    val last = d.lastVisitDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Row(
        modifier.fillMaxWidth().background(c.accentSoft, RoundedCornerShape(EodiTheme.space.radiusM)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(c.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EodiIcons.Calendar, null, tint = c.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            if (d.visitDays > 0 && last != null) {
                stringResource(R.string.place_detail_visits, d.visitDays, dateText(last))
            } else {
                stringResource(R.string.place_detail_no_visits)
            },
            style = EodiTheme.type.bodyStrong,
            color = if (d.visitDays > 0) c.textPrimary else c.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 장소 상세 창을 여는 줄 (onClickLabel). 카드 · 사진의 clickable에 쓴다. */
@Composable
fun placeDetailOpenLabel(): String = stringResource(R.string.place_detail_open)
