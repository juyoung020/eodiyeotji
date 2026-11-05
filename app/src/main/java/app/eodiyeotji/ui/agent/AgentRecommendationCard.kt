package app.eodiyeotji.ui.agent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eodiyeotji.R
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.ui.map.DayMap
import app.eodiyeotji.ui.place.PlacePhotoGallery
import java.time.LocalDate

private val CardShape = RoundedCornerShape(32.dp)

/**
 * 추천 카드: 지도(그날 경로) + 종류·날짜 + 제목 + 요약 + 들른 곳 번호 칩.
 * 칩을 누르면 그곳이 "여기만 가기" 대상이 되고, 위쪽이 그곳 대표 사진(구글 지도 사진)으로 바뀐다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecommendationCard(
    rec: RecommendationUi,
    today: LocalDate,
    selectedStop: Int,
    onSelectStop: (Int) -> Unit,
    mapHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val facts = rec.hit.episode.facts
    val stops = rec.stops
    var showPhotos by rememberSaveable(facts.id) { mutableStateOf(false) }
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // 위쪽: 지도 ↔ 고른 곳 대표 사진 (들른 곳 칩을 누르면 사진으로 바뀐다)
            val photoStop = stops.getOrNull(selectedStop)?.takeIf { showPhotos }
            Box(Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))) {
                val map = rec.map
                when {
                    photoStop != null -> PlacePhotoGallery(photoStop, Modifier.fillMaxSize())
                    !rec.mapLoaded -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    map?.bounds != null -> DayMap(data = map, selectedKey = null, onSelectKey = {}, modifier = Modifier.fillMaxSize())
                    else -> CourseSketch(stops, selectedStop, Modifier.fillMaxSize())
                }
                if (stops.isNotEmpty()) {
                    MediaToggle(
                        photos = photoStop != null,
                        onChange = { showPhotos = it },
                        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    )
                }
                if (photoStop != null) {
                    Text(
                        "${selectedStop + 1}  ${photoStop.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                            .background(Color(0x99000000), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(kindText(facts.kind))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        memoryDateLine(facts.day, today),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    rec.hit.episode.title ?: rec.hit.dayTitle ?: facts.ruleLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    listOf(
                        facts.region,
                        stringResource(R.string.agent_stops_count, stops.size),
                        kmText(facts.distanceM),
                        spanText(facts.endMs - facts.startMs),
                    ).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = AgentSubStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stops.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        stops.forEachIndexed { i, stop ->
                            StopChip(i + 1, stop, selected = i == selectedStop, onClick = { onSelectStop(i); showPhotos = true })
                        }
                    }
                }
            }
        }
    }
}

/** 지도 | 사진 전환 (카드 위쪽 오른편). */
@Composable
private fun MediaToggle(photos: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color(0xE6FFFFFF), CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to "지도", true to "사진").forEach { (value, label) ->
            val on = value == photos
            Surface(
                onClick = { onChange(value) },
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else Color(0xFF333333),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 들른 곳 칩: [① 경포해변]. 고른 칩은 진하게. */
@Composable
private fun StopChip(number: Int, stop: StopFact, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.primary else colors.surfaceContainerHighest,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier.heightIn(min = AgentTouchMin),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 24.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).background(if (selected) colors.onPrimary else colors.primary, CircleShape),
            ) {
                Text(
                    number.toString(),
                    style = AgentSubStyle,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) colors.primary else colors.onPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stop.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (stop.category.isNotBlank()) {
                    Text(
                        stop.category,
                        style = AgentSubStyle,
                        color = if (selected) colors.onPrimary.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun KindBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text,
            style = AgentSubStyle,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * 지도 기록이 없을 때(오래된 날·지도 파일 없음) 지도 대신 그리는 코스 그림.
 * 위치는 실제 좌표가 아니라 순서만 보이도록 고르게 늘어놓는다.
 */
@Composable
private fun CourseSketch(stops: List<StopFact>, selected: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val caption = stringResource(R.string.agent_map_none)
    val numberStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val nameStyle = AgentSubStyle.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    val captionStyle = AgentSubStyle.copy(color = colors.onSurfaceVariant)
    Canvas(modifier.background(colors.surfaceContainerHigh)) {
        // 바탕 점 격자 (지도 느낌)
        val step = 28.dp.toPx()
        val dot = colors.outlineVariant.copy(alpha = 0.6f)
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(dot, radius = 1.5.dp.toPx(), center = Offset(x, y))
                x += step
            }
            y += step
        }
        if (stops.isEmpty()) return@Canvas

        val padX = size.width * 0.12f
        val midY = size.height * 0.48f
        val amp = size.height * 0.14f
        val n = stops.size
        val points = List(n) { i ->
            val t = if (n == 1) 0.5f else i / (n - 1f)
            Offset(padX + t * (size.width - 2 * padX), midY + if (i % 2 == 0) -amp else amp)
        }
        // 들른 곳을 잇는 부드러운 점선 경로
        if (n > 1) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until n) {
                    val a = points[i - 1]
                    val b = points[i]
                    val cx = (a.x + b.x) / 2
                    cubicTo(cx, a.y, cx, b.y, b.x, b.y)
                }
            }
            drawPath(path, colors.surface, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                path,
                colors.primary,
                style = Stroke(
                    width = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 12.dp.toPx())),
                ),
            )
        }
        val r = 22.dp.toPx()
        points.forEachIndexed { i, p ->
            val on = i == selected
            // 고른 곳은 바깥 고리로 표시 (칩과 같은 색)
            if (on) drawCircle(colors.primary.copy(alpha = 0.25f), radius = r + 14.dp.toPx(), center = p)
            drawCircle(colors.surface, radius = r + 4.dp.toPx(), center = p)
            drawCircle(colors.primary, radius = r, center = p)
            val num = measurer.measure((i + 1).toString(), numberStyle.copy(color = colors.onPrimary))
            drawText(num, topLeft = Offset(p.x - num.size.width / 2f, p.y - num.size.height / 2f))
            val label = measurer.measure(stops[i].name, nameStyle, maxLines = 1)
            val ly = if (i % 2 == 0) p.y - r - 8.dp.toPx() - label.size.height else p.y + r + 8.dp.toPx()
            drawText(label, topLeft = Offset((p.x - label.size.width / 2f).coerceIn(8f, size.width - label.size.width - 8f), ly))
        }
        val cap = measurer.measure(caption, captionStyle)
        drawText(cap, topLeft = Offset(size.width - cap.size.width - 20.dp.toPx(), size.height - cap.size.height - 16.dp.toPx()))
    }
}

/** "다른 날" 한 줄. 누르면 그날로 다시 묻는다. */
@Composable
internal fun OtherDayRow(rec: RecommendationUi, today: LocalDate, onAsk: (String) -> Unit) {
    val facts = rec.hit.episode.facts
    val title = rec.hit.episode.title ?: rec.hit.dayTitle ?: facts.ruleLabel
    val dateLine = memoryDateLine(facts.day, today)
    val question = stringResource(R.string.agent_followup_pick_day, memoryDateText(facts.day), title)
    Surface(
        onClick = { onAsk(question) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            ) {
                Icon(AgentIcons.Calendar, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dateLine + "  ·  " + rec.stops.joinToString(" → ") { it.name },
                    style = AgentSubStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(AgentIcons.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}
