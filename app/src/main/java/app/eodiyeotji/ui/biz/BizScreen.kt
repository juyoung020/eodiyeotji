package app.eodiyeotji.ui.biz

import androidx.compose.ui.platform.LocalConfiguration
import app.eodiyeotji.ui.common.PressSurface
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.eodiyeotji.core.rust.BizExpense
import app.eodiyeotji.core.rust.BizExpenseKind
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.rust.BizMonth
import app.eodiyeotji.core.rust.BizClient
import app.eodiyeotji.core.rust.BizTrip
import androidx.compose.foundation.horizontalScroll
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.ui.community.CenterMessage
import app.eodiyeotji.ui.community.LoadingBox
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.recap.Load
import app.eodiyeotji.ui.replay.LocalOpenReplay
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 출장 관리 (열려 있을 때만 그린다): 머리(뒤로 · 출장 관리 · ‹ 달 › · 정산서 내보내기) → 한 달 요약 칸 → 출장 카드 목록.
 * 출장 찾기 · 거리 · 에너지 · 경비 · CSV는 Rust(`biztrip.rs`)가 하고, 여기서는 그리기만 한다.
 */
@Composable
fun BizOverlay(viewModel: BizViewModel, onOpenDay: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.open) return
    BackHandler(onBack = viewModel::close)
    val c = EodiTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 정산서 파일 공유는 공유용 FileProvider가 있는 판(폰)에서만
    val canExport = remember(context) {
        runCatching { context.packageManager.resolveContentProvider("${context.packageName}.share", 0) != null }.getOrDefault(false)
    }
    Surface(color = c.background, contentColor = c.textPrimary, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        // 좁은 창(폰 세로): 머리 두 줄, 요약 칸 2x2 (한 줄에 두면 글자가 세로로 꺾인다)
        val narrow = maxWidth < NarrowWidth
        Column(Modifier.fillMaxSize()) {
            val back: @Composable () -> Unit = {
                RoundIconButton(EodiIcons.Back, stringResource(R.string.biz_back), viewModel::close, containerColor = c.surface, iconSize = 36.dp)
                Spacer(Modifier.width(16.dp))
            }
            val export: @Composable () -> Unit = {
                val ready = (state.month as? Load.Ready)?.value
                if (canExport && ready != null && ready.trips.isNotEmpty()) {
                    // 파일을 만드는 동안 버튼 안 로더 + 누름 막기
                    var exporting by remember { mutableStateOf(false) }
                    SecondaryPill(
                        stringResource(R.string.biz_export), EodiIcons.Calendar,
                        {
                            exporting = true
                            scope.launch {
                                try { share(context, viewModel, ready) } finally { exporting = false }
                            }
                        },
                        loading = exporting,
                    )
                }
            }
            val month: @Composable () -> Unit = {
                RoundIconButton(EodiIcons.Chevron, stringResource(R.string.biz_prev), { viewModel.shift(-1) }, containerColor = c.surface, iconModifier = Modifier.rotate(180f))
                Text(
                    stringResource(R.string.biz_month, state.ym.year, state.ym.monthValue),
                    style = EodiTheme.type.bodyStrong,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 150.dp).padding(horizontal = 8.dp),
                )
                // 이번 달 다음으로는 못 넘어간다 → 흐리게
                val canNext = state.ym < YearMonth.now()
                RoundIconButton(EodiIcons.Chevron, stringResource(R.string.biz_next), { viewModel.shift(1) }, Modifier.alpha(if (canNext) 1f else 0.35f), enabled = canNext, containerColor = c.surface)
            }
            if (narrow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = EodiTheme.space.page, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        back()
                        Text(stringResource(R.string.biz_title), style = EodiTheme.type.title, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                        export()
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { month() }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = EodiTheme.space.page, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    back()
                    Text(stringResource(R.string.biz_title), style = EodiTheme.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    export()
                    Spacer(Modifier.width(16.dp))
                    month()
                }
            }
            val ready = (state.month as? Load.Ready)?.value
            val selected = ready?.trips?.firstOrNull { it.episodeId == state.selected }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // 넓은 차 화면은 목록 옆에 내역 창, 좁은 화면(폰)은 내역이 목록 자리를 덮는다
                val wide = maxWidth >= 1100.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide || selected == null) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                                .padding(start = EodiTheme.space.page, end = if (selected != null) 12.dp else EodiTheme.space.page, top = 8.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            val inner = Modifier.widthIn(max = 2000.dp).fillMaxWidth().padding(bottom = EodiTheme.space.page)
                            when (val m = state.month) {
                                Load.Loading -> LoadingBox(Modifier.padding(top = 80.dp))
                                Load.Failed -> CenterMessage(stringResource(R.string.biz_failed), Modifier.padding(top = 48.dp))
                                is Load.Ready -> Column(inner, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    SummaryRow(m.value, narrow || selected != null)
                                    HowToggle()
                                    state.removedDay?.let { UndoBar(it, viewModel::undoNotBiz) }
                                    if (m.value.trips.isEmpty()) {
                                        CenterMessage(stringResource(R.string.biz_empty), Modifier.padding(vertical = 32.dp))
                                    } else {
                                        Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
                                            Column {
                                                m.value.trips.forEachIndexed { i, t ->
                                                    key(t.episodeId) {
                                                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = c.outline)
                                                        TripRow(t, t.episodeId == state.selected) { viewModel.select(t.episodeId) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    ClientSection(state.clients, state.suggestions, viewModel::setClient)
                                }
                            }
                        }
                    }
                    if (selected != null) {
                        val openReplay = LocalOpenReplay.current
                        DetailPane(
                            selected,
                            onClose = { viewModel.select(null) },
                            onReplay = { openReplay(selected.day) },
                            onOpenDay = { viewModel.close(); onOpenDay(selected.day) },
                            onNotBiz = { viewModel.markNotBiz(selected.day) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .padding(start = if (wide) 12.dp else EodiTheme.space.page, end = EodiTheme.space.page, top = 8.dp, bottom = EodiTheme.space.page),
                        )
                    }
                }
            }
        }
        }
    }
}

/** 이보다 좁으면(폰 세로) 머리 · 요약 칸을 여러 줄로 나눈다. */
private val NarrowWidth = 760.dp
private const val NarrowSuggestMax = 3

@Composable
private fun SummaryRow(m: BizMonth, narrow: Boolean) {
    val count: @Composable (Modifier) -> Unit = { Tile(stringResource(R.string.biz_count_label), stringResource(R.string.biz_count, m.trips.size), it) }
    val km: @Composable (Modifier) -> Unit = {
        Tile(stringResource(if (m.totalHasGps) R.string.biz_km_label_mixed else R.string.biz_km_label), stringResource(R.string.biz_km_value, m.totalKm), it)
    }
    val time: @Composable (Modifier) -> Unit = { Tile(stringResource(R.string.biz_time_tile), durationText(m.totalMin * 60_000), it) }
    val cost: @Composable (Modifier) -> Unit = { Tile(stringResource(R.string.biz_cost_tile), wonPlain(m.totalWon), it, accent = true) }
    val row = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    if (narrow) {
        // 네 칸을 한 줄에 두면 값이 잘린다 → 두 칸씩 두 줄
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(row, horizontalArrangement = Arrangement.spacedBy(12.dp)) { count(Modifier.weight(1f)); km(Modifier.weight(1f)) }
            Row(row, horizontalArrangement = Arrangement.spacedBy(12.dp)) { time(Modifier.weight(1f)); cost(Modifier.weight(1f)) }
        }
    } else {
        Row(row, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            count(Modifier.weight(1f)); km(Modifier.weight(1f)); time(Modifier.weight(1f)); cost(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, accent: Boolean = false) {
    val c = EodiTheme.colors
    Surface(shape = RoundedCornerShape(24.dp), color = if (accent) c.infoSoft else c.surface, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = EodiTheme.type.meta, color = if (accent) c.info else c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = if (accent) c.info else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** ⓘ 어떻게 계산하나요 — 누르면 규칙 설명을 펼친다. */
@Composable
private fun HowToggle() {
    val c = EodiTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            Modifier.clip(RoundedCornerShape(38.dp)).clickable { open = !open }.heightIn(min = 76.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BizIcons.Info, null, tint = c.textSecondary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.biz_how), style = EodiTheme.type.meta, color = c.textSecondary)
            Spacer(Modifier.width(6.dp))
            Icon(EodiIcons.Chevron, null, tint = c.textSecondary, modifier = Modifier.size(24.dp).rotate(if (open) 270f else 90f))
        }
        if (open) {
            Text(
                stringResource(R.string.biz_hint) + "\n" + stringResource(R.string.biz_detail_note),
                style = EodiTheme.type.meta,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun UndoBar(day: String, onUndo: () -> Unit) {
    val c = EodiTheme.colors
    val d = java.time.LocalDate.parse(day)
    Surface(shape = RoundedCornerShape(24.dp), color = c.ink, contentColor = c.onInk, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 24.dp, end = 8.dp).heightIn(min = 88.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.biz_removed, "${d.monthValue}/${d.dayOfMonth}"), style = EodiTheme.type.body, modifier = Modifier.weight(1f))
            PressSurface(onClick = onUndo, shape = RoundedCornerShape(38.dp), color = c.ink, contentColor = c.onInk) {
                Box(Modifier.heightIn(min = 76.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.biz_undo), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 출장 한 줄: 날짜 · 제목 / 행선지 · 운전 · 거리 / 오른쪽 금액(굵은 파랑) ›. 줄 전체를 누르면 경비 내역. */
@Composable
private fun TripRow(t: BizTrip, selected: Boolean, onClick: () -> Unit) {
    val c = EodiTheme.colors
    val d = java.time.LocalDate.parse(t.day)
    PressSurface(onClick = onClick, color = if (selected) c.infoSoft else c.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 112.dp).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${d.dayOfMonth}", style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = c.info)
                Text(t.weekday, style = EodiTheme.type.meta, color = c.textSecondary)
            }
            Spacer(Modifier.width(16.dp))
            // 폰(좁은 화면)은 금액 옆 글 칸이 좁아 "경기 안산시 …"로 잘렸다 → 두 줄까지
            val lines = if (LocalConfiguration.current.screenWidthDp < 600) 2 else 1
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.title, style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold, maxLines = lines, overflow = TextOverflow.Ellipsis)
                val where = t.stops.firstOrNull()?.name?.ifBlank { null } ?: t.region
                Text(
                    stringResource(R.string.biz_row_meta, where, durationText(t.driveMin * 60_000), t.distanceKm),
                    style = EodiTheme.type.body,
                    color = c.textSecondary,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(wonPlain(t.costWon), style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = c.info, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Icon(EodiIcons.Chevron, stringResource(R.string.biz_open_detail), tint = c.textTertiary, modifier = Modifier.size(28.dp))
        }
    }
}

/** 경비 내역 창: 카드 내역처럼 한 줄씩 (아이콘 · 이름 · 시각 · 금액 · 추정/실제), 아래 합계와 "출장 아님". 항목은 Rust `biz_expenses`. */
@Composable
private fun DetailPane(t: BizTrip, onClose: () -> Unit, onReplay: () -> Unit, onOpenDay: () -> Unit, onNotBiz: () -> Unit, modifier: Modifier) {
    val c = EodiTheme.colors
    val d = java.time.LocalDate.parse(t.day)
    Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onOpenDay)) {
                    Text(
                        stringResource(R.string.biz_date, d.monthValue, d.dayOfMonth, t.weekday) + "  " +
                            stringResource(R.string.biz_detail_range, hm(t.startMs), hm(t.endMs)),
                        style = EodiTheme.type.meta,
                        color = c.info,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(t.title, style = EodiTheme.type.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundIconButton(EodiIcons.Close, stringResource(R.string.biz_detail_close), onClose, containerColor = c.surfaceHigh)
            }
            val km = stringResource(if (t.gpsDistance) R.string.biz_km_gps else R.string.biz_km_odo, t.distanceKm)
            val energy = t.energyPct?.let { stringResource(if (t.ev) R.string.biz_battery else R.string.biz_fuel, it.toInt()) }
            // 탑승 인원은 좌석 센서로 센 숫자뿐이다 (누구인지 · 누가 운전했는지는 모른다). 모르면 뺀다.
            val people = t.people?.takeIf { it > 0u }?.let { stringResource(R.string.biz_people, it.toInt()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(km, stringResource(R.string.biz_drive, durationText(t.driveMin * 60_000)), energy, people).joinToString("  ·  "),
                    style = EodiTheme.type.meta,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                SecondaryPill(stringResource(R.string.replay_open), EodiIcons.Map, onReplay)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.biz_detail_title), style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold)
            if (t.expenses.isEmpty()) {
                Text(stringResource(R.string.biz_detail_empty), style = EodiTheme.type.meta, color = c.textSecondary)
            }
            t.expenses.forEach { x -> ExpenseRow(x) }
            HorizontalDivider(color = c.outline, modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.biz_detail_total), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                EstTag(t.expenses.all { it.estimated })
                Spacer(Modifier.width(12.dp))
                Text(wonPlain(t.costWon), style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = c.info)
            }
            Text(stringResource(R.string.biz_detail_note), style = EodiTheme.type.meta, color = c.textTertiary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.biz_not_biz_hint), style = EodiTheme.type.meta, color = c.textTertiary, modifier = Modifier.weight(1f))
                SecondaryPill(stringResource(R.string.biz_not_biz), EodiIcons.Close, onNotBiz)
            }
        }
    }
}

@Composable
private fun ExpenseRow(x: BizExpense) {
    val c = EodiTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.infoSoft), contentAlignment = Alignment.Center) {
            Icon(BizIcons.of(x.kind), null, tint = c.info, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(x.title, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${hm(x.tMs)} · ${x.detail}", style = EodiTheme.type.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        EstTag(x.estimated)
        Spacer(Modifier.width(12.dp))
        Text(wonPlain(x.won), style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 120.dp))
    }
}

/** "추정"(회색) / "실제"(파랑) 작은 표. */
@Composable
private fun EstTag(estimated: Boolean) {
    val c = EodiTheme.colors
    Text(
        stringResource(if (estimated) R.string.biz_tag_est else R.string.biz_tag_real),
        style = EodiTheme.type.meta,
        color = if (estimated) c.textSecondary else c.info,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (estimated) c.surfaceHigh else c.infoSoft).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

private val hmFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private fun hm(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(hmFormat)

/** 경비 아이콘 (Material 기호 모양을 옮긴 24 격자 경로). */
private object BizIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

    val Energy = icon("bolt", "M7,2v11h3v9l7,-12h-4l4,-8z")
    val Toll = icon("road", "M11,4h2v3h-2zM11,10h2v4h-2zM11,17h2v3h-2zM4,20L7,4h2L6,20zM18,20L15,4h2L20,20z")
    val Parking = icon("parking", "M13,3H6v18h4v-6h3c3.31,0 6,-2.69 6,-6s-2.69,-6 -6,-6zM13.2,11H10V7h3.2c1.1,0 2,0.9 2,2s-0.9,2 -2,2z")
    val Meal = icon("meal", "M11,9H9V2H7v7H5V2H3v7c0,2.12 1.66,3.84 3.75,3.97V22h2.5v-9.03C11.34,12.84 13,11.12 13,9V2h-2V9zM16,6v8h2.5v8H21V2C18.24,2 16,4.24 16,6z")
    val Other = icon("receipt", "M18,17H6v-2h12v2zM18,13H6v-2h12v2zM18,9H6V7h12v2zM3,22l1.5,-1.5L6,22l1.5,-1.5L9,22l1.5,-1.5L12,22l1.5,-1.5L15,22l1.5,-1.5L18,22l1.5,-1.5L21,22V2l-1.5,1.5L18,2l-1.5,1.5L15,2l-1.5,1.5L12,2l-1.5,1.5L9,2L7.5,3.5L6,2L4.5,3.5L3,2v20z")
    val Info = icon("info", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z")

    fun of(k: BizExpenseKind) = when (k) {
        BizExpenseKind.ENERGY -> Energy
        BizExpenseKind.TOLL -> Toll
        BizExpenseKind.PARKING -> Parking
        BizExpenseKind.MEAL -> Meal
        BizExpenseKind.OTHER -> Other
    }
}

/**
 * 거래처 줄: 정해 둔 거래처(누르면 뺀다)와 권하는 곳(평일에 자주 간 곳, 누르면 거래처로). 고르기 · 날 수는 Rust(`bizclient.rs`).
 * 거래처에 들른 주행은 요일 · 시각 · 거리와 상관없이 출장으로 묶인다 (근무 시간이 다른 사람 · 영업직).
 */
@Composable
private fun ClientSection(clients: List<BizClient>, suggestions: List<BizClient>, onSet: (BizClient, Boolean) -> Unit) {
    val c = EodiTheme.colors
    // 좁은 창(세로 · 두 앱)에서는 옆으로 굴리는 줄이 오른쪽 끝에서 잘려 보였다 → 칩을 줄 바꿈(FlowRow)으로 놓고,
    // 권하는 곳은 3개까지 + "N곳 더보기". 설명은 짧은 한 줄로.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val narrow = maxWidth < NarrowWidth
    var showAll by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.biz_clients_title), style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(if (narrow) R.string.biz_clients_hint_short else R.string.biz_clients_hint), style = EodiTheme.type.meta, color = c.textSecondary)
            val chips: @Composable (@Composable () -> Unit) -> Unit = { content ->
                if (narrow) {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { content() }
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
                }
            }
            if (clients.isNotEmpty()) {
                chips {
                    clients.forEach { cl ->
                        SecondaryPill(stringResource(R.string.biz_client_chip, cl.name.ifBlank { stringResource(R.string.biz_client_unnamed) }, cl.visitDays.toInt()), EodiIcons.Close, { onSet(cl, false) })
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                if (!narrow) Text(stringResource(R.string.biz_clients_suggest), style = EodiTheme.type.meta, color = c.textSecondary)
                val shown = if (narrow && !showAll) suggestions.take(NarrowSuggestMax) else suggestions
                chips {
                    shown.forEach { s ->
                        SecondaryPill(stringResource(R.string.biz_client_suggest_chip, s.name, s.visitDays.toInt()), EodiIcons.Pin, { onSet(s, true) })
                    }
                    if (shown.size < suggestions.size) {
                        SecondaryPill(stringResource(R.string.biz_clients_more, suggestions.size - shown.size), EodiIcons.Chevron, { showAll = true })
                    }
                }
            }
        }
    }
    }
}

/** 4300 → "4,300원" (칸 · 내역용, "추정" 표시는 따로). */
@Composable
private fun wonPlain(won: Long): String = stringResource(R.string.biz_won, "%,d".format(won))

/** 정산서 CSV를 캐시 share/에 저장하고 공유 시트(메일 · 드라이브 · 카톡)로 넘긴다. */
private suspend fun share(context: Context, viewModel: BizViewModel, m: BizMonth) {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "출장정산서-${m.month}.csv")
            file.writeText(viewModel.csv(m), Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        }.onFailure { Log.w("EodiBiz", "정산서 저장 실패", it) }.getOrNull()
    } ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.biz_subject, m.month))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.biz_export)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
