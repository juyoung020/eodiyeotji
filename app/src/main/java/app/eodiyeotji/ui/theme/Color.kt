package app.eodiyeotji.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 색 토큰. 회색 단계는 Pleos 디자인 가이드 Grayscale(basic_00 ~ basic_900, 살짝 푸른 중립 회색)을,
 * 강조 파랑은 Pleos informative_positive(#0064FF / 다크 #0082FF)를 따른다 → 시스템 UI 옆에서 같은 앱처럼 보인다.
 * 종류별 색: 주행 = 파랑(브랜드), 머문 곳 = 주황, 지금 여기 = 초록(Pleos informative_active).
 */

// Pleos Grayscale (라이트 기준 이름)
private val Gray00 = Color(0xFFFFFFFF)
private val Gray50 = Color(0xFFF7F8FA)
private val Gray100 = Color(0xFFEDEEF2)
private val Gray200 = Color(0xFFE0E1E6)
private val Gray300 = Color(0xFFC7C9CE)
private val Gray400 = Color(0xFFA4A8AE)
private val Gray500 = Color(0xFF686A72)
private val Gray600 = Color(0xFF44464E)
private val Gray700 = Color(0xFF313236)
private val Gray800 = Color(0xFF1D1E21)
private val Gray900 = Color(0xFF131417)

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
    val isDark: Boolean,
)

internal val LightColors = EodiColors(
    background = Color(0xFFF2F3F6),
    surface = Gray00,
    surfaceHigh = Gray50,
    outline = Gray200,
    textPrimary = Gray900,
    textSecondary = Gray600,
    textTertiary = Gray500,
    accent = Color(0xFF0064FF),
    onAccent = Gray00,
    accentSoft = Color(0xFFE6EFFF),
    trip = Color(0xFF0064FF),
    tripSoft = Color(0xFFE6EFFF),
    stay = Color(0xFFF26A1B),
    staySoft = Color(0xFFFFEEE3),
    here = Color(0xFF02B25D),
    hereSoft = Color(0xFFDDF7E9),
    onMarker = Gray00,
    error = Color(0xFFE5340F),
    isDark = false,
)

internal val DarkColors = EodiColors(
    background = Gray900,
    surface = Gray800,
    surfaceHigh = Gray700,
    outline = Gray600,
    textPrimary = Color(0xFFF7F8FA),
    textSecondary = Gray300,
    textTertiary = Gray400,
    accent = Color(0xFF2E8BFF),
    onAccent = Gray00,
    accentSoft = Color(0xFF14284A),
    trip = Color(0xFF2E8BFF),
    tripSoft = Color(0xFF14284A),
    stay = Color(0xFFFF8A3D),
    staySoft = Color(0xFF3A2416),
    here = Color(0xFF00E074),
    hereSoft = Color(0xFF113524),
    onMarker = Gray00,
    error = Color(0xFFFF5A3A),
    isDark = true,
)

/**
 * 지도 위 경로 · 핀 색. 바탕 지도(assets/map/style.json)가 라이트 하나뿐이라
 * 앱이 다크여도 지도 위 색은 라이트 지도에 맞춘 값 하나를 쓴다.
 */
internal object MapInk {
    val trip = Color(0xFF0064FF)
    val casing = Gray00
    val stay = Color(0xFFF26A1B)
    val here = Color(0xFF02B25D)
    val selectedStroke = Gray00
    val onMarker = Gray00
    val label = Gray900
    val labelHalo = Gray00
}

internal val LocalEodiColors = staticCompositionLocalOf { LightColors }
