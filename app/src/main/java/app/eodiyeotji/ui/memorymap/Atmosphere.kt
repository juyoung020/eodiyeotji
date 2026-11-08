package app.eodiyeotji.ui.memorymap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.eodiyeotji.core.SkyPhase
import kotlin.math.PI
import kotlin.math.sin

/**
 * 3D 지도 위에 얹는 하늘 (MapLibre Native 13에는 sky 레이어가 없다): 기울어진 지도의 지평선 위를 하늘 그라데이션으로 덮고,
 * 지평선에 빛 띠(노을 · 도시 불빛)를 번지게, 가장자리는 비네트로 어둡게 한다. [stars]면 윗부분에 반짝이는 별.
 * 색은 Rust가 정한 때([SkyPhase], 도착 시각 기준)로 고른다. 한 장의 Canvas라 지도 프레임에 부담이 없다.
 */
@Composable
fun CinematicSky(phase: SkyPhase, modifier: Modifier = Modifier, stars: Boolean = phase == SkyPhase.NIGHT || phase == SkyPhase.BLUE, skyFraction: Float = 0.34f) {
    val p = remember(phase) { skyPalette(phase) }
    val field = remember { starField() }
    val twinkle = if (stars) {
        val t by rememberInfiniteTransition(label = "stars").animateFloat(
            0f, 1f, infiniteRepeatable(tween(TWINKLE_MS, easing = LinearEasing), RepeatMode.Restart), label = "twinkle",
        )
        t
    } else 0f
    Canvas(modifier.fillMaxSize()) {
        val h = size.height
        val sky = h * skyFraction
        // 하늘: 위는 짙게 → 지평선 쪽으로 밝아지다 → 지평선 빛 띠 → 지도 쪽으로 투명
        drawRect(
            Brush.verticalGradient(
                0f to p.zenith,
                0.55f to p.mid,
                0.85f to p.horizon,
                1f to p.horizon.copy(alpha = 0f),
                startY = 0f,
                endY = sky * 1.35f,
            ),
        )
        // 지평선 빛: 가운데가 밝은 띠 (위아래로 부드럽게 사라진다) + 가운데 번짐
        val band = h * 0.14f
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.5f to p.glow, 1f to Color.Transparent, startY = sky - band, endY = sky + band),
            topLeft = Offset(0f, sky - band),
            size = Size(size.width, band * 2f),
        )
        drawCircle(
            Brush.radialGradient(0f to p.glow, 1f to Color.Transparent, center = Offset(size.width * 0.5f, sky), radius = band * 2.2f),
            radius = band * 2.2f,
            center = Offset(size.width * 0.5f, sky),
        )
        if (stars) drawStars(field, twinkle, sky * 0.9f, p.star)
        // 아래 안개 + 네 귀퉁이 비네트
        drawRect(Brush.verticalGradient(0.7f to Color.Transparent, 1f to p.floor))
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = p.vignette),
                center = Offset(size.width / 2f, h / 2f),
                radius = maxOf(size.width, h) * 0.75f,
            ),
        )
    }
}

private const val TWINKLE_MS = 4_000
private const val STAR_COUNT = 90

@Immutable
private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Int)

/** 고정된 별 자리 (매번 같은 하늘, 씨앗 고정 LCG). */
private fun starField(): List<Star> {
    var seed = 0x2F6E2B1L
    fun next(): Float {
        seed = (seed * 6364136223846793005L + 1442695040888963407L)
        return ((seed ushr 33) and 0xFFFFFF).toFloat() / 0xFFFFFF
    }
    return List(STAR_COUNT) {
        val y = next()
        Star(next(), y * y, 0.8f + next() * 1.8f, next(), 1 + (next() * 2.99f).toInt())
    }
}

private fun DrawScope.drawStars(field: List<Star>, t: Float, maxY: Float, color: Color) {
    for (s in field) {
        // 별마다 다른 빠르기 · 위상으로 깜빡인다 (지평선 가까울수록 흐리게)
        val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t * s.speed + s.phase)))
        val fade = 1f - s.y
        drawCircle(color.copy(alpha = (a * fade).coerceIn(0f, 1f)), radius = s.r, center = Offset(s.x * size.width, s.y * maxY))
    }
}

@Immutable
private class SkyPalette(
    val zenith: Color,
    val mid: Color,
    val horizon: Color,
    val glow: Color,
    val floor: Color,
    val star: Color,
    val vignette: Float,
)

private fun skyPalette(phase: SkyPhase): SkyPalette = when (phase) {
    SkyPhase.DAWN -> SkyPalette(Color(0xF02B3566), Color(0xC0866A9C), Color(0xA0F6B49A), Color(0x80FFD2B0), Color(0x55302040), Color(0xFFFFF3E6), 0.35f)
    SkyPhase.DAY -> SkyPalette(Color(0xE07FB6E8), Color(0xB0A9D2F2), Color(0x80E8F3FA), Color(0x55FFFFFF), Color(0x33203040), Color.White, 0.22f)
    SkyPhase.GOLDEN -> SkyPalette(Color(0xF05A3A6E), Color(0xC0D9735C), Color(0xB0FFB070), Color(0x99FFC77A), Color(0x8C2A1410), Color(0xFFFFF0D8), 0.38f)
    SkyPhase.BLUE -> SkyPalette(Color(0xF20E1A3D), Color(0xC02C3F7A), Color(0x99B3758F), Color(0x70FF9E7A), Color(0x80080C1C), Color(0xFFE8EEFF), 0.45f)
    SkyPhase.NIGHT -> SkyPalette(Color(0xFF040812), Color(0xE0091430), Color(0xA0162650), Color(0x66FFB25A), Color(0x99030610), Color(0xFFF3EEDF), 0.55f)
}
