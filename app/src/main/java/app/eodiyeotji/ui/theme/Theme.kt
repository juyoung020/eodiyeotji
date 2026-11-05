package app.eodiyeotji.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

/** 화면 코드에서 토큰을 꺼내는 곳: `EodiTheme.colors.accent`, `EodiTheme.type.title`, `EodiTheme.space.page`. */
object EodiTheme {
    val colors: EodiColors
        @Composable @ReadOnlyComposable get() = LocalEodiColors.current
    val type: EodiType
        @Composable @ReadOnlyComposable get() = LocalEodiType.current
    val space: EodiSpace
        @Composable @ReadOnlyComposable get() = LocalEodiSpace.current
}

/**
 * 앱 테마. 시스템 다크 설정을 따른다 (Pleos 시스템 UI와 같이 바뀐다).
 * Material 컴포넌트가 쓰는 colorScheme도 모든 역할을 토큰으로 채워서 기본 보라색이 나오지 않게 한다.
 */
@Composable
fun EodiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val type = remember { EodiType() }
    val scheme = remember(colors) { colors.toMaterial() }
    val typography = remember(type) { materialTypography(type) }
    CompositionLocalProvider(
        LocalEodiColors provides colors,
        LocalEodiType provides type,
        LocalEodiSpace provides EodiSpace(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = EodiShapes, content = content)
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
