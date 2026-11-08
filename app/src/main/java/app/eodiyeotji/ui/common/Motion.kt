package app.eodiyeotji.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import app.eodiyeotji.ui.theme.EodiTheme

// 모든 화면이 같이 쓰는 가벼운 움직임. 전부 graphicsLayer(그리기 단계)만 바꿔서 재구성 · 다시 배치가 없다.

/**
 * 누르는 동안 살짝 줄어드는 반응 (버튼 · 카드). 차 화면은 손끝이 화면을 가려서 물결(ripple)만으로는
 * 눌렸는지 알기 어렵다 → 크기 변화로 한 번 더 알려 준다.
 */
fun Modifier.pressScale(interaction: InteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val motion = EodiTheme.motion
    val scale by animateFloatAsState(
        if (pressed) motion.pressScale else 1f,
        tween(motion.quick, easing = FastOutSlowInEasing),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * 처음 보일 때 한 번: 투명 → 보임 + 아래에서 살짝 올라옴. [key]가 바뀌면(탭 전환) 다시 한 번.
 * 목록 항목마다 쓰지 않는다 (화면 · 큰 판 단위로만).
 */
fun Modifier.enterRise(key: Any?): Modifier = composed {
    val motion = EodiTheme.motion
    val offsetPx = with(LocalDensity.current) { motion.enterOffset.toPx() }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(motion.medium, easing = FastOutSlowInEasing)) }
    graphicsLayer {
        alpha = t.value
        translationY = (1f - t.value) * offsetPx
    }
}
