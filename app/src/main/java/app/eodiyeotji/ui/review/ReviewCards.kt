package app.eodiyeotji.ui.review

import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.community.TogglePill
import app.eodiyeotji.ui.community.ChipTone
import app.eodiyeotji.ui.community.InfoChip
import app.eodiyeotji.ui.community.FromRecordChip
import app.eodiyeotji.ui.community.StopPhotoBox
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.core.KeywordCount
import app.eodiyeotji.core.PlaceReview
import app.eodiyeotji.core.TimeOfDay
import app.eodiyeotji.core.TravelReview
import app.eodiyeotji.core.DriveCourse
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.ui.offers.CourseCouponBadge
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.core.WithWhom
import app.eodiyeotji.ui.community.HeartButton
import app.eodiyeotji.ui.community.HeartCount
import app.eodiyeotji.ui.community.MineBadge
import app.eodiyeotji.ui.community.OrderDot
import app.eodiyeotji.ui.community.PostHeader
import app.eodiyeotji.ui.community.RatingStars
import app.eodiyeotji.ui.community.VisitLine
import app.eodiyeotji.ui.common.agoText
import app.eodiyeotji.ui.offers.PlaceCouponBadge
import app.eodiyeotji.ui.place.LocalOpenPlace
import app.eodiyeotji.ui.place.placeDetailOpenLabel
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.common.durationText

/*
 * 자세한 여행 리뷰 (네이버 지도 리뷰처럼). 리뷰 탭 목록과 장소 상세 창이 같은 카드를 쓴다.
 * 키워드 요약 · 정렬 · 몇 번째 방문 · 머문 시간은 모두 Rust가 계산해 오고, 여기서는 그리기만 한다.
 */

/** 접힌 리뷰 글 줄 수 (넘치면 "더보기"). */
private const val CollapsedLines = 3

/**
 * 자세한 리뷰 카드: (장소 사진 · 이름 →) 글쓴이 동그라미 · 이름 · "N번째 방문" → 별점 → 키워드 칩 → 글(3줄, 더보기) →
 * 다녀온 줄 "11월 3일 · 가을 단풍철 · 흐림 11°C · 아침 · 가족과 · 1시간 머묾" → 쿠폰 · 하트.
 * 카드를 누르면 글을 펼치고 접는다. 장소 사진 · 이름을 누르면 장소 상세 창 ([LocalOpenPlace]).
 * [showPlace] = false면 장소 줄을 빼고 (장소 창 안) 옅은 바탕으로 그린다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RichReviewCard(
    t: TravelReview,
    onHeart: () -> Unit,
    modifier: Modifier = Modifier,
    showPlace: Boolean = true,
    onHidden: (() -> Unit)? = null,
) {
    val c = EodiTheme.colors
    val r = t.review
    var expanded by rememberSaveable(r.id) { mutableStateOf(false) }
    // 접힌 글이 넘친 적이 있으면 "더보기 / 접기"를 보인다
    var canExpand by rememberSaveable(r.id) { mutableStateOf(false) }
    PressSurface(
        onClick = { if (canExpand) expanded = !expanded },
        color = if (showPlace) c.surface else c.surfaceHigh,
        contentColor = c.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showPlace) {
                val openPlace = LocalOpenPlace.current
                FeedHero(
                    r.place,
                    title = r.place.name,
                    sub = listOf(r.place.category, regionOf(r.place.address)).filter { it.isNotBlank() }.joinToString(" · "),
                    mine = r.mine,
                    onClickLabel = placeDetailOpenLabel(),
                ) { openPlace(r.place) }
            }
            val sub = listOfNotNull(
                r.visitNth.takeIf { it > 0 }?.let { stringResource(R.string.review_visit_nth, it) },
                agoText(r.createdMs),
            ).joinToString(" · ")
            PostHeader(r.author, sub, r.mine && !showPlace)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingStars(r.rating, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(r.rating.toString(), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold)
            }
            if (r.keywords.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.keywords.forEach { InfoChip(it, tone = ChipTone.Accent) }
                }
            }
            if (r.text.isNotBlank()) {
                Column {
                    Text(
                        r.text,
                        style = EodiTheme.type.body,
                        color = c.textPrimary,
                        maxLines = if (expanded) Int.MAX_VALUE else CollapsedLines,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded && it.hasVisualOverflow) canExpand = true },
                    )
                    if (canExpand) {
                        Text(
                            stringResource(if (expanded) R.string.review_less else R.string.review_more),
                            style = EodiTheme.type.label,
                            fontWeight = FontWeight.Bold,
                            color = c.accent,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            VisitLine(t.visit, extra = reviewVisitExtras(r), maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPlace) PlaceCouponBadge(t.offers)
                Spacer(Modifier.weight(1f))
                if (onHidden != null && !r.mine) {
                    app.eodiyeotji.ui.community.ReportButton(app.eodiyeotji.core.ReportTarget.REVIEW, r.id, onHidden)
                    Spacer(Modifier.width(12.dp))
                }
                HeartButton(r.hearts, r.heartedByMe, onHeart, compact = true)
            }
        }
    }
}

/**
 * 여행 코스 후기 카드: 장소 리뷰 카드와 같은 모양 (큰 사진 · 제목 → 글쓴이 → 들른 곳 사진 줄 → 글 → 다녀온 줄 → 쿠폰 · 하트).
 * 큰 사진은 첫 번째 들른 곳, 왼쪽 위에 "여행 코스 · N곳". 카드를 누르면 코스 상세 (지도 · 들른 곳 · 이 코스로 안내).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TripReviewCard(t: DriveCourse, onOpen: () -> Unit, onHeart: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val p = t.post
    PressSurface(
        onClick = onOpen,
        color = c.surface,
        contentColor = c.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            p.stops.firstOrNull()?.let { first ->
                FeedHero(
                    first,
                    title = p.title,
                    sub = p.region,
                    mine = p.mine,
                    label = stringResource(R.string.review_trip_badge, p.stops.size),
                    onClickLabel = stringResource(R.string.course_view),
                    onClick = onOpen,
                )
            }
            PostHeader(p.author, agoText(p.createdMs), mine = false)
            if (p.stops.isNotEmpty()) StopStrip(p.stops, onMore = onOpen, photos = p.stopPhotos)
            if (p.body.isNotBlank()) {
                Text(p.body, style = EodiTheme.type.body, color = c.textPrimary, maxLines = CollapsedLines, overflow = TextOverflow.Ellipsis)
            }
            VisitLine(t.visit, extra = listOf(distanceText(p.distanceM), durationText(p.durationMs)), maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FromRecordChip()
                CourseCouponBadge(t.offers)
                Spacer(Modifier.weight(1f))
                if (onHeart != null) HeartButton(p.hearts, p.heartedByMe, onHeart) else HeartCount(p.hearts, p.heartedByMe, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

/** 들른 곳 사진 줄 칸 수: 곳이 적어도 이만큼 칸을 나눠 사진이 너무 커지지 않게, 많으면 마지막 칸은 "+N". */
private const val StripMinSlots = 4
private const val StripMaxSlots = 5

/**
 * 들른 곳 사진 줄: 곳마다 정사각 사진 + 코랄 번호(지도 핀과 같은 색) + 아래 이름. 사진을 누르면 그곳 장소 창
 * ([onPick]이 있으면 그것을 부른다), 다 못 보여 준 곳은 "+N" (누르면 [onMore]). [selected] 곳은 파란 테두리.
 */
@Composable
internal fun StopStrip(
    stops: List<StopFact>, onMore: () -> Unit, selected: Int? = null, onPick: ((Int) -> Unit)? = null,
    /** 글쓴이가 붙인 들른 곳 사진 ([stops]와 같은 순서, 빈 문자열 = 없음) */
    photos: List<String> = emptyList(),
) {
    val c = EodiTheme.colors
    val openPlace = LocalOpenPlace.current
    val slots = stops.size.coerceIn(StripMinSlots, StripMaxSlots)
    val shown = if (stops.size <= slots) stops else stops.take(slots - 1)
    val hidden = stops.size - shown.size
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEachIndexed { i, s ->
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(shape)
                        .then(if (i == selected) Modifier.border(4.dp, c.accent, shape) else Modifier)
                        .clickable(onClickLabel = placeDetailOpenLabel(), role = Role.Button) { if (onPick != null) onPick(i) else openPlace(s) },
                ) {
                    StopPhotoBox(
                        s.name, s.address, s.lat, s.lon, order = i + 1, modifier = Modifier.fillMaxSize(), radius = 14.dp, iconSize = 36.dp,
                        localPhoto = photos.getOrNull(i)?.takeIf { it.isNotBlank() },
                    )
                    OrderDot(i + 1, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                Text(
                    s.name,
                    style = EodiTheme.type.caption,
                    fontWeight = if (i == selected) FontWeight.Bold else null,
                    color = if (i == selected) c.accent else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
        }
        if (hidden > 0) {
            val desc = stringResource(R.string.course_more_stops_description, hidden)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(c.surfaceHigh)
                    .clickable(onClickLabel = desc, role = Role.Button, onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.course_more_stops, hidden), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, color = c.textSecondary)
            }
        }
        // 곳이 적으면 빈 칸으로 채워 사진 크기를 맞춘다
        repeat(slots - shown.size - (if (hidden > 0) 1 else 0)) { Spacer(Modifier.weight(1f)) }
    }
}

/** 누를 수 없는 하트 수 ("♥ 149", 검색 결과 카드). */
/**
 * 카드 맨 위 큰 사진 (게시글처럼 카드 폭 가득, 2.1:1): [photo] 곳의 대표 사진 위 아래쪽에 [title] · [sub],
 * [label]이 있으면 왼쪽 위에 작은 띠. 누르면 [onClick] (장소 리뷰 → 장소 창, 여행 코스 후기 → 코스 상세).
 */
@Composable
internal fun FeedHero(
    photo: StopFact,
    title: String,
    sub: String,
    mine: Boolean,
    onClickLabel: String,
    label: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2.1f)
            .clip(shape)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
    ) {
        StopPhotoBox(photo.name, photo.address, photo.lat, photo.lon, order = null, modifier = Modifier.fillMaxSize(), radius = 18.dp)
        if (label != null) {
            Text(
                label,
                style = EodiTheme.type.caption,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
                .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = EodiTheme.type.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (mine) MineBadge()
                }
                if (sub.isNotBlank()) {
                    Text(sub, style = EodiTheme.type.caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 다녀온 줄 뒤에 붙는 조각: 다녀온 때 · 누구와 · 머문 시간 (모르는 것은 뺀다). */
@Composable
@ReadOnlyComposable
private fun reviewVisitExtras(r: PlaceReview): List<String> = listOfNotNull(
    r.timeOfDay?.let { timeOfDayLabel(it) },
    r.withWhom?.let { withWhomPhrase(it) },
    r.stayMin.takeIf { it > 0 }?.let { stringResource(R.string.review_stay, durationText(it * 60_000L)) },
)

/**
 * 장소 창 "이런 점이 좋았어요": 키워드마다 가로 막대 (길이 = Rust가 계산한 ratio) + 수. 키워드가 없으면 그리지 않는다.
 */
@Composable
internal fun LikedKeywords(keywords: List<KeywordCount>, voters: Int, modifier: Modifier = Modifier) {
    if (keywords.isEmpty()) return
    val c = EodiTheme.colors
    val shape = RoundedCornerShape(EodiTheme.space.radiusS)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.review_liked_title), style = EodiTheme.type.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.review_liked_voters, voters), style = EodiTheme.type.meta, color = c.textTertiary)
        }
        keywords.forEachIndexed { i, k ->
            val desc = stringResource(R.string.review_liked_row_description, k.keyword, k.count)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(shape)
                    .background(c.surfaceHigh)
                    .clearAndSetSemantics { contentDescription = desc },
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(k.ratio.coerceIn(0.03f, 1f))
                        .background(if (i == 0) c.accent.copy(alpha = 0.28f) else c.accentSoft),
                )
                Row(
                    Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(k.keyword, style = EodiTheme.type.label, color = c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(k.count.toString(), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.accent)
                }
            }
        }
    }
}

/** 리뷰 쓰기: 키워드 여러 개 고르기 ([limit]개까지, 다 고르면 나머지는 흐리게). 칩 하나 76dp. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeywordPicker(options: List<String>, picked: List<String>, limit: Int, onToggle: (String) -> Unit) {
    if (options.isEmpty()) return
    val c = EodiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.review_keywords_title, limit), style = EodiTheme.type.label, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.review_keywords_picked, picked.size, limit), style = EodiTheme.type.caption, color = c.textTertiary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { k ->
                val on = k in picked
                TogglePill(k, on, { onToggle(k) }, enabled = on || picked.size < limit, role = Role.Checkbox, minHeight = EodiTheme.space.touch)
            }
        }
    }
}

/** 리뷰 쓰기: 누구와 (하나만, 다시 누르면 풀린다). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WithWhomPicker(picked: WithWhom?, onPick: (WithWhom) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.review_with_title), style = EodiTheme.type.label, color = EodiTheme.colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WithWhom.entries.forEach { w -> TogglePill(withWhomLabel(w), w == picked, { onPick(w) }, role = Role.Checkbox, minHeight = EodiTheme.space.touch) }
        }
    }
}

@Composable
@ReadOnlyComposable
internal fun timeOfDayLabel(t: TimeOfDay): String = stringResource(
    when (t) {
        TimeOfDay.MORNING -> R.string.review_time_morning
        TimeOfDay.DAY -> R.string.review_time_day
        TimeOfDay.EVENING -> R.string.review_time_evening
        TimeOfDay.NIGHT -> R.string.review_time_night
    },
)

@Composable
@ReadOnlyComposable
internal fun withWhomLabel(w: WithWhom): String = stringResource(
    when (w) {
        WithWhom.ALONE -> R.string.review_with_alone
        WithWhom.PARTNER -> R.string.review_with_partner
        WithWhom.FAMILY -> R.string.review_with_family
        WithWhom.KIDS -> R.string.review_with_kids
        WithWhom.FRIENDS -> R.string.review_with_friends
        WithWhom.PET -> R.string.review_with_pet
    },
)

@Composable
@ReadOnlyComposable
private fun withWhomPhrase(w: WithWhom): String = stringResource(
    when (w) {
        WithWhom.ALONE -> R.string.review_with_alone_phrase
        WithWhom.PARTNER -> R.string.review_with_partner_phrase
        WithWhom.FAMILY -> R.string.review_with_family_phrase
        WithWhom.KIDS -> R.string.review_with_kids_phrase
        WithWhom.FRIENDS -> R.string.review_with_friends_phrase
        WithWhom.PET -> R.string.review_with_pet_phrase
    },
)

/** "강원 강릉시 초당동 309-1" → "강원 강릉시" (보여 주기만). */
internal fun regionOf(address: String): String = address.split(' ').take(2).joinToString(" ")
