package app.eodiyeotji.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import java.text.NumberFormat

@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, onRecheck = viewModel::check)
}

/** 뼈대 단계 첫 화면: 앱이 뜨고 Rust 코어 결과가 보이는지 확인한다. 창 폭이 바뀌어도 가운데 정렬된 카드 하나만 쓴다. */
@Composable
fun HomeScreen(state: CoreUiState, onRecheck: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoreStatusText(state)
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRecheck,
                enabled = state !is CoreUiState.Checking,
                // 차량 터치 기준: 76dp 이상
                modifier = Modifier.heightIn(min = 76.dp).widthIn(min = 240.dp),
            ) {
                Text(stringResource(R.string.core_recheck), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun CoreStatusText(state: CoreUiState) {
    val big = MaterialTheme.typography.headlineSmall
    val body = MaterialTheme.typography.titleLarge
    when (state) {
        CoreUiState.Checking -> Text(stringResource(R.string.core_checking), style = big)
        is CoreUiState.Failed -> Text(
            stringResource(R.string.core_failed, state.message),
            style = big,
            color = MaterialTheme.colorScheme.error,
        )
        is CoreUiState.Ready -> {
            val s = state.status
            val nf = NumberFormat.getIntegerInstance()
            Text(stringResource(R.string.core_connected, s.version), style = big)
            Text(stringResource(R.string.core_simplify, nf.format(s.pointsIn), nf.format(s.pointsOut)), style = body)
            Text(stringResource(R.string.core_elapsed, "%.2f".format(s.elapsedMicros / 1000.0)), style = body)
        }
    }
}
