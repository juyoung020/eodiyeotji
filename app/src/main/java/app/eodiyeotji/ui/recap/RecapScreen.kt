package app.eodiyeotji.ui.recap

import app.eodiyeotji.ui.community.SectionTitle
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.CenterMessage
import app.eodiyeotji.ui.community.LoadingBox
import app.eodiyeotji.ui.community.StopPhotoBox
import app.eodiyeotji.ui.place.LocalOpenPlace
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
                        Load.Loading -> LoadingBox(Modifier.padding(top = 80.dp), message = stringResource(R.string.recap_loading))
                        Load.Failed -> CenterMessage(stringResource(R.string.recap_failed), Modifier.padding(top = 48.dp))
                        is Load.Ready -> MonthRecapCard(m.value, cardModifier, onOpenDay = open)
                    }
                    RecapMode.YEAR -> when (val y = state.yearRecap) {
                        Load.Loading -> LoadingBox(Modifier.padding(top = 80.dp), message = stringResource(R.string.recap_loading))
                        Load.Failed -> CenterMessage(stringResource(R.string.recap_failed), Modifier.padding(top = 48.dp))
                        is Load.Ready -> Column(cardModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!state.driving && y.value.recordDays > 0) {
                                EntryPill(stringResource(R.string.memory_map_entry), EodiIcons.Map, ai = false, Modifier.fillMaxWidth()) { viewModel.openMemoryMap() }
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
    val back: @Composable () -> Unit = {
        RoundIconButton(EodiIcons.Back, stringResource(R.string.recap_back), onBack, containerColor = c.surface, iconSize = 36.dp)
    }
    val tabs: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeTab(stringResource(R.string.recap_tab_month), mode == RecapMode.MONTH, Modifier.weight(1f, fill = false)) { onMode(RecapMode.MONTH) }
            ModeTab(stringResource(R.string.recap_tab_year), mode == RecapMode.YEAR, Modifier.weight(1f, fill = false)) { onMode(RecapMode.YEAR) }
        }
    }
    val shift: @Composable () -> Unit = {
        // 넘어갈 수 없는 쪽 화살표는 흐리게 (눌러도 아무 일 없는 버튼이 살아 있는 것처럼 보이지 않게)
        RoundIconButton(EodiIcons.Chevron, stringResource(R.string.recap_prev), { onShift(-1) }, Modifier.alpha(if (hasPrev) 1f else DisabledAlpha), enabled = hasPrev, containerColor = c.surface, iconModifier = Modifier.rotate(180f))
        Text(
            title,
            style = EodiTheme.type.bodyStrong,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 120.dp).padding(horizontal = 8.dp).semantics { heading() },
        )
        RoundIconButton(EodiIcons.Chevron, stringResource(R.string.recap_next), { onShift(1) }, Modifier.alpha(if (hasNext) 1f else DisabledAlpha), enabled = hasNext, containerColor = c.surface)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= NarrowHeader) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = EodiTheme.space.page, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                back()
                Spacer(Modifier.width(16.dp))
                tabs(Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                shift()
            }
        } else {
            // 좁은 창(폰 세로): 한 줄에 다 두면 월간 · 연간 탭이 글자 없이 찌그러진다 → 탭 한 줄, 달 넘기기 한 줄
            Column(Modifier.fillMaxWidth().padding(horizontal = EodiTheme.space.page, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    back()
                    Spacer(Modifier.width(16.dp))
                    tabs(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { shift() }
            }
        }
    }
}

/** 이보다 좁으면 머리를 두 줄로 나눈다. */
private val NarrowHeader = 760.dp

/** 넘어갈 수 없는 화살표의 흐림 정도. */
private const val DisabledAlpha = 0.35f

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
        Text(eyebrow, style = EodiTheme.type.meta, color = c.textTertiary)
        RecapHeadline(distanceText(recap.totals.distanceM))
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
            // 위 칸(나들이 + 여행)과 같은 셈의 두 값을 그대로 적는다 ("지난달 1번 → 이번 달 2번")
            val outings = if (recap.outingCount != recap.prevOutingCount) {
                stringResource(R.string.recap_compare_outings, recap.prevOutingCount, recap.outingCount)
            } else {
                null
            }
            Text(listOfNotNull(distance, outings).joinToString(" · "), style = EodiTheme.type.body, color = c.textSecondary)
        }
        recap.bestDay?.let {
            SectionTitle(stringResource(R.string.recap_best_day_month), Modifier.padding(top = 8.dp), icon = EodiIcons.Sparkle)
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
        // 기간을 밝힌다 ("2026년 1월 1일 ~ 11월 11일") — 기억 지도(최근 1년)와 숫자가 다른 이유
        Text(recap.periodLabel, style = EodiTheme.type.meta, color = c.textTertiary)
        RecapHeadline(distanceText(recap.totals.distanceM))
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
        SectionTitle(stringResource(R.string.recap_months_chart), Modifier.padding(top = 8.dp), icon = EodiIcons.Sparkle)
        MonthBars(recap.monthDistanceM)
        SectionTitle(stringResource(R.string.recap_seasons), Modifier.padding(top = 8.dp), icon = EodiIcons.Sparkle)
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
                                Text(stringResource(R.string.recap_season_outings, s.outings), style = EodiTheme.type.caption, color = c.textTertiary)
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
            SectionTitle(stringResource(R.string.recap_longest_trip), Modifier.padding(top = 8.dp), icon = EodiIcons.Sparkle)
            RecapDayRow(stringResource(R.string.recap_longest_trip), it, onOpenDay)
        }
        recap.bestDay?.takeIf { it.day != recap.longestTrip?.day }?.let {
            SectionTitle(stringResource(R.string.recap_best_day_year), Modifier.padding(top = 8.dp), icon = EodiIcons.Sparkle)
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
    // 0인 칸("0번 나들이")은 빼고 있는 것만 크게 (운전 시간 · 달린 날은 늘 둔다)
    StatStrip(
        listOfNotNull(
            durationText(t.driveMillis) to stringResource(R.string.recap_stat_time),
            stringResource(R.string.recap_count_days, t.driveDays) to stringResource(R.string.recap_stat_days),
            (stringResource(R.string.recap_count_times, t.outings) to stringResource(R.string.recap_stat_outings)).takeIf { t.outings > 0 },
            (stringResource(R.string.recap_count_times, t.trips) to stringResource(R.string.recap_stat_trips)).takeIf { t.trips > 0 },
            (stringResource(R.string.recap_count_places, t.newPlaces) to stringResource(R.string.recap_stat_new)).takeIf { t.newPlaces > 0 },
        ),
    )
}

/** 자주 간 곳 | 다녀온 곳 (넓으면 두 칸, 좁으면 위아래). */
@Composable
private fun PlacesAndRegions(places: List<RecapPlace>, regions: List<RecapRegion>) {
    if (places.isEmpty() && regions.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 한쪽이 비면 두 칸으로 나누지 않는다 (다녀온 곳이 없을 때 "1일"이 카드 가운데에 떠 있었다)
        if (maxWidth >= TwoColumn && places.isNotEmpty() && regions.isNotEmpty()) {
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
    SectionTitle(stringResource(R.string.recap_top_places), Modifier.padding(top = 8.dp), icon = EodiIcons.Sparkle)
    val openPlace = LocalOpenPlace.current
    places.forEachIndexed { i, p ->
        // 줄 전체가 누르는 자리 (전에는 76dp 사진만 눌렸다)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { openPlace(p.place) },
        ) {
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
    SectionTitle(stringResource(R.string.recap_regions), Modifier.padding(top = 8.dp), icon = EodiIcons.Sparkle)
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

/**
 * 달마다 달린 거리 막대 12개: 막대 위에 그달 km (0인 달은 비운다), 가장 긴 달은 진한 파랑, 나머지도 흰 바탕에서 보이는 파랑(대비 3:1 이상).
 * 숫자는 Rust가 준 그대로 높이만 맞춘다.
 */
@Composable
private fun MonthBars(distances: List<Double>) {
    val c = EodiTheme.colors
    val max = distances.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val rest = c.accent.copy(alpha = 0.7f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 좁은 폰 화면(한 달 칸이 좁으면)에서는 숫자가 겹치니 가장 긴 달만 적는다
        val roomy = maxWidth / distances.size.coerceAtLeast(1) >= 72.dp
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(BarArea + BarLabel), verticalAlignment = Alignment.Bottom) {
                distances.forEach { d ->
                    val top = d == max
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (d > 0 && (roomy || top)) {
                            Text(
                                stringResource(R.string.timeline_km, String.format(Locale.ROOT, "%,d", (d / 1000.0).roundToInt())),
                                style = EodiTheme.type.caption,
                                fontWeight = if (top) FontWeight.Bold else FontWeight.Medium,
                                color = if (top) c.accent else c.textSecondary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Box(
                            Modifier
                                .fillMaxWidth(0.56f)
                                .height(if (d > 0) maxOf(BarArea * (d / max).toFloat().coerceIn(0f, 1f), 2.dp) else 0.dp)
                                .background(if (top) c.accent else rest, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                distances.indices.forEach { i ->
                    Text("${i + 1}", style = EodiTheme.type.caption, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private val BarArea = 140.dp
private val BarLabel = 40.dp

/**
 * 결산 머리 한 줄 "4,090km 달렸어요": 거리 숫자만 강조 파랑(accent) 굵게, 나머지는 보통 굵기로 (금색은 글자 강조에 쓰지 않는다) — 결산의 주인공은 숫자 하나.
 */
@Composable
private fun RecapHeadline(distance: String) {
    val c = EodiTheme.colors
    val full = stringResource(R.string.recap_headline, distance)
    val text = androidx.compose.runtime.remember(full, c) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(full)
            val i = full.indexOf(distance)
            if (i >= 0) addStyle(androidx.compose.ui.text.SpanStyle(color = c.accent, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), i, i + distance.length)
        }
    }
    Text(text, style = EodiTheme.type.display, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, color = c.textPrimary)
}
