package app.eodiyeotji.ui.recap

import app.eodiyeotji.ui.community.StopPhotoBox
import app.eodiyeotji.ui.place.LocalOpenPlace
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.MonthRecap
import app.eodiyeotji.core.RecapPlace
import app.eodiyeotji.core.RecapRegion
import app.eodiyeotji.core.RecapTotals
import app.eodiyeotji.core.YearRecap
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import kotlin.math.abs

/** 요약 화면을 이 폭 이상에서 두 칸으로 나눈다 (자주 간 곳 | 다녀온 곳). */
private val TwoColumn = 900.dp

/**
 * 요약 화면 (열려 있을 때만 그린다): 머리(뒤로 · 달별 요약 | 올해 결산 · ‹ 달 ›) + 요약 카드.
 * 내 기록 화면 위를 덮는다. [onOpenDay]: 최고의 날 등을 누르면 요약을 닫고 그날을 연다.
 */
@Composable
fun RecapOverlay(viewModel: RecapViewModel, onOpenDay: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.open) return
    BackHandler(onBack = viewModel::closeRecap)
    val c = EodiTheme.colors
    val open: (String) -> Unit = { day ->
        viewModel.closeRecap()
        onOpenDay(day)
    }
    Surface(color = c.background, contentColor = c.textPrimary, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            RecapHeader(
                mode = state.mode,
                title = when (state.mode) {
                    RecapMode.MONTH -> stringResource(R.string.recap_title_month, state.year, state.month)
                    RecapMode.YEAR -> state.year.toString()
                },
                hasPrev = when (state.mode) {
                    RecapMode.MONTH -> (state.monthRecap as? Load.Ready)?.value?.hasPrev ?: false
                    RecapMode.YEAR -> (state.yearRecap as? Load.Ready)?.value?.hasPrev ?: false
                },
                hasNext = when (state.mode) {
                    RecapMode.MONTH -> (state.monthRecap as? Load.Ready)?.value?.hasNext ?: false
                    RecapMode.YEAR -> (state.yearRecap as? Load.Ready)?.value?.hasNext ?: false
                },
                onBack = viewModel::closeRecap,
                onMode = viewModel::setMode,
                onShift = viewModel::shift,
            )
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = EodiTheme.space.page, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                val cardModifier = Modifier.widthIn(max = 1280.dp).fillMaxWidth().padding(bottom = EodiTheme.space.page)
                when (state.mode) {
                    RecapMode.MONTH -> when (val m = state.monthRecap) {
                        Load.Loading -> StateText(stringResource(R.string.recap_loading))
                        Load.Failed -> StateText(stringResource(R.string.recap_failed))
                        is Load.Ready -> MonthRecapCard(m.value, cardModifier, onOpenDay = open)
                    }
                    RecapMode.YEAR -> when (val y = state.yearRecap) {
                        Load.Loading -> StateText(stringResource(R.string.recap_loading))
                        Load.Failed -> StateText(stringResource(R.string.recap_failed))
                        is Load.Ready -> Column(cardModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!state.driving && y.value.recordDays > 0) {
                                EntryPill(stringResource(R.string.memory_map_entry), gold = true, Modifier.fillMaxWidth()) { viewModel.openMemoryMap() }
                            }
                            YearRecapCard(y.value, Modifier.fillMaxWidth(), onOpenDay = open)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapHeader(
    mode: RecapMode,
    title: String,
    hasPrev: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onMode: (RecapMode) -> Unit,
    onShift: (Int) -> Unit,
) {
    val c = EodiTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = EodiTheme.space.page, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(onBack, stringResource(R.string.recap_back)) {
            Icon(EodiIcons.Back, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.width(16.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeTab(stringResource(R.string.recap_tab_month), mode == RecapMode.MONTH, Modifier.weight(1f, fill = false)) { onMode(RecapMode.MONTH) }
            ModeTab(stringResource(R.string.recap_tab_year), mode == RecapMode.YEAR, Modifier.weight(1f, fill = false)) { onMode(RecapMode.YEAR) }
        }
        Spacer(Modifier.width(16.dp))
        RoundButton({ if (hasPrev) onShift(-1) }, stringResource(R.string.recap_prev), enabled = hasPrev) {
            Icon(EodiIcons.Chevron, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(32.dp).rotate(180f))
        }
        Text(
            title,
            style = EodiTheme.type.bodyStrong,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 120.dp).padding(horizontal = 8.dp).semantics { heading() },
        )
        RoundButton({ if (hasNext) onShift(1) }, stringResource(R.string.recap_next), enabled = hasNext) {
            Icon(EodiIcons.Chevron, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, label: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    val c = EodiTheme.colors
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(c.surface)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.background(c.background.copy(alpha = 0.55f))),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Text(
        text,
        style = EodiTheme.type.label,
        fontWeight = FontWeight.Bold,
        color = if (selected) c.onAccent else c.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent else c.surface)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    )
}

@Composable
private fun StateText(text: String) {
    Text(text, style = EodiTheme.type.body, color = EodiTheme.colors.textTertiary, modifier = Modifier.padding(top = 80.dp))
}

// ---------------------------------------------------------------- 요약 카드 (공유 이미지로 찍을 수 있게 상태만 받는다)

/**
 * 한 달 요약 카드. 화면 상태(ViewModel)를 모르고 [recap]만 그린다 → 공유 이미지 만들 때 그대로 찍을 수 있다.
 * [onOpenDay]가 null이면 누를 곳이 없다 (공유 이미지용).
 */
@Composable
fun MonthRecapCard(recap: MonthRecap, modifier: Modifier = Modifier, onOpenDay: ((String) -> Unit)? = null) {
    val c = EodiTheme.colors
    RecapCardFrame(modifier) {
        val eyebrow = stringResource(R.string.recap_title_month, recap.year, recap.month) +
            if (recap.inProgress) " · " + stringResource(R.string.recap_until_day, java.time.LocalDate.now().dayOfMonth) else ""
        Text(eyebrow, style = EodiTheme.type.eyebrow, color = c.memoryInk)
        Text(stringResource(R.string.recap_headline, distanceText(recap.totals.distanceM)), style = EodiTheme.type.hero, color = c.textPrimary)
        if (recap.recordDays == 0) {
            Text(stringResource(R.string.recap_empty), style = EodiTheme.type.body, color = c.textTertiary)
        } else {
            MonthBody(recap, onOpenDay)
        }
    }
}

@Composable
private fun MonthBody(recap: MonthRecap, onOpenDay: ((String) -> Unit)?) {
    val c = EodiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TotalsStrip(recap.totals)
        if (recap.hasPrevRecords) {
            val km = distanceText(abs(recap.vsPrevDistanceM))
            val distance = when {
                recap.inProgress && recap.vsPrevDistanceM >= 0 -> stringResource(R.string.recap_compare_more_same_period, km)
                recap.inProgress -> stringResource(R.string.recap_compare_less_same_period, km)
                recap.vsPrevDistanceM >= 0 -> stringResource(R.string.recap_compare_more, km)
                else -> stringResource(R.string.recap_compare_less, km)
            }
            val outings = when {
                recap.vsPrevOutings > 0 -> stringResource(R.string.recap_compare_outings_more, recap.vsPrevOutings)
                recap.vsPrevOutings < 0 -> stringResource(R.string.recap_compare_outings_less, -recap.vsPrevOutings)
                else -> null
            }
            Text(listOfNotNull(distance, outings).joinToString(" · "), style = EodiTheme.type.body, color = c.textSecondary)
        }
        recap.bestDay?.let {
            SectionTitle(stringResource(R.string.recap_best_day_month))
            RecapDayRow(stringResource(R.string.recap_best_day_month), it, onOpenDay)
        }
        PlacesAndRegions(recap.topPlaces, recap.regions)
    }
}

/** 올해 결산 카드 ("올해의 어디였지"). [MonthRecapCard]처럼 상태만 받는다. */
@Composable
fun YearRecapCard(recap: YearRecap, modifier: Modifier = Modifier, onOpenDay: ((String) -> Unit)? = null) {
    val c = EodiTheme.colors
    RecapCardFrame(modifier) {
        Text(stringResource(R.string.recap_title_year, recap.year), style = EodiTheme.type.eyebrow, color = c.memoryInk)
        Text(stringResource(R.string.recap_headline, distanceText(recap.totals.distanceM)), style = EodiTheme.type.hero, color = c.textPrimary)
        if (recap.recordDays == 0) {
            Text(stringResource(R.string.recap_empty), style = EodiTheme.type.body, color = c.textTertiary)
        } else {
            YearBody(recap, onOpenDay)
        }
    }
}

@Composable
private fun YearBody(recap: YearRecap, onOpenDay: ((String) -> Unit)?) {
    val c = EodiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TotalsStrip(recap.totals)
        SectionTitle(stringResource(R.string.recap_months_chart))
        MonthBars(recap.monthDistanceM)
        SectionTitle(stringResource(R.string.recap_seasons))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth >= TwoColumn) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                recap.seasons.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { s ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(EodiTheme.space.radiusM))
                                    .background(c.surfaceHigh)
                                    .then(if (onOpenDay != null && s.bestDay != null) Modifier.clickable { onOpenDay(s.bestDay.day) } else Modifier)
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(s.name, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                Text(distanceText(s.distanceM), style = EodiTheme.type.titleSmall, color = c.textPrimary)
                                Text(stringResource(R.string.recap_season_days, s.outingDays), style = EodiTheme.type.caption, color = c.textTertiary)
                                Text(
                                    s.bestDay?.title ?: "—",
                                    style = EodiTheme.type.meta,
                                    color = c.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        recap.longestTrip?.let {
            SectionTitle(stringResource(R.string.recap_longest_trip))
            RecapDayRow(stringResource(R.string.recap_longest_trip), it, onOpenDay)
        }
        recap.bestDay?.takeIf { it.day != recap.longestTrip?.day }?.let {
            SectionTitle(stringResource(R.string.recap_best_day_year))
            RecapDayRow(stringResource(R.string.recap_best_day_year), it, onOpenDay)
        }
        PlacesAndRegions(recap.topPlaces, recap.regions)
    }
}

@Composable
private fun RecapCardFrame(modifier: Modifier, content: @Composable () -> Unit) {
    val c = EodiTheme.colors
    Surface(shape = RoundedCornerShape(EodiTheme.space.radiusL), color = c.surface, modifier = modifier) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

@Composable
private fun TotalsStrip(t: RecapTotals) {
    StatStrip(
        listOf(
            durationText(t.driveMillis) to stringResource(R.string.recap_stat_time),
            stringResource(R.string.recap_count_days, t.driveDays) to stringResource(R.string.recap_stat_days),
            stringResource(R.string.recap_count_times, t.outings) to stringResource(R.string.recap_stat_outings),
            stringResource(R.string.recap_count_times, t.trips) to stringResource(R.string.recap_stat_trips),
            stringResource(R.string.recap_count_places, t.newPlaces) to stringResource(R.string.recap_stat_new),
        ),
    )
}

/** 자주 간 곳 | 다녀온 곳 (넓으면 두 칸, 좁으면 위아래). */
@Composable
private fun PlacesAndRegions(places: List<RecapPlace>, regions: List<RecapRegion>) {
    if (places.isEmpty() && regions.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= TwoColumn) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) { TopPlaces(places) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { Regions(regions) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopPlaces(places)
                Regions(regions)
            }
        }
    }
}

@Composable
private fun TopPlaces(places: List<RecapPlace>) {
    if (places.isEmpty()) return
    val c = EodiTheme.colors
    SectionTitle(stringResource(R.string.recap_top_places))
    val openPlace = LocalOpenPlace.current
    places.forEachIndexed { i, p ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${i + 1}", style = EodiTheme.type.titleSmall, color = c.textTertiary, modifier = Modifier.width(40.dp))
            StopPhotoBox(p.place.name, p.place.address, p.place.lat, p.place.lon, null, Modifier.size(76.dp), iconSize = 36.dp, loadingColor = c.surfaceHigh, onClick = { openPlace(p.place) })
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.place.name, style = EodiTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.place.category, style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(R.string.recap_count_days, p.days), style = EodiTheme.type.time, color = c.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Regions(regions: List<RecapRegion>) {
    if (regions.isEmpty()) return
    val c = EodiTheme.colors
    SectionTitle(stringResource(R.string.recap_regions))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        regions.forEach { r ->
            Text(
                stringResource(R.string.recap_region_days, r.region, r.days),
                style = EodiTheme.type.label,
                color = c.textPrimary,
                modifier = Modifier
                    .border(2.dp, c.outline, CircleShape)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** 달마다 달린 거리 막대 12개 (가장 긴 달은 파랑, 나머지 옅은 파랑). 숫자는 Rust가 준 그대로 높이만 맞춘다. */
@Composable
private fun MonthBars(distances: List<Double>) {
    val c = EodiTheme.colors
    val max = distances.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = distances.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.56f
            distances.forEachIndexed { i, d ->
                val h = (d / max * size.height).toFloat().coerceAtLeast(if (d > 0) 4f else 0f)
                drawRoundRect(
                    color = if (d == max) c.accent else c.tripSoft,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            distances.indices.forEach { i ->
                Text("${i + 1}", style = EodiTheme.type.caption, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}
