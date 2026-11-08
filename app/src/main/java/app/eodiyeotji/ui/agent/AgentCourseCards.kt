package app.eodiyeotji.ui.agent

import app.eodiyeotji.ui.common.distanceText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.eodiyeotji.core.DriveRouteItem
import app.eodiyeotji.ui.community.DriveRouteCard
import app.eodiyeotji.ui.community.HeartCount
import app.eodiyeotji.ui.map.RouteSnapshotImage

// 근처 추천 드라이브 길 카드 ("여기 근처 드라이브 추천코스"). 순위·거리·하트는 Rust가 끝내고 오고, 여기는 받은 순서대로 그리기만 한다.
// 정차 중 카드는 드라이브 코스 탭과 같은 글 카드(community/DriveRoutePane.kt DriveRouteCard), 주행 중에는 큰 카드 하나.

/**
 * 정차 중: 드라이브 코스 탭과 같은 글 카드 목록 (3D 대표 그림 · 순위 · 제목 · 소개 · 시작 → 끝 · 정보 칩 · 하트 수).
 * 카드를 누르면 "N번으로 안내해줘"로 이어서 묻는다 (안내는 제안 → 확인 뒤에만).
 */
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
            DriveRouteCard(
                DriveRouteItem(c.route, c.distanceKm),
                onOpen = { onAsk(question) },
                onHeart = null,
                rank = c.rank,
                focused = i == turn.courseFocus,
                action = stringResource(R.string.agent_pick_course, c.rank),
            )
        }
    }
}

/** 주행 중 카드의 큰 숫자 줄: "하트 486   여기서 4.1km" */
@Composable
private fun CourseNumbers(c: RecommendedCourse) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.headlineSmall
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        HeartCount(c.route.hearts, c.route.heartedByMe, large = true)
        c.distanceKm?.let {
            Text(stringResource(R.string.agent_course_from_here, distanceText(it * 1000.0)), style = style, fontWeight = FontWeight.SemiBold, color = colors.primary)
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
                CourseNumbers(c)
            }
        }
    }
}
