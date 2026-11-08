package app.eodiyeotji.ui.community

import app.eodiyeotji.ui.common.agoText
import androidx.compose.foundation.BorderStroke

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.BestTime
import app.eodiyeotji.core.DriveRoute
import app.eodiyeotji.core.DriveRouteItem
import app.eodiyeotji.core.DriveRouteSort
import app.eodiyeotji.core.RouteTheme
import app.eodiyeotji.pleos.CourseProgress
import app.eodiyeotji.ui.map.RouteSnapshotImage
import app.eodiyeotji.ui.map.ScenicRouteMap
import app.eodiyeotji.ui.theme.AlbumPhoto
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.ui.common.durationText
import kotlinx.coroutines.delay

/** 이 폭 이상이면 상세를 3D 지도 | 글 두 칸으로 나눈다. */
private val WideDetail = 1100.dp

/** 카드 한 장 폭 (코스 카드와 같다). */
private val PostMinWidth = 500.dp
private val PostMaxWidth = 640.dp
private val PostGap = 24.dp

/**
 * 커뮤니티 > 드라이브 코스: 차에서 즐기는 경치 길 (들를 곳 없음). 정렬[인기 · 최신 · 내 근처] + 내 주행에서 올리기 + 글 카드 격자.
 * 카드 대표 그림은 3D 스냅숏, 누르면 상세(라이브 3D 지도 · 미리보기 비행 · 이 코스로 안내).
 */
@Composable
fun DriveRoutePane(viewModel: DriveRouteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sharing by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DriveToolbar(state.sort, viewModel::setSort, onShare = { sharing = true })
            if (state.sort == DriveRouteSort.NEAREST && !state.nearKnown) {
                Text(
                    stringResource(R.string.community_near_unknown),
                    style = EodiTheme.type.caption,
                    color = EodiTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
                )
            }
            when (val l = state.list) {
                Load.Loading -> LoadingBox()
                is Load.Failed -> ErrorState(stringResource(R.string.community_failed, l.message), viewModel::reload)
                is Load.Loaded ->
                    if (l.items.isEmpty()) {
                        CenterMessage(stringResource(R.string.drive_empty), icon = CommunityIcons.Road)
                    } else {
                        DriveRouteGrid(l.items, onOpen = viewModel::open, onHeart = viewModel::toggleHeart)
                    }
            }
        }
        state.detail?.let { d ->
            BackHandler(onBack = viewModel::closeDetail)
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            val driving by viewModel.isDriving.collectAsStateWithLifecycle()
            DriveRouteDetailScreen(
                d,
                driving = driving,
                progress = progress?.takeIf { it.courseId == d.item.route.id && !it.finished },
                onBack = viewModel::closeDetail,
                onHeart = { viewModel.toggleHeart(d.item.route.id) },
                onNavigate = viewModel::navigate,
                onFly = viewModel::toggleFlight,
                onFlightEnd = viewModel::stopFlight,
                onMapSize = viewModel::onMapSize,
            )
        }
        if (sharing) {
            BackHandler { sharing = false }
            ShareDriveRouteSheet(onDone = { shared ->
                sharing = false
                if (shared != null) viewModel.onShared()
            })
        }
        if (state.justShared) {
            LaunchedEffect(Unit) {
                delay(2_500)
                viewModel.dismissShared()
            }
            Notice(stringResource(R.string.drive_shared_done), Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun DriveToolbar(sort: DriveRouteSort, onSort: (DriveRouteSort) -> Unit, onShare: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp)) {
        val compact = maxWidth < CompactToolbar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                ChoiceRow(
                    options = listOf(
                        DriveRouteSort.POPULAR to stringResource(R.string.community_sort_popular),
                        DriveRouteSort.RECENT to stringResource(R.string.community_sort_recent),
                        DriveRouteSort.NEAREST to stringResource(R.string.drive_sort_near),
                    ),
                    selected = sort,
                    onSelect = onSort,
                )
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

/** 글 카드 격자: 넓은 창은 2~3칸 엇갈림 격자, 좁은 창은 가운데 한 줄 (코스 카드와 같은 규칙). */
@Composable
private fun DriveRouteGrid(items: List<DriveRouteItem>, onOpen: (DriveRouteItem) -> Unit, onHeart: (String) -> Unit) {
    val state = rememberLazyStaggeredGridState()
    val firstKey = items.firstOrNull()?.route?.id
    LaunchedEffect(firstKey) { state.scrollToItem(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inner = maxWidth - 64.dp
        val cols = ((inner + PostGap) / (PostMinWidth + PostGap)).toInt().coerceAtLeast(1)
        val side = if (cols == 1) maxOf(32.dp, (maxWidth - PostMaxWidth) / 2) else 32.dp
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(cols),
            state = state,
            contentPadding = PaddingValues(start = side, end = side, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(PostGap),
            verticalItemSpacing = PostGap,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.route.id }, contentType = { "drive" }) { it ->
                DriveRouteCard(it, onOpen = { onOpen(it) }, onHeart = { onHeart(it.route.id) })
            }
        }
    }
}

/**
 * 드라이브 코스 글 카드 (사람이 올린 게시글처럼): 글쓴이 머리 → 3D 대표 그림(스냅숏, "야경 추천" 띠) → 제목 → 소개 →
 * 시작 → 끝 → 조용한 정보 칩(언제·다녀온 날·날씨 / 거리·시간 / 여기서 거리) → 하트 · 길 보기.
 * 에이전트 추천("여기 근처 드라이브 추천코스")도 같은 카드: [rank]면 그림 오른쪽 위 순위, [focused]면 파란 테두리와
 * "안내 제안 중", [onHeart]가 없으면 하트 수만, [action]은 아래 오른쪽 글자.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DriveRouteCard(
    item: DriveRouteItem,
    onOpen: () -> Unit,
    onHeart: (() -> Unit)?,
    rank: Int? = null,
    focused: Boolean = false,
    action: String = stringResource(R.string.drive_view),
) {
    val colors = EodiTheme.colors
    val type = EodiTheme.type
    val r = item.route
    Surface(
        onClick = onOpen,
        color = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        border = if (focused) BorderStroke(3.dp, colors.accent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 12.dp)) {
            PostHeader(r.author, stringResource(R.string.community_byline, r.region, agoText(r.createdMs)), r.mine)
            Spacer(Modifier.height(18.dp))
            AlbumPhoto(Modifier.fillMaxWidth().aspectRatio(2.1f), radius = 18.dp) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
                    RouteSnapshotImage(r, stringResource(R.string.drive_preview_description, r.title), Modifier.fillMaxSize())
                    BestTimeBadge(r.bestTime, r.theme, Modifier.align(Alignment.TopStart).padding(14.dp))
                    if (rank != null) RankBadge(rank, focused, Modifier.align(Alignment.TopEnd).padding(12.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            if (focused) {
                Text(stringResource(R.string.agent_course_focus), style = type.label, fontWeight = FontWeight.Bold, color = colors.accent)
                Spacer(Modifier.height(4.dp))
            }
            Text(r.title, style = type.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (r.body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(r.body, style = type.body, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            FromToLine(r, Modifier.padding(top = 14.dp))
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxLines = 2) {
                VisitChip(r.visit)
                InfoChip(routeStatsText(r))
                item.distanceKm?.let { InfoChip(stringResource(R.string.drive_from_here, distanceText(it * 1000.0))) }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outline))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onHeart != null) {
                    HeartButton(r.hearts, r.heartedByMe, onHeart)
                } else {
                    HeartCount(r.hearts, r.heartedByMe, Modifier.padding(horizontal = 12.dp, vertical = 20.dp))
                }
                Spacer(Modifier.weight(1f))
                TextAction(action, onOpen)
            }
        }
    }
}

/** "22.5km · 25분" */
@Composable
@ReadOnlyComposable
private fun routeStatsText(r: DriveRoute): String = listOf(distanceText(r.distanceM), durationText(r.durationMs)).joinToString(" · ")

/** 시작 → 끝 한 줄 (길 아이콘). */
@Composable
private fun FromToLine(r: DriveRoute, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(CommunityIcons.Road, null, tint = c.stay, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.drive_from_to, r.startName, r.endName), style = EodiTheme.type.meta, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
@ReadOnlyComposable
fun bestTimeLabel(b: BestTime): String = stringResource(
    when (b) {
        BestTime.NIGHT -> R.string.drive_best_night
        BestTime.SUNSET -> R.string.drive_best_sunset
        BestTime.MORNING -> R.string.drive_best_morning
        BestTime.DAY -> R.string.drive_best_day
        BestTime.FOLIAGE -> R.string.drive_best_foliage
        BestTime.BLOSSOM -> R.string.drive_best_blossom
    },
)

/** 그림 위 "야경 추천" 띠 (그림 위에서도 읽히게 때 색 바탕 + 흰 글자). */
@Composable
fun BestTimeBadge(b: BestTime, theme: RouteTheme, modifier: Modifier = Modifier) {
    val bg = when (theme) {
        RouteTheme.NIGHT -> androidx.compose.ui.graphics.Color(0xE61B2440)
        RouteTheme.SUNSET -> androidx.compose.ui.graphics.Color(0xE6C2553B)
        RouteTheme.DAY -> androidx.compose.ui.graphics.Color(0xE6236B4E)
    }
    Text(
        stringResource(R.string.drive_best_chip, bestTimeLabel(b)),
        style = EodiTheme.type.caption,
        fontWeight = FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.White,
        modifier = modifier.background(bg, CircleShape).padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

/**
 * 드라이브 코스 상세: 큰 3D 지도(라이브, 미리보기 비행) + 글(글쓴이·제목·소개·시작 → 끝·정보 칩·가는 길 풍경) + 하트 · 이 코스로 안내.
 * 주행 중이면 라이브 지도 대신 스냅숏 한 장, 비행 버튼 없음.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveRouteDetailScreen(
    d: DriveRouteDetail,
    driving: Boolean,
    progress: CourseProgress?,
    onBack: () -> Unit,
    onHeart: () -> Unit,
    onNavigate: () -> Unit,
    onFly: () -> Unit,
    onFlightEnd: () -> Unit,
    onMapSize: (Float, Float) -> Unit,
) {
    val colors = EodiTheme.colors
    val r = d.item.route
    val description = stringResource(R.string.drive_preview_description, r.title)
    Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.textPrimary) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 24.dp)) {
            val wide = maxWidth >= WideDetail
            val map: @Composable (Modifier) -> Unit = { m ->
                Surface(shape = RoundedCornerShape(EodiTheme.space.radiusL), color = colors.surface, modifier = m) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val w = maxWidth.value
                        val h = maxHeight.value
                        LaunchedEffect(r.id, w, h) { onMapSize(w, h) }
                        if (driving) {
                            RouteSnapshotImage(r, description, Modifier.fillMaxSize())
                        } else {
                            ScenicRouteMap(
                                style = d.style,
                                theme = r.theme,
                                camera = d.camera,
                                frames = d.frames,
                                flying = d.flying,
                                flightMs = d.flightMs,
                                onFlightEnd = onFlightEnd,
                                contentDescription = description,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        BestTimeBadge(r.bestTime, r.theme, Modifier.align(Alignment.TopStart).padding(18.dp))
                        if (!driving && d.frames.size >= 2) {
                            PrimaryPill(
                                stringResource(if (d.flying) R.string.drive_fly_stop else R.string.drive_fly),
                                if (d.flying) CommunityIcons.Stop else CommunityIcons.Play,
                                onFly,
                                Modifier.align(Alignment.TopEnd).padding(18.dp),
                            )
                        }
                    }
                }
            }
            val info: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        item(key = "head") {
                            Surface(color = colors.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(24.dp)) {
                                    PostHeader(r.author, stringResource(R.string.community_byline, r.region, agoText(r.createdMs)), r.mine)
                                    Spacer(Modifier.height(20.dp))
                                    Text(r.title, style = EodiTheme.type.title, modifier = Modifier.semantics { heading() })
                                    if (r.body.isNotBlank()) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(r.body, style = EodiTheme.type.body, color = colors.textPrimary)
                                    }
                                    FromToLine(r, Modifier.padding(top = 16.dp))
                                    Spacer(Modifier.height(16.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        VisitChip(r.visit)
                                        InfoChip(routeStatsText(r))
                                        d.item.distanceKm?.let { InfoChip(stringResource(R.string.drive_from_here, distanceText(it * 1000.0))) }
                                    }
                                }
                            }
                        }
                        if (r.highlights.isNotEmpty()) {
                            item(key = "sights") {
                                Column(Modifier.padding(start = 8.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.drive_sights_title), style = EodiTheme.type.label, color = colors.textTertiary)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        r.highlights.forEach { InfoChip(it.name) }
                                    }
                                }
                            }
                        }
                        if (driving) {
                            item(key = "driving") {
                                Text(stringResource(R.string.drive_driving_note), style = EodiTheme.type.caption, color = colors.textSecondary, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HeartButton(r.hearts, r.heartedByMe, onHeart)
                        val label = when {
                            progress == null -> stringResource(R.string.course_navigate)
                            progress.hasNextLeg -> stringResource(R.string.drive_navigate_end)
                            else -> stringResource(R.string.course_navigate_again)
                        }
                        PrimaryPill(label, EodiIcons.Navi, onNavigate, Modifier.weight(1f), enabled = d.legs.isNotEmpty())
                    }
                    val hint = when {
                        d.naviFailed -> stringResource(R.string.course_navi_failed)
                        progress != null && progress.hasNextLeg -> stringResource(R.string.drive_navi_on_way)
                        else -> stringResource(R.string.drive_navi_hint)
                    }
                    Text(
                        hint,
                        style = EodiTheme.type.caption,
                        color = if (d.naviFailed) colors.error else colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp, start = 8.dp),
                    )
                }
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryPill(stringResource(R.string.drive_back_to_list), EodiIcons.Back, onBack)
                }
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        map(Modifier.weight(0.6f).fillMaxHeight())
                        info(Modifier.weight(0.4f).fillMaxHeight())
                    }
                } else {
                    map(Modifier.fillMaxWidth().weight(0.5f))
                    Spacer(Modifier.height(20.dp))
                    info(Modifier.fillMaxWidth().weight(0.5f))
                }
            }
        }
    }
}
