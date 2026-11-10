package app.eodiyeotji.ui.agent

import app.eodiyeotji.ui.community.SubTextStyle
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.common.agoText
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.ui.common.distanceText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import app.eodiyeotji.core.MemoryHit
import app.eodiyeotji.ui.map.DayMap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.review.FeedHero
import app.eodiyeotji.ui.review.StopStrip
import app.eodiyeotji.ui.place.placeDetailOpenLabel
import app.eodiyeotji.ui.theme.MemoryStamp
import app.eodiyeotji.ui.place.LocalOpenPlace
import app.eodiyeotji.ui.community.StopPhotoBox
import app.eodiyeotji.ui.share.ComposableImageShare
import app.eodiyeotji.ui.share.ShareButtonStyle
import app.eodiyeotji.ui.share.ShareImageButton
import app.eodiyeotji.ui.share.ShareTarget
import java.time.LocalDate

private val CardShape = RoundedCornerShape(32.dp)

/**
 * 추천 카드 (여행 리뷰 카드와 같은 모양): 큰 사진(고른 곳, 위에 하루 제목 · "강원 강릉시 · 4곳 · 384km · 9시간 13분",
 * 왼쪽 위 "1년 전 · 여행") → 날짜 → 들른 곳 사진 줄(번호 · 이름).
 * 사진 줄에서 고른 곳이 "여기만 가기" 대상이 되고 큰 사진도 그곳으로 바뀐다. 큰 사진이나 고른 곳을 누르면 장소 창.
 */
@Composable
internal fun RecommendationCard(
    rec: RecommendationUi,
    today: LocalDate,
    selectedStop: Int,
    onSelectStop: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val facts = rec.hit.episode.facts
    val stops = rec.stops
    val c = EodiTheme.colors
    // 들른 곳을 누르면 그곳 장소 창(사진 · 별점 · 리뷰 · 안내 · 저장)
    val openPlace = LocalOpenPlace.current
    Surface(
        color = c.surface,
        contentColor = c.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val hero = stops.getOrNull(selectedStop) ?: stops.firstOrNull()
            val title = rec.hit.episode.title ?: rec.hit.dayTitle ?: facts.ruleLabel
            val sub = listOf(
                facts.region,
                stringResource(R.string.agent_stops_count, stops.size),
                distanceText(facts.distanceM),
                durationText(facts.endMs - facts.startMs),
            ).filter { it.isNotBlank() }.joinToString(" · ")
            val map = rec.map
            if (map != null) {
                // 그때 갔던 코스가 먼저: 제목 한 줄 + 그날 달린 길 · 들른 곳 지도 (사진은 아래 들른 곳 줄에)
                Text(title, style = EodiTheme.type.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(agoText(LocalDate.parse(facts.day), today), kindText(facts.kind), sub).joinToString(" · "),
                    style = EodiTheme.type.meta,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DayMap(
                    data = map,
                    selectedKey = null,
                    onSelectKey = {},
                    modifier = Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(20.dp)),
                )
            } else if (hero != null) {
                FeedHero(
                    hero,
                    title = title,
                    sub = sub,
                    mine = false,
                    onClickLabel = placeDetailOpenLabel(),
                    label = listOf(agoText(LocalDate.parse(facts.day), today), kindText(facts.kind)).joinToString(" · "),
                    onClick = { openPlace(hero) },
                )
            } else {
                Text(title, style = EodiTheme.type.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(sub, style = EodiTheme.type.meta, color = c.textSecondary, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(EodiIcons.Calendar, null, tint = c.textTertiary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    memoryDateText(facts.day),
                    style = EodiTheme.type.meta,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 이 나들이·여행을 사진 카드로 공유 (폰 판만 보인다, ui/share)
                if (ComposableImageShare.supported) ShareImageButton(ShareTarget.Episode(facts.id), style = ShareButtonStyle.ICON)
            }
            // 왜 이 기억인지: 질문과 맞은 AI 꼬리표 (하루 라벨링 때 LLM이 붙인 태그)
            if (rec.hit.matchedTags.isNotEmpty()) AiTagRow(rec.hit.matchedTags)
            if (stops.isNotEmpty()) {
                StopStrip(
                    stops,
                    onMore = { hero?.let(openPlace) },
                    selected = selectedStop,
                    onPick = { i -> if (i == selectedStop) openPlace(stops[i]) else onSelectStop(i) },
                )
            }
        }
    }
}

/**
 * 후보 칸: 흐릿한 질문의 후보 1 · 2 · 3번을 사진 카드로 나란히 (고른 후보는 강조 테두리).
 * 누르면 새 질문 없이 그 후보가 아래 자세한 카드 · 안내 버튼이 된다. 폰 세로 화면([compact])은 옆으로 넘겨 본다.
 */
@Composable
internal fun CandidateStrip(candidates: List<MemoryHit>, focus: Int, today: LocalDate, onSelect: (Int) -> Unit, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.agent_candidates_title),
                style = EodiTheme.type.titleSmall,
                fontWeight = FontWeight.Bold,
                color = EodiTheme.colors.textPrimary,
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.agent_candidates_hint), style = EodiTheme.type.meta, color = EodiTheme.colors.textTertiary)
        }
        if (compact || candidates.size > 3) {
            // 후보가 많으면 옆으로 넘겨 본다 (개수 제한 없음)
            val w = if (compact) 220.dp else 300.dp
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min)) {
                candidates.forEachIndexed { i, hit -> CandidateTile(hit, i + 1, i == focus, today, { onSelect(i) }, Modifier.width(w).fillMaxHeight()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                candidates.forEachIndexed { i, hit ->
                    CandidateTile(hit, i + 1, i == focus, today, { onSelect(i) }, Modifier.weight(1f).fillMaxHeight())
                }
                // 후보가 둘이면 빈 칸을 남겨 카드 크기를 맞춘다
                repeat((3 - candidates.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CandidateTile(hit: MemoryHit, rank: Int, selected: Boolean, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val facts = hit.episode.facts
    val place = hit.bestStop ?: facts.stops.firstOrNull()
    val title = hit.episode.title ?: hit.dayTitle ?: facts.ruleLabel
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) c.accentSoft else c.surface,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.outline),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                StopPhotoBox(place?.name.orEmpty(), place?.address.orEmpty(), place?.lat ?: 0.0, place?.lon ?: 0.0, null, Modifier.fillMaxSize(), radius = 16.dp)
                // 후보 번호 ("2번 보여줘"와 같은 번호)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(8.dp).size(40.dp).background(if (selected) c.accent else c.textPrimary.copy(alpha = 0.72f), CircleShape),
                ) {
                    Text("$rank", style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold, color = c.onAccent)
                }
            }
            Text(
                place?.name ?: title,
                style = EodiTheme.type.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) c.accent else c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 같은 곳을 여러 날 갔으면 "4개월 전"으로는 카드가 같아 보이니 날짜로 쓴다
            val d = LocalDate.parse(facts.day)
            val date = if (d.year == today.year) {
                stringResource(R.string.community_date_md, d.monthValue, d.dayOfMonth)
            } else {
                stringResource(R.string.community_date_ymd, d.year, d.monthValue, d.dayOfMonth)
            }
            Text(
                listOf(date, facts.region).filter { it.isNotBlank() }.joinToString(" · "),
                style = EodiTheme.type.meta,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.matchedTags.isNotEmpty()) {
                Text(
                    hit.matchedTags.take(2).joinToString("  ") { "#$it" },
                    style = EodiTheme.type.meta,
                    fontWeight = FontWeight.SemiBold,
                    color = c.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "AI 꼬리표  #바다  #가족": 검색이 LLM 라벨에서 찾았다는 표시. */
@Composable
private fun AiTagRow(tags: List<String>) {
    val c = EodiTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.agent_ai_tags), style = EodiTheme.type.meta, color = c.textTertiary)
        tags.forEach { tag ->
            Surface(shape = RoundedCornerShape(50), color = c.accentSoft, contentColor = c.accent) {
                Text("#$tag", style = EodiTheme.type.meta, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
    }
}

/** 다른 후보 한 줄 ([rank]번). 누르면 그 후보로 다시 묻는다 ("2번 보여줘"). */
@Composable
internal fun OtherDayRow(rec: RecommendationUi, rank: Int, today: LocalDate, onAsk: (String) -> Unit) {
    val facts = rec.hit.episode.facts
    val title = rec.hit.episode.title ?: rec.hit.dayTitle ?: facts.ruleLabel
    val dateLine = memoryDateLine(facts.day, today)
    val question = stringResource(R.string.agent_followup_pick_candidate, rank)
    Surface(
        onClick = { onAsk(question) },
        shape = RoundedCornerShape(24.dp),
        color = EodiTheme.colors.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).background(EodiTheme.colors.surfaceHigh, CircleShape),
            ) {
                Text("$rank", style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold, color = EodiTheme.colors.textPrimary)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = EodiTheme.type.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dateLine + "  ·  " + rec.stops.joinToString(" → ") { it.name },
                    style = SubTextStyle,
                    color = EodiTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(AgentIcons.Send, contentDescription = null, tint = EodiTheme.colors.accent, modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * 주행 중 기억 답 카드: 그날 첫 들른 곳 사진 한 장 + "1년 전" 도장 + 하루 제목. 버튼 · 목록 없이 한눈에 읽는 한 장.
 */
@Composable
internal fun DrivingMemoryCard(rec: RecommendationUi, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val facts = rec.hit.episode.facts
    val today = remember { LocalDate.now() }
    val first = rec.stops.firstOrNull()
    Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceHigh, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            StopPhotoBox(first?.name.orEmpty(), first?.address.orEmpty(), first?.lat ?: 0.0, first?.lon ?: 0.0, null, Modifier.size(128.dp), radius = 22.dp)
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MemoryStamp(agoText(LocalDate.parse(facts.day), today), large = true)
                Text(
                    rec.hit.episode.title ?: rec.hit.dayTitle ?: facts.ruleLabel,
                    style = EodiTheme.type.drivingHeadline,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
