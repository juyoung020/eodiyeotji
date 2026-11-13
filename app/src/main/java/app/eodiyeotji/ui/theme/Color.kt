package app.eodiyeotji.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 색 토큰 — "길 위의 앨범" (docs/90_디자인_컨셉.md). 차의 낮 · 밤 모드를 따라 바뀐다.
 * - 라이트 = 낮: 옅은 회색 바탕에 흰 카드, 먹색 글자.
 * - 다크 = 밤, 검은 앨범: 검은 종이 바탕에 흰 글자 (밤 운전에 눈이 덜 부시다).
 * 강조색은 파랑(#0064FF) 하나: 달린 길 · 기억 · 혜택 · 정보 모두 파랑 계열. 그 밖의 뜻 색은 먹색/흰색(머문 곳) · 초록(지금 여기) · 빨강(하트 · 오류)뿐.
 * 바탕 회색은 노란 기가 없는 중립 회색 (베이지 · 금색 · 귤색은 쓰지 않는다).
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
    /** 바탕 위에 바로 쓰는 기억 색 (반짝임 아이콘 · 기억 글자). 라이트는 읽히도록 진한 파랑 */
    val memoryInk: Color,
    val heart: Color,
    val heartSoft: Color,
    val star: Color,
    /** 제휴 혜택 · 할인 쿠폰 (파랑 계열). 배지 바탕은 [couponSoft], 그 위 글자는 [couponInk] */
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
    background = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEEF0F3),
    outline = Color(0xFFDDE1E6),
    textPrimary = Color(0xFF0F1419),
    textSecondary = Color(0xFF4B5563),
    textTertiary = Color(0xFF656B76), // 옅은 판(surfaceHigh) 위에서도 4.5:1 (6B7280은 4.2:1)
    accent = Color(0xFF0064FF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE8F0FF),
    trip = Color(0xFF0064FF),
    tripSoft = Color(0xFFE8F0FF),
    stay = Color(0xFF1F2937),
    staySoft = Color(0xFFEEF0F3),
    here = Color(0xFF008F4F),
    hereSoft = Color(0xFFDDF5E8),
    onMarker = Color(0xFFFFFFFF),
    error = Color(0xFFD93A1E),
    memory = Color(0xFF0064FF),
    memorySoft = Color(0xFFE8F0FF),
    onMemory = Color(0xFFFFFFFF),
    memoryInk = Color(0xFF0050CC),
    heart = Color(0xFFE5364F),
    heartSoft = Color(0xFFFFE6EA),
    star = Color(0xFF0064FF),
    coupon = Color(0xFF0064FF),
    couponSoft = Color(0xFFE8F0FF),
    couponInk = Color(0xFF0050CC),
    info = Color(0xFF0064FF),
    infoSoft = Color(0xFFE8F0FF),
    ink = Color(0xFF111827),
    onInk = Color(0xFFFFFFFF),
    onInkMuted = Color(0xFFD1D5DB),
    scrim = Color(0x8C121211),
    isDark = false,
)

internal val DarkColors = EodiColors(
    background = Color(0xFF111214),
    surface = Color(0xFF1B1C1F),
    surfaceHigh = Color(0xFF26282C),
    outline = Color(0xFF383B40),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFFD1D5DB),
    textTertiary = Color(0xFF9CA3AF),
    accent = Color(0xFF3A87FF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF16294A),
    trip = Color(0xFF3A87FF),
    tripSoft = Color(0xFF16294A),
    stay = Color(0xFFE5E7EB),
    staySoft = Color(0xFF26282C),
    here = Color(0xFF2FD98C),
    hereSoft = Color(0xFF0F3324),
    onMarker = Color(0xFF121211),
    error = Color(0xFFFF6B57),
    memory = Color(0xFF3A87FF),
    memorySoft = Color(0xFF16294A),
    onMemory = Color(0xFFFFFFFF),
    memoryInk = Color(0xFF8AB4FF),
    heart = Color(0xFFFF5C74),
    heartSoft = Color(0xFF3D1A22),
    star = Color(0xFF8AB4FF),
    coupon = Color(0xFF3A87FF),
    couponSoft = Color(0xFF16294A),
    couponInk = Color(0xFF8AB4FF),
    info = Color(0xFF8AB4FF),
    infoSoft = Color(0xFF16294A),
    ink = Color(0xFF22304B),
    onInk = Color(0xFFF3F4F6),
    onInkMuted = Color(0xFFD1D5DB),
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

/**
 * 같이 가기: 다른 차 점 색 (보라 · 청록 · 분홍 · 회청). 나(앱 파랑 [EodiColors.accent])와 잔량 부족 경고([EodiColors.error])와
 * 헷갈리지 않는 색만 쓴다. 위에 흰 글자(이름 첫 글자)가 올라가므로 라이트 · 다크 공통으로 진한 쪽.
 */
val ConvoyCarColors = listOf(Color(0xFF7C4DDB), Color(0xFF0F8F7A), Color(0xFFC2306A), Color(0xFF4B5B70))
