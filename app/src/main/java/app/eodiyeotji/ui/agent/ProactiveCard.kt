package app.eodiyeotji.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.agent.ProactiveCenter
import app.eodiyeotji.agent.ProactiveUi
import app.eodiyeotji.core.rust.ProactiveKind
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** 먼저 말 걸기 카드 (계산 없음: [ProactiveCenter] 상태를 그리고 버튼을 넘긴다). */
@HiltViewModel
class ProactiveViewModel @Inject constructor(private val center: ProactiveCenter) : ViewModel() {
    val state: StateFlow<ProactiveUi?> = center.state

    fun yes() = center.accept()
    fun no() = center.decline()
    fun close() = center.dismiss()
}

/**
 * 에이전트가 먼저 거는 말 한 장 (출발 · 잔량 · 도착). 모든 화면 위 아래쪽 가운데에 큰 카드 하나:
 * 무슨 일인지(작은 글) · 말한 문장(큰 글) · 덧붙임 한 줄 · [예] [아니요] (회상은 [닫기]).
 * 주행 중에도 이 카드 한 장 + 음성뿐이다. 버튼은 Pleos 터치 크기(76dp) 이상.
 */
@Composable
fun BoxScope.ProactiveCard(viewModel: ProactiveViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<ProactiveUi?>(null) }
    ui?.let { shown = it }
    AnimatedVisibility(
        visible = ui != null,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        shown?.let { Card(it, viewModel::yes, viewModel::no, viewModel::close) }
    }
}

@Composable
private fun Card(ui: ProactiveUi, onYes: () -> Unit, onNo: () -> Unit, onClose: () -> Unit) {
    val s = ui.suggestion
    val c = EodiTheme.colors
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = c.surfaceHigh,
        shadowElevation = 16.dp,
        modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 40.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = c.accent, contentColor = c.onAccent) {
                    Text(
                        stringResource(badge(s.kind)),
                        style = EodiTheme.type.label,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
                Text(s.title, style = EodiTheme.type.titleSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                s.line,
                style = EodiTheme.type.drivingBody,
                color = c.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            val sub = when (ui.phase) {
                ProactiveUi.Phase.Listening -> stringResource(R.string.proactive_listening)
                ProactiveUi.Phase.Running -> stringResource(R.string.proactive_running)
                ProactiveUi.Phase.Done, ProactiveUi.Phase.Failed -> ui.result?.let { stringResource(it) } ?: s.detail
                ProactiveUi.Phase.Asking -> s.detail
            }
            Text(
                sub,
                style = EodiTheme.type.bodyStrong,
                color = if (ui.phase == ProactiveUi.Phase.Listening || ui.phase == ProactiveUi.Phase.Done) c.accent else c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val finished = ui.phase == ProactiveUi.Phase.Done || ui.phase == ProactiveUi.Phase.Failed
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (s.ask && !finished) {
                    BigButton(stringResource(R.string.proactive_yes), primary = true, enabled = ui.phase != ProactiveUi.Phase.Running, onClick = onYes, modifier = Modifier.weight(1f))
                    BigButton(stringResource(R.string.proactive_no), primary = false, enabled = ui.phase != ProactiveUi.Phase.Running, onClick = onNo, modifier = Modifier.weight(1f))
                } else {
                    BigButton(stringResource(R.string.proactive_close), primary = false, enabled = true, onClick = onClose, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BigButton(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = if (primary) c.accent else c.surface,
        contentColor = if (primary) c.onAccent else c.textPrimary,
        modifier = modifier.heightIn(min = 96.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            if (!primary && text == stringResource(R.string.proactive_close)) {
                Icon(EodiIcons.Close, contentDescription = null, modifier = Modifier.size(32.dp).padding(end = 6.dp))
            }
            Text(text, style = EodiTheme.type.drivingBody, fontWeight = FontWeight.Bold)
        }
    }
}

private fun badge(k: ProactiveKind) = when (k) {
    ProactiveKind.ROUTINE -> R.string.proactive_badge_routine
    ProactiveKind.CHARGE -> R.string.proactive_badge_charge
    ProactiveKind.RECALL -> R.string.proactive_badge_recall
    ProactiveKind.BIZ -> R.string.proactive_badge_biz
}
