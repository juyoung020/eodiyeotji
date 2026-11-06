package app.eodiyeotji.ui.community

import app.eodiyeotji.ui.offers.CourseCouponBadge
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import app.eodiyeotji.core.Visit
import app.eodiyeotji.pleos.CourseProgress
import app.eodiyeotji.ui.map.DayMap
import app.eodiyeotji.ui.map.MapPhotoPin
import app.eodiyeotji.ui.place.PlacePhotoViewer
import app.eodiyeotji.ui.theme.AlbumPhoto
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.distanceText
import app.eodiyeotji.ui.timeline.durationText
import app.eodiyeotji.ui.share.ShareButtonStyle
import app.eodiyeotji.ui.share.ShareImageButton
import app.eodiyeotji.ui.share.ShareTarget
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
    var viewingPlace by remember { mutableStateOf<StopFact?>(null) }
    val openPlace: (StopFact) -> Unit = remember { { viewingPlace = it } }
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
                        CourseGrid(l.items, onOpen = viewModel::open, onHeart = viewModel::toggleHeart, onOpenPlace = openPlace)
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
                onOpenPlace = openPlace,
            )
        }
        // 들르는 곳 사진을 누르면: 지금은 그곳 사진 크게 보기 (장소 정보 시트가 생기면 여기서 바꾼다)
        viewingPlace?.let { s ->
            PlacePhotoViewer(s.name, s.address, s.lat, s.lon) { viewingPlace = null }
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

/** 카드 한 장 폭: 이보다 좁아지면 칸 수를 줄이고, 한 칸일 때는 이 폭에서 멈추고 가운데에 둔다. */
private val PostMinWidth = 500.dp
private val PostMaxWidth = 640.dp
private val PostGap = 24.dp

/**
 * 코스 글 피드: 넓은 창은 2~3칸 엇갈림 격자(글 길이만큼 카드 높이가 달라도 빈틈 없이), 좁은 창은 가운데 한 줄.
 * 칸 수는 창 폭에서 바로 정한다 (카드 폭 [PostMinWidth] 이상, 한 칸이면 [PostMaxWidth]까지).
 */
@Composable
internal fun CourseGrid(items: List<DriveCourse>, onOpen: (DriveCourse) -> Unit, onHeart: (String) -> Unit, onOpenPlace: (StopFact) -> Unit) {
    val state = rememberLazyStaggeredGridState()
    val firstKey = items.firstOrNull()?.post?.id
    // 정렬을 바꾸거나 새 글을 올려 맨 위 글이 바뀌면 맨 위로 (rememberTopGridState와 같은 이유)
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
            items(items, key = { it.post.id }, contentType = { "course" }) { c ->
                CoursePostCard(c, onOpen = { onOpen(c) }, onHeart = { onHeart(c.post.id) }, onOpenPlace = onOpenPlace)
            }
        }
    }
}

/**
 * 코스 글 카드 (사람이 올린 게시글처럼 읽히게): 글쓴이 머리 → 큰 대표 사진(+ 나머지 들르는 곳 작은 사진) →
 * 제목 → 글쓴이 소개 → 들르는 곳 순서 → 조용한 정보 칩(다녀온 날·날씨 / 곳·거리·시간 / 쿠폰) → 하트 · 코스 보기.
 * 태그는 상세에서만 보여 준다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoursePostCard(c: DriveCourse, onOpen: () -> Unit, onHeart: () -> Unit, onOpenPlace: (StopFact) -> Unit) {
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
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 12.dp)) {
            PostHeader(p.author, stringResource(R.string.community_byline, p.region, agoText(p.createdMs)), p.mine)
            Spacer(Modifier.height(18.dp))
            CourseHero(p.stops, onOpenPlace = onOpenPlace, onMore = onOpen)
            Spacer(Modifier.height(18.dp))
            Text(p.title, style = type.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(p.body, style = type.body, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            RouteLine(p.stops, Modifier.padding(top = 14.dp))
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxLines = 2,
            ) {
                VisitChip(c.visit)
                MetaChip(courseStatsText(p.stops.size, p.distanceM, p.durationMs))
                CourseCouponBadge(c.offers)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outline))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeartButton(p.hearts, p.heartedByMe, onHeart)
                Spacer(Modifier.weight(1f))
                TextAction(stringResource(R.string.course_view), onOpen)
            }
        }
    }
}

/** "4곳 · 56.2km · 4시간" */
@Composable
private fun courseStatsText(stops: Int, distanceM: Double, durationMs: Long): String =
    listOf(stringResource(R.string.community_stops_count, stops), distanceText(distanceM), durationText(durationMs)).joinToString(" · ")

/** 카드 아래 작은 사진 한 칸 (흰 테두리 포함, 누르는 자리 76dp 이상). */
private val StripThumb = 84.dp

/** 작은 사진 줄이 큰 사진 아래로 걸쳐 내려오는 만큼. */
private val StripOverlap = 40.dp

private val StripGap = 10.dp

/** 큰 사진 아래 보여 주는 작은 사진 수 (더 있으면 "+N"). 넓은 카드에서 4곳까지는 "+1" 대신 사진 네 장. */
private const val MaxStripThumbs = 3

/**
 * 대표 사진: 첫 들르는 곳을 폭 가득 16:9로 (앨범에 붙인 사진처럼 흰 테두리), 나머지 곳은 아래 모서리에 걸친 작은 사진 줄.
 * 사진을 누르면 그곳 사진 보기, 다 못 보여 준 곳은 "+N" (누르면 코스 상세).
 */
@Composable
private fun CourseHero(stops: List<StopFact>, onOpenPlace: (StopFact) -> Unit, onMore: () -> Unit) {
    val first = stops.firstOrNull()
    val rest = stops.drop(1)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = if (rest.isEmpty()) 0.dp else StripOverlap)) {
        // 카드 폭에 들어가는 칸 수만큼만 (좁은 폰 화면에서 줄이 카드 밖으로 넘치지 않게). 다 못 넣으면 마지막 칸은 "+N"
        val slots = ((maxWidth - 32.dp + StripGap) / (StripThumb + StripGap)).toInt().coerceIn(1, MaxStripThumbs + 1)
        val shown = if (rest.size <= slots) rest else rest.take(slots - 1)
        val hidden = rest.size - shown.size
        AlbumPhoto(Modifier.fillMaxWidth().aspectRatio(2.1f), radius = 18.dp) {
            if (first != null) {
                StopPhotoBox(
                    first.name, first.address, first.lat, first.lon, order = 1,
                    modifier = Modifier.fillMaxSize().clickable(role = Role.Image) { onOpenPlace(first) },
                    radius = 18.dp,
                    iconSize = 72.dp,
                )
            }
        }
        if (rest.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 16.dp).offset(y = StripOverlap),
                horizontalArrangement = Arrangement.spacedBy(StripGap),
            ) {
                shown.forEachIndexed { i, s ->
                    AlbumPhoto(Modifier.size(StripThumb), radius = 12.dp) {
                        StopPhotoBox(
                            s.name, s.address, s.lat, s.lon, order = i + 2,
                            modifier = Modifier.fillMaxSize().clickable(role = Role.Image) { onOpenPlace(s) },
                            radius = 12.dp,
                            iconSize = 30.dp,
                        )
                    }
                }
                if (hidden > 0) {
                    val desc = stringResource(R.string.course_more_stops_description, hidden)
                    AlbumPhoto(Modifier.size(StripThumb), radius = 12.dp) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(EodiTheme.colors.surfaceHigh)
                                .clickable(onClickLabel = desc, onClick = onMore)
                                .semantics { contentDescription = desc },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.course_more_stops, hidden),
                                style = EodiTheme.type.bodyStrong,
                                fontWeight = FontWeight.Bold,
                                color = EodiTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 들르는 곳 순서 한 줄: "경포해변 → 주문진수산시장 → 하조대 → 낙산사" (넘치면 …). */
@Composable
private fun RouteLine(stops: List<StopFact>, modifier: Modifier = Modifier) {
    if (stops.isEmpty()) return
    val c = EodiTheme.colors
    val route = remember(stops) { stops.joinToString("  →  ") { it.name } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(CommunityIcons.Road, null, tint = c.stay, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(route, style = EodiTheme.type.meta, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 조용한 정보 칩 (옅은 회색 바탕, 작은 글씨). */
@Composable
private fun MetaChip(text: String, icon: (@Composable () -> Unit)? = null) {
    val c = EodiTheme.colors
    Row(
        Modifier.background(c.surfaceHigh, CircleShape).padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = EodiTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 다녀온 날·계절·날씨 칩 (아이콘은 그날 날씨). */
@Composable
private fun VisitChip(v: Visit) {
    val text = visitText(v)
    if (text.isBlank()) return
    val (icon, tint) = weatherIcon(v.weather)
    MetaChip(text) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** 글자 + › 모양 가벼운 동작 (카드 아래 "코스 보기"). 누르는 자리는 76dp. */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.accent, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(EodiIcons.Chevron, null, tint = c.accent, modifier = Modifier.size(26.dp))
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

/**
 * 코스 상세: 지도(들르는 곳 핀·사진) + 글(글쓴이 머리·제목·소개·경로·다녀온 날·태그) + 들르는 곳 목록 + 하트·이 코스로 안내.
 * 들르는 곳 사진을 누르면 [onOpenPlace].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseDetailScreen(
    d: CourseDetail,
    progress: CourseProgress?,
    onBack: () -> Unit,
    onHeart: () -> Unit,
    onNavigate: () -> Unit,
    onOpenPlace: (StopFact) -> Unit,
) {
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
                            // 게시글 본문처럼: 글쓴이 머리 → 제목 → 글쓴이 소개(크게) → 경로 → 조용한 정보 칩 → 태그
                            Surface(color = colors.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(24.dp)) {
                                    PostHeader(p.author, stringResource(R.string.community_byline, p.region, agoText(p.createdMs)), p.mine)
                                    Spacer(Modifier.height(20.dp))
                                    Text(p.title, style = EodiTheme.type.title, modifier = Modifier.semantics { heading() })
                                    if (p.body.isNotBlank()) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(p.body, style = EodiTheme.type.body, color = colors.textPrimary)
                                    }
                                    RouteLine(p.stops, Modifier.padding(top = 16.dp))
                                    Spacer(Modifier.height(16.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        VisitChip(d.course.visit)
                                        MetaChip(courseStatsText(p.stops.size, p.distanceM, p.durationMs))
                                        CourseCouponBadge(d.course.offers)
                                    }
                                    if (p.tags.isNotEmpty()) {
                                        Spacer(Modifier.height(14.dp))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            p.tags.forEach { TagChip(it) }
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "stops-title") {
                            Text(
                                stringResource(R.string.course_stops_title),
                                style = EodiTheme.type.label,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                            )
                        }
                        itemsIndexed(p.stops, key = { i, s -> "stop-$i-${s.name}" }) { i, s ->
                            StopRow(
                                i + 1, s,
                                selected = selectedKey == "s${i + 1}",
                                onClick = { selectedKey = if (selectedKey == "s${i + 1}") null else "s${i + 1}" },
                                onOpenPhoto = { onOpenPlace(s) },
                            )
                        }
                    }
                    val total = d.legs.size
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HeartButton(p.hearts, p.heartedByMe, onHeart)
                        // 이 코스를 사진 카드로 공유 (폰 판만 보인다, ui/share)
                        ShareImageButton(ShareTarget.Course(p.id), style = ShareButtonStyle.ICON)
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
private fun StopRow(order: Int, s: StopFact, selected: Boolean, onClick: () -> Unit, onOpenPhoto: () -> Unit) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        color = if (selected) c.accentSoft else c.surface,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 사진을 누르면 그곳 사진 크게 보기, 줄의 나머지를 누르면 지도에서 그곳 고르기
            Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Image, onClick = onOpenPhoto)) {
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
