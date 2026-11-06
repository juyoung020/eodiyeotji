package app.eodiyeotji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.ui.agent.AgentEntryButton
import app.eodiyeotji.ui.agent.AgentPanel
import app.eodiyeotji.ui.agent.AgentViewModel
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.CommunityRoute
import app.eodiyeotji.ui.theme.BrandMark
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.TimelineRoute
import java.util.Locale

/** 왼쪽 레일 목적지. */
enum class HomeTab { TIMELINE, COMMUNITY }

/**
 * 앱 첫 화면 (MainActivity가 부른다).
 * - 정차 중: 왼쪽 레일 [내 기록 | 커뮤니티] + 고른 화면. 머리의 "어디였지? 물어보세요"를 누르면 기억 에이전트가 위에 덮인다.
 * - 주행 중: 레일·타임라인·커뮤니티(긴 목록·피드)를 모두 감추고 기억 에이전트 주행 화면(음성 + 큰 카드 1장)만 보인다.
 */
@Composable
fun HomeRoute(agentViewModel: AgentViewModel = hiltViewModel(), guideViewModel: CourseGuideViewModel = hiltViewModel()) {
    // 코스 안내 이어 가기(CourseGuide)는 주행 중에도 살아 있어야 한다 → 화면 갈래와 상관없이 여기서 만든다
    // (운전 중 "응, 안내해 줘"로 시작한 에이전트 코스도 도착하면 다음 구간으로 이어진다)
    val agentState by agentViewModel.state.collectAsStateWithLifecycle()
    var agentOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.TIMELINE) }
    Box(Modifier.fillMaxSize()) {
        if (agentState.isDriving && agentViewModel.drivingLocksScreen) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { AgentPanel(onClose = null, viewModel = agentViewModel) }
                // 주행 중에는 버튼 없이 진행 상황 한 줄만 (몇 번째 구간 · 다음 들를 곳)
                CourseGuideBanner(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                    viewModel = guideViewModel,
                    interactive = false,
                )
            }
        } else {
            val entry: @Composable () -> Unit = { AgentEntryButton(onClick = { agentOpen = true }) }
            Row(Modifier.fillMaxSize().background(EodiTheme.colors.background)) {
                NavRail(tab, onSelect = { tab = it })
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            HomeTab.TIMELINE -> TimelineRoute(headerAction = entry)
                            HomeTab.COMMUNITY -> CommunityRoute(headerAction = entry)
                        }
                    }
                    // 코스 안내 중이면 어느 탭에서든 맨 아래에 진행 카드 (내비에서 돌아왔을 때 몇 번째 구간인지 바로 보이게).
                    // 화면을 덮지 않고 그만큼 위 내용이 줄어든다.
                    CourseGuideBanner(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp), viewModel = guideViewModel)
                }
            }
            if (agentOpen) AgentPanel(onClose = { agentOpen = false; agentViewModel.clear() }, viewModel = agentViewModel) // 닫으면 대화를 비워 다음엔 새 질문부터
        }
    }
}

/** 왼쪽 레일: 앱 표시(BrandMark) + 목적지 2개 (한 칸 112dp, 아이콘 + 이름). 좁은 창(약 840dp)에서도 폭 120dp만 쓴다. */
@Composable
private fun NavRail(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val c = EodiTheme.colors
    Column(
        Modifier
            .fillMaxHeight()
            .width(120.dp)
            .background(c.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .padding(vertical = 24.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 앱 표시 (앱 아이콘을 줄인 모양): 어느 탭에서든 맨 위에
        BrandMark(Modifier.padding(top = 4.dp, bottom = 20.dp), size = 64.dp)
        RailItem(CommunityIcons.Clock, stringResource(R.string.nav_timeline), selected == HomeTab.TIMELINE) { onSelect(HomeTab.TIMELINE) }
        RailItem(CommunityIcons.People, stringResource(R.string.nav_community), selected == HomeTab.COMMUNITY) { onSelect(HomeTab.COMMUNITY) }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(EodiTheme.space.radiusM))
            .background(if (selected) c.accentSoft else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) c.accent else c.textSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = EodiTheme.type.caption,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) c.accent else c.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** 개발용 Rust 코어 상태 한 줄: 버전 + 하루 경로(점 1만 개) 단순화 시간. 디버그 빌드에서만 머리 구석에 작게 둔다. */
@Composable
fun CoreStatusLine(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CoreStatusText(state, modifier)
}

@Composable
fun CoreStatusText(state: CoreUiState, modifier: Modifier = Modifier) {
    val text = when (state) {
        CoreUiState.Checking -> stringResource(R.string.core_checking)
        is CoreUiState.Failed -> stringResource(R.string.core_failed, state.message)
        is CoreUiState.Ready -> stringResource(
            R.string.core_status_line,
            state.status.version,
            String.format(Locale.ROOT, "%.2f", state.status.elapsedMicros / 1000.0),
        )
    }
    val c = EodiTheme.colors
    Text(
        text,
        style = EodiTheme.type.caption,
        color = if (state is CoreUiState.Failed) c.error else c.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(c.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
