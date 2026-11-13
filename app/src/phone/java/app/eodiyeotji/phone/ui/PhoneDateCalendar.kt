package app.eodiyeotji.phone.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/*
 * 폰 내 기록의 "날짜로 찾기" 달력 (아래 시트). 기록 목록은 최근 날부터 길게 이어져서 몇 달 전 날을 찾으려면 한참 굴려야 했다.
 * - 기록이 있는 날만 누를 수 있다 (숫자 진하게 + 아래 점). 기록 없는 날 · 앞날은 흐리게, 누르지 않는다.
 * - 오늘은 테두리, 지금 열어 둔 날은 청록으로 채운다. 달은 옆으로 밀거나 ‹ › 로 넘기고, 제목을 누르면 해 · 달을 바로 고른다.
 * - 날은 이미 불러 둔 날 목록(Rust day_summaries)에서 온다 → 새로 부르지 않는다.
 */

/** 기록이 있는 날을 달별로 묶은 것. [first]~[last] 사이 달만 넘겨 볼 수 있다. */
@Immutable
internal class RecordMonths(val byMonth: Map<YearMonth, Set<Int>>, val first: YearMonth, val last: YearMonth) {
    val count: Int get() = (ChronoUnit.MONTHS.between(first, last) + 1).toInt()
    fun at(page: Int): YearMonth = first.plusMonths(page.toLong())
    fun pageOf(m: YearMonth): Int = ChronoUnit.MONTHS.between(first, m).toInt().coerceIn(0, count - 1)

    companion object {
        fun of(days: List<DaySummary>, today: LocalDate): RecordMonths {
            val map = HashMap<YearMonth, MutableSet<Int>>()
            for (d in days) {
                val date = runCatching { LocalDate.parse(d.day) }.getOrNull() ?: continue
                map.getOrPut(YearMonth.from(date)) { HashSet() }.add(date.dayOfMonth)
            }
            val now = YearMonth.from(today)
            val first = map.keys.minOrNull()?.let { minOf(it, now) } ?: now
            return RecordMonths(map, first, now)
        }
    }
}

/** 달력 시트. [selected]는 지금 열어 둔 날(없으면 null), 날을 누르면 [onPick] 후 시트를 닫는다. */
@Composable
internal fun PhoneDateCalendarSheet(days: List<DaySummary>, today: LocalDate, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    PhoneSheet(onDismiss) {
        PhoneDateCalendar(days, today, selected, onPick = { onDismiss(); onPick(it) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneDateCalendar(days: List<DaySummary>, today: LocalDate, selected: String?, onPick: (String) -> Unit) {
    val c = EodiTheme.colors
    val months = remember(days, today) { RecordMonths.of(days, today) }
    val selectedDate = remember(selected) { selected?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val start = months.pageOf(selectedDate?.let(YearMonth::from) ?: YearMonth.from(today))
    val pager = rememberPagerState(initialPage = start) { months.count }
    val scope = rememberCoroutineScope()
    val shown by remember(months) { derivedStateOf { months.at(pager.currentPage) } }
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp)) {
        // 머리: ‹ 2026년 11월 ▾ › · 오늘
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                EodiIcons.Chevron, stringResource(R.string.phone_calendar_prev),
                { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = !picking && pager.currentPage > 0, iconModifier = Modifier.rotate(180f), iconSize = 36.dp,
            )
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 80.dp)
                    .clip(RoundedCornerShape(EodiTheme.space.radiusM))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.phone_calendar_pick)) { picking = !picking },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.phone_calendar_title, shown.year, shown.monthValue),
                    style = EodiTheme.type.title, color = c.textPrimary, maxLines = 1,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.width(8.dp))
                Icon(EodiIcons.Chevron, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(32.dp).rotate(if (picking) -90f else 90f))
            }
            RoundIconButton(
                EodiIcons.Chevron, stringResource(R.string.phone_calendar_next),
                { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                enabled = !picking && pager.currentPage < months.count - 1, iconSize = 36.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (picking) {
            MonthPicker(months, shown, today) { m ->
                picking = false
                scope.launch { pager.scrollToPage(months.pageOf(m)) }
            }
        } else {
            WeekdayHeader()
            Spacer(Modifier.height(8.dp))
            HorizontalPager(pager, beyondViewportPageCount = 1, key = { months.at(it).toString() }) { page ->
                MonthGrid(months.at(page), months.byMonth[months.at(page)].orEmpty(), today, selectedDate, onPick)
            }
        }
        Spacer(Modifier.height(16.dp))
        // 아래 줄: 이 달 기록 수(또는 없음 안내) · 오늘로
        Row(verticalAlignment = Alignment.CenterVertically) {
            val n = months.byMonth[shown]?.size ?: 0
            Text(
                when {
                    months.byMonth.isEmpty() -> stringResource(R.string.phone_calendar_no_records)
                    n == 0 -> stringResource(R.string.phone_calendar_month_empty)
                    else -> stringResource(R.string.phone_calendar_month_count, n)
                },
                style = EodiTheme.type.body, color = c.textSecondary, modifier = Modifier.weight(1f),
            )
            val thisMonth = YearMonth.from(today)
            if (shown != thisMonth || picking) {
                Spacer(Modifier.width(12.dp))
                Surface(
                    onClick = {
                        picking = false
                        scope.launch { pager.animateScrollToPage(months.pageOf(thisMonth)) }
                    },
                    shape = CircleShape,
                    color = c.accentSoft,
                    contentColor = c.accent,
                    modifier = Modifier.heightIn(min = EodiTheme.space.touch),
                ) {
                    Box(Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.phone_calendar_today), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 요일 머리 (일요일부터: 한국 달력 관례. 일 = 코랄, 토 = 청록 — 날짜 타일과 같은 색). */
@Composable
private fun WeekdayHeader() {
    val c = EodiTheme.colors
    val names = stringArrayResource(R.array.phone_calendar_weekdays)
    Row(Modifier.fillMaxWidth()) {
        names.forEachIndexed { i, n ->
            Text(
                n,
                style = EodiTheme.type.caption, fontWeight = FontWeight.Bold,
                color = when (i) { 0 -> c.stay; 6 -> c.accent; else -> c.textTertiary },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val CellHeight = 88.dp

/** 한 달 칸. 늘 6줄을 그려 달을 넘겨도 시트 높이가 흔들리지 않는다. */
@Composable
private fun MonthGrid(month: YearMonth, recorded: Set<Int>, today: LocalDate, selected: LocalDate?, onPick: (String) -> Unit) {
    // 일요일 = 0번 칸
    val lead = month.atDay(1).dayOfWeek.value % 7
    val length = month.lengthOfMonth()
    Column {
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val d = row * 7 + col - lead + 1
                    Box(Modifier.weight(1f).height(CellHeight), contentAlignment = Alignment.Center) {
                        if (d in 1..length) {
                            val date = month.atDay(d)
                            DayCell(date, d in recorded, date == today, date == selected, onPick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, recorded: Boolean, isToday: Boolean, isSelected: Boolean, onPick: (String) -> Unit) {
    val c = EodiTheme.colors
    val desc = stringResource(
        if (recorded) R.string.phone_calendar_day_recorded else R.string.phone_calendar_day_empty,
        date.monthValue, date.dayOfMonth,
    ) + if (isToday) stringResource(R.string.phone_calendar_today_suffix) else ""
    val fg = when {
        isSelected -> c.onAccent
        recorded -> c.textPrimary
        else -> c.textTertiary.copy(alpha = 0.55f)
    }
    Box(
        Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(if (isSelected) c.accent else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(BorderStroke(3.dp, c.accent), CircleShape) else Modifier)
            .then(
                if (recorded) Modifier.clickable(role = Role.Button) { onPick(date.toString()) } else Modifier,
            )
            .semantics {
                contentDescription = desc
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = EodiTheme.type.bodyStrong,
                fontWeight = if (recorded) FontWeight.Bold else FontWeight.Normal,
                color = fg, maxLines = 1,
            )
            // 기록 있는 날 표시 점 (색만으로 구분하지 않게 굵기 · 점을 같이 쓴다)
            Box(Modifier.padding(top = 4.dp).size(10.dp).background(if (recorded) (if (isSelected) c.onAccent else c.accent) else Color.Transparent, CircleShape))
        }
    }
}

/** 해 · 달 바로 고르기: 위에 해 칩, 아래 12달 (기록 없는 달 · 앞 달은 흐리게 · 누르지 않음). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthPicker(months: RecordMonths, shown: YearMonth, today: LocalDate, onPick: (YearMonth) -> Unit) {
    val c = EodiTheme.colors
    var year by remember(shown) { mutableStateOf(shown.year) }
    val years = remember(months) { (months.first.year..months.last.year).toList().asReversed() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        years.forEach { y ->
            val on = y == year
            Surface(
                onClick = { year = y },
                shape = CircleShape,
                color = if (on) c.accent else c.surfaceHigh,
                contentColor = if (on) c.onAccent else c.textPrimary,
                modifier = Modifier.heightIn(min = EodiTheme.space.touch).semantics { selected = on },
            ) {
                Box(Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.home_year, y), style = EodiTheme.type.label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    for (row in 0 until 3) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (col in 0 until 4) {
                val m = YearMonth.of(year, row * 4 + col + 1)
                val inRange = m >= months.first && m <= months.last
                val n = months.byMonth[m]?.size ?: 0
                val on = m == shown
                Surface(
                    onClick = { onPick(m) },
                    enabled = inRange,
                    shape = RoundedCornerShape(EodiTheme.space.radiusM),
                    color = if (on) c.accentSoft else Color.Transparent,
                    border = if (on) BorderStroke(2.dp, c.accent) else null,
                    contentColor = if (n > 0) c.textPrimary else c.textTertiary.copy(alpha = if (inRange) 0.8f else 0.45f),
                    modifier = Modifier.weight(1f).height(104.dp).padding(vertical = 6.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.home_month, m.monthValue), style = EodiTheme.type.bodyStrong, fontWeight = if (n > 0) FontWeight.Bold else FontWeight.Normal)
                        if (n > 0) Text(stringResource(R.string.phone_calendar_month_days, n), style = EodiTheme.type.caption, color = c.accent)
                    }
                }
            }
        }
    }
}
