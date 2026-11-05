package app.eodiyeotji.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * 글자 단계. 크기 · 줄 높이는 Pleos 디자인 가이드 Font Hierarchy(160dpi 기준 px = sp)를 그대로 쓴다:
 *   Headline 60/80 · 56/72 · 48/64 (Bold)   Title 40/52 · 36/44 · 32/40   Body 30/38 · 28/36 · 26/34   Label 24/28 · 20/24
 * 팔 길이에서 1~2초 안에 읽히도록 휴대폰 앱보다 두 배쯤 크다. 글꼴은 시스템(한글 Noto Sans CJK)을 쓴다.
 * 시각 · 거리 · 시간 숫자는 고정폭 숫자(tnum)로 → 목록에서 세로로 줄이 맞는다.
 */

private const val TABULAR = "tnum"

/** 한글은 글자 사이가 넓어 보여서 큰 글자일수록 살짝 좁힌다. */
private fun style(size: Int, line: Int, weight: FontWeight, tracking: TextUnit = 0.sp, tabular: Boolean = false) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking,
    fontFeatureSettings = if (tabular) TABULAR else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** 화면 코드가 쓰는 의미 단위 글자 스타일. */
@Immutable
data class EodiType(
    /** 하루 상세 제목 (날짜) */
    val hero: TextStyle = style(48, 60, FontWeight.Bold, (-0.02).em),
    /** 머문 곳 이름, 목록의 날짜 */
    val title: TextStyle = style(34, 44, FontWeight.Bold, (-0.015).em),
    /** 앱 이름 · 통계 값 */
    val titleSmall: TextStyle = style(32, 40, FontWeight.Bold, (-0.01).em, tabular = true),
    /** 주행 경로 이름처럼 제목보다 한 단계 낮은 본문 */
    val bodyStrong: TextStyle = style(28, 36, FontWeight.SemiBold),
    val body: TextStyle = style(26, 34, FontWeight.Normal),
    /** 시각 (항목 왼쪽 열) */
    val time: TextStyle = style(28, 34, FontWeight.Bold, tabular = true),
    /** 숫자가 섞인 둘째 줄 (거리 · 시간) */
    val meta: TextStyle = style(24, 30, FontWeight.Normal, tabular = true),
    val label: TextStyle = style(24, 28, FontWeight.SemiBold),
    /** 작은 표시 (내비 · 오늘 배지, 통계 이름). Pleos 최소 크기 20 */
    val caption: TextStyle = style(20, 24, FontWeight.Medium, tabular = true),
)

internal val LocalEodiType = staticCompositionLocalOf { EodiType() }

/** Material 컴포넌트(버튼 글자 등)가 쓰는 단계도 같은 크기로 맞춘다. */
internal fun materialTypography(t: EodiType) = Typography(
    displayLarge = style(60, 80, FontWeight.ExtraBold),
    displayMedium = style(56, 72, FontWeight.ExtraBold),
    displaySmall = style(48, 64, FontWeight.Bold),
    headlineLarge = style(48, 64, FontWeight.Bold),
    headlineMedium = style(40, 52, FontWeight.Bold),
    headlineSmall = style(36, 44, FontWeight.Bold),
    titleLarge = t.titleSmall,
    titleMedium = t.bodyStrong,
    titleSmall = t.label,
    bodyLarge = style(30, 38, FontWeight.Normal),
    bodyMedium = style(28, 36, FontWeight.Normal),
    bodySmall = t.body,
    labelLarge = t.label,
    labelMedium = t.caption,
    labelSmall = t.caption,
)
