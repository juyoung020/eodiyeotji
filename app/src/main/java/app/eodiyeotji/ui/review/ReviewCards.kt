package app.eodiyeotji.ui.review

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import app.eodiyeotji.core.WithWhom
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.HeartButton
import app.eodiyeotji.ui.community.MineBadge
import app.eodiyeotji.ui.community.PostHeader
import app.eodiyeotji.ui.community.RatingStars
import app.eodiyeotji.ui.community.StopPhoto
import app.eodiyeotji.ui.community.VisitLine
import app.eodiyeotji.ui.community.agoText
import app.eodiyeotji.ui.offers.PlaceCouponBadge
import app.eodiyeotji.ui.place.LocalOpenPlace
import app.eodiyeotji.ui.place.placeDetailOpenLabel
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.durationText

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
internal fun RichReviewCard(t: TravelReview, onHeart: () -> Unit, modifier: Modifier = Modifier, showPlace: Boolean = true) {
    val c = EodiTheme.colors
    val r = t.review
    var expanded by rememberSaveable(r.id) { mutableStateOf(false) }
    // 접힌 글이 넘친 적이 있으면 "더보기 / 접기"를 보인다
    var canExpand by rememberSaveable(r.id) { mutableStateOf(false) }
    Surface(
        onClick = { if (canExpand) expanded = !expanded },
        color = if (showPlace) c.surface else c.surfaceHigh,
        contentColor = c.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showPlace) ReviewPlaceRow(r)
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
                    r.keywords.forEach { KeywordChip(it) }
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
                HeartButton(r.hearts, r.heartedByMe, onHeart, compact = true)
            }
        }
    }
}

/** 카드 맨 위 장소 줄 (사진 · 이름 · 분류 · 지역). 누르면 장소 상세 창. 누르는 자리 76dp 이상. */
@Composable
private fun ReviewPlaceRow(r: PlaceReview) {
    val c = EodiTheme.colors
    val openPlace = LocalOpenPlace.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EodiTheme.space.radiusM))
            .clickable(onClickLabel = placeDetailOpenLabel(), role = Role.Button) { openPlace(r.place) }
            .heightIn(min = EodiTheme.space.touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StopPhoto(r.place.name, r.place.address, r.place.lat, r.place.lon, order = null, size = 96.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.place.name,
                    style = EodiTheme.type.bodyStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (r.mine) MineBadge()
            }
            Text(
                listOf(r.place.category, regionOf(r.place.address)).filter { it.isNotBlank() }.joinToString(" · "),
                style = EodiTheme.type.caption,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

/** 옅은 키워드 칩 ("뷰가 좋아요"). 보여 주기만 한다 (누르지 않는다). */
@Composable
internal fun KeywordChip(text: String, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    Text(
        text,
        style = EodiTheme.type.caption,
        color = c.accent,
        maxLines = 1,
        modifier = modifier.background(c.accentSoft, CircleShape).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

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
                SelectChip(k, on, enabled = on || picked.size < limit) { onToggle(k) }
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
            WithWhom.entries.forEach { w -> SelectChip(withWhomLabel(w), w == picked, enabled = true) { onPick(w) } }
        }
    }
}

/** 고르는 칩 (켜지면 파란 테두리 · 옅은 파랑). 누르는 자리 76dp (Pleos 터치 크기). */
@Composable
private fun SelectChip(text: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (on) c.accentSoft else c.surfaceHigh,
        contentColor = when {
            on -> c.accent
            enabled -> c.textPrimary
            else -> c.textTertiary
        },
        border = BorderStroke(2.dp, if (on) c.accent else Color.Transparent),
        modifier = Modifier.heightIn(min = EodiTheme.space.touch).semantics { role = Role.Checkbox; selected = on },
    ) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (on) {
                Icon(CommunityIcons.Check, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = EodiTheme.type.label, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
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
