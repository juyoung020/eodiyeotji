package app.eodiyeotji.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 색 토큰 — "길 위의 앨범" (docs/90_디자인_컨셉.md).
 * - 라이트 = 종이 앨범: 따뜻한 종이색 바탕 위에 흰 카드, 글자는 남색 먹색.
 * - 다크 = 밤길: 회색이 아니라 깊은 남색 바탕 (밤 운전 때 눈부시지 않고, 파랑 브랜드와 한 가족으로 보인다).
 * 의미 색은 넷: 길 파랑(주행 · 주된 동작, Pleos #0064FF) · 노을 코랄(머문 곳) · 반짝 금색(기억 · AI 회상) · 지금 초록(지금 여기).
 * 대비: 본문 글자는 바탕 대비 4.5:1 이상, 색 글자(20sp 굵게 이상)와 아이콘은 3:1 이상 (WCAG AA, 값은 컨셉 문서에).
 */

/**
 * 앱 전체가 쓰는 의미 단위 색. 화면 코드는 MaterialTheme.colorScheme 대신 이것을 쓴다
 * (Material 기본 보라색이 섞여 들어오지 않게).
 */
@Immutable
data class EodiColors(
    /** 창 바탕 (가장 아래) */
    val background: Color,
    /** 카드 · 목록 행 바탕 */
    val surface: Color,
    /** 카드 안의 한 단계 위 바탕 (통계 칸, 아이콘 버튼) */
    val surfaceHigh: Color,
    /** 얇은 테두리 · 구분선 */
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** 브랜드 강조 (선택 · 버튼 · 오늘) */
    val accent: Color,
    val onAccent: Color,
    /** 고른 행 바탕처럼 넓은 면에 까는 옅은 강조색 */
    val accentSoft: Color,
    val trip: Color,
    val tripSoft: Color,
    val stay: Color,
    val staySoft: Color,
    /** 아직 머무는 곳 = 지금 차가 있는 곳 */
    val here: Color,
    val hereSoft: Color,
    val onMarker: Color,
    val error: Color,
    /** 기억 · AI 회상 표시 ("1년 전" 도장, 반짝임). 면으로 칠하고 그 위 글자는 [onMemory] */
    val memory: Color,
    val memorySoft: Color,
    val onMemory: Color,
    /** 바탕 위에 바로 쓰는 기억 색 (반짝임 아이콘 · 기억 글자). 라이트는 읽히도록 진한 금색 */
    val memoryInk: Color,
    val heart: Color,
    val heartSoft: Color,
    val star: Color,
    /** 떠 있는 안내 띠(코스 안내 중)처럼 테마와 상관없이 진한 남색 판 */
    val ink: Color,
    val onInk: Color,
    val onInkMuted: Color,
    /** 사진 위 글자 받침 */
    val scrim: Color,
    val isDark: Boolean,
)

internal val LightColors = EodiColors(
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF0F3F8),
    outline = Color(0xFFE2E7EF),
    textPrimary = Color(0xFF151A24),
    textSecondary = Color(0xFF464D5C),
    textTertiary = Color(0xFF646B7A),
    accent = Color(0xFF0064FF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE8F0FF),
    trip = Color(0xFF0064FF),
    tripSoft = Color(0xFFE8F0FF),
    stay = Color(0xFFE0522A),
    staySoft = Color(0xFFFFEDE4),
    here = Color(0xFF008F4F),
    hereSoft = Color(0xFFDDF5E8),
    onMarker = Color(0xFFFFFFFF),
    error = Color(0xFFD93A1E),
    memory = Color(0xFFFFC233),
    memorySoft = Color(0xFFFFF3D1),
    onMemory = Color(0xFF3A2600),
    memoryInk = Color(0xFF9A6200),
    heart = Color(0xFFE5364F),
    heartSoft = Color(0xFFFFE6EA),
    star = Color(0xFFF5A800),
    ink = Color(0xFF141B2B),
    onInk = Color(0xFFFFFFFF),
    onInkMuted = Color(0xFFC9D2E3),
    scrim = Color(0x8C0A0F1A),
    isDark = false,
)

internal val DarkColors = EodiColors(
    background = Color(0xFF0A0F1A),
    surface = Color(0xFF131B29),
    surfaceHigh = Color(0xFF1C2638),
    outline = Color(0xFF2C3850),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFFB9C2D0),
    textTertiary = Color(0xFF8F9AAD),
    accent = Color(0xFF3A87FF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF16294A),
    trip = Color(0xFF3A87FF),
    tripSoft = Color(0xFF16294A),
    stay = Color(0xFFF0643C),
    staySoft = Color(0xFF3A2219),
    here = Color(0xFF2FD98C),
    hereSoft = Color(0xFF0F3324),
    onMarker = Color(0xFFFFFFFF),
    error = Color(0xFFFF6B57),
    memory = Color(0xFFFFC94D),
    memorySoft = Color(0xFF3A2E12),
    onMemory = Color(0xFF2B1D00),
    memoryInk = Color(0xFFFFC94D),
    heart = Color(0xFFFF5C74),
    heartSoft = Color(0xFF3D1A22),
    star = Color(0xFFFFC233),
    ink = Color(0xFF22304B),
    onInk = Color(0xFFFFFFFF),
    onInkMuted = Color(0xFFC9D2E3),
    scrim = Color(0x990A0F1A),
    isDark = true,
)

/**
 * 지도 위 경로 · 핀 색. 바탕 지도(assets/map/style.json)가 라이트 하나뿐이라
 * 앱이 다크여도 지도 위 색은 라이트 지도에 맞춘 값 하나를 쓴다 (라이트 토큰과 같은 색 가족).
 */
internal object MapInk {
    val trip = Color(0xFF0064FF)
    val casing = Color(0xFFFFFFFF)
    val stay = Color(0xFFE0522A)
    val here = Color(0xFF00A35A)
    val selectedStroke = Color(0xFFFFFFFF)
    val onMarker = Color(0xFFFFFFFF)
    val label = Color(0xFF151A24)
    val labelHalo = Color(0xFFFFFFFF)
}

internal val LocalEodiColors = staticCompositionLocalOf { LightColors }
