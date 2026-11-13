package app.eodiyeotji.ui.common

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.Modifier.Node
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// 누름 반응 한 벌 (Pleos 가이드 foundations/interaction.md "Button interaction" · Introduction "Icons & Feedback").
// - 누르는 동안 95%로 줄고 배경이 살짝 진해진다. 손가락이 영역 밖으로 나가면 Compose가 PressInteraction.Cancel을 보내
//   원래 크기로 돌아오고 눌리지 않는다 (clickable 기본 동작).
// - 누르면 가벼운 진동(햅틱) — 화면을 보지 않아도 눌렸는지 안다.
// - 같은 버튼을 빨리 두 번 누르면 두 번째는 버린다 (네트워크 · LLM을 부르는 버튼의 중복 요청 막기).

/** 두 번 누름으로 보는 간격. */
private const val DOUBLE_TAP_GUARD_MS = 600L

/**
 * 가벼운 진동 한 번. 주요 동작(안내 · 저장 · 공유 · 경유지)에 쓴다. 시스템 "터치 진동" 설정을 따른다 (꺼져 있으면 울리지 않는다).
 * 진동기가 없는 기기(에뮬레이터)에서는 아무 일도 없다.
 */
class Haptics internal constructor(private val view: View) {
    /** 누름 확인 (버튼). */
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** 일이 끝남 (저장됨 · 안내 시작). */
    fun confirm() {
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** 실패 · 거절. */
    fun reject() {
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * 누름 반응 + 진동 + 두 번 누름 막기를 붙인 클릭 함수. [loading]이면 누름을 받지 않는다 (가이드 Basic button "Loading" 상태).
 */
@Composable
fun guardedClick(onClick: () -> Unit, loading: Boolean = false, haptic: Boolean = true): () -> Unit {
    val haptics = rememberHaptics()
    val latest = rememberUpdatedState(onClick)
    val busy = rememberUpdatedState(loading)
    val last = remember { longArrayOf(0L) }
    return remember(haptics, haptic) {
        {
            val now = SystemClock.uptimeMillis()
            if (!busy.value && now - last[0] >= DOUBLE_TAP_GUARD_MS) {
                last[0] = now
                if (haptic) haptics.tap()
                latest.value()
            }
        }
    }
}

/**
 * Material [Surface] (누를 수 있는 판)와 같은 모양 + 95% 누름 · 진동 · 두 번 누름 막기 · [loading] 상태.
 * 화면 코드의 `Surface(onClick = …)`는 모두 이것을 쓴다.
 */
@Composable
fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    loading: Boolean = false,
    haptic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Surface(
        onClick = guardedClick(onClick, loading, haptic),
        modifier = modifier.pressScale(source),
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = source,
        content = content,
    )
}

/** 고를 수 있는 판 (탭 · 칩): Material `Surface(selected, onClick)`와 같은 모양 + 누름 반응. */
@Composable
fun PressSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Surface(
        selected = selected,
        onClick = guardedClick(onClick),
        modifier = modifier.pressScale(source),
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = source,
        content = content,
    )
}

/** Material [IconButton]과 같은 모양 + 95% 누름 · 진동 · 두 번 누름 막기. */
@Composable
fun PressIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    IconButton(
        onClick = guardedClick(onClick),
        modifier = modifier.pressScale(source),
        enabled = enabled,
        colors = colors,
        interactionSource = source,
        content = content,
    )
}

/**
 * `Modifier.clickable { }`(indication을 따로 주지 않은 것)에 앱 전체로 붙는 누름 반응: 안쪽 그림을 95%로 줄이고
 * 살짝 어두운(밤에는 밝은) 막을 덮는다. [app.eodiyeotji.ui.theme.EodiTheme]이 `LocalIndication`으로 넣는다.
 */
class PressIndication(private val overlay: Color, private val scale: Float, private val durationMs: Int) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource, overlay, scale, durationMs)

    override fun equals(other: Any?): Boolean =
        other is PressIndication && other.overlay == overlay && other.scale == scale && other.durationMs == durationMs

    override fun hashCode(): Int = (overlay.hashCode() * 31 + scale.hashCode()) * 31 + durationMs
}

private class PressNode(
    private val source: InteractionSource,
    private val overlay: Color,
    private val minScale: Float,
    private val durationMs: Int,
) : Node(), DrawModifierNode {
    private val t = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = 0
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> pressed++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                }
                val target = if (pressed > 0) 1f else 0f
                launch {
                    t.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) { invalidateDraw() }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = t.value
        if (p == 0f) {
            drawContent()
            return
        }
        val s = 1f - (1f - minScale) * p
        scale(s) {
            this@draw.drawContent()
            drawRect(overlay.copy(alpha = overlay.alpha * p))
        }
    }
}
