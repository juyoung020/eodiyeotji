package app.eodiyeotji.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import app.eodiyeotji.ui.theme.ShadowLevel

/**
 * Pleos 가이드 그림자 (foundations/shadow.md)를 값 그대로 그린다: 겹마다 아래로 y만큼, blur만큼 번진 검은 그림자.
 * Compose 기본 `shadow()`는 빛 위치로 계산하는 그림자라 가이드의 y · blur · 투명도를 맞출 수 없어서 직접 그린다.
 * (API 28+ 하드웨어 그리기에서 `setShadowLayer`가 모든 도형에 동작한다. CSS blur ≈ Android 반경 × 2)
 *
 * 그림자를 그릴 판보다 앞에 둔다: `Modifier.eodiShadow(EodiTheme.elevation.level2, shape).background(...)`.
 */
fun Modifier.eodiShadow(level: ShadowLevel, shape: Shape): Modifier =
    if (level.layers.isEmpty()) this else drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        drawIntoCanvas { canvas ->
            level.layers.forEach { l ->
                val paint = Paint()
                paint.asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = Color.Transparent.toArgb()
                    setShadowLayer(l.blur.toPx() / 2f, 0f, l.y.toPx(), Color.Black.copy(alpha = l.alpha).toArgb())
                }
                canvas.drawPath(path, paint)
            }
        }
    }
