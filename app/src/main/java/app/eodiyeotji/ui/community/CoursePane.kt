package app.eodiyeotji.ui.community

import app.eodiyeotji.ui.common.agoText
import app.eodiyeotji.ui.offers.CourseCouponBadge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.core.CommunitySort
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.Visit
import app.eodiyeotji.core.StopReviews
import app.eodiyeotji.core.TravelReview
import app.eodiyeotji.ui.review.FeedHero
import app.eodiyeotji.ui.review.StopStrip
import app.eodiyeotji.ui.place.placeDetailOpenLabel
import kotlin.math.roundToInt
import app.eodiyeotji.pleos.CourseProgress
import app.eodiyeotji.ui.map.DayMap
import app.eodiyeotji.ui.map.MapPhotoPin
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.ui.share.ShareButtonStyle
import app.eodiyeotji.ui.share.ShareImageButton
import app.eodiyeotji.ui.share.ShareTarget

/** 도구 줄이 이보다 좁으면 버튼 이름을 짧게 쓴다 (여행 리뷰 · 드라이브 코스 도구 줄). */
internal val CompactToolbar = 900.dp

/** 이 폭 이상이면 상세를 지도 | 정보 두 칸으로 나눈다. */
private val WideDetail = 1100.dp

@Composable
fun SortChoice(sort: CommunitySort, onSort: (CommunitySort) -> Unit) {
    ChoiceRow(
        options = listOf(
            CommunitySort.POPULAR to stringResource(R.string.community_sort_popular),
            CommunitySort.RECENT to stringResource(R.string.community_sort_recent),
        ),
        selected = sort,
        onSelect = onSort,
    )
}

/** "4곳 · 56.2km · 4시간" */
@Composable
private fun courseStatsText(stops: Int, distanceM: Double, durationMs: Long): String =
    listOf(stringResource(R.string.community_stops_count, stops), distanceText(distanceM), durationText(durationMs)).joinToString(" · ")

/** 다녀온 날·계절·날씨 칩 (아이콘은 그날 날씨). */
@Composable
internal fun VisitChip(v: Visit) {
    val text = visitText(v)
    if (text.isBlank()) return
    val (icon, tint) = weatherIcon(v.weather)
    InfoChip(text) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** 글자 + › 모양 가벼운 동작 (카드 아래 "코스 보기"). 누르는 자리는 76dp. */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.accent, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(EodiIcons.Chevron, null, tint = c.accent, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun MineBadge() {
    Text(
        stringResource(R.string.community_mine),
        style = EodiTheme.type.caption,
        color = EodiTheme.colors.onAccent,
        modifier = Modifier
            .padding(start = 8.dp)
            .background(EodiTheme.colors.accent, androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
fun OrderDot(order: Int, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    // 사진 위에 붙인 코랄 스티커: 흰 테두리로 어떤 사진 위에서도 또렷하다 (지도 핀 번호와 같은 색)
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier.size(38.dp).background(c.onMarker, shape).padding(2.5.dp).background(c.stay, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(order.toString(), style = EodiTheme.type.caption, color = c.onMarker, fontWeight = FontWeight.Bold)
    }
}

/**
 * 코스 상세: 지도(들르는 곳 핀·사진) + 글(글쓴이 머리·제목·소개·경로·다녀온 날·태그) + 들르는 곳 목록 + 하트·이 코스로 안내.
 * 들르는 곳 사진을 누르면 [onOpenPlace].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseDetailScreen(
    d: CourseDetail,
    progress: CourseProgress?,
    onBack: () -> Unit,
    onHeart: () -> Unit,
    onNavigate: () -> Unit,
    onOpenPlace: (StopFact) -> Unit,
) {
    val colors = EodiTheme.colors
    val p = d.course.post
    var selectedKey by remember(p.id) { mutableStateOf<String?>(null) }
    val pins = remember(p.id) { p.stops.mapIndexed { i, s -> MapPhotoPin("s${i + 1}", s.name, s.address, s.lat, s.lon) } }
    Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.textPrimary) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 24.dp)) {
            val wide = maxWidth >= WideDetail
            val map: @Composable (Modifier) -> Unit = { m ->
                Surface(shape = RoundedCornerShape(EodiTheme.space.radiusL), color = colors.surface, modifier = m) {
                    DayMap(data = d.map, selectedKey = selectedKey, onSelectKey = { selectedKey = it }, photoPins = pins, modifier = Modifier.fillMaxSize())
                }
            }
            val info: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item(key = "head") {
                            // 게시글 본문처럼: 글쓴이 머리 → 표지(첫 곳 큰 사진 위 제목) → 들른 곳 사진 줄 → 글쓴이 소개 → 조용한 정보 칩 → 태그
                            Surface(color = colors.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(24.dp)) {
                                    PostHeader(p.author, stringResource(R.string.community_byline, p.region, agoText(p.createdMs)), p.mine)
                                    Spacer(Modifier.height(18.dp))
                                    val cover = p.stops.firstOrNull()
                                    if (cover != null) {
                                        Box(Modifier.semantics { heading() }) {
                                            FeedHero(
                                                cover,
                                                title = p.title,
                                                sub = p.region,
                                                mine = false,
                                                onClickLabel = placeDetailOpenLabel(),
                                                label = stringResource(R.string.review_trip_badge, p.stops.size),
                                                onClick = { onOpenPlace(cover) },
                                            )
                                        }
                                        Spacer(Modifier.height(14.dp))
                                        // 사진을 누르면 지도에서 그곳을 고르고, 고른 곳을 한 번 더 누르면 장소 창
                                        val picked = selectedKey?.removePrefix("s")?.toIntOrNull()?.minus(1)
                                        StopStrip(
                                            p.stops,
                                            photos = p.stopPhotos,
                                            onMore = {},
                                            selected = picked,
                                            onPick = { i -> if (i == picked) onOpenPlace(p.stops[i]) else selectedKey = "s${i + 1}" },
                                        )
                                    } else {
                                        Text(p.title, style = EodiTheme.type.title, modifier = Modifier.semantics { heading() })
                                    }
                                    if (p.body.isNotBlank()) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(p.body, style = EodiTheme.type.body, color = colors.textPrimary)
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        VisitChip(d.course.visit)
                                        InfoChip(courseStatsText(p.stops.size, p.distanceM, p.durationMs))
                                        CourseCouponBadge(d.course.offers)
                                    }
                                    if (p.tags.isNotEmpty()) {
                                        Spacer(Modifier.height(14.dp))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            p.tags.forEach { InfoChip("#$it", tone = ChipTone.Accent) }
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "stops-title") {
                            Text(
                                stringResource(R.string.course_stops_title),
                                style = EodiTheme.type.label,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                            )
                        }
                        itemsIndexed(p.stops, key = { i, s -> "stop-$i-${s.name}" }) { i, s ->
                            StopRow(
                                i + 1, s,
                                reviews = d.stopReviews.getOrNull(i),
                                author = p.author,
                                localPhoto = p.stopPhotos.getOrNull(i)?.takeIf { it.isNotBlank() },
                                selected = selectedKey == "s${i + 1}",
                                onClick = { selectedKey = if (selectedKey == "s${i + 1}") null else "s${i + 1}" },
                                onOpenPhoto = { onOpenPlace(s) },
                            )
                        }
                    }
                    val total = d.legs.size
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HeartButton(p.hearts, p.heartedByMe, onHeart)
                        // 이 코스를 사진 카드로 공유 (폰 판만 보인다, ui/share)
                        ShareImageButton(ShareTarget.Course(p.id), style = ShareButtonStyle.ICON)
                        val label = when {
                            progress == null -> stringResource(R.string.course_navigate)
                            progress.hasNextLeg -> stringResource(R.string.course_navigate_next, progress.leg + 1, progress.total)
                            else -> stringResource(R.string.course_navigate_again)
                        }
                        PrimaryPill(label, EodiIcons.Navi, onNavigate, Modifier.weight(1f), enabled = total > 0)
                    }
                    val hint = when {
                        d.naviFailed -> stringResource(R.string.course_navi_failed)
                        progress != null -> stringResource(R.string.course_navi_auto, progress.leg, progress.total)
                        total > 1 -> stringResource(R.string.course_navi_legs_hint, total)
                        else -> null
                    }
                    if (hint != null) {
                        Text(
                            hint,
                            style = EodiTheme.type.caption,
                            color = if (d.naviFailed) colors.error else colors.textSecondary,
                            modifier = Modifier.padding(top = 8.dp, start = 8.dp),
                        )
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryPill(stringResource(R.string.course_back_to_list), EodiIcons.Back, onBack)
                }
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        map(Modifier.weight(0.55f).fillMaxHeight())
                        info(Modifier.weight(0.45f).fillMaxHeight())
                    }
                } else {
                    map(Modifier.fillMaxWidth().weight(0.4f))
                    Spacer(Modifier.height(20.dp))
                    info(Modifier.fillMaxWidth().weight(0.6f))
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    order: Int, s: StopFact, reviews: StopReviews?, author: String, selected: Boolean, onClick: () -> Unit, onOpenPhoto: () -> Unit,
    localPhoto: String? = null,
) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        color = if (selected) c.accentSoft else c.surface,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                // 사진을 누르면 그곳 사진 크게 보기, 줄의 나머지를 누르면 지도에서 그곳 고르기
                Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Image, onClick = onOpenPhoto)) {
                    StopPhoto(s.name, s.address, s.lat, s.lon, order = order, size = 104.dp, localPhoto = localPhoto)
                    OrderDot(order, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = EodiTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(s.category, s.address).filter { it.isNotBlank() }.joinToString(" · "),
                        style = EodiTheme.type.meta,
                        color = c.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (reviews != null && reviews.reviewCount > 0) {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RatingStars(reviews.avgRating.roundToInt(), size = 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.course_stop_rating, reviews.avgRating, reviews.reviewCount),
                                style = EodiTheme.type.caption,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }
            reviews?.pick?.let { t -> StopReviewQuote(t, if (reviews.byAuthor) author else null, onOpenPhoto) }
        }
    }
}

/**
 * 들른 곳 줄 아래 그곳 리뷰 한 편 (옅은 바탕): "바다좋아 님이 남긴 리뷰"(글쓴이 것이면) · 별점 · 키워드 · 글 3줄.
 * 누르면 그곳 장소 창 (리뷰 전부 · 안내 · 리뷰 쓰기).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopReviewQuote(t: TravelReview, author: String?, onOpen: () -> Unit) {
    val c = EodiTheme.colors
    val r = t.review
    Surface(
        onClick = onOpen,
        color = c.surfaceHigh,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (author != null) stringResource(R.string.course_stop_author_review, author) else stringResource(R.string.course_stop_top_review, r.author),
                    style = EodiTheme.type.label,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                RatingStars(r.rating, size = 20.dp)
            }
            if (r.keywords.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.keywords.take(3).forEach { InfoChip(it, tone = ChipTone.Accent) }
                }
            }
            if (r.text.isNotBlank()) {
                Text(r.text, style = EodiTheme.type.meta, color = c.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 화면 아래 알림 한 줄. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = EodiTheme.colors.textPrimary,
        contentColor = EodiTheme.colors.surface,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        modifier = modifier.padding(32.dp),
    ) {
        Text(text, style = EodiTheme.type.bodyStrong, modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp))
    }
}
