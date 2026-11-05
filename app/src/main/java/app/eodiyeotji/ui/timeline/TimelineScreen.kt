package app.eodiyeotji.ui.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.ui.home.CoreStatusLine
import java.time.LocalDate

/** 이 폭 이상이면 목록과 상세를 나란히 둔다 (Pleos 넓은 창 1682dp, 나눈 창 약 841dp). */
private val WideBreakpoint = 1200.dp

/** 차량 터치 기준: 76dp 이상. */
private val TouchMin = 76.dp

private val PagePadding = 32.dp

/** 타임라인 진입점 (정차 중 화면). */
@Composable
fun TimelineRoute(viewModel: TimelineViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimelineScreen(
        state = state,
        onSelectDay = viewModel::select,
        onBack = viewModel::clearSelection,
        onRetry = viewModel::reload,
        onWideChange = viewModel::setWide,
        status = { CoreStatusLine() },
    )
}

/**
 * 상태만 받아 그리는 화면. 창 폭은 실제 배치 크기(BoxWithConstraints)로 읽어서
 * Pleos에서 실행 중에 창을 넓히거나 나눠도 바로 따라간다.
 */
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onSelectDay: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWideChange: (Boolean) -> Unit,
    status: @Composable () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= WideBreakpoint
            val reportWide by rememberUpdatedState(onWideChange)
            LaunchedEffect(wide) { reportWide(wide) }

            // 목록 스크롤 위치는 좁은 창에서 상세를 갔다 와도, 창 폭이 바뀌어도 유지한다
            val listState = rememberLazyListState()

            Column(Modifier.fillMaxSize()) {
                TimelineHeader(status)
                when (val days = state.days) {
                    DaysState.Loading -> LoadingPane(Modifier.fillMaxSize())
                    is DaysState.Failed -> FailedPane(days.message, onRetry, Modifier.fillMaxSize())
                    is DaysState.Loaded -> when {
                        days.days.isEmpty() -> EmptyPane(Modifier.fillMaxSize())
                        wide -> Row(Modifier.fillMaxSize()) {
                            DayList(
                                days = days.days,
                                today = days.today,
                                highlightedDay = state.detail.shownSummary?.day,
                                onSelect = onSelectDay,
                                listState = listState,
                                modifier = Modifier.weight(0.42f).fillMaxHeight(),
                            )
                            VerticalDivider()
                            DayDetailPane(
                                detail = state.detail,
                                today = days.today,
                                onBack = null,
                                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                            )
                        }
                        state.selectedDay != null -> {
                            BackHandler(onBack = onBack)
                            DayDetailPane(
                                detail = state.detail,
                                today = days.today,
                                onBack = onBack,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        else -> DayList(
                            days = days.days,
                            today = days.today,
                            highlightedDay = null,
                            onSelect = onSelectDay,
                            listState = listState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(status: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PagePadding, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(24.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { status() }
    }
}

// ---------------------------------------------------------------- 날짜 목록

@Composable
private fun DayList(
    days: List<DaySummary>,
    today: LocalDate,
    highlightedDay: String?,
    onSelect: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(days, key = { it.day }, contentType = { "day" }) { s ->
            DayRow(s, today, selected = s.day == highlightedDay, onSelect = onSelect)
        }
    }
}

@Composable
private fun DayRow(s: DaySummary, today: LocalDate, selected: Boolean, onSelect: (String) -> Unit) {
    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(
        onClick = { onSelect(s.day) },
        colors = colors,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchMin),
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayLabel(s.day, today),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    timeRangeText(s.firstMs, s.lastMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(daySummaryLine(s), style = MaterialTheme.typography.titleLarge)
        }
    }
}

// ---------------------------------------------------------------- 하루 상세

/** [onBack]이 있으면(좁은 창) 위에 "목록으로" 버튼을 둔다. 시스템 뒤로 가기는 TimelineScreen의 BackHandler가 맡는다. */
@Composable
private fun DayDetailPane(
    detail: DetailState,
    today: LocalDate,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val summary = detail.shownSummary
    Column(modifier.padding(horizontal = PagePadding)) {
        if (onBack != null) {
            FilledTonalButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchMin).widthIn(min = 200.dp)) {
                Text(stringResource(R.string.timeline_back), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(20.dp))
        }
        if (summary != null) {
            Text(
                dayLabel(summary.day, today),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                daySummaryLine(summary),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }
        when (detail) {
            DetailState.None -> Unit
            is DetailState.Loading -> LoadingPane(Modifier.fillMaxSize())
            is DetailState.Failed -> CenteredText(stringResource(R.string.timeline_failed, detail.message))
            is DetailState.Loaded ->
                if (detail.entries.isEmpty()) {
                    CenteredText(stringResource(R.string.timeline_detail_empty))
                } else {
                    EntryList(detail.entries)
                }
        }
    }
}

@Composable
private fun EntryList(entries: List<TimelineEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            entries,
            key = { it.key },
            contentType = { if (it is TimelineEntry.Trip) "trip" else "stay" },
        ) { e ->
            when (e) {
                is TimelineEntry.Trip -> EntryRow(
                    marker = MaterialTheme.colorScheme.primary,
                    kind = stringResource(R.string.timeline_kind_trip),
                    time = timeRangeText(e.trip.startMs, e.trip.endMs),
                    main = stringResource(
                        R.string.timeline_trip_line,
                        distanceText(e.trip.distanceM),
                        durationText(e.trip.endMs - e.trip.startMs),
                    ),
                    sub = stringResource(R.string.timeline_from_to, coordText(e.trip.start), coordText(e.trip.end)),
                )
                is TimelineEntry.Stay -> EntryRow(
                    marker = MaterialTheme.colorScheme.tertiary,
                    kind = stringResource(R.string.timeline_kind_stay),
                    time = timeRangeText(e.stay.arriveMs, e.stay.leaveMs),
                    main = stringResource(R.string.timeline_stay_line, durationText(e.stay.leaveMs - e.stay.arriveMs)),
                    sub = coordText(e.stay.center),
                )
            }
        }
    }
}

/** 왼쪽 색 막대(주행 = primary, 머문 곳 = tertiary) + 시간 · 요약 · 좌표. */
@Composable
private fun EntryRow(marker: Color, kind: String, time: String, main: String, sub: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = TouchMin)) {
            Box(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(marker, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
            )
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    Text(kind, style = MaterialTheme.typography.titleMedium, color = marker)
                }
                Text(main, style = MaterialTheme.typography.headlineSmall)
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 상태 화면

@Composable
private fun LoadingPane(modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.timeline_loading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 기록이 없을 때. 시작 버튼은 일부러 없다 → 시동을 걸면 저절로 기록된다고만 알려 준다. */
@Composable
private fun EmptyPane(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(PagePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.timeline_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.timeline_empty_body),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 640.dp),
        )
    }
}

@Composable
private fun FailedPane(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(PagePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.timeline_failed, message),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = TouchMin).widthIn(min = 240.dp)) {
            Text(stringResource(R.string.timeline_retry), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
