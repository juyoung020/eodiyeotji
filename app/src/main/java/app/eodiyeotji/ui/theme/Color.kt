package app.eodiyeotji.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 색 토큰 — "길 위의 앨범" (docs/90_디자인_컨셉.md). 차의 낮 · 밤 모드를 따라 바뀐다.
 * - 라이트 = 낮, 종이 앨범: 종이색 바탕에 흰 카드, 먹색 글자.
 * - 다크 = 밤, 검은 앨범: 검은 종이 바탕에 흰 글자 (밤 운전에 눈이 덜 부시다).
 * 파랑은 정보 강조(info, 출장 경비 · 날짜)에만 쓴다. 의미 색은 셋: 금색(달린 길 · 기억) · 먹색/흰색(머문 곳 · 주된 동작) · 초록(지금 여기).
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
    /** 정보 강조 파랑 (앱 아이콘과 같은 산뜻한 파랑, 남색 아님). 출장 경비 · 날짜 강조. 넓은 면은 [infoSoft] */
    val info: Color,
    val infoSoft: Color,
    /** 떠 있는 안내 띠(코스 안내 중)처럼 테마와 상관없이 진한 먹색 판 */
    val ink: Color,
    val onInk: Color,
    val onInkMuted: Color,
    /** 사진 위 글자 받침 */
    val scrim: Color,
    val isDark: Boolean,
)

internal val LightColors = EodiColors(
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEDECE8),
    outline = Color(0xFFDEDCD7),
    textPrimary = Color(0xFF0F1419),
    textSecondary = Color(0xFF52504B),
    textTertiary = Color(0xFF6F6C66),
    accent = Color(0xFF0064FF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE8F0FF),
    trip = Color(0xFF0064FF),
    tripSoft = Color(0xFFE8F0FF),
    stay = Color(0xFF2C2B28),
    staySoft = Color(0xFFEDECE8),
    here = Color(0xFF008F4F),
    hereSoft = Color(0xFFDDF5E8),
    onMarker = Color(0xFFFFFFFF),
    error = Color(0xFFD93A1E),
    memory = Color(0xFFFFC233),
    memorySoft = Color(0xFFFFF3D1),
    onMemory = Color(0xFF3A2600),
    memoryInk = Color(0xFF8A5A00),
    heart = Color(0xFFE5364F),
    heartSoft = Color(0xFFFFE6EA),
    star = Color(0xFFF5A800),
    coupon = Color(0xFFF26B1D),
    couponSoft = Color(0xFFFFF0E5),
    couponInk = Color(0xFFB6460A),
    info = Color(0xFF2F6BEB),
    infoSoft = Color(0xFFE6EEFD),
    ink = Color(0xFF141B2B),
    onInk = Color(0xFFFFFFFF),
    onInkMuted = Color(0xFFD0CEC9),
    scrim = Color(0x8C121211),
    isDark = false,
)

internal val DarkColors = EodiColors(
    background = Color(0xFF121211),
    surface = Color(0xFF1C1B1A),
    surfaceHigh = Color(0xFF282725),
    outline = Color(0xFF3A3936),
    textPrimary = Color(0xFFF2F1EE),
    textSecondary = Color(0xFFD0CEC9),
    textTertiary = Color(0xFF95928C),
    accent = Color(0xFF3A87FF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF16294A),
    trip = Color(0xFF3A87FF),
    tripSoft = Color(0xFF16294A),
    stay = Color(0xFFE9E9E4),
    staySoft = Color(0xFF2C2B28),
    here = Color(0xFF2FD98C),
    hereSoft = Color(0xFF0F3324),
    onMarker = Color(0xFF121211),
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
    info = Color(0xFF7FA6FF),
    infoSoft = Color(0xFF1C2A44),
    ink = Color(0xFF22304B),
    onInk = Color(0xFFF2F1EE),
    onInkMuted = Color(0xFFD0CEC9),
    scrim = Color(0x99121211),
    isDark = true,
)

/**
 * 지도 위 경로 · 핀 색. 바탕 지도가 낮에는 밝은 지도, 밤에는 어두운 지도(MapStyleSource가 색을 바꾼다)라
 * 지도 위 색도 둘이다. 뜻은 같다: 길은 파랑, 머문 곳은 점(낮 = 먹색, 밤 = 흰색), 지금 있는 곳은 초록.
 */
internal class MapInk(
    val trip: Color,
    val casing: Color,
    val stay: Color,
    val here: Color,
    val selectedStroke: Color,
    val onMarker: Color,
    val label: Color,
    val labelHalo: Color,
)

private val MapInkDay = MapInk(
    trip = Color(0xFF0064FF),
    casing = Color(0xFFFFFFFF),
    stay = Color(0xFF2C2B28),
    here = Color(0xFF00A35A),
    selectedStroke = Color(0xFFFFFFFF),
    onMarker = Color(0xFFFFFFFF),
    label = Color(0xFF0F1419),
    labelHalo = Color(0xFFFFFFFF),
)

private val MapInkNight = MapInk(
    trip = Color(0xFF3A87FF),
    casing = Color(0xFF121211),
    stay = Color(0xFFE9E9E4),
    here = Color(0xFF2FD98C),
    selectedStroke = Color(0xFF9DBBFF),
    onMarker = Color(0xFF121211),
    label = Color(0xFFF2F1EE),
    labelHalo = Color(0xFF121211),
)

internal fun mapInk(dark: Boolean): MapInk = if (dark) MapInkNight else MapInkDay

internal val LocalEodiColors = staticCompositionLocalOf { LightColors }
