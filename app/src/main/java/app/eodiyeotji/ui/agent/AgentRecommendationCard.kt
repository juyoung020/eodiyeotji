package app.eodiyeotji.ui.agent

import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.common.agoText
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.ui.common.distanceText
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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
            if (hero != null) {
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

/** "다른 날" 한 줄. 누르면 그날로 다시 묻는다. */
@Composable
internal fun OtherDayRow(rec: RecommendationUi, today: LocalDate, onAsk: (String) -> Unit) {
    val facts = rec.hit.episode.facts
    val title = rec.hit.episode.title ?: rec.hit.dayTitle ?: facts.ruleLabel
    val dateLine = memoryDateLine(facts.day, today)
    val question = stringResource(R.string.agent_followup_pick_day, memoryDateText(facts.day), title)
    Surface(
        onClick = { onAsk(question) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            ) {
                Icon(EodiIcons.Calendar, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dateLine + "  ·  " + rec.stops.joinToString(" → ") { it.name },
                    style = AgentSubStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(AgentIcons.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
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
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
