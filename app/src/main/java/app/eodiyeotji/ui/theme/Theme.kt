package app.eodiyeotji.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import app.eodiyeotji.ui.common.BounceOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import app.eodiyeotji.ui.common.PressIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 간격 · 모서리 · 터치 크기. 4dp 단위. */
@Immutable
data class EodiSpace(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    /** 화면 가장자리 여백 */
    val page: Dp = 32.dp,
    /** 차량 터치 기준 (Pleos · CLAUDE.md): 76dp 이상 */
    val touch: Dp = 76.dp,
    val radiusS: Dp = 12.dp,
    val radiusM: Dp = 20.dp,
    val radiusL: Dp = 28.dp,
)

internal val LocalEodiSpace = staticCompositionLocalOf { EodiSpace() }

/**
 * 움직임 길이 (ms). Pleos 가이드 Animation: 짧고 부드럽게, 운전자 시선을 붙잡지 않는다.
 * - quick: 누름 반응 · 색 바뀜  - medium: 화면 안 요소 등장 · 탭 전환  - slow: 큰 판(시트 · 결산 숫자) 등장
 * 무한 반복 움직임은 "기록 중" 점 · 3D 기억 지도처럼 의미가 있는 곳에만 쓴다.
 */
@Immutable
data class EodiMotion(
    val quick: Int = 120,
    val medium: Int = 240,
    val slow: Int = 420,
    /** 누르는 동안 줄어드는 비율 (버튼 · 카드). Pleos 가이드 Interaction "Button interaction": 95% */
    val pressScale: Float = 0.95f,
    /** 누른 뒤 눈에 보이는 반응까지의 한도 (Pleos 가이드 Introduction 즉시성: 0.25초) */
    val feedbackMs: Int = 250,
    /** Gleo AI Standby: 대화가 끝난 뒤 응답 화면을 유지하는 시간 (Pleos 가이드 Experiences/Gleo AI) */
    val standbyMs: Long = 5_000L,
    /** Gleo AI Listening_Passive: 말이 들어오지 않으면 이 시간 뒤 "듣는 중…" 안내 */
    val passiveHintMs: Long = 2_000L,
    /** 화면이 들어올 때 아래에서 올라오는 거리 */
    val enterOffset: Dp = 24.dp,
)

/**
 * 그림자 한 겹: 아래로 [y] 내려간 곳에 [blur]만큼 번진 검은 그림자 ([alpha] 진하기).
 * 값은 Pleos 가이드 foundations/shadow.md의 Figma 값(CSS box-shadow와 같은 뜻) 그대로다.
 */
@Immutable
data class ShadowLayer(val y: Dp, val blur: Dp, val alpha: Float)

/** 그림자 단계 하나 = 겹 1~2개. [Modifier.eodiShadow][app.eodiyeotji.ui.common.eodiShadow]로 그린다. */
@Immutable
data class ShadowLevel(val layers: List<ShadowLayer>)

/**
 * 그림자 3단계 (Pleos 가이드 foundations/shadow.md). 바탕 위 카드는 [flat](테두리 · 색 차이로 구분),
 * 떠 있는 것만 그림자: level1 = 살짝 뜬 카드 · 칩, level2 = 떠 있는 판(안내 띠 · 사진 위 버튼 · 토스트),
 * level3 = 모든 것 위에 겹치는 판(제안 카드 · 듣는 중 카드 · 시트).
 */
@Immutable
data class EodiElevation(
    val flat: ShadowLevel = ShadowLevel(emptyList()),
    val level1: ShadowLevel = ShadowLevel(listOf(ShadowLayer(6.dp, 24.dp, 0.08f))),
    val level2: ShadowLevel = ShadowLevel(listOf(ShadowLayer(8.dp, 32.dp, 0.16f), ShadowLayer(2.dp, 2.dp, 0.08f))),
    val level3: ShadowLevel = ShadowLevel(listOf(ShadowLayer(20.dp, 40.dp, 0.24f), ShadowLayer(2.dp, 4.dp, 0.20f))),
)

internal val LocalEodiMotion = staticCompositionLocalOf { EodiMotion() }
internal val LocalEodiElevation = staticCompositionLocalOf { EodiElevation() }

/** 화면 코드에서 토큰을 꺼내는 곳: `EodiTheme.colors.accent`, `EodiTheme.type.title`, `EodiTheme.space.page`. */
object EodiTheme {
    val colors: EodiColors
        @Composable @ReadOnlyComposable get() = LocalEodiColors.current
    val type: EodiType
        @Composable @ReadOnlyComposable get() = LocalEodiType.current
    val space: EodiSpace
        @Composable @ReadOnlyComposable get() = LocalEodiSpace.current
    val motion: EodiMotion
        @Composable @ReadOnlyComposable get() = LocalEodiMotion.current
    val elevation: EodiElevation
        @Composable @ReadOnlyComposable get() = LocalEodiElevation.current
}

/**
 * 앱 테마. 시스템(차의 낮 · 밤 모드)을 따른다: 낮에는 종이 앨범(라이트), 밤에는 검은 앨범(다크).
 * Material 컴포넌트가 쓰는 colorScheme도 모든 역할을 토큰으로 채워서 기본 보라색이 나오지 않게 한다.
 */
@Composable
fun EodiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val type = remember { EodiType() }
    val scheme = remember(colors) { colors.toMaterial() }
    val typography = remember(type) { materialTypography(type) }
    val motion = remember { EodiMotion() }
    // 앱 전체 clickable 누름 반응: 95% 축소 + 낮엔 어둡게 · 밤엔 밝게 (Pleos 가이드 Button interaction)
    val press = remember(colors.isDark) {
        PressIndication(if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f), motion.pressScale, motion.quick)
    }
    val bounce = remember { BounceOverscrollFactory() }
    CompositionLocalProvider(
        LocalEodiColors provides colors,
        LocalEodiType provides type,
        LocalEodiSpace provides EodiSpace(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = EodiShapes) {
            // MaterialTheme이 LocalIndication을 ripple로 덮으므로 그 안에서 다시 넣는다.
            // 목록 끝은 늘이기 대신 튕기기 (Pleos 가이드 End of scroll bounce, 최대 약 2/5)
            CompositionLocalProvider(
                LocalIndication provides press,
                LocalOverscrollFactory provides bounce,
                content = content,
            )
        }
    }
}

private val EodiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private fun EodiColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = textSecondary,
        onSecondary = surface,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = textPrimary,
        tertiary = stay,
        onTertiary = onMarker,
        tertiaryContainer = staySoft,
        onTertiaryContainer = stay,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = textSecondary,
        surfaceTint = surface,
        inverseSurface = textPrimary,
        inverseOnSurface = surface,
        error = error,
        onError = onAccent,
        errorContainer = surfaceHigh,
        onErrorContainer = error,
        outline = outline,
        outlineVariant = outline,
        scrim = textPrimary,
        surfaceBright = surface,
        surfaceDim = background,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh,
    )
}
