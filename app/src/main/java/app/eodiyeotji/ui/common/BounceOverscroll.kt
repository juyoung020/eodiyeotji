package app.eodiyeotji.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 목록 끝에서 더 끌면 내용이 따라 밀렸다가 놓으면 튕겨 돌아오는 효과 (Pleos 가이드 foundations/interaction.md
 * "End of scroll": bounce, 최대 화면의 약 2/5). 안드로이드 기본 늘이기(stretch) 대신 앱 전체 목록에 쓴다
 * ([app.eodiyeotji.ui.theme.EodiTheme]이 `LocalOverscrollFactory`로 넣는다).
 * 끌수록 덜 밀리고(저항), 놓으면 스프링으로 돌아온다. 그리기 단계(레이어 이동)만 바꿔 재구성이 없다.
 */
class BounceOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = BounceOverscroll()

    override fun equals(other: Any?): Boolean = other is BounceOverscrollFactory

    override fun hashCode(): Int = 1
}

/** 목록 크기 대비 최대로 밀리는 비율 (가이드: 약 2/5). */
private const val MAX_FRACTION = 0.4f

private class BounceOverscroll : OverscrollEffect {
    private val x = Animatable(0f)
    private val y = Animatable(0f)
    private var width = 0
    private var height = 0

    override val isInProgress: Boolean get() = x.value != 0f || y.value != 0f || drag.value != Offset.Zero

    override val node: DelegatableNode = BounceNode()

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        // 1) 밀려 있는 상태에서 반대로 끌면 먼저 되돌린다
        val relaxX = relax(xNow, delta.x)
        val relaxY = relax(yNow, delta.y)
        if (relaxX != 0f || relaxY != 0f) snap(xNow + relaxX, yNow + relaxY)
        val left = Offset(delta.x - relaxX, delta.y - relaxY)
        // 2) 목록이 스크롤하고
        val consumed = performScroll(left)
        val leftover = left - consumed
        // 3) 끝에 닿아 남은 만큼(손가락으로 끌 때만) 저항을 주며 밀린다
        if (source == NestedScrollSource.UserInput && (leftover.x != 0f || leftover.y != 0f)) {
            snap(push(xNow, leftover.x, width), push(yNow, leftover.y, height))
            return Offset(relaxX, relaxY) + consumed + leftover
        }
        return Offset(relaxX, relaxY) + consumed
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val left = performFling(velocity)
        // 놓으면 (남은 속도를 안고) 스프링으로 돌아온다
        // 끌던 위치를 애니메이션 값으로 넘기고 돌아오게 한다
        x.snapTo(x.value + xNow)
        y.snapTo(y.value + yNow)
        drag.value = Offset.Zero
        coroutineScope {
            val spec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            launch { x.animateTo(0f, spec, initialVelocity = left.x) }
            launch { y.animateTo(0f, spec, initialVelocity = left.y) }
        }
    }

    /** 밀린 [current]에서 반대 방향 [delta]로 되돌릴 양 (0까지만). */
    private fun relax(current: Float, delta: Float): Float {
        if (current == 0f || delta == 0f || sign(current) == sign(delta)) return 0f
        return if (abs(delta) > abs(current)) -current else delta
    }

    /** 끝에서 [delta]만큼 더 끌었을 때 새 위치: 최대에 가까울수록 덜 밀린다. */
    private fun push(current: Float, delta: Float, size: Int): Float {
        if (delta == 0f || size == 0) return current
        val max = size * MAX_FRACTION
        val resistance = (1f - abs(current) / max).coerceIn(0f, 1f) * 0.5f
        return (current + delta * resistance).coerceIn(-max, max)
    }

    private fun snap(nx: Float, ny: Float) {
        drag.value = Offset(nx, ny)
    }

    // 끄는 동안의 값 (스냅샷 상태: 레이어가 읽으면 자동으로 다시 그린다)
    private val drag = androidx.compose.runtime.mutableStateOf(Offset.Zero)
    private var xNow: Float
        get() = drag.value.x
        set(v) { drag.value = Offset(v, drag.value.y) }
    private var yNow: Float
        get() = drag.value.y
        set(v) { drag.value = Offset(drag.value.x, v) }

    private inner class BounceNode : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val p = measurable.measure(constraints)
            width = p.width
            height = p.height
            return layout(p.width, p.height) {
                p.placeWithLayer(0, 0) {
                    translationX = xNow + x.value
                    translationY = yNow + y.value
                }
            }
        }
    }
}
