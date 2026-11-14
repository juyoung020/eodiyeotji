package app.eodiyeotji.ui.community

import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.gallery.MyPhotoStrip
import app.eodiyeotji.ui.gallery.MyPhotoViewer
import app.eodiyeotji.ui.common.agoText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import app.eodiyeotji.ui.theme.mapInk
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
import androidx.compose.runtime.remember
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
import app.eodiyeotji.core.FlightSight
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
                        DriveRouteGrid(l.items, onOpen = { viewModel.open(it) }, onFly = { viewModel.open(it, fly = true) }, onHeart = viewModel::toggleHeart)
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
                onHidden = viewModel::onHidden,
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

/** 이보다 좁으면 폰 세로 화면 (차 화면 · 나눈 창은 이보다 넓다). */
private val PhoneToolbar = 720.dp

@Composable
private fun DriveToolbar(sort: DriveRouteSort, onSort: (DriveRouteSort) -> Unit, onShare: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp)) {
        val compact = maxWidth < CompactToolbar
        val phone = maxWidth < PhoneToolbar
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
            // 폰 세로(아주 좁은 창)에서는 "코스 올리기" 알약이 정렬 줄을 밀어 "내 근처"가 잘렸다 → 파란 동그라미 아이콘 버튼
            if (phone) RoundIconButton(
                CommunityIcons.Upload,
                stringResource(R.string.course_share_short),
                onShare,
                containerColor = EodiTheme.colors.accent,
                contentColor = EodiTheme.colors.onAccent,
            ) else PrimaryPill(
                stringResource(if (compact) R.string.course_share_short else R.string.course_share_from_record),
                CommunityIcons.Upload,
                onShare,
            )
        }
    }
}

/** 글 카드 격자: 넓은 창은 2~3칸 엇갈림 격자, 좁은 창은 가운데 한 줄 (코스 카드와 같은 규칙). */
@Composable
private fun DriveRouteGrid(items: List<DriveRouteItem>, onOpen: (DriveRouteItem) -> Unit, onFly: (DriveRouteItem) -> Unit, onHeart: (String) -> Unit) {
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
                DriveRouteCard(it, onOpen = { onOpen(it) }, onHeart = { onHeart(it.route.id) }, onAction = { onFly(it) })
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
    /** 아래 오른쪽 글자 버튼. 목록에서는 "3D로 미리 달리기"라 상세를 열고 비행까지 바로 시작한다 (전에는 상세만 열려 한 번 더 눌러야 했다). */
    onAction: () -> Unit = onOpen,
) {
    val colors = EodiTheme.colors
    val type = EodiTheme.type
    val r = item.route
    PressSurface(
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
                FromRecordChip()
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
                TextAction(action, onAction)
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
    // 때와 상관없이 먹색 반투명 바탕 하나 (주황 · 초록 띠가 강조색 파랑 하나 원칙과 어긋났다). 어느 그림 위에서도 흰 글자가 읽힌다
    val bg = androidx.compose.ui.graphics.Color(0xD9111827)
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
    onHidden: () -> Unit,
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
                        // 비행 진행(0~1): 매 화면 바뀌므로 상태 하나에 두고 진행 막대는 그리기 단계에서만 읽는다
                        val flightT = remember(r.id) { mutableFloatStateOf(0f) }
                        // 끝까지 달려 도착했으면 끝점 카드(가 보고 싶게 한 줄 + 안내 · 다시 보기)를 띄운다 (다시 달리면 감춘다)
                        var arrived by remember(r.id) { mutableStateOf(false) }
                        LaunchedEffect(d.flying) { if (d.flying) arrived = false }
                        if (driving) {
                            RouteSnapshotImage(r, description, Modifier.fillMaxSize())
                        } else {
                            ScenicRouteMap(
                                style = d.style,
                                theme = r.theme,
                                camera = d.camera,
                                frames = d.frames,
                                flying = d.flying,
                                plan = d.flightPlan,
                                onFlightEnd = onFlightEnd,
                                contentDescription = description,
                                modifier = Modifier.fillMaxSize(),
                                onFlightProgress = { flightT.floatValue = it },
                                onArrive = { arrived = true },
                            )
                        }
                        AnimatedVisibility(
                            visible = arrived && !driving,
                            enter = fadeIn(tween(900, delayMillis = 500)),
                            exit = fadeOut(tween(500)),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, bottom = 52.dp),
                        ) {
                            ArrivedCard(r, distanceText(r.distanceM), onNavigate = onNavigate, onReplay = onFly, canNavigate = d.legs.isNotEmpty())
                        }
                        if (!driving && d.flying) {
                            LaunchedEffect(Unit) { flightT.floatValue = 0f }
                            SightPopup(flightT, d.sights, Modifier.align(Alignment.TopCenter).padding(top = 96.dp))
                            FlightProgressBar(flightT, d.sights, stringResource(R.string.drive_from_to, r.startName, r.endName), Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, bottom = 52.dp))
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
                                    if (r.photos.isNotEmpty()) {
                                        // 글쓴이가 이 길에서 찍어 붙인 사진 (누르면 크게)
                                        var viewing by remember(r.id) { mutableStateOf<Int?>(null) }
                                        Spacer(Modifier.height(14.dp))
                                        MyPhotoStrip(r.photos, r.photos.size, onOpen = { viewing = it }, size = 120.dp, maxShown = 4)
                                        viewing?.let { MyPhotoViewer(r.photos, it, r.title, onDismiss = { viewing = null }) }
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
                        if (!r.mine) ReportButton(app.eodiyeotji.core.ReportTarget.DRIVE_ROUTE, r.id, onHidden)
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

/**
 * 미리보기 비행 아래 띠: "지금: 주문진항"(지나는 풍경, Rust가 정한 구간) + 진행 막대(풍경 자리에 눈금).
 * 진행 값은 매 화면 바뀌므로 막대는 그리기 단계에서만 읽고, 글은 풍경이 바뀔 때만 다시 그린다.
 */
@Composable
private fun FlightProgressBar(t: FloatState, sights: List<FlightSight>, fallback: String, modifier: Modifier = Modifier) {
    val now by remember(sights) { derivedStateOf { sights.firstOrNull { t.floatValue in it.from..it.to }?.name } }
    Column(
        modifier.fillMaxWidth().background(FlightScrim, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            // 풍경 구간 밖에서는 빈 검은 띠 대신 "시작 → 끝"을 보인다
            now?.let { stringResource(R.string.drive_fly_now, it) } ?: fallback,
            style = EodiTheme.type.label,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(
            Modifier.fillMaxWidth().height(8.dp).drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(FlightTrack, cornerRadius = r)
                drawRoundRect(FlightFill, size = size.copy(width = size.width * t.floatValue.coerceIn(0f, 1f)), cornerRadius = r)
                sights.forEach { s -> drawCircle(Color.White, radius = size.height * 0.45f, center = Offset(size.width * s.at, size.height / 2)) }
            },
        )
    }
}

/**
 * 지나는 풍경 카드: 풍경 구간에 들어서면 이름이 1.6초쯤 살며시 떴다 사라진다.
 * 진행 값은 매 화면 바뀌지만 이 카드는 지나는 풍경이 바뀔 때만 다시 그린다. (풍경마다 사진은 아직 없어 이름만)
 */
@Composable
private fun SightPopup(t: FloatState, sights: List<FlightSight>, modifier: Modifier = Modifier) {
    val now by remember(sights) { derivedStateOf { sights.firstOrNull { t.floatValue in it.from..it.to }?.name } }
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(now) {
        val n = now ?: return@LaunchedEffect
        shown = n
        delay(SIGHT_SHOW_MS)
        shown = null
    }
    AnimatedVisibility(shown != null, modifier, enter = fadeIn(tween(450)), exit = fadeOut(tween(650))) {
        var last by remember { mutableStateOf("") }
        shown?.let { last = it }
        Row(
            Modifier.background(FlightScrim, RoundedCornerShape(28.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(FlightFill, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(last, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 미리 달리기 도착 카드: 끝점 · 가 보고 싶게 한 줄("주말 아침, 이 길 어때요?") · 거리 + [이 코스로 안내] [다시 보기]. */
@Composable
private fun ArrivedCard(r: DriveRoute, distance: String, onNavigate: () -> Unit, onReplay: () -> Unit, canNavigate: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(FlightScrim, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(arrivedInvite(r.bestTime)), style = EodiTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.drive_fly_arrived, r.endName, distance),
            style = EodiTheme.type.label,
            color = FlightTextSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryPill(stringResource(R.string.course_navigate), EodiIcons.Navi, onNavigate, Modifier.weight(1f), enabled = canNavigate)
            SecondaryPill(stringResource(R.string.drive_fly_again), CommunityIcons.Play, onReplay)
        }
    }
}

private fun arrivedInvite(b: BestTime): Int = when (b) {
    BestTime.NIGHT -> R.string.drive_invite_night
    BestTime.SUNSET -> R.string.drive_invite_sunset
    BestTime.MORNING -> R.string.drive_invite_morning
    BestTime.DAY -> R.string.drive_invite_day
    BestTime.FOLIAGE -> R.string.drive_invite_foliage
    BestTime.BLOSSOM -> R.string.drive_invite_blossom
}

private const val SIGHT_SHOW_MS = 1_600L
private val FlightScrim = Color(0xCC111722)

/** 진행 막대 · 도착 글: 앱의 길 색(파랑, 어두운 띠 위라 밤 색) */
private val FlightFill = mapInk(true).trip
private val FlightTextSoft = Color.White.copy(alpha = 0.82f)
private val FlightTrack = Color(0x55FFFFFF)
