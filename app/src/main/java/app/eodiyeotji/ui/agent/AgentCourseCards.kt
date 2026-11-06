package app.eodiyeotji.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eodiyeotji.R
import app.eodiyeotji.agent.RecommendedCourse
import app.eodiyeotji.core.DriveRoute
import app.eodiyeotji.ui.community.BestTimeBadge
import app.eodiyeotji.ui.community.visitText
import app.eodiyeotji.ui.map.RouteSnapshotImage

// 근처 추천 드라이브 길 카드 ("여기 근처 드라이브 추천코스"). 순위·거리·하트는 Rust가 끝내고 오고, 여기는 받은 순서대로 그리기만 한다.
// 드라이브 코스는 들를 곳 없는 경치 길이라 카드에는 들른 곳 대신 길의 3D 미리보기 그림(스냅숏)과 시작 → 끝을 보여 준다.

/** 다녀온 날 · 계절 · 날씨 한 줄 (값이 있을 때만). 예: "10월 18일 · 가을 단풍철 · 맑음 14°C". */
@androidx.compose.runtime.Composable
internal fun routeWhenLine(route: DriveRoute): String? =
    if (route.visit.day.isBlank() && route.visit.season.isBlank()) null else visitText(route.visit)

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

@Composable
private fun CourseRecommendationCard(c: RecommendedCourse, focused: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val r = c.route
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (focused) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLow,
        border = if (focused) BorderStroke(3.dp, colors.primary) else null,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            // 길 미리보기 (3D 스냅숏) 위에 순위
            Box(Modifier.size(width = 236.dp, height = 136.dp).clip(RoundedCornerShape(20.dp))) {
                RouteSnapshotImage(r, stringResource(R.string.drive_preview_description, r.title), Modifier.matchParentSize())
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { RankBadge(c.rank, focused, size = 44) }
            }
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                if (focused) {
                    Text(
                        stringResource(R.string.agent_course_focus),
                        style = AgentSubStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                    )
                }
                Text(r.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.drive_from_to, r.startName, r.endName),
                    style = AgentSubStyle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                CourseNumbers(c)
                Text(
                    listOfNotNull(stringResource(R.string.agent_course_by, r.author), routeWhenLine(r)).joinToString("  ·  "),
                    style = AgentSubStyle,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                BestTimeBadge(r.bestTime, r.theme)
            }
        }
    }
}

/** "♥ 486   여기서 4.1km   22km · 25분" */
@Composable
private fun CourseNumbers(c: RecommendedCourse, large: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.agent_course_hearts, c.route.hearts), style = style, fontWeight = FontWeight.Bold, color = app.eodiyeotji.ui.theme.EodiTheme.colors.heart)
        c.distanceKm?.let {
            Text(stringResource(R.string.agent_course_from_here, kmText(it * 1000.0)), style = style, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
        if (!large) {
            Text(
                listOf(kmText(c.route.distanceM), spanText(c.route.durationMs)).joinToString(" · "),
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

/** 주행 중: 지금 제안한 길 하나만 크게 (미리보기 그림 · 순위 · 이름 · 하트 · 거리). 목록·움직이는 지도는 없다. */
@Composable
internal fun DrivingCourseCard(c: RecommendedCourse, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            Box(Modifier.size(width = 196.dp, height = 112.dp).clip(RoundedCornerShape(24.dp))) {
                RouteSnapshotImage(c.route, stringResource(R.string.drive_preview_description, c.route.title), Modifier.matchParentSize())
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { RankBadge(c.rank, focused = true, size = 44) }
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(c.route.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                CourseNumbers(c, large = true)
            }
        }
    }
}
