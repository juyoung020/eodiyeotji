package app.eodiyeotji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.TimelineRoute
import java.util.Locale

/** 앱 첫 화면 (MainActivity가 부른다). 정차 중 첫 화면은 타임라인이다. */
@Composable
fun HomeRoute() {
    TimelineRoute()
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
