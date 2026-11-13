package app.eodiyeotji.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
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
    // 좁은 창(3분할 세로 창 등)에서는 창 폭 − 여백, 넓은 창에서는 가운데 최대 1100dp.
    // 위쪽 자막([SpokenCaption]) 자리는 비워 두고, 창 밖으로 나가지 않게 높이를 창 안으로 묶는다.
    BoxWithConstraints(Modifier.matchParentSize().safeDrawingPadding()) {
        val narrow = maxWidth < NARROW_DP
        val margin = if (narrow) 16.dp else 24.dp
        // 반투명하게 겹쳐 보이지 않게 투명도 애니메이션 없이 밀어 올리기만 한다
        AnimatedVisibility(
            visible = ui != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = margin, end = margin, bottom = margin)
                .heightIn(max = (maxHeight - CAPTION_RESERVE_DP - margin).coerceAtLeast(160.dp)),
        ) {
            shown?.let { Card(it, narrow, viewModel::yes, viewModel::no, viewModel::close) }
        }
    }
}

private val NARROW_DP = 1000.dp
/** 위쪽 자막 한 줄 + 여백 높이 (자막과 겹치지 않게 비워 둔다). */
private val CAPTION_RESERVE_DP = 120.dp

@Composable
private fun Card(ui: ProactiveUi, narrow: Boolean, onYes: () -> Unit, onNo: () -> Unit, onClose: () -> Unit) {
    val s = ui.suggestion
    val c = EodiTheme.colors
    Surface(
        shape = RoundedCornerShape(if (narrow) 28.dp else 36.dp),
        // 불투명 바탕 + 테두리 + 그림자: 아래 목록 글자와 섞여 보이지 않게
        color = c.surface,
        border = BorderStroke(2.dp, c.outline),
        tonalElevation = 6.dp,
        shadowElevation = 24.dp,
        modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = if (narrow) 24.dp else 40.dp, vertical = if (narrow) 22.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 14.dp),
        ) {
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
                style = if (narrow) EodiTheme.type.title else EodiTheme.type.drivingBody,
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
        color = if (primary) c.accent else c.surfaceHigh,
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
