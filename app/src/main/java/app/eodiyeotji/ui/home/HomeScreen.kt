package app.eodiyeotji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.ui.agent.AgentEntryButton
import app.eodiyeotji.ui.agent.AgentPanel
import app.eodiyeotji.ui.agent.AgentViewModel
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.TimelineRoute
import java.util.Locale

/**
 * 앱 첫 화면 (MainActivity가 부른다).
 * - 정차 중: 타임라인. 머리의 "어디였지? 물어보세요"를 누르면 기억 에이전트가 위에 덮인다.
 * - 주행 중: 타임라인(긴 목록)을 감추고 기억 에이전트 주행 화면(음성 + 큰 카드 1장)만 보인다.
 */
@Composable
fun HomeRoute(agentViewModel: AgentViewModel = hiltViewModel()) {
    val agentState by agentViewModel.state.collectAsStateWithLifecycle()
    var agentOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (agentState.isDriving) {
            AgentPanel(onClose = null, viewModel = agentViewModel)
        } else {
            TimelineRoute(headerAction = { AgentEntryButton(onClick = { agentOpen = true }, Modifier.widthIn(max = 420.dp)) })
            if (agentOpen) AgentPanel(onClose = { agentOpen = false }, viewModel = agentViewModel)
        }
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
