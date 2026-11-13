package app.eodiyeotji.ui.theme

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
    /** 누르는 동안 줄어드는 비율 (버튼 · 카드) */
    val pressScale: Float = 0.96f,
    /** 화면이 들어올 때 아래에서 올라오는 거리 */
    val enterOffset: Dp = 24.dp,
)

/** 그림자 높이: 바탕 위 카드는 0(테두리 · 색 차이로 구분), 떠 있는 판(안내 띠 · 사진 위 버튼)만 그림자. */
@Immutable
data class EodiElevation(
    val flat: Dp = 0.dp,
    val raised: Dp = 2.dp,
    val floating: Dp = 8.dp,
    val overlay: Dp = 16.dp,
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
 * 앱 테마. 화면은 늘 어두운 앨범(다크)이다: 사진이 돋보이고 밤 운전에 눈이 덜 부시다. 라이트는 공유 그림처럼 따로 부를 때만.
 * Material 컴포넌트가 쓰는 colorScheme도 모든 역할을 토큰으로 채워서 기본 보라색이 나오지 않게 한다.
 */
@Composable
fun EodiTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
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
