package app.eodiyeotji.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.R
import app.eodiyeotji.core.AskAi
import app.eodiyeotji.core.BoardPost
import app.eodiyeotji.core.DriveCourse
import app.eodiyeotji.core.EpisodeKind
import app.eodiyeotji.core.PopularPlace
import app.eodiyeotji.core.SearchChip
import app.eodiyeotji.core.SearchChipKind
import app.eodiyeotji.core.SearchFilter
import app.eodiyeotji.core.SearchGroup
import app.eodiyeotji.core.SearchPeriod
import app.eodiyeotji.core.SearchRow
import app.eodiyeotji.core.SearchRowKind
import app.eodiyeotji.core.SearchScope
import app.eodiyeotji.ui.agent.AgentIcons
import app.eodiyeotji.ui.agent.VoiceUi
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.OverlayCard
import app.eodiyeotji.ui.community.StopPhoto
import app.eodiyeotji.ui.community.TogglePill
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.SparkleBadge
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * 손으로 치는 검색 화면 부품 (내 기록 · 커뮤니티가 같이 쓴다).
 * - [SearchField]: 화면 머리의 검색 칸 (돋보기 · 지우기 × · 마이크). 누르면 결과 자리가 열린다.
 * - [SearchResults]: 머리 아래 본문 자리를 대신하는 결과 (처음엔 최근 검색어 · 추천 칩, 치면 묶음별 결과).
 * 계산(말 풀기 · 묶기 · 순위 · 날짜 글)은 Rust `search_ui`가 하고, 여기서는 받은 순서대로 그린다.
 */

/** 화면이 검색을 끼워 넣는 자리: 머리의 [bar], 검색 중이면([active]) 본문 대신 [results]. */
@Immutable
class SearchSlot(
    val active: Boolean,
    val bar: @Composable (Modifier) -> Unit,
    val results: @Composable (Modifier) -> Unit,
)

/** 결과를 열었을 때 화면이 할 일 (안내는 [SearchViewModel.navigate]가 한다). */
@Immutable
class SearchActions(
    /** 하루 열기. [entryKey]가 있으면 그날 지도 · 목록에서 그 항목을 고른다 ("s{도착 ms}"). */
    val openDay: (day: String, entryKey: String?) -> Unit,
    val openCourse: (DriveCourse) -> Unit,
    val openReview: (PopularPlace) -> Unit,
    val openPost: (BoardPost) -> Unit,
    /** 기억 에이전트에 이 말로 묻는다 (열고 바로 보낸다). */
    val askAi: (String) -> Unit,
)

/** 검색 아이콘 (24 격자, 선 2.25, 둥근 끝 — EodiIcons와 같은 규칙). */
object SearchIcons {
    private val Ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 2.25f, b: PathBuilder.() -> Unit) =
        path(stroke = Ink, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = b)

    /** 돋보기 */
    val Search: ImageVector by lazy {
        icon("Search") {
            stroke(2.4f) {
                moveTo(10.5f, 4f); arcToRelative(6.5f, 6.5f, 0f, true, true, 0f, 13f); arcToRelative(6.5f, 6.5f, 0f, true, true, 0f, -13f); close()
                moveTo(15.4f, 15.4f); lineTo(20.5f, 20.5f)
            }
        }
    }

    /** 지도 (접힌 지도) */
    val Map: ImageVector by lazy {
        icon("Map") {
            stroke {
                moveTo(3.5f, 6.5f); lineTo(9f, 4.5f); lineTo(15f, 6.5f); lineTo(20.5f, 4.5f)
                lineTo(20.5f, 17.5f); lineTo(15f, 19.5f); lineTo(9f, 17.5f); lineTo(3.5f, 19.5f); close()
                moveTo(9f, 4.5f); lineTo(9f, 17.5f)
                moveTo(15f, 6.5f); lineTo(15f, 19.5f)
            }
        }
    }
}

// =====================================================================================
// 검색 칸
// =====================================================================================

/**
 * 검색 칸 (알약). [돋보기 | 입력 … | × | 🎤]. 누르면(포커스) [onOpen], 칠 때마다 [onQuery], 키보드 "검색"은 [onSubmit].
 * - 마이크는 말로 찾기: 알아들은 말이 칸에 들어간다 ([voice] 받아쓰는 중이면 마이크가 파랗게 숨 쉬고 들은 말이 흐리게 보인다).
 * - [active]가 아니면(결과를 열고 돌아온 뒤) 포커스를 풀고 키보드를 내린다. 말은 남아 있어 다시 누르면 결과가 보인다.
 * - [onClose]가 있으면 검색 중일 때 왼쪽 돋보기 자리가 "‹ 닫기"로 바뀐다.
 */
@Composable
fun SearchField(
    query: String,
    active: Boolean,
    hint: String,
    voice: VoiceUi,
    onOpen: () -> Unit,
    onQuery: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMic: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 88.dp,
) {
    val c = EodiTheme.colors
    var value by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    // 말소리 · 칩 · 디버그 입력처럼 밖에서 바뀐 말은 칸에 넣고 커서를 끝으로 (치는 중 한글 조합은 건드리지 않는다)
    LaunchedEffect(query) {
        if (value.text != query) value = TextFieldValue(query, TextRange(query.length))
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (!active) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    val dictating = voice.dictating
    val pulse by animateFloatAsState(if (dictating) 1f + voice.level * 0.25f else 1f, tween(120), label = "mic-pulse")
    TextField(
        value = value,
        onValueChange = {
            value = it
            onQuery(it.text)
        },
        singleLine = true,
        textStyle = EodiTheme.type.bodyStrong.copy(color = c.textPrimary),
        placeholder = {
            Text(
                if (dictating) voice.partial.ifBlank { stringResource(R.string.search_listening) } else hint,
                style = EodiTheme.type.body,
                color = if (dictating) c.accent else c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            if (active) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.search_close)) { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(EodiIcons.Back, stringResource(R.string.search_close), tint = c.textSecondary, modifier = Modifier.size(32.dp))
                }
            } else {
                Icon(SearchIcons.Search, null, tint = c.textSecondary, modifier = Modifier.padding(start = 16.dp).size(32.dp))
            }
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                if (value.text.isNotEmpty()) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).clickable(role = Role.Button) {
                            value = TextFieldValue("")
                            onQuery("")
                            requester.requestFocus()
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(CommunityIcons.Close, stringResource(R.string.search_clear), tint = c.textTertiary, modifier = Modifier.size(28.dp))
                    }
                }
                Surface(
                    onClick = onMic,
                    shape = CircleShape,
                    color = if (dictating) c.accent else c.accentSoft,
                    contentColor = if (dictating) c.onAccent else c.accent,
                    modifier = Modifier.size(height - 16.dp).scale(pulse),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AgentIcons.Mic, stringResource(R.string.search_mic), modifier = Modifier.size(30.dp))
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            onSubmit(value.text)
        }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = c.accent,
        ),
        modifier = modifier
            .heightIn(min = height)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onOpen() },
    )
}

// =====================================================================================
// 결과
// =====================================================================================

/**
 * 검색 결과 자리 (본문을 대신한다). 뒤로 가기 = 검색 닫기.
 * - 말도 칩도 없으면: (내 기록) 조건 칩 · 최근 검색어 · 추천 칩.
 * - 있으면: (질문 같은 말이면) 맨 위 "AI에게 물어보기" → 기간 글 → 묶음별 결과 → 아래 작은 "AI에게 물어보기".
 * [columns] 2면 결과 줄을 두 칸씩 (넓은 차 화면), 묶음 이름은 한 줄 전체.
 * [drivingHint]: 폰 운전 중 — 막지 않고 "운전 중에는 말로 찾아보세요"를 맨 위에 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResults(
    state: SearchUiState,
    actions: SearchActions,
    viewModel: SearchViewModel,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
    drivingHint: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    BackHandler(enabled = state.active) { viewModel.close() }
    val c = EodiTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun leave() {
        keyboard?.hide()
        focus.clearFocus()
        viewModel.remember()
        viewModel.hide()
    }
    val open: (SearchRow) -> Unit = { r ->
        leave()
        when (r.kind) {
            SearchRowKind.PLACE, SearchRowKind.DAY -> r.day?.let { actions.openDay(it, null) }
            SearchRowKind.COURSE -> r.course?.let(actions.openCourse)
            SearchRowKind.REVIEW -> r.reviewPlace?.let(actions.openReview)
            SearchRowKind.POST -> r.post?.let(actions.openPost)
        }
    }
    val map: (SearchRow) -> Unit = { r ->
        val day = r.day
        if (day != null) {
            leave()
            actions.openDay(day, r.place?.let { "s${it.arriveMs}" })
        }
    }
    val navigate: (SearchRow) -> Unit = { r ->
        r.place?.let { p ->
            viewModel.remember()
            viewModel.navigate(p.name, p.lat, p.lon, p.address)
        }
    }
    val ask: (String) -> Unit = { t ->
        leave()
        actions.askAi(t)
    }
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.background(c.background)) {
        val columns = if (maxWidth >= TwoColumns) 2 else 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (drivingHint) {
                full("driving") { DrivingHint(onMic) }
            }
            if (state.scope == SearchScope.RECORDS) {
                full("filters") {
                    FilterChips(
                        filter = state.filter,
                        onPeriod = viewModel::setPeriod,
                        onKind = viewModel::toggleKind,
                        onFirstVisit = viewModel::toggleFirstVisit,
                        onReset = viewModel::clearFilter,
                        onPickMonth = {
                            keyboard?.hide()
                            pickingMonth = true
                        },
                    )
                }
            }
            val answer = state.answer
            when {
                state.idle -> suggestions(state, viewModel)
                answer == null -> Unit // 첫 결과를 기다리는 중 (수 ms)
                else -> {
                    val ai = answer.askAi
                    if (ai != null && ai.primary) full("ai-top") { AskAiCard(ai, primary = true, onAsk = ask) }
                    if (answer.dateLabel != null || BuildConfig.DEBUG) {
                        full("meta") { MetaLine(answer.dateLabel, answer.total, answer.elapsedMicros) }
                    }
                    if (answer.groups.isEmpty()) {
                        full("empty") { NoResults() }
                    }
                    answer.groups.forEach { g -> group(g, columns, open, map, navigate) }
                    if (ai != null && !ai.primary) full("ai-bottom") { AskAiCard(ai, primary = false, onAsk = ask) }
                    full("bottom-space") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
        if (pickingMonth) {
            MonthPicker(
                selected = state.filter.from?.takeIf { state.filter.period == SearchPeriod.CUSTOM },
                onPick = { first ->
                    viewModel.setPeriod(SearchPeriod.CUSTOM, first, first.plusMonths(1).minusDays(1))
                    pickingMonth = false
                },
                onDismiss = { pickingMonth = false },
            )
        }
        state.navi?.let { ok ->
            Box(Modifier.fillMaxSize().padding(bottom = 32.dp), contentAlignment = Alignment.BottomCenter) {
                app.eodiyeotji.ui.community.Notice(stringResource(if (ok) R.string.search_navi_ok else R.string.search_navi_fail))
            }
        }
    }
}

/** 이 폭 이상이면 결과 줄을 두 칸씩. */
private val TwoColumns = 1100.dp

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

private fun LazyGridScope.group(
    g: SearchGroup,
    columns: Int,
    onOpen: (SearchRow) -> Unit,
    onMap: (SearchRow) -> Unit,
    onNavigate: (SearchRow) -> Unit,
) {
    full("h-${g.kind}") { GroupHeader(g.title, g.rows.size + g.more) }
    itemsIndexed(g.rows, key = { _, r -> "${g.kind}-${r.key}" }, contentType = { _, _ -> "row" }) { index, r ->
        // 사진은 묶음 위쪽 몇 줄만 (구글 사진 요청을 적게)
        ResultRow(r, showPhoto = index < PhotoRows * columns, onOpen = onOpen, onMap = onMap, onNavigate = onNavigate)
    }
    if (g.more > 0) full("more-${g.kind}") { MoreLine(g.more) }
}

/** 묶음마다 사진을 보이는 줄 수 (한 칸 기준). */
private const val PhotoRows = 4

@Composable
private fun GroupHeader(title: String, count: Int) {
    val c = EodiTheme.colors
    Row(Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = EodiTheme.type.eyebrow, color = c.textTertiary, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.width(10.dp))
        Text(count.toString(), style = EodiTheme.type.eyebrow, color = c.accent)
    }
}

@Composable
private fun MoreLine(more: Int) {
    Text(
        stringResource(R.string.search_more, more),
        style = EodiTheme.type.caption,
        color = EodiTheme.colors.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** 기간 글 ("기간: 10월 26일 ~ 11월 1일") · (디버그) 결과 수와 Rust 검색 시간. */
@Composable
private fun MetaLine(dateLabel: String?, total: Int, elapsedMicros: Long) {
    val c = EodiTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        if (dateLabel != null) {
            Icon(AgentIcons.Calendar, null, tint = c.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.search_date_range, dateLabel), style = EodiTheme.type.label, color = c.accent, maxLines = 1)
            Spacer(Modifier.width(20.dp))
        }
        if (BuildConfig.DEBUG) {
            val ms = String.format(java.util.Locale.ROOT, "%.1f", elapsedMicros / 1000.0)
            Text(stringResource(R.string.search_count, total, ms), style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1)
        }
    }
}

@Composable
private fun NoResults() {
    val c = EodiTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.search_empty_title), style = EodiTheme.type.title, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.search_empty_body), style = EodiTheme.type.body, color = c.textSecondary)
    }
}

/** 결과 한 줄: [사진] 제목 / 부제 / 날짜 줄 · [지도] [안내]. 줄을 누르면 열기. */
@Composable
private fun ResultRow(
    r: SearchRow,
    showPhoto: Boolean,
    onOpen: (SearchRow) -> Unit,
    onMap: (SearchRow) -> Unit,
    onNavigate: (SearchRow) -> Unit,
) {
    val c = EodiTheme.colors
    val openLabel = stringResource(R.string.search_open)
    Surface(
        color = c.surface,
        contentColor = c.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EodiTheme.space.radiusL))
            .clickable(role = Role.Button, onClickLabel = openLabel) { onOpen(r) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RowPicture(r, showPhoto)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.title,
                        style = EodiTheme.type.bodyStrong,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (r.firstVisit && r.kind == SearchRowKind.PLACE) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.search_first_visit),
                            style = EodiTheme.type.caption,
                            fontWeight = FontWeight.Bold,
                            color = c.accent,
                            modifier = Modifier.background(c.accentSoft, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                if (r.subtitle.isNotBlank()) {
                    Text(r.subtitle, style = EodiTheme.type.meta, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (r.dateLine.isNotBlank()) {
                    Text(r.dateLine, style = EodiTheme.type.meta, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val canMap = r.day != null && r.place != null && (r.kind == SearchRowKind.PLACE || r.kind == SearchRowKind.DAY)
            val canNavi = r.place != null && (r.kind == SearchRowKind.PLACE || r.kind == SearchRowKind.DAY || r.kind == SearchRowKind.REVIEW)
            if (canMap) {
                Spacer(Modifier.width(8.dp))
                RowAction(SearchIcons.Map, stringResource(R.string.search_map), filled = false) { onMap(r) }
            }
            if (canNavi) {
                Spacer(Modifier.width(8.dp))
                RowAction(AgentIcons.Navigate, stringResource(R.string.search_navigate), filled = true) { onNavigate(r) }
            }
        }
    }
}

@Composable
private fun RowPicture(r: SearchRow, showPhoto: Boolean) {
    val c = EodiTheme.colors
    val size = 104.dp
    val p = r.place
    when {
        p != null && showPhoto && p.name.isNotBlank() -> StopPhoto(p.name, p.address, p.lat, p.lon, order = null, size = size, radius = EodiTheme.space.radiusM)
        else -> Box(
            Modifier.size(size).clip(RoundedCornerShape(EodiTheme.space.radiusM)).background(if (p != null) c.staySoft else c.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (r.kind) {
                SearchRowKind.POST -> CommunityIcons.Chat
                SearchRowKind.COURSE -> CommunityIcons.Road
                SearchRowKind.DAY -> AgentIcons.Calendar
                else -> EodiIcons.Pin
            }
            Icon(icon, null, tint = if (p != null) c.stay.copy(alpha = 0.7f) else c.textTertiary, modifier = Modifier.size(36.dp))
        }
    }
}

/** 줄 오른쪽 작은 동작 (76dp 동그라미 + 아래 이름). */
@Composable
private fun RowAction(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Column(
        Modifier
            .widthIn(min = 84.dp)
            .clip(RoundedCornerShape(EodiTheme.space.radiusM))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).background(if (filled) c.accent else c.surfaceHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (filled) c.onAccent else c.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = EodiTheme.type.caption, color = c.textSecondary, maxLines = 1)
    }
}

/** "AI에게 물어보기" — 질문 같은 말이면 맨 위 큰 카드, 아니면 결과 아래 작은 줄. 금색 반짝임 = AI. */
@Composable
private fun AskAiCard(ai: AskAi, primary: Boolean, onAsk: (String) -> Unit) {
    val c = EodiTheme.colors
    if (primary) {
        Surface(
            onClick = { onAsk(ai.text) },
            color = c.surface,
            contentColor = c.textPrimary,
            shape = RoundedCornerShape(EodiTheme.space.radiusL),
            border = BorderStroke(2.dp, c.memory),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                SparkleBadge(size = 72.dp)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.search_ask_ai), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, color = c.memoryInk)
                    Text(
                        stringResource(R.string.search_ask_ai_body, ai.text),
                        style = EodiTheme.type.body,
                        color = c.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(64.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AgentIcons.Send, null, tint = c.onAccent, modifier = Modifier.size(30.dp))
                }
            }
        }
    } else {
        Row(
            Modifier
                .padding(top = 8.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onAsk(ai.text) }
                .background(c.surfaceHigh, CircleShape)
                .heightIn(min = 72.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AgentIcons.Sparkle, null, tint = c.memoryInk, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.search_ask_ai_more, ai.text),
                style = EodiTheme.type.label,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 폰 운전 중: 막지 않고 말로 찾기를 먼저 권한다. */
@Composable
private fun DrivingHint(onMic: () -> Unit) {
    val c = EodiTheme.colors
    Surface(onClick = onMic, color = c.accent, contentColor = c.onAccent, shape = RoundedCornerShape(EodiTheme.space.radiusL)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).background(c.onAccent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(AgentIcons.Mic, stringResource(R.string.search_mic), tint = c.accent, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.search_driving_hint), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold)
        }
    }
}

// ---- 처음 화면: 최근 검색어 · 추천 칩 ----

@OptIn(ExperimentalLayoutApi::class)
private fun LazyGridScope.suggestions(state: SearchUiState, vm: SearchViewModel) {
    val recent = state.suggestions.recent
    if (recent.isNotEmpty()) {
        full("recent-h") {
            val c = EodiTheme.colors
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.search_recent), style = EodiTheme.type.eyebrow, color = c.textTertiary, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = vm::clearRecent, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.search_recent_clear), style = EodiTheme.type.label, color = c.accent)
                }
            }
        }
        full("recent") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                recent.forEach { t -> RecentChip(t, onPick = { vm.submit(t) }, onRemove = { vm.forget(t) }) }
            }
        }
    }
    full("chips-h") {
        Text(
            stringResource(R.string.search_suggestions),
            style = EodiTheme.type.eyebrow,
            color = EodiTheme.colors.textTertiary,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp).semantics { heading() },
        )
    }
    full("chips") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.suggestions.chips.forEach { chip -> SuggestionChip(chip) { vm.submit(chip.text) } }
        }
    }
}

@Composable
private fun RecentChip(text: String, onPick: () -> Unit, onRemove: () -> Unit) {
    val c = EodiTheme.colors
    Surface(shape = CircleShape, color = c.surface, contentColor = c.textPrimary, border = BorderStroke(2.dp, c.outline)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onPick).heightIn(min = 72.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CommunityIcons.Clock, null, tint = c.textTertiary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = EodiTheme.type.label, maxLines = 1)
            }
            val removeLabel = stringResource(R.string.search_recent_remove, text)
            Box(
                Modifier.size(64.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = removeLabel, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CommunityIcons.Close, removeLabel, tint = c.textTertiary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun SuggestionChip(chip: SearchChip, onClick: () -> Unit) {
    val c = EodiTheme.colors
    val icon = when (chip.kind) {
        SearchChipKind.DATE -> AgentIcons.Calendar
        SearchChipKind.PLACE -> EodiIcons.Pin
        SearchChipKind.KEYWORD -> SearchIcons.Search
    }
    val tint = when (chip.kind) {
        SearchChipKind.DATE -> c.accent
        SearchChipKind.PLACE -> c.stay
        SearchChipKind.KEYWORD -> c.textSecondary
    }
    Surface(onClick = onClick, shape = CircleShape, color = c.surface, contentColor = c.textPrimary, border = BorderStroke(2.dp, c.outline)) {
        Row(Modifier.heightIn(min = 72.dp).padding(start = 20.dp, end = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(chip.label, style = EodiTheme.type.label, maxLines = 1)
        }
    }
}

// ---- 조건 칩 ----

/** 기간(하나만) · 종류(여럿) · 처음 간 곳. 옆으로 민다. "직접 고르기"는 달력에서 기간을 고른다. */
@Composable
private fun FilterChips(
    filter: SearchFilter,
    onPeriod: (SearchPeriod, LocalDate?, LocalDate?) -> Unit,
    onKind: (EpisodeKind) -> Unit,
    onFirstVisit: () -> Unit,
    onReset: () -> Unit,
    onPickMonth: () -> Unit,
) {
    val c = EodiTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val periods = listOf(
            SearchPeriod.ANY to stringResource(R.string.search_period_any),
            SearchPeriod.THIS_WEEK to stringResource(R.string.search_period_week),
            SearchPeriod.THIS_MONTH to stringResource(R.string.search_period_month),
            SearchPeriod.THIS_YEAR to stringResource(R.string.search_period_year),
        )
        periods.forEach { (p, label) -> TogglePill(label, filter.period == p, { onPeriod(p, null, null) }) }
        val custom = filter.period == SearchPeriod.CUSTOM
        val from = filter.from
        val to = filter.to
        val customLabel = when {
            !custom || from == null || to == null -> stringResource(R.string.search_period_custom)
            // 한 달을 골랐으면 "10월" (올해가 아니면 "작년 10월")
            from.dayOfMonth == 1 && to == from.plusMonths(1).minusDays(1) -> when (from.year) {
                LocalDate.now().year -> stringResource(R.string.search_month, from.monthValue)
                LocalDate.now().year - 1 -> stringResource(R.string.search_month_last_year, from.monthValue)
                else -> stringResource(R.string.search_month_year, from.year, from.monthValue)
            }
            else -> stringResource(R.string.search_period_custom_range, shortDate(from), shortDate(to))
        }
        TogglePill(customLabel, custom, onPickMonth)
        Box(Modifier.size(width = 2.dp, height = 36.dp).background(c.outline))
        val kinds = listOf(
            listOf(EpisodeKind.TRIP) to stringResource(R.string.search_kind_trip),
            listOf(EpisodeKind.OUTING) to stringResource(R.string.search_kind_outing),
            listOf(EpisodeKind.ERRAND) to stringResource(R.string.search_kind_errand),
            listOf(EpisodeKind.COMMUTE_TO_WORK, EpisodeKind.COMMUTE_HOME) to stringResource(R.string.search_kind_commute),
        )
        kinds.forEach { (ks, label) ->
            TogglePill(label, ks.all { it in filter.kinds }, { ks.forEach(onKind) })
        }
        Box(Modifier.size(width = 2.dp, height = 36.dp).background(c.outline))
        TogglePill(stringResource(R.string.search_first_only), filter.firstVisitOnly, onFirstVisit)
        if (filter.isSet) {
            TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(R.string.search_filter_reset), style = EodiTheme.type.label, color = c.accent)
            }
        }
    }
}

/**
 * "직접 고르기": 최근 13개월을 큰 알약으로 (달력의 작은 날짜 칸은 차 화면에서 누르기 어렵다).
 * 차는 가운데 카드, 폰은 아래 시트 ([OverlayCard]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthPicker(selected: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val c = EodiTheme.colors
    val thisMonth = remember { LocalDate.now().withDayOfMonth(1) }
    val months = remember(thisMonth) { (0L until 13L).map { thisMonth.minusMonths(it) } }
    OverlayCard(onDismiss = onDismiss, maxWidth = 1000.dp) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.search_pick_range), style = EodiTheme.type.title, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                months.forEach { m ->
                    val label = when (m.year) {
                        thisMonth.year -> stringResource(R.string.search_month, m.monthValue)
                        thisMonth.year - 1 -> stringResource(R.string.search_month_last_year, m.monthValue)
                        else -> stringResource(R.string.search_month_year, m.year, m.monthValue)
                    }
                    TogglePill(label, m == selected, { onPick(m) }, Modifier.heightIn(min = 76.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 76.dp)) {
                    Text(stringResource(R.string.search_pick_cancel), style = EodiTheme.type.label, color = c.accent)
                }
            }
        }
    }
}

private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")

private fun shortDate(d: LocalDate): String = d.format(ShortDate)
