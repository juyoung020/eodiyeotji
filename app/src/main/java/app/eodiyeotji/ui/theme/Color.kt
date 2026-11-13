package app.eodiyeotji.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 색 토큰 — "길 위의 앨범" (docs/90_디자인_컨셉.md).
 * - 앱 화면 = 어두운 앨범(다크): 검은 종이 바탕에 흰 글자, 사진이 주인공. 파랑은 쓰지 않는다.
 *   의미 색은 셋: 금색(달린 길 · 기억) · 흰색(머문 곳 · 주된 동작) · 초록(지금 여기).
 * - 라이트 = 종이 앨범: 밖으로 내보내는 공유 그림에만 쓴다 (길 파랑 · 노을 코랄 · 금색 · 초록).
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
    /** 제휴 혜택 · 할인 쿠폰 (귤색). 배지 바탕은 [couponSoft], 그 위 글자는 [couponInk] */
    val coupon: Color,
    val couponSoft: Color,
    val couponInk: Color,
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
    coupon = Color(0xFFF26B1D),
    couponSoft = Color(0xFFFFF0E5),
    couponInk = Color(0xFFB6460A),
    ink = Color(0xFF141B2B),
    onInk = Color(0xFFFFFFFF),
    onInkMuted = Color(0xFFC9D2E3),
    scrim = Color(0x8C0A0F1A),
    isDark = false,
)

internal val DarkColors = EodiColors(
    background = Color(0xFF101214),
    surface = Color(0xFF1A1D20),
    surfaceHigh = Color(0xFF25292D),
    outline = Color(0xFF353A40),
    textPrimary = Color(0xFFF1F1EE),
    textSecondary = Color(0xFFCFD2D4),
    textTertiary = Color(0xFF8D949C),
    accent = Color(0xFFF1F1EE),
    onAccent = Color(0xFF101214),
    accentSoft = Color(0xFF2A2E33),
    trip = Color(0xFFF0C36A),
    tripSoft = Color(0xFF332A14),
    stay = Color(0xFFE9E9E4),
    staySoft = Color(0xFF2A2E33),
    here = Color(0xFF2FD98C),
    hereSoft = Color(0xFF0F3324),
    onMarker = Color(0xFF101214),
    error = Color(0xFFFF6B57),
    memory = Color(0xFFF0C36A),
    memorySoft = Color(0xFF332A14),
    onMemory = Color(0xFF2B1D00),
    memoryInk = Color(0xFFF0C36A),
    heart = Color(0xFFFF5C74),
    heartSoft = Color(0xFF3D1A22),
    star = Color(0xFFF0C36A),
    coupon = Color(0xFFFF8A4C),
    couponSoft = Color(0xFF3A2416),
    couponInk = Color(0xFFFFB083),
    ink = Color(0xFF2A2E33),
    onInk = Color(0xFFF1F1EE),
    onInkMuted = Color(0xFFCFD2D4),
    scrim = Color(0x99101214),
    isDark = true,
)

/**
 * 지도 위 경로 · 핀 색. 바탕 지도는 밤 지도 하나(MapStyleSource가 색을 어둡게 바꾼다)라
 * 지도 위 색도 그 지도에 맞춘 값 하나를 쓴다: 길은 금색, 머문 곳은 흰 점, 지금 있는 곳은 초록.
 */
internal object MapInk {
    val trip = Color(0xFFF0B84A)
    val casing = Color(0xFF101214)
    val stay = Color(0xFFE9E9E4)
    val here = Color(0xFF2FD98C)
    val selectedStroke = Color(0xFFF0C36A)
    val onMarker = Color(0xFF101214)
    val label = Color(0xFFF1F1EE)
    val labelHalo = Color(0xFF101214)
}

internal val LocalEodiColors = staticCompositionLocalOf { LightColors }
