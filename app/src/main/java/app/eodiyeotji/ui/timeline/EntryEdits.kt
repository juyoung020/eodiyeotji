package app.eodiyeotji.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eodiyeotji.core.DayEntry
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.ui.places.PlacesIcons
import app.eodiyeotji.ui.theme.EodiTheme

/**
 * 타임라인 항목 · 하루 상세 머리의 "⋯"(길게 누르기 포함)를 누르면 부를 곳 (ui/places의 기록 고치기 메뉴).
 * [TimelineRoute]가 넣어 준다. 없으면(미리보기 등) ⋯ 버튼을 그리지 않는다.
 */
@Stable
class TimelineEdits(val onEntry: (DayEntry) -> Unit, val onDay: (DaySummary) -> Unit)

val LocalTimelineEdits = staticCompositionLocalOf<TimelineEdits?> { null }

/** 둥근 ⋯ 버튼 (터치 [size], 기본 76dp). */
@Composable
internal fun MoreButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 76.dp, filled: Boolean = true) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) c.surfaceHigh else Color.Transparent,
        contentColor = c.textSecondary,
        modifier = modifier.size(size).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(PlacesIcons.More, null, modifier = Modifier.size(32.dp)) }
    }
}
