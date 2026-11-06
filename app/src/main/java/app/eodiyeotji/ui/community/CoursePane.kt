package app.eodiyeotji.ui.community

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.CommunitySort
import app.eodiyeotji.core.DriveCourse
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.pleos.CourseProgress
import app.eodiyeotji.ui.map.DayMap
import app.eodiyeotji.ui.map.MapPhotoPin
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.distanceText
import app.eodiyeotji.ui.timeline.durationText
import kotlinx.coroutines.delay

/** 도구 줄이 이보다 좁으면 버튼 이름을 짧게 쓴다. */
internal val CompactToolbar = 900.dp

/** 이 폭 이상이면 상세를 지도 | 정보 두 칸으로 나눈다. */
private val WideDetail = 1100.dp

/** 커뮤니티 > 드라이브 코스: 정렬·근처·내 기록에서 올리기 + 코스 카드 격자. 카드를 누르면 상세(지도·들르는 곳·안내). */
@Composable
fun CoursePane(viewModel: CourseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var shareEpisode by rememberSaveable { mutableStateOf<Long?>(null) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CourseToolbar(
                sort = state.sort,
                nearOn = state.nearOn,
                onSort = viewModel::setSort,
                onNear = viewModel::toggleNear,
                onShare = { picking = true },
            )
            if (state.nearOn && !state.nearKnown) {
                Text(
                    stringResource(R.string.community_near_unknown),
                    style = EodiTheme.type.caption,
                    color = EodiTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
                )
            }
            when (val l = state.list) {
                Load.Loading -> LoadingBox()
                is Load.Failed -> CenterMessage(stringResource(R.string.community_failed, l.message)) {
                    SecondaryPill(stringResource(R.string.community_retry), EodiIcons.Refresh, viewModel::reload)
                }
                is Load.Loaded ->
                    if (l.items.isEmpty()) {
                        CenterMessage(
                            if (state.nearOn) stringResource(R.string.course_empty_near, 30) else stringResource(R.string.course_empty),
                        )
                    } else {
                        CourseGrid(l.items, onOpen = viewModel::open, onHeart = viewModel::toggleHeart)
                    }
            }
        }
        state.detail?.let { d ->
            BackHandler(onBack = viewModel::closeDetail)
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            CourseDetailScreen(
                d,
                progress = progress?.takeIf { it.courseId == d.course.post.id && !it.finished },
                onBack = viewModel::closeDetail,
                onHeart = { viewModel.toggleHeart(d.course.post.id) },
                onNavigate = viewModel::navigate,
            )
        }
        if (picking) {
            BackHandler { picking = false }
            ShareEpisodePicker(onPick = { picking = false; shareEpisode = it }, onDismiss = { picking = false })
        }
        shareEpisode?.let { id ->
            BackHandler { shareEpisode = null }
            ShareCourseSheet(episodeId = id, onDone = { shared ->
                shareEpisode = null
                if (shared != null) viewModel.onShared()
            })
        }
        if (state.justShared) {
            LaunchedEffect(Unit) {
                delay(2_500)
                viewModel.dismissShared()
            }
            Notice(stringResource(R.string.course_shared_done), Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun CourseToolbar(sort: CommunitySort, nearOn: Boolean, onSort: (CommunitySort) -> Unit, onNear: () -> Unit, onShare: () -> Unit) {
    // 좁은 창: 버튼 이름을 줄이고, 정렬·근처는 옆으로 밀어 볼 수 있게 한다 (버튼 글자가 잘리지 않게 버튼이 먼저 자리를 받는다)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp)) {
        val compact = maxWidth < CompactToolbar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SortChoice(sort, onSort)
                TogglePill(stringResource(R.string.community_near_me, 30), nearOn, onNear)
            }
            Spacer(Modifier.width(16.dp))
            PrimaryPill(
                stringResource(if (compact) R.string.course_share_short else R.string.course_share_from_record),
                CommunityIcons.Upload,
                onShare,
            )
        }
    }
}

@Composable
fun SortChoice(sort: CommunitySort, onSort: (CommunitySort) -> Unit) {
    ChoiceRow(
        options = listOf(
            CommunitySort.POPULAR to stringResource(R.string.community_sort_popular),
            CommunitySort.RECENT to stringResource(R.string.community_sort_recent),
        ),
        selected = sort,
        onSelect = onSort,
    )
}

@Composable
private fun CourseGrid(items: List<DriveCourse>, onOpen: (DriveCourse) -> Unit, onHeart: (String) -> Unit) {
    LazyVerticalGrid(
        state = rememberTopGridState(items.firstOrNull()?.post?.id),
        columns = GridCells.Adaptive(minSize = 640.dp),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.post.id }, contentType = { "course" }) { c ->
            CourseCard(c, onOpen = { onOpen(c) }, onHeart = { onHeart(c.post.id) })
        }
    }
}

/** 코스 카드: 지역·작성자 → 제목 → 다녀온 날·날씨 → 들르는 곳 사진 칩 → 태그 → 거리·시간·하트. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseCard(c: DriveCourse, onOpen: () -> Unit, onHeart: () -> Unit) {
    val colors = EodiTheme.colors
    val type = EodiTheme.type
    val p = c.post
    Surface(
        onClick = onOpen,
        color = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(CommunityIcons.Road, null, tint = colors.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(p.region, style = type.label, color = colors.accent, maxLines = 1)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.community_byline, p.author, agoText(p.createdMs)),
                    style = type.caption,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (p.mine) MineBadge()
            }
            Text(p.title, style = type.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            VisitLine(c.visit)
            StopChips(p.stops)
            if (p.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxLines = 1) {
                    p.tags.forEach { TagChip(it) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(
                        stringResource(R.string.community_stops_count, p.stops.size),
                        distanceText(p.distanceM),
                        durationText(p.durationMs),
                    ).joinToString(" · "),
                    style = type.meta,
                    color = colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                HeartButton(p.hearts, p.heartedByMe, onHeart)
            }
        }
    }
}

@Composable
fun MineBadge() {
    Text(
        stringResource(R.string.community_mine),
        style = EodiTheme.type.caption,
        color = EodiTheme.colors.onAccent,
        modifier = Modifier
            .padding(start = 8.dp)
            .background(EodiTheme.colors.accent, androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** 들르는 곳 가로 칩: 사진(없으면 순서 번호) + 이름. */
@Composable
fun StopChips(stops: List<StopFact>, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(stops, key = { i, s -> "$i-${s.name}" }) { i, s ->
            Column(Modifier.width(136.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    StopPhoto(s.name, s.address, s.lat, s.lon, order = i + 1, size = 136.dp)
                    OrderDot(i + 1, Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(s.name, style = EodiTheme.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun OrderDot(order: Int, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    // 사진 위에 붙인 코랄 스티커: 흰 테두리로 어떤 사진 위에서도 또렷하다 (지도 핀 번호와 같은 색)
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier.size(38.dp).background(c.onMarker, shape).padding(2.5.dp).background(c.stay, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(order.toString(), style = EodiTheme.type.caption, color = c.onMarker, fontWeight = FontWeight.Bold)
    }
}

/** 코스 상세: 지도(들르는 곳 핀·사진) + 제목·작성자·다녀온 날·소개·태그·들르는 곳 목록 + 하트·이 코스로 안내. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseDetailScreen(d: CourseDetail, progress: CourseProgress?, onBack: () -> Unit, onHeart: () -> Unit, onNavigate: () -> Unit) {
    val colors = EodiTheme.colors
    val p = d.course.post
    var selectedKey by remember(p.id) { mutableStateOf<String?>(null) }
    val pins = remember(p.id) { p.stops.mapIndexed { i, s -> MapPhotoPin("s${i + 1}", s.name, s.address, s.lat, s.lon) } }
    Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.textPrimary) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 24.dp)) {
            val wide = maxWidth >= WideDetail
            val map: @Composable (Modifier) -> Unit = { m ->
                Surface(shape = RoundedCornerShape(EodiTheme.space.radiusL), color = colors.surface, modifier = m) {
                    DayMap(data = d.map, selectedKey = selectedKey, onSelectKey = { selectedKey = it }, photoPins = pins, modifier = Modifier.fillMaxSize())
                }
            }
            val info: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item(key = "head") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(p.region, style = EodiTheme.type.label, color = colors.accent)
                                Text(p.title, style = EodiTheme.type.hero, modifier = Modifier.semantics { heading() })
                                Text(
                                    stringResource(R.string.community_byline, p.author, agoText(p.createdMs)),
                                    style = EodiTheme.type.meta,
                                    color = colors.textTertiary,
                                )
                                VisitLine(d.course.visit)
                                if (p.body.isNotBlank()) Text(p.body, style = EodiTheme.type.body, color = colors.textSecondary)
                                if (p.tags.isNotEmpty()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        p.tags.forEach { TagChip(it) }
                                    }
                                }
                                Text(
                                    listOf(
                                        stringResource(R.string.community_stops_count, p.stops.size),
                                        distanceText(p.distanceM),
                                        durationText(p.durationMs),
                                    ).joinToString(" · "),
                                    style = EodiTheme.type.meta,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.course_stops_title), style = EodiTheme.type.label, color = colors.textTertiary)
                            }
                        }
                        itemsIndexed(p.stops, key = { i, s -> "stop-$i-${s.name}" }) { i, s ->
                            StopRow(i + 1, s, selected = selectedKey == "s${i + 1}", onClick = { selectedKey = if (selectedKey == "s${i + 1}") null else "s${i + 1}" })
                        }
                    }
                    val total = d.legs.size
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HeartButton(p.hearts, p.heartedByMe, onHeart)
                        val label = when {
                            progress == null -> stringResource(R.string.course_navigate)
                            progress.hasNextLeg -> stringResource(R.string.course_navigate_next, progress.leg + 1, progress.total)
                            else -> stringResource(R.string.course_navigate_again)
                        }
                        PrimaryPill(label, EodiIcons.Navi, onNavigate, Modifier.weight(1f), enabled = total > 0)
                    }
                    val hint = when {
                        d.naviFailed -> stringResource(R.string.course_navi_failed)
                        progress != null -> stringResource(R.string.course_navi_auto, progress.leg, progress.total)
                        total > 1 -> stringResource(R.string.course_navi_legs_hint, total)
                        else -> null
                    }
                    if (hint != null) {
                        Text(
                            hint,
                            style = EodiTheme.type.caption,
                            color = if (d.naviFailed) colors.error else colors.textSecondary,
                            modifier = Modifier.padding(top = 8.dp, start = 8.dp),
                        )
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryPill(stringResource(R.string.course_back_to_list), EodiIcons.Back, onBack)
                }
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        map(Modifier.weight(0.55f).fillMaxHeight())
                        info(Modifier.weight(0.45f).fillMaxHeight())
                    }
                } else {
                    map(Modifier.fillMaxWidth().weight(0.4f))
                    Spacer(Modifier.height(20.dp))
                    info(Modifier.fillMaxWidth().weight(0.6f))
                }
            }
        }
    }
}

@Composable
private fun StopRow(order: Int, s: StopFact, selected: Boolean, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        color = if (selected) c.accentSoft else c.surface,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                StopPhoto(s.name, s.address, s.lat, s.lon, order = order, size = 104.dp)
                OrderDot(order, Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = EodiTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(s.category, s.address).filter { it.isNotBlank() }.joinToString(" · "),
                    style = EodiTheme.type.meta,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 화면 아래 알림 한 줄. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = EodiTheme.colors.textPrimary,
        contentColor = EodiTheme.colors.surface,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        modifier = modifier.padding(32.dp),
    ) {
        Text(text, style = EodiTheme.type.bodyStrong, modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp))
    }
}
