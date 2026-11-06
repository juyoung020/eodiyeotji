package app.eodiyeotji.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eodiyeotji.R
import app.eodiyeotji.agent.RecommendedCourse
import app.eodiyeotji.core.CoursePost
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.ui.offers.CourseCouponBadge
import app.eodiyeotji.ui.place.PlaceThumbnail

// 근처 추천 코스 카드 ("여기 근처 드라이브 추천코스"). 순위·거리·하트는 Rust가 끝내고 오고, 여기는 받은 순서대로 그리기만 한다.

private const val MAX_STOP_CHIPS = 4

/**
 * 다녀온 날 · 계절 · 날씨 한 줄 (값이 있을 때만). 예: "2025년 10월 18일 · 가을 · 단풍철 · 맑음 · 14°C".
 * 통합 지점: CoursePost에 visitedDay·season·weather가 생기면 여기 한 곳에서 채운다 (지금 계약에는 아직 없다).
 */
@Suppress("UNUSED_PARAMETER")
@androidx.compose.runtime.Composable
internal fun courseWhenLine(post: CoursePost): String? =
    if (post.visitedDay.isBlank() && post.season.isBlank()) null
    else app.eodiyeotji.ui.community.visitText(app.eodiyeotji.core.Visit(post.visitedDay, post.season, post.weather))

/** 정차 중: 순위 카드 목록. 카드를 누르면 "N번으로 안내해줘"로 이어서 묻는다 (안내는 제안 → 확인 뒤에만). */
@Composable
internal fun CourseRecommendationList(turn: AgentTurn, onAsk: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        Text(
            stringResource(R.string.agent_courses_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        turn.courses.forEachIndexed { i, c ->
            val question = stringResource(R.string.agent_followup_pick_course, c.rank)
            CourseRecommendationCard(c, focused = i == turn.courseFocus, onClick = { onAsk(question) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseRecommendationCard(c: RecommendedCourse, focused: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val post = c.post
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (focused) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLow,
        border = if (focused) BorderStroke(3.dp, colors.primary) else null,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            RankBadge(c.rank, focused)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                if (focused) {
                    Text(
                        stringResource(R.string.agent_course_focus),
                        style = AgentSubStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                    )
                }
                Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height4())
                Text(
                    listOf(stringResource(R.string.agent_course_by, post.author), post.region).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = AgentSubStyle,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height4())
                CourseNumbers(c)
                CourseCouponBadge(c.offers)
                courseWhenLine(post)?.let {
                    Text(it, style = AgentSubStyle, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (post.stops.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        post.stops.take(MAX_STOP_CHIPS).forEachIndexed { i, s -> CourseStopChip(i + 1, s) }
                        val more = post.stops.size - MAX_STOP_CHIPS
                        if (more > 0) {
                            Text(
                                stringResource(R.string.agent_course_more_stops, more),
                                style = AgentSubStyle,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "♥ 312   여기서 18km   92km · 5시간 30분" */
@Composable
private fun CourseNumbers(c: RecommendedCourse, large: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.agent_course_hearts, c.post.hearts), style = style, fontWeight = FontWeight.Bold, color = app.eodiyeotji.ui.theme.EodiTheme.colors.heart)
        c.distanceKm?.let {
            Text(stringResource(R.string.agent_course_from_here, kmText(it * 1000.0)), style = style, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
        if (!large) {
            Text(
                listOf(kmText(c.post.distanceM), spanText(c.post.durationMs)).joinToString(" · "),
                style = AgentSubStyle,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int, focused: Boolean, size: Int = 56) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).background(if (focused) colors.primary else colors.secondaryContainer, CircleShape),
    ) {
        Text(
            rank.toString(),
            fontSize = (size * 0.45f).sp,
            fontWeight = FontWeight.Bold,
            color = if (focused) colors.onPrimary else colors.onSecondaryContainer,
        )
    }
}

/** 들른 곳 칩: [사진 ① 두물머리]. 카드 전체를 누르므로 칩은 누르지 않는다. */
@Composable
private fun CourseStopChip(number: Int, stop: StopFact) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = colors.surfaceContainerHighest) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp)) {
            Box(Modifier.size(44.dp)) {
                PlaceThumbnail(
                    stop.name, stop.address, stop.lat, stop.lon,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    fallback = { Box(Modifier.size(44.dp).background(colors.primary.copy(alpha = 0.25f), CircleShape)) },
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).size(22.dp).background(app.eodiyeotji.ui.theme.EodiTheme.colors.stay, CircleShape),
                ) {
                    Text(number.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = app.eodiyeotji.ui.theme.EodiTheme.colors.onMarker)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(stop.name, style = AgentSubStyle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 주행 중: 지금 제안한 코스 하나만 크게 (사진 · 순위 · 이름 · 하트 · 거리). 목록·칩은 없다. */
@Composable
internal fun DrivingCourseCard(c: RecommendedCourse, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            val first = c.post.stops.firstOrNull()
            Box(Modifier.size(112.dp)) {
                if (first != null) {
                    PlaceThumbnail(
                        first.name, first.address, first.lat, first.lon,
                        modifier = Modifier.size(112.dp).clip(RoundedCornerShape(24.dp)),
                        fallback = { Box(Modifier.size(112.dp).background(colors.primaryContainer, RoundedCornerShape(24.dp))) },
                    )
                }
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { RankBadge(c.rank, focused = true, size = 44) }
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(c.post.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height4())
                CourseNumbers(c, large = true)
            }
        }
    }
}

private fun Modifier.height4() = padding(top = 4.dp)
