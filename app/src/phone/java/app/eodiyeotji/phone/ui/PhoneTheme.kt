package app.eodiyeotji.phone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.eodiyeotji.ui.theme.EodiColors
import app.eodiyeotji.ui.theme.LocalEodiColors

/*
 * 폰 판 색 = 차 판과 같은 색 토큰(ui/theme/Color.kt의 LightColors · DarkColors).
 * 한때 폰만 slate 회색 + 파랑 600(#2563EB)을 따로 썼는데, 지도 경로 · 핀은 차 판 파랑(#0064FF)이라
 * 같은 화면에서 파랑이 두 가지로 보였다. 이제 두 판이 같은 파랑 · 같은 회색 · 같은 의미 색을 쓴다.
 */
private val PhoneLight = app.eodiyeotji.ui.theme.LightColors
private val PhoneDark = app.eodiyeotji.ui.theme.DarkColors

/**
 * 폰 판 색을 입힌다 (낮 · 밤은 시스템 설정). 앱 전체를 감싸는 자리(AppScale)에서 한 번 쓴다.
 * Material 부품이 쓰는 colorScheme도 같은 토큰으로 채운다 (차 판 테마와 같은 대응).
 */
@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) PhoneDark else PhoneLight
    val base = MaterialTheme.colorScheme
    val scheme = remember(c, base) {
        base.copy(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentSoft,
            onPrimaryContainer = c.accent,
            inversePrimary = c.accent,
            secondary = c.textSecondary,
            onSecondary = c.surface,
            secondaryContainer = c.surfaceHigh,
            onSecondaryContainer = c.textPrimary,
            tertiary = c.stay,
            onTertiary = c.onMarker,
            tertiaryContainer = c.staySoft,
            onTertiaryContainer = c.stay,
            background = c.background,
            onBackground = c.textPrimary,
            surface = c.surface,
            onSurface = c.textPrimary,
            surfaceVariant = c.surfaceHigh,
            onSurfaceVariant = c.textSecondary,
            surfaceTint = c.surface,
            inverseSurface = c.textPrimary,
            inverseOnSurface = c.surface,
            error = c.error,
            onError = c.onAccent,
            errorContainer = c.surfaceHigh,
            onErrorContainer = c.error,
            outline = c.outline,
            outlineVariant = c.outline,
            scrim = c.textPrimary,
            surfaceBright = c.surface,
            surfaceDim = c.background,
            surfaceContainerLowest = c.surface,
            surfaceContainerLow = c.surface,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surfaceHigh,
            surfaceContainerHighest = c.surfaceHigh,
        )
    }
    CompositionLocalProvider(LocalEodiColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}
