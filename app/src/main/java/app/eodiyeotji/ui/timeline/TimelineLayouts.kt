package app.eodiyeotji.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.core.DayEntry
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.map.DayMap
import app.eodiyeotji.ui.map.MapPhotoPin
import app.eodiyeotji.ui.place.LocalOpenPlace
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.mapInk
import java.time.LocalDate
import kotlin.math.roundToInt

private val RailWidth = 116.dp
private val InfoWidth = 640.dp
private val TopWidth = 580.dp
private val SheetWidth = 600.dp
private val StopCardsHeight = 132.dp
private val Gap = 20.dp
/** 오른쪽 위 카드가 이보다 높으면 길을 그 왼쪽으로 맞춘다 */
private val TallTop = 140.dp

/**
 * 넓은 창(차 화면) 내 기록: 지도가 화면을 채우고 그 위에 그날 머리 판 · 요약 입구 · 들른 곳 카드만 얹는다.
 * 위에 얹은 판은 빈 곳을 잡고 끌어 옮길 수 있다 ([Movable]). 전체 시간표는 들른 곳 카드 끝의 버튼으로 연다.
 */
@Composable
internal fun WideHome(
    days: List<DaySummary>,
    today: LocalDate,
    state: TimelineUiState,
    railState: LazyListState,
    onSelectDay: (String) -> Unit,
    onEntryClick: (String) -> Unit,
    onMapSelect: (String?) -> Unit,
    onShare: (Long) -> Unit,
    listTop: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    val summary = detail.shownSummary
    val loaded = detail as? DetailState.Loaded
    val share: (() -> Unit)? = loaded?.shareEpisode?.let { id -> { onShare(id) } }
    // 전체 시간표 (주행 줄 · 쿠폰 · 내 사진 · 고치기까지): 들른 곳 카드 끝의 버튼으로 연다
    var sheet by remember(summary?.day) { mutableStateOf(false) }
    // 이미 고른 곳을 한 번 더 누르면(지도 핀 · 아래 카드) 그곳 화면을 연다: 큰 사진 · 별점 · 안내
    val openPlace = LocalOpenPlace.current
    fun openStay(key: String): Boolean {
        val stay = loaded?.entries?.filterIsInstance<DayEntry.Stay>()?.firstOrNull { it.key == key }?.stay ?: return false
        val p = stay.place ?: return false
        openPlace(StopFact(p.name, p.category, p.address, stay.arriveMs, stay.leaveMs, stay.center.lat, stay.center.lon, false))
        return true
    }
    val info: @Composable (Modifier) -> Unit = { m -> if (summary != null) StageInfo(summary, today, loaded != null, share, m) }
    val stops: @Composable (Modifier) -> Unit = { m ->
        if (loaded != null) StopCards(loaded.entries, state.selectedEntry, { key -> if (key != state.selectedEntry || !openStay(key)) onEntryClick(key) }, onAll = { sheet = !sheet }, modifier = m)
    }
    val map: @Composable (Modifier, IntArray) -> Unit = { m, pad ->
        DayMap(
            data = loaded?.map,
            selectedKey = state.selectedEntry,
            onSelectKey = { key -> if (key == null || key != state.selectedEntry || !openStay(key)) onMapSelect(key) },
            modifier = m,
            photoPins = remember(loaded?.entries, loaded?.myPhotos) { stayPhotoPins(loaded) },
            myPhotos = loaded?.mapPhotos.orEmpty(),
            fitInsets = pad,
        )
    }
    val timeline: @Composable () -> Unit = {
        when (detail) {
            is DetailState.Loaded -> EntryList(detail.summary.day, detail.entries, state.selectedEntry, onEntryClick, offers = detail.offers, myPhotos = detail.myPhotos)
            else -> Unit
        }
    }
    val density = LocalDensity.current.density
    fun px(dp: Dp) = (dp.value * density).toInt()
    val shape = RoundedCornerShape(EodiTheme.space.radiusL)

    // 지도 상자 크기 · 오른쪽 위 카드(요약 입구 · 늘 가던 날) 크기: 판이 지도 밖으로 못 나가게, 길이 카드 밑에 깔리지 않게 하는 데 쓴다
    var area by remember { mutableStateOf(IntSize.Zero) }
    var infoSize by remember { mutableStateOf(IntSize.Zero) }
    var topSize by remember { mutableStateOf(IntSize.Zero) }
    val gap = px(Gap)
    // 길은 머리 판 아래에 맞춘다. 오른쪽 카드가 높으면(늘 가던 날 카드까지 떴을 때) 그 왼쪽으로 더 좁힌다
    val right = if (sheet) px(SheetWidth + Gap) else if (listTop != null && topSize.height > px(TallTop)) topSize.width + gap else 0

    Row(modifier) {
        DateRail(days, today, summary?.day, onSelectDay, railState)
        Spacer(Modifier.width(Gap))
        Box(Modifier.weight(1f).fillMaxHeight().clip(shape).onSizeChanged { area = it }) {
            map(Modifier.fillMaxSize(), intArrayOf(0, infoSize.height + gap, right, px(StopCardsHeight + Gap)))
            Movable(area, start = { IntOffset(gap, gap) }, modifier = Modifier.width(InfoWidth), onSize = { infoSize = it }) {
                Panel { info(Modifier.padding(24.dp)) }
            }
            if (sheet) {
                TimelineSheet(onClose = { sheet = false }, Modifier.align(Alignment.TopEnd).padding(start = Gap, top = Gap, end = Gap, bottom = StopCardsHeight + Gap).width(SheetWidth).fillMaxHeight(), timeline)
            } else if (listTop != null) {
                Movable(area, start = { IntOffset(area.width - it.width - gap, gap) }, modifier = Modifier.width(TopWidth), onSize = { topSize = it }) { listTop() }
            }
            stops(Modifier.align(Alignment.BottomStart).padding(start = Gap, bottom = Gap))
        }
    }
}

/**
 * 지도 위 판을 끌어 옮길 수 있게 한다: 버튼이 아닌 빈 곳을 잡고 끌면 따라온다 (판이 길을 가릴 때 비켜 두려고).
 * 지도 상자([area]) 밖으로는 나가지 않는다. 옮기기 전 자리는 [start] (판 크기를 받아 왼쪽 위 좌표를 낸다).
 */
@Composable
private fun Movable(
    area: IntSize,
    start: (IntSize) -> IntOffset,
    modifier: Modifier = Modifier,
    onSize: (IntSize) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var moved by remember { mutableStateOf<IntOffset?>(null) }
    val box by rememberUpdatedState(area)
    val home by rememberUpdatedState(start)
    fun place(): IntOffset {
        val o = moved ?: home(size)
        return IntOffset(o.x.coerceIn(0, (box.width - size.width).coerceAtLeast(0)), o.y.coerceIn(0, (box.height - size.height).coerceAtLeast(0)))
    }
    Box(
        modifier
            .offset { place() }
            .onSizeChanged { size = it; onSize(it) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    moved = place() + IntOffset(drag.x.roundToInt(), drag.y.roundToInt())
                }
            },
    ) { content() }
}

/** 지도 위에 얹는 판: 바탕이 살짝 비치는 흰 카드. */
@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = EodiTheme.colors.surface.copy(alpha = 0.95f),
        contentColor = EodiTheme.colors.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        // 어두운 지도 위에서 판 가장자리가 묻히지 않게 가는 테두리
        border = BorderStroke(1.dp, EodiTheme.colors.outline),
        shadowElevation = 6.dp,
        modifier = modifier,
        content = content,
    )
}

/** 그날 머리: 날짜 · 제목 · 한 줄 통계 · 버튼 (통계 칸 · 태그는 두지 않는다). */
@Composable
private fun StageInfo(summary: DaySummary, today: LocalDate, loaded: Boolean, onShare: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val title = summary.title?.takeIf { it.isNotBlank() }
    Column(modifier) {
        if (title != null) {
            val relative = relativeDayName(summary.day, today)
            Text(
                listOfNotNull(relative, dayTitle(summary.day, today)).joinToString(" · "),
                style = EodiTheme.type.label, color = c.textTertiary, maxLines = 1,
            )
        }
        Text(
            title ?: dayTitle(summary.day, today),
            style = EodiTheme.type.hero, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(daySummaryLine(summary), style = EodiTheme.type.body, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(20.dp))
        DayActions(summary, loaded, onShare)
    }
}

/**
 * 날짜 줄: 일 · 요일 타일만 세로로. 맨 위에 지금 보이는 달("2026년 / 11월")을 붙박아 두고 굴리면 따라 바뀐다.
 * 달이 바뀌는 자리에는 가는 선 + 굵은 "10월". 고른 날은 옅은 칸.
 */
@Composable
private fun DateRail(days: List<DaySummary>, today: LocalDate, selected: String?, onSelect: (String) -> Unit, listState: LazyListState) {
    val c = EodiTheme.colors
    LaunchedEffect(selected) {
        val i = days.indexOfFirst { it.day == selected }
        if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == selected }) listState.animateScrollToItem(i)
    }
    Column(Modifier.width(RailWidth).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 지금 맨 위에 보이는 날의 해 · 달 (작년 날짜인지도 여기서 보인다)
        val top by remember(days) { derivedStateOf { days.getOrNull(listState.firstVisibleItemIndex)?.day } }
        top?.let { d ->
            Text(stringResource(R.string.home_year, d.substring(0, 4).toInt()), style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1)
            Text(
                stringResource(R.string.home_month, d.substring(5, 7).toInt()),
                style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        // 지난 날을 보고 있으면(검색 · 여행 모음에서 들어왔을 때 등) 맨 위에 가장 최근 날로 돌아가는 버튼
        val latest = days.firstOrNull()?.day
        if (latest != null && selected != null && selected != latest) {
            Surface(
                onClick = { onSelect(latest) },
                color = c.ink,
                contentColor = c.onInk,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(bottom = 12.dp).width(RailWidth - 24.dp).height(76.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(EodiIcons.Chevron, contentDescription = null, modifier = Modifier.size(24.dp).rotate(-90f))
                    Text(stringResource(R.string.home_latest_day), style = EodiTheme.type.caption, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(days, key = { _, s -> s.day }) { i, s ->
                val month = s.day.substring(5, 7).toInt()
                // 달이 바뀌는 자리: 가는 선 + 굵은 달 이름 (맨 처음 달은 위에 붙박은 이름이 대신한다)
                if (i > 0 && days[i - 1].day.substring(0, 7) != s.day.substring(0, 7)) {
                    Box(Modifier.padding(top = 10.dp).width(RailWidth - 40.dp).height(2.dp).background(c.outline))
                    Text(
                        stringResource(R.string.home_month, month),
                        style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.textPrimary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                    )
                }
                val on = s.day == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (on) c.accentSoft else Color.Transparent)
                        .clickable(role = Role.Button) { onSelect(s.day) }
                        .padding(6.dp),
                ) {
                    DateTile(s.day, s.day == today.toString(), on)
                }
            }
        }
    }
}

/** 들른 곳 카드 줄 (번호 · 이름 · 도착 시각 · 머문 시간) + 오른쪽 끝에 붙박은 전체 시간표 버튼. 누르면 지도에서 그곳을 고르고, 고른 곳을 한 번 더 누르면 그곳 화면을 연다. */
@Composable
private fun StopCards(entries: List<DayEntry>, selectedKey: String?, onClick: (String) -> Unit, onAll: () -> Unit, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val ink = mapInk(c.isDark)
    val stays = remember(entries) { entries.filterIsInstance<DayEntry.Stay>() }
    // 전체 시간표 버튼은 줄 끝에 붙박아 둔다 (들른 곳이 많은 날에도 카드를 끝까지 밀지 않고 누를 수 있게)
    Row(modifier.height(StopCardsHeight - 24.dp).padding(end = Gap), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(stays, key = { _, e -> e.key }) { i, e ->
                val stay = e.stay
                val on = e.key == selectedKey
                // 그날 처음 · 마지막 곳(대개 집)은 밤새 머문 시간까지 세져서 머문 시간을 뺀다 (시간표 줄과 같은 규칙)
                val overnight = stay.isOpen || i == 0 || i == stays.lastIndex
                Surface(
                    onClick = { onClick(e.key) },
                    color = c.surface.copy(alpha = 0.95f),
                    contentColor = c.textPrimary,
                    shape = RoundedCornerShape(24.dp),
                    border = if (on) BorderStroke(3.dp, ink.trip) else BorderStroke(1.dp, c.outline),
                    shadowElevation = 6.dp,
                    modifier = Modifier.width(340.dp).fillMaxHeight(),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(if (stay.isOpen) ink.here else ink.stay, CircleShape), contentAlignment = Alignment.Center) {
                            Text(if (stay.order > 0) stay.order.toString() else "", style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = ink.onMarker)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                stay.place?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.place_pending),
                                style = EodiTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (overnight) clockText(stay.arriveMs) else clockText(stay.arriveMs) + " · " + durationText(stay.leaveMs - stay.arriveMs),
                                style = EodiTheme.type.meta, color = c.textTertiary, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Surface(
            onClick = onAll,
            color = c.ink,
            contentColor = c.onInk,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Box(Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.home_all_timeline), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** 전체 시간표 판 (지도 오른쪽에 얹는다). */
@Composable
private fun TimelineSheet(onClose: () -> Unit, modifier: Modifier, timeline: @Composable () -> Unit) {
    Panel(modifier) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_all_timeline), style = EodiTheme.type.title, modifier = Modifier.weight(1f))
                RoundIconButton(EodiIcons.Close, stringResource(R.string.home_close_timeline), onClose)
            }
            Box(Modifier.weight(1f)) { timeline() }
        }
    }
}

/** 정차한 곳 사진 상자 자리 (이름을 찾았거나 내 사진이 있는 머문 곳만). */
internal fun stayPhotoPins(loaded: DetailState.Loaded?): List<MapPhotoPin> =
    loaded?.entries.orEmpty().mapNotNull { e ->
        val stay = (e as? DayEntry.Stay)?.stay ?: return@mapNotNull null
        // 내 사진이 있으면 그것이 대표 사진 (없으면 장소 대표 사진)
        val mine = loaded?.myPhotos?.get("s${stay.arriveMs}")?.photos?.firstOrNull()?.uri
        val p = stay.place?.takeIf { it.name.isNotBlank() }
        if (p == null && mine == null) return@mapNotNull null
        MapPhotoPin("s${stay.arriveMs}", p?.name.orEmpty(), p?.address.orEmpty(), stay.center.lat, stay.center.lon, mine)
    }
