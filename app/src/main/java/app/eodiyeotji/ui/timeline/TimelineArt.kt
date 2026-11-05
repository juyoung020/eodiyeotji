package app.eodiyeotji.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.eodiyeotji.ui.theme.EodiTheme

/**
 * 기록이 없을 때의 그림: 지도 조각 위에 굽은 길, 출발점(파랑)에서 머문 곳 핀(주황)까지.
 * 테마 색으로 그려서 라이트 · 다크에 같이 맞는다. 정적인 그림이라 다시 그릴 일이 없다.
 */
@Composable
internal fun EmptyRoadArt(modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    Canvas(modifier.size(width = 360.dp, height = 248.dp)) {
        val w = size.width
        val h = size.height
        val u = w / 320f
        // 지도 조각
        drawRoundRect(c.surfaceHigh, cornerRadius = CornerRadius(36 * u))
        // 블록 몇 개 (지도 느낌)
        val block = c.outline.copy(alpha = if (c.isDark) 0.55f else 0.6f)
        drawRoundRect(block, Offset(24 * u, 22 * u), Size(70 * u, 46 * u), CornerRadius(10 * u))
        drawRoundRect(block, Offset(214 * u, 140 * u), Size(82 * u, 56 * u), CornerRadius(10 * u))
        drawRoundRect(block, Offset(120 * u, 20 * u), Size(54 * u, 34 * u), CornerRadius(10 * u))
        // 길
        val road = Path().apply {
            moveTo(52 * u, h - 30 * u)
            cubicTo(70 * u, 120 * u, 170 * u, 170 * u, 190 * u, 110 * u)
            cubicTo(205 * u, 66 * u, 236 * u, 64 * u, 250 * u, 78 * u)
        }
        drawPath(road, c.surface, style = Stroke(width = 30 * u, cap = StrokeCap.Round))
        drawPath(road, c.trip, style = Stroke(width = 10 * u, cap = StrokeCap.Round))
        drawPath(
            road,
            c.surface,
            style = Stroke(width = 3 * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * u, 12 * u))),
        )
        // 출발점
        drawCircle(c.surface, 15 * u, Offset(52 * u, h - 30 * u))
        drawCircle(c.trip, 10 * u, Offset(52 * u, h - 30 * u))
        // 머문 곳 핀 + 그림자
        val pinX = 250 * u
        val pinBottom = 84 * u
        drawOval(c.textPrimary.copy(alpha = 0.10f), Offset(pinX - 18 * u, pinBottom - 6 * u), Size(36 * u, 12 * u))
        val pin = Path().apply {
            moveTo(pinX, pinBottom)
            cubicTo(pinX - 8 * u, pinBottom - 14 * u, pinX - 26 * u, pinBottom - 30 * u, pinX - 26 * u, pinBottom - 48 * u)
            cubicTo(pinX - 26 * u, pinBottom - 63 * u, pinX - 14 * u, pinBottom - 74 * u, pinX, pinBottom - 74 * u)
            cubicTo(pinX + 14 * u, pinBottom - 74 * u, pinX + 26 * u, pinBottom - 63 * u, pinX + 26 * u, pinBottom - 48 * u)
            cubicTo(pinX + 26 * u, pinBottom - 30 * u, pinX + 8 * u, pinBottom - 14 * u, pinX, pinBottom)
            close()
        }
        drawPath(pin, c.stay)
        drawCircle(c.onMarker, 9 * u, Offset(pinX, pinBottom - 48 * u))
        // 오른쪽 위 반짝임 (기억)
        val spark = c.accent.copy(alpha = 0.55f)
        drawCircle(spark, 4 * u, Offset(290 * u, 36 * u))
        drawCircle(spark, 3 * u, Offset(272 * u, 22 * u))
        drawCircle(spark, 2.5f * u, Offset(300 * u, 58 * u))
    }
}
