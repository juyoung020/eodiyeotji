package app.eodiyeotji.ui.agent

import app.eodiyeotji.ui.common.eodiShadow
import app.eodiyeotji.ui.common.PressSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import app.eodiyeotji.ui.common.EodiLoader
import app.eodiyeotji.ui.common.rememberHaptics
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
    val speaking: StateFlow<Boolean> = center.speaking

    fun yes() = center.accept()
    fun no() = center.decline()
    fun close() = center.dismiss()
}

/**
 * 에이전트가 먼저 거는 말 (출발 · 잔량 · 도착) = Gleo "proactive suggestion" 패널 (Pleos 가이드 Experiences/gleo-ai.md Layout):
 * - Chrome 자리: 상태 알약 ([GleoStatusChip] — 말하는 중 · 대답을 기다려요 · 생각 중 · 대기 중)
 * - Bubble: Gleo가 한 말 (큰 글)
 * - Panel Template(card): 무슨 일인지 · 덧붙임 한 줄
 * - Chips: [그래] [아니] (회상 · 끝난 카드는 [닫기])
 * 패널 바깥을 누르거나 패널을 위로 끌면 대화를 끝낸다 (Panel Interaction). 주행 중에도 이 카드 한 장 + 음성뿐이다.
 */
@Composable
fun BoxScope.ProactiveCard(viewModel: ProactiveViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val speaking by viewModel.speaking.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<ProactiveUi?>(null) }
    ui?.let { shown = it }
    // 바깥 누름 = 대화 끝 (카드가 떠 있을 때만 막을 깐다. 어둡게 하지 않는다 — 아래 화면을 계속 보이게)
    if (ui != null) {
        Box(
            Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.gleo_dismiss),
                onClick = viewModel::close,
            ),
        )
    }
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
            shown?.let { Card(it, speaking, narrow, viewModel::yes, viewModel::no, viewModel::close) }
        }
    }
}

private val NARROW_DP = 1000.dp
/** 위쪽 자막 한 줄 + 여백 높이 (자막과 겹치지 않게 비워 둔다). */
private val CAPTION_RESERVE_DP = 120.dp
/** 위로 이만큼 끌면 대화 끝. */
private val SWIPE_DISMISS_DP = 96.dp

@Composable
private fun Card(ui: ProactiveUi, speaking: Boolean, narrow: Boolean, onYes: () -> Unit, onNo: () -> Unit, onClose: () -> Unit) {
    val s = ui.suggestion
    val c = EodiTheme.colors
    val shape = RoundedCornerShape(if (narrow) 28.dp else 36.dp)
    val dismissPx = with(LocalDensity.current) { SWIPE_DISMISS_DP.toPx() }
    var drag by remember(ui.id) { mutableFloatStateOf(0f) }
    val haptics = rememberHaptics()
    Surface(
        shape = shape,
        // 불투명 바탕 + 테두리 + 그림자(가이드 3단계 중 가장 높은 판): 아래 목록 글자와 섞여 보이지 않게
        color = c.surface,
        border = BorderStroke(2.dp, c.outline),
        modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth()
            .graphicsLayer { translationY = drag }
            .pointerInput(ui.id) {
                // 위로 끌기 = 대화 끝 (아래로 끄는 것은 Gleo 앱 전환이라 우리 앱에는 없다 → 무시)
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag < -dismissPx) {
                            haptics.tap()
                            onClose()
                        }
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, dy -> drag = (drag + dy).coerceAtMost(0f) }
            }
            .eodiShadow(EodiTheme.elevation.level3, shape)
            // 카드 안 누름은 바깥 누름(닫기)으로 새지 않게
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.padding(horizontal = if (narrow) 24.dp else 40.dp, vertical = if (narrow) 22.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
        ) {
            // Chrome 자리: 무슨 계기인지 + Gleo 상태
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = c.accent, contentColor = c.onAccent) {
                    Text(
                        stringResource(badge(s.kind)),
                        style = EodiTheme.type.label,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
                GleoStatusChip(gleoStatus(ui, speaking, ui.standby))
            }
            // Bubble: Gleo가 한 말
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                color = c.accentSoft,
                contentColor = c.textPrimary,
            ) {
                Text(
                    s.line,
                    style = if (narrow) EodiTheme.type.title else EodiTheme.type.drivingBody,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            // Panel Template(card): 무슨 일 · 덧붙임 / 진행 상태
            val sub = when (ui.phase) {
                ProactiveUi.Phase.Listening -> stringResource(R.string.proactive_listening)
                ProactiveUi.Phase.Running -> stringResource(R.string.proactive_running)
                ProactiveUi.Phase.Done, ProactiveUi.Phase.Failed -> ui.result?.let { stringResource(it) } ?: s.detail
                ProactiveUi.Phase.Asking -> s.detail
            }
            Surface(shape = RoundedCornerShape(24.dp), color = c.surfaceHigh, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.title, style = EodiTheme.type.titleSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            sub,
                            style = EodiTheme.type.bodyStrong,
                            color = if (ui.phase == ProactiveUi.Phase.Listening || ui.phase == ProactiveUi.Phase.Done) c.accent else c.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (ui.phase == ProactiveUi.Phase.Running) EodiLoader(size = 48.dp)
                }
            }
            // Chips: 선택지
            val finished = ui.phase == ProactiveUi.Phase.Done || ui.phase == ProactiveUi.Phase.Failed
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (s.ask && !finished) {
                    val running = ui.phase == ProactiveUi.Phase.Running
                    GleoChip(stringResource(R.string.proactive_yes), primary = true, loading = running, onClick = onYes, modifier = Modifier.weight(1f))
                    GleoChip(stringResource(R.string.proactive_no), primary = false, loading = false, enabled = !running, onClick = onNo, modifier = Modifier.weight(1f))
                } else {
                    GleoChip(stringResource(R.string.proactive_close), primary = false, loading = false, onClick = onClose, modifier = Modifier.weight(1f), close = true)
                }
            }
        }
    }
}

/** Gleo Chips 버튼 (터치 96dp). [loading]이면 버튼 안 로더 + 누름 막기 (가이드 Basic button Loading). */
@Composable
internal fun GleoChip(
    text: String,
    primary: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    close: Boolean = false,
) {
    val c = EodiTheme.colors
    PressSurface(
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        shape = CircleShape,
        color = if (primary) c.accent else c.surfaceHigh,
        contentColor = if (primary) c.onAccent else c.textPrimary,
        border = if (primary) null else BorderStroke(2.dp, c.outline),
        modifier = modifier.heightIn(min = 96.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            if (loading) {
                EodiLoader(size = 40.dp)
                Spacer(Modifier.width(12.dp))
            } else if (close) {
                Icon(EodiIcons.Close, contentDescription = null, modifier = Modifier.size(32.dp).padding(end = 6.dp))
            }
            Text(text, style = EodiTheme.type.drivingBody, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun badge(k: ProactiveKind) = when (k) {
    ProactiveKind.ROUTINE -> R.string.proactive_badge_routine
    ProactiveKind.CHARGE -> R.string.proactive_badge_charge
    ProactiveKind.RECALL -> R.string.proactive_badge_recall
    ProactiveKind.BIZ -> R.string.proactive_badge_biz
}
