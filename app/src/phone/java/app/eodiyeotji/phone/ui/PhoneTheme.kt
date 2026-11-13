package app.eodiyeotji.phone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.eodiyeotji.ui.theme.EodiColors
import app.eodiyeotji.ui.theme.LocalEodiColors

/*
 * 폰 판 색: 차가운 회색 바탕 + 파랑 하나 (디자인 시안 "Neutral Style Transformation"의 배치에 파랑 조합을 입힌 것).
 * 화면 코드는 차 판과 같은 것을 쓰고(EodiTheme.colors), 여기서는 그 색 토큰만 폰용으로 바꿔 끼운다.
 * - 바탕은 slate 계열 회색(따뜻한 종이색 · 금색은 쓰지 않는다), 강조는 파랑 600 하나.
 * - 의미 색: 파랑(달린 길 · 주된 버튼 · 고른 것 · "1년 전" 도장), 회색(머문 곳), 초록(지금 여기).
 * 대비: 본문 글자는 바탕 대비 4.5:1 이상 (글자1 16:1, 글자2 7.6:1, 글자3 4.8:1, 흰 글자 / 파랑 버튼 5.2:1).
 */

private val PhoneLight = EodiColors(
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF1F5F9),
    outline = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF64748B),
    accent = Color(0xFF2563EB),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFDBEAFE),
    trip = Color(0xFF2563EB),
    tripSoft = Color(0xFFDBEAFE),
    stay = Color(0xFF334155),
    staySoft = Color(0xFFF1F5F9),
    here = Color(0xFF16A34A),
    hereSoft = Color(0xFFDCFCE7),
    onMarker = Color(0xFFFFFFFF),
    error = Color(0xFFDC2626),
    memory = Color(0xFF2563EB),
    memorySoft = Color(0xFFDBEAFE),
    onMemory = Color(0xFFFFFFFF),
    memoryInk = Color(0xFF1D4ED8),
    heart = Color(0xFFE11D48),
    heartSoft = Color(0xFFFFE4E6),
    star = Color(0xFFF59E0B),
    coupon = Color(0xFFEA580C),
    couponSoft = Color(0xFFFFEDD5),
    couponInk = Color(0xFFC2410C),
    ink = Color(0xFF0F172A),
    onInk = Color(0xFFFFFFFF),
    onInkMuted = Color(0xFFCBD5E1),
    scrim = Color(0x8C0F172A),
    info = Color(0xFF0284C7),
    infoSoft = Color(0xFFE0F2FE),
    isDark = false,
)

private val PhoneDark = EodiColors(
    background = Color(0xFF0B1220),
    surface = Color(0xFF151E2E),
    surfaceHigh = Color(0xFF1E293B),
    outline = Color(0xFF334155),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFFCBD5E1),
    textTertiary = Color(0xFF94A3B8),
    accent = Color(0xFF3B82F6),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF172554),
    trip = Color(0xFF60A5FA),
    tripSoft = Color(0xFF172554),
    stay = Color(0xFF94A3B8),
    staySoft = Color(0xFF1E293B),
    here = Color(0xFF4ADE80),
    hereSoft = Color(0xFF14532D),
    onMarker = Color(0xFF0B1220),
    error = Color(0xFFF87171),
    memory = Color(0xFF3B82F6),
    memorySoft = Color(0xFF172554),
    onMemory = Color(0xFFFFFFFF),
    memoryInk = Color(0xFF93C5FD),
    heart = Color(0xFFFB7185),
    heartSoft = Color(0xFF4C0519),
    star = Color(0xFFFBBF24),
    coupon = Color(0xFFFB923C),
    couponSoft = Color(0xFF431407),
    couponInk = Color(0xFFFDBA74),
    ink = Color(0xFF1E293B),
    onInk = Color(0xFFFFFFFF),
    onInkMuted = Color(0xFFCBD5E1),
    scrim = Color(0x990B1220),
    info = Color(0xFF38BDF8),
    infoSoft = Color(0xFF0C4A6E),
    isDark = true,
)

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
