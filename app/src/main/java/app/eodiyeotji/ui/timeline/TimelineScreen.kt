package app.eodiyeotji.ui.timeline

import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.core.StayMyPhotos
import app.eodiyeotji.ui.gallery.AskGalleryOnce
import app.eodiyeotji.ui.gallery.MyPhotoStrip
import app.eodiyeotji.ui.gallery.MyPhotoViewer
import app.eodiyeotji.ui.community.CenterMessage
import app.eodiyeotji.ui.community.ErrorState
import app.eodiyeotji.ui.community.LoadingBox
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.core.PartnerOffer
import app.eodiyeotji.ui.offers.PlaceCouponBadge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.Notice
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.community.ShareCourseSheet
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.R
import app.eodiyeotji.ui.place.LocalOpenPlace
import app.eodiyeotji.ui.place.placeDetailOpenLabel
import app.eodiyeotji.ui.community.StopPhotoBox
import app.eodiyeotji.ui.places.EditTarget
import app.eodiyeotji.ui.places.PlaceEditHost
import app.eodiyeotji.ui.places.PlaceEditViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.CompositionLocalProvider
import app.eodiyeotji.ui.search.SearchSlot
import app.eodiyeotji.ui.map.MapPhotoPin
import app.eodiyeotji.core.DayEntry
import app.eodiyeotji.core.DaySummary
import app.eodiyeotji.core.PlaceSource
import app.eodiyeotji.core.StaySummary
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.TripSummary
import app.eodiyeotji.ui.home.CoreStatusLine
import app.eodiyeotji.ui.map.DayMap
import app.eodiyeotji.ui.share.ShareButtonStyle
import app.eodiyeotji.ui.share.ShareImageButton
import app.eodiyeotji.ui.share.ShareTarget
import app.eodiyeotji.ui.recap.RecapListTop
import app.eodiyeotji.ui.recap.RecapOverlay
import app.eodiyeotji.ui.memorymap.MemoryMapOverlay
import app.eodiyeotji.ui.biz.BizOverlay
import app.eodiyeotji.ui.biz.BizViewModel
import app.eodiyeotji.ui.recap.RecapViewModel
import app.eodiyeotji.ui.theme.AlbumPhoto
import app.eodiyeotji.ui.theme.DottedRoute
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import java.time.DayOfWeek
import java.time.LocalDate

/** 이 폭 이상이면 목록과 상세를 나란히 둔다 (Pleos 넓은 창 1682dp, 나눈 창 약 841dp). */
private val WideBreakpoint = 1200.dp

/** 상세 카드에서 지도가 차지하는 높이 비율 (머리 아래 남은 높이 기준, 나머지는 항목 목록). */
private const val MapWeight = 0.46f

/** 고른 행 색이 바뀌는 시간. 짧게 → 누르자마자 반응이 보인다 (Pleos: 응답 0.25초 이내). */
private const val SelectMs = 160

/** 좁은 창에서 목록 ↔ 상세가 바뀌는 시간. */
private const val PageMs = 220

/** 타임라인 진입점 (정차 중 화면). Rust 코어 상태 줄은 디버그 빌드에서만 머리 구석에 둔다. */
@Composable
fun TimelineRoute(
    headerAction: (@Composable () -> Unit)? = null,
    viewModel: TimelineViewModel = hiltViewModel(),
    compact: Boolean = false,
    headerBanner: (@Composable () -> Unit)? = null,
    search: SearchSlot? = null,
    /** 여행 간 날만 모아 보기 (위쪽 입구 띠의 "여행 모음") */
    onOpenTrips: (() -> Unit)? = null,
    /** 날짜 목록 맨 위 입구 띠. 차 홈은 "모아보기" 탭이 맡아 false로 넘긴다. */
    showEntries: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 날짜 목록 맨 위: "N년 전 오늘" 카드 · 이번 달 요약 입구 (ui/recap)
    val recap: RecapViewModel = hiltViewModel()
    val biz: BizViewModel = hiltViewModel()
    val openDay: (String) -> Unit = { viewModel.openDay(it, null) }
    var shareEpisode by rememberSaveable { mutableStateOf<Long?>(null) }
    var shared by remember { mutableStateOf(false) }
    // 항목 · 하루의 ⋯ 메뉴 (숨기기 · 지우기 · 이름 고치기 · 집 · 회사 · 저장, ui/places)
    val editVm: PlaceEditViewModel = hiltViewModel()
    val edits = remember(editVm) {
        TimelineEdits(
            onEntry = { e ->
                editVm.open(
                    when (e) {
                        is DayEntry.Trip -> EditTarget.Trip(e.trip)
                        is DayEntry.Stay -> EditTarget.Stay(e.stay)
                    },
                )
            },
            onDay = { editVm.open(EditTarget.Day(it)) },
        )
    }
    CompositionLocalProvider(LocalTimelineEdits provides edits) { TimelineScreen(
        state = state,
        onShare = { shareEpisode = it },
        onSelectDay = viewModel::select,
        onBack = viewModel::clearSelection,
        onRetry = viewModel::reload,
        onWideChange = viewModel::setWide,
        onEntryClick = viewModel::toggleEntry,
        onMapSelect = viewModel::selectEntry,
        status = if (BuildConfig.DEBUG) DebugStatus else null,
        headerAction = headerAction,
        compact = compact,
        headerBanner = headerBanner,
        search = search,
        listTop = { RecapListTop(recap, onOpenDay = openDay, onOpenBiz = biz::open, onOpenTrips = onOpenTrips, showEntries = showEntries) },
    ) }
    PlaceEditHost(editVm)
    // 내 사진 카드: 머문 곳이 있는 하루 상세를 처음 열 때 한 번만 갤러리 권한을 묻는다 (허용하면 다시 읽어 사진을 채운다)
    val askGallery = (state.detail as? DetailState.Loaded)?.entries?.any { it is DayEntry.Stay } == true
    AskGalleryOnce(enabled = askGallery, onGranted = viewModel::reload)
    RecapOverlay(recap, onOpenDay = openDay)
    MemoryMapOverlay(recap)
    BizOverlay(biz, onOpenDay = openDay)
    // 하루 상세에서 바로 커뮤니티에 코스로 올리기 (커뮤니티 탭까지 가지 않는다)
    shareEpisode?.let { id ->
        BackHandler { shareEpisode = null }
        ShareCourseSheet(episodeId = id, onDone = { post ->
            shareEpisode = null
            if (post != null) shared = true
        })
    }
    if (shared) {
        LaunchedEffect(Unit) {
            delay(2_500)
            shared = false
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Notice(stringResource(R.string.timeline_shared))
        }
    }
}

private val DebugStatus: @Composable () -> Unit = { CoreStatusLine() }

/**
 * 상태만 받아 그리는 화면. 창 폭은 실제 배치 크기(BoxWithConstraints)로 읽어서
 * Pleos에서 실행 중에 창을 넓히거나 나눠도 바로 따라간다.
 *
 * 넓은 창: 머리 아래 왼쪽 날짜 목록 | 오른쪽 하루 상세 카드. 좁은 창: 목록 → (날 고르면) 상세 카드 한 장.
 */
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onSelectDay: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWideChange: (Boolean) -> Unit,
    onEntryClick: (String) -> Unit,
    onMapSelect: (String?) -> Unit,
    status: (@Composable () -> Unit)?,
    headerAction: (@Composable () -> Unit)? = null,
    onShare: (Long) -> Unit = {},
    compact: Boolean = false,
    headerBanner: (@Composable () -> Unit)? = null,
    search: SearchSlot? = null,
    listTop: (@Composable () -> Unit)? = null,
) {
    val c = EodiTheme.colors
    val space = EodiTheme.space
    if (compact) {
        CompactTimeline(state, onSelectDay, onBack, onRetry, onWideChange, onEntryClick, onMapSelect, status, headerAction, onShare, headerBanner, search, listTop)
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = c.background, contentColor = c.textPrimary) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= WideBreakpoint
            val reportWide by rememberUpdatedState(onWideChange)
            LaunchedEffect(wide) { reportWide(wide) }

            // 목록 스크롤 위치는 좁은 창에서 상세를 갔다 와도, 창 폭이 바뀌어도 유지한다
            val listState = rememberLazyListState()
            val bodyPadding = Modifier.padding(start = space.page, end = space.page, bottom = space.page)

            Column(Modifier.fillMaxSize()) {
                TimelineHeader(wide, headerAction, search = search)
                // 검색 중이면 목록 · 상세 자리에 검색 결과
                if (search?.active == true) {
                    search.results(Modifier.fillMaxSize().padding(start = space.page, end = space.page))
                } else when (val days = state.days) {
                    DaysState.Loading -> LoadingBox(message = stringResource(R.string.timeline_loading))
                    is DaysState.Failed -> ErrorState(days.message, onRetry, Modifier.fillMaxSize(), title = stringResource(R.string.timeline_failed_title), retryLabel = stringResource(R.string.timeline_retry))
                    is DaysState.Loaded -> when {
                        days.days.isEmpty() -> EmptyPane(Modifier.fillMaxSize())
                        // 넓은 창(차): 지도가 바탕인 구성 (TimelineLayouts.kt). 검색 결과처럼 밖에서 날을 골라도 날짜 줄이 그날까지 굴러간다
                        wide -> WideHome(
                            days = days.days,
                            today = days.today,
                            state = state,
                            railState = listState,
                            onSelectDay = onSelectDay,
                            onEntryClick = onEntryClick,
                            onMapSelect = onMapSelect,
                            onShare = onShare,
                            listTop = listTop,
                            modifier = Modifier.fillMaxSize().then(bodyPadding),
                        )
                        else -> AnimatedContent(
                            targetState = state.selectedDay != null,
                            transitionSpec = {
                                // 상세로 갈 때는 오른쪽에서, 돌아올 때는 왼쪽에서 살짝 밀려 들어온다
                                val dir = if (targetState) 1 else -1
                                (slideInHorizontally(tween(PageMs)) { dir * it / 8 } + fadeIn(tween(PageMs))) togetherWith
                                    (slideOutHorizontally(tween(PageMs)) { -dir * it / 8 } + fadeOut(tween(PageMs / 2)))
                            },
                            label = "list-detail",
                            modifier = Modifier.fillMaxSize(),
                        ) { showDetail ->
                            if (showDetail) {
                                BackHandler(onBack = onBack)
                                DayDetailCard(
                                    detail = state.detail,
                                    today = days.today,
                                    selectedEntry = state.selectedEntry,
                                    onEntryClick = onEntryClick,
                                    onMapSelect = onMapSelect,
                                    onShare = onShare,
                                    onBack = onBack,
                                    modifier = Modifier.fillMaxSize().then(bodyPadding),
                                )
                            } else {
                                DayList(
                                    days = days.days,
                                    today = days.today,
                                    highlightedDay = null,
                                    showChevron = true,
                                    onSelect = onSelectDay,
                                    listState = listState,
                                    footer = status,
                                    top = listTop,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = space.page),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 폰 세로 화면 (compact)

/**
 * 폰 판 세로 화면 (폭 약 360~430dp). 목록 → 날을 누르면 상세가 화면 전체를 쓴다 (머리·아래 탭 없이).
 * 상세는 위 약 40%가 지도(머문 곳 말풍선), 그 아래 통계 · 커뮤니티에 올리기 · 시간순 항목이 한 목록으로 굴러간다.
 * 차 화면은 이 함수를 쓰지 않는다 ([TimelineScreen]의 compact = false).
 */
@Composable
private fun CompactTimeline(
    state: TimelineUiState,
    onSelectDay: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWideChange: (Boolean) -> Unit,
    onEntryClick: (String) -> Unit,
    onMapSelect: (String?) -> Unit,
    status: (@Composable () -> Unit)?,
    headerAction: (@Composable () -> Unit)?,
    onShare: (Long) -> Unit,
    headerBanner: (@Composable () -> Unit)?,
    search: SearchSlot?,
    listTop: (@Composable () -> Unit)?,
) {
    val c = EodiTheme.colors
    val space = EodiTheme.space
    val reportWide by rememberUpdatedState(onWideChange)
    LaunchedEffect(Unit) { reportWide(false) }
    val listState = rememberLazyListState()
    Surface(modifier = Modifier.fillMaxSize(), color = c.background, contentColor = c.textPrimary) {
        val loaded = state.days as? DaysState.Loaded
        AnimatedContent(
            targetState = loaded != null && state.selectedDay != null,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (slideInHorizontally(tween(PageMs)) { dir * it / 8 } + fadeIn(tween(PageMs))) togetherWith
                    (slideOutHorizontally(tween(PageMs)) { -dir * it / 8 } + fadeOut(tween(PageMs / 2)))
            },
            label = "compact-list-detail",
            modifier = Modifier.fillMaxSize(),
        ) { showDetail ->
            if (showDetail && loaded != null) {
                BackHandler(onBack = onBack)
                CompactDayDetail(
                    detail = state.detail,
                    today = loaded.today,
                    selectedEntry = state.selectedEntry,
                    onEntryClick = onEntryClick,
                    onMapSelect = onMapSelect,
                    onShare = onShare,
                    onBack = onBack,
                )
            } else {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val searching = search?.active == true
                    // 검색 중에는 검색 칸이 맨 위, 결과가 화면 전체
                    if (!searching) TimelineHeader(wide = false, action = headerAction, height = 80.dp)
                    if (search != null) {
                        search.bar(
                            Modifier.fillMaxWidth().padding(start = space.page, end = space.page, top = if (searching) space.m else 0.dp, bottom = space.m),
                        )
                    }
                    if (headerBanner != null && !searching) {
                        Box(Modifier.padding(start = space.page, end = space.page, bottom = space.m)) { headerBanner() }
                    }
                    if (searching) {
                        search!!.results(Modifier.fillMaxSize().padding(horizontal = space.page))
                    } else when (val days = state.days) {
                        DaysState.Loading -> LoadingBox(message = stringResource(R.string.timeline_loading))
                        is DaysState.Failed -> ErrorState(days.message, onRetry, Modifier.fillMaxSize(), title = stringResource(R.string.timeline_failed_title), retryLabel = stringResource(R.string.timeline_retry))
                        is DaysState.Loaded ->
                            if (days.days.isEmpty()) {
                                EmptyPane(Modifier.fillMaxSize())
                            } else {
                                DayList(
                                    days = days.days,
                                    today = days.today,
                                    highlightedDay = null,
                                    showChevron = true,
                                    onSelect = onSelectDay,
                                    listState = listState,
                                    footer = status,
                                    top = listTop,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = space.page),
                                )
                            }
                    }
                }
            }
        }
    }
}

/** 폰 하루 상세: 뒤로 · 제목 한 줄 → 지도(높이 약 40%) → 통계 · 태그 · 올리기 + 시간순 항목 (한 목록). */
@Composable
private fun CompactDayDetail(
    detail: DetailState,
    today: LocalDate,
    selectedEntry: String?,
    onEntryClick: (String) -> Unit,
    onMapSelect: (String?) -> Unit,
    onShare: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val c = EodiTheme.colors
    val space = EodiTheme.space
    val summary = detail.shownSummary
    val loaded = detail as? DetailState.Loaded
    val shareEpisode = loaded?.shareEpisode
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 20.dp, end = space.page, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(EodiIcons.Back, stringResource(R.string.timeline_back_description), onBack, iconSize = 36.dp)
            Spacer(Modifier.width(20.dp))
            if (summary != null) {
                val title = summary.title?.takeIf { it.isNotBlank() }
                val relative = relativeDayName(summary.day, today)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (relative != null) {
                            Text(relative, style = EodiTheme.type.eyebrow, color = c.accent)
                            Spacer(Modifier.width(10.dp))
                        }
                        if (title != null) {
                            Text(dayTitle(summary.day, today), style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1)
                        }
                    }
                    Text(
                        title ?: dayTitle(summary.day, today),
                        style = EodiTheme.type.title,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                LocalTimelineEdits.current?.let { edits ->
                    Spacer(Modifier.width(12.dp))
                    MoreButton(stringResource(R.string.edit_more_day), { edits.onDay(summary) })
                }
            }
        }
        val photoPins = remember(loaded?.entries, loaded?.myPhotos) {
            loaded?.entries.orEmpty().mapNotNull { e ->
                val stay = (e as? DayEntry.Stay)?.stay ?: return@mapNotNull null
                // 내 사진이 있으면 그것이 대표 사진 (없으면 장소 대표 사진)
                val mine = loaded?.myPhotos?.get("s${stay.arriveMs}")?.photos?.firstOrNull()?.uri
                val p = stay.place?.takeIf { it.name.isNotBlank() }
                if (p == null && mine == null) return@mapNotNull null
                MapPhotoPin("s${stay.arriveMs}", p?.name.orEmpty(), p?.address.orEmpty(), stay.center.lat, stay.center.lon, mine)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val mapHeight = maxHeight * CompactMapFraction
            Column(Modifier.fillMaxSize()) {
                DayMap(
                    data = loaded?.map,
                    selectedKey = selectedEntry,
                    onSelectKey = onMapSelect,
                    modifier = Modifier.fillMaxWidth().height(mapHeight),
                    photoPins = photoPins,
                    myPhotos = loaded?.mapPhotos.orEmpty(),
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    when (detail) {
                        DetailState.None -> Unit
                        is DetailState.Loading -> LoadingBox(compact = true)
                        is DetailState.Failed -> CenterMessage(stringResource(R.string.timeline_failed, detail.message))
                        is DetailState.Loaded -> EntryList(
                            day = detail.summary.day,
                            entries = detail.entries,
                            offers = detail.offers,
                            myPhotos = detail.myPhotos,
                            selectedKey = selectedEntry,
                            onClick = onEntryClick,
                            headerCount = 1,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = space.page + bottom),
                            header = {
                                item(key = "__day-head", contentType = "head") {
                                    Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        StatRow(detail.summary)
                                        if (detail.summary.tags.isNotEmpty()) TagRow(detail.summary.tags)
                                        // 커뮤니티에 올리기 옆에 카드로 공유(카톡 등, ui/share). 자리는 날마다 같고, 올릴 기록이 없는 날은 흐리게 + 누르면 이유.
                                        var reason by remember(detail.summary.day) { mutableStateOf(false) }
                                        LaunchedEffect(reason) {
                                            if (reason) {
                                                delay(ReasonMs)
                                                reason = false
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            DimPill(
                                                stringResource(R.string.timeline_share), CommunityIcons.Upload, available = shareEpisode != null,
                                                onClick = { shareEpisode?.let(onShare) }, onUnavailable = { reason = true },
                                                modifier = Modifier.weight(1f),
                                            )
                                            ShareImageButton(ShareTarget.Day(detail.summary.day), style = ShareButtonStyle.ICON)
                                        }
                                        if (reason) {
                                            Text(stringResource(R.string.timeline_share_unavailable), style = EodiTheme.type.body, color = c.textSecondary)
                                        }
                                        if (detail.entries.isEmpty()) {
                                            Text(stringResource(R.string.timeline_detail_empty), style = EodiTheme.type.body, color = c.textSecondary)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 폰 하루 상세에서 지도가 차지하는 높이 (제목 줄 아래 남은 높이 기준). */
private const val CompactMapFraction = 0.44f

/**
 * 앱 이름 (앱 표시 그림은 왼쪽 레일 맨 위에 있다). 검색 칸([search])이 있으면 이름 옆을 검색 칸이 채우고,
 * 없으면 넓은 창에서 한 줄 소개를 옆에 붙인다.
 * 오른쪽 끝 [action] 자리는 화면의 대표 동작 하나(ui/agent의 "어디였지? 물어보세요" 버튼)가 들어가고, 글자가 잘리지 않게
 * 먼저 자리를 받는다. 소개 글은 남는 폭에서만 보인다. 개발용 코어 상태 줄은 머리가 아니라 날짜 목록 맨 아래에 둔다.
 */
@Composable
private fun TimelineHeader(wide: Boolean, action: (@Composable () -> Unit)?, height: Dp = 104.dp, search: SearchSlot? = null) {
    val c = EodiTheme.colors
    val space = EodiTheme.space
    Row(
        modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = space.page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = EodiTheme.type.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = c.textPrimary,
            maxLines = 1,
            modifier = Modifier.semantics { heading() },
        )
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (search != null) {
                // 검색 칸이 소개 글 자리를 쓴다 (앱 이름 옆 ~ 물어보기 버튼 앞)
                Spacer(Modifier.width(24.dp))
                search.bar(Modifier.weight(1f))
            } else if (wide) {
                Spacer(Modifier.width(20.dp))
                Box(Modifier.size(width = 2.dp, height = 28.dp).background(c.outline))
                Spacer(Modifier.width(20.dp))
                Text(
                    stringResource(R.string.tagline),
                    style = EodiTheme.type.body,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(space.m))
            action()
        }
    }
}

// ---------------------------------------------------------------- 날짜 목록

@Composable
private fun DayList(
    days: List<DaySummary>,
    today: LocalDate,
    highlightedDay: String?,
    showChevron: Boolean,
    onSelect: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    top: (@Composable () -> Unit)? = null,
) {
    // 1년 전 날을 열고 나면 목록이 그날까지 내려가 있다 → 멀리 내려가 있으면 "오늘로" 버튼으로 한 번에 맨 위(요약·오늘)로
    val farFromTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    val scope = rememberCoroutineScope()
    Box(modifier) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = EodiTheme.space.page),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (top != null) {
            item(key = "top", contentType = "top") { top() }
        }
        item(key = "title", contentType = "title") {
            // 머리말 + 점선 길 (앨범의 목차 줄)
            Row(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.timeline_list_title),
                    style = EodiTheme.type.eyebrow,
                    color = EodiTheme.colors.textTertiary,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.width(16.dp))
                DottedRoute(Modifier.weight(1f), pin = EodiTheme.colors.stay.copy(alpha = 0.8f))
            }
        }
        items(days, key = { it.day }, contentType = { "day" }) { s ->
            DayRow(s, today, selected = s.day == highlightedDay, showChevron = showChevron, onSelect = onSelect, modifier = Modifier.animateItem())
        }
        if (footer != null) {
            item(key = "footer", contentType = "footer") {
                // 맨 아래까지 내려도 떠 있는 "오늘로" 버튼에 가리지 않게 그 높이만큼 띄운다
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = BackToTodayClearance), contentAlignment = Alignment.Center) { footer() }
            }
        }
    }
    AnimatedVisibility(
        visible = farFromTop,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
    ) {
        Surface(
            onClick = { scope.launch { listState.scrollToItem(0) } },
            shape = CircleShape,
            color = EodiTheme.colors.ink,
            contentColor = EodiTheme.colors.onInk,
            shadowElevation = 6.dp,
            modifier = Modifier.heightIn(min = 64.dp),
        ) {
            Text(
                stringResource(R.string.timeline_back_to_today),
                style = EodiTheme.type.label,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            )
        }
    }
    }
}

/**
 * 날 한 줄: 왼쪽 날짜 타일(일 · 요일, 오늘은 파랑으로 채움), 가운데 글줄, 오른쪽 시간대.
 * - 하루 제목이 있으면: 작은 날짜 줄("오늘 · 11월 6일") → 큰 제목 → 머문 곳 이름 → 통계
 * - 없으면: 큰 날짜("오늘" / "11월 4일") → 머문 곳 이름 → 통계 (이름도 없으면 그 줄을 뺀다)
 * 고른 날(넓은 창에서 상세에 보이는 날)은 옅은 파랑 바탕 + 파랑 테두리.
 */
@Composable
private fun DayRow(
    s: DaySummary,
    today: LocalDate,
    selected: Boolean,
    showChevron: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val relative = relativeDayName(s.day, today)
    val isToday = s.day == today.toString()
    val title = s.title?.takeIf { it.isNotBlank() }
    val dateText = relative ?: dayListTitle(s.day, today)
    val stops = stopNamesText(s)
    val bg by animateColorAsState(if (selected) c.accentSoft else c.surface, tween(SelectMs), label = "day-bg")
    val edge by animateColorAsState(if (selected) c.accent else Color.Transparent, tween(SelectMs), label = "day-edge")
    Surface(
        onClick = { onSelect(s.day) },
        color = bg,
        contentColor = c.textPrimary,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        border = BorderStroke(2.dp, edge),
        modifier = modifier.fillMaxWidth().heightIn(min = 112.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            DateTile(s.day, isToday, selected)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 첫 줄: 제목이 있으면 작은 날짜, 없으면 큰 날짜. 오른쪽 끝에 시간대.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) {
                        Text(
                            relative?.let { stringResource(R.string.timeline_relative_date, it, dayListTitle(s.day, today)) }
                                ?: dateText,
                            style = type.label,
                            color = if (isToday) c.accent else c.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            dateText,
                            style = type.title,
                            color = if (isToday) c.accent else c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(timeRangeText(s.firstMs, s.lastMs), style = type.caption, color = c.textTertiary, maxLines = 1)
                }
                if (title != null) {
                    Text(title, style = type.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (stops != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(EodiIcons.Pin, contentDescription = null, tint = c.stay, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stops, style = type.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // 긴 날("주행 5회 · 들른 곳 4곳 · 384km · 4시간 18분")은 한 줄에 안 들어가 시간이 "4시간 1…"로 잘렸다 → 두 줄까지
                Text(
                    daySummaryLine(s),
                    style = type.meta,
                    color = c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                Icon(EodiIcons.Chevron, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** 날짜 타일: 큰 일(日) 숫자 + 요일. 오늘은 파랑으로 채운다. */
@Composable
internal fun DateTile(day: String, isToday: Boolean, selected: Boolean) {
    val c = EodiTheme.colors
    val bg = when {
        isToday -> c.accent
        selected -> c.surface
        else -> c.surfaceHigh
    }
    val fg = if (isToday) c.onAccent else c.textPrimary
    Column(
        Modifier.size(76.dp).background(bg, RoundedCornerShape(20.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(dayOfMonthText(day), style = EodiTheme.type.titleSmall, color = fg, maxLines = 1)
        // 달력처럼 토요일은 파랑, 일요일은 코랄
        val dow = runCatching { LocalDate.parse(day).dayOfWeek }.getOrNull()
        val weekdayColor = when {
            isToday -> fg.copy(alpha = 0.85f)
            dow == DayOfWeek.SATURDAY -> c.accent
            dow == DayOfWeek.SUNDAY -> c.stay
            else -> c.textTertiary
        }
        Text(weekdayShort(day), style = EodiTheme.type.caption, fontWeight = FontWeight.Bold, color = weekdayColor, maxLines = 1)
    }
}

// ---------------------------------------------------------------- 하루 상세

/**
 * 하루 상세 카드. 위에서부터: 날짜 제목(+ [onBack]이 있으면 뒤로 버튼) · 통계 4칸 · 지도(카드 폭 꽉 채움) · 시간순 항목.
 * 시스템 뒤로 가기는 TimelineScreen의 BackHandler가 맡는다.
 *
 * 지도는 불러오는 중에도 자리를 지켜서 날을 바꿀 때 MapView를 새로 만들지 않는다.
 * 항목을 누르면 지도에서 강조하고, 지도를 누르면 그 항목을 고른다.
 */
@Composable
private fun DayDetailCard(
    detail: DetailState,
    today: LocalDate,
    selectedEntry: String?,
    onEntryClick: (String) -> Unit,
    onMapSelect: (String?) -> Unit,
    onShare: (Long) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = EodiTheme.colors
    val space = EodiTheme.space
    val summary = detail.shownSummary
    val shareEpisode = (detail as? DetailState.Loaded)?.shareEpisode
    Surface(
        color = c.surface,
        contentColor = c.textPrimary,
        shape = RoundedCornerShape(space.radiusL),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize()) {
            DetailHeader(summary, today, onBack, loaded = detail is DetailState.Loaded, onShare = shareEpisode?.let { id -> { onShare(id) } })
            val loaded = detail as? DetailState.Loaded
            // 정차한 곳 사진 상자 (이름을 찾은 머문 곳만)
            val photoPins = remember(loaded?.entries, loaded?.myPhotos) {
                loaded?.entries.orEmpty().mapNotNull { e ->
                    val stay = (e as? DayEntry.Stay)?.stay ?: return@mapNotNull null
                    val mine = loaded?.myPhotos?.get("s${stay.arriveMs}")?.photos?.firstOrNull()?.uri
                    val p = stay.place?.takeIf { it.name.isNotBlank() }
                    if (p == null && mine == null) return@mapNotNull null
                    MapPhotoPin("s${stay.arriveMs}", p?.name.orEmpty(), p?.address.orEmpty(), stay.center.lat, stay.center.lon, mine)
                }
            }
            DayMap(
                data = loaded?.map,
                selectedKey = selectedEntry,
                onSelectKey = onMapSelect,
                modifier = Modifier.fillMaxWidth().weight(MapWeight),
                photoPins = photoPins,
                myPhotos = loaded?.mapPhotos.orEmpty(),
            )
            Box(Modifier.fillMaxWidth().weight(1f - MapWeight)) {
                when (detail) {
                    DetailState.None -> Unit
                    is DetailState.Loading -> LoadingBox(compact = true)
                    is DetailState.Failed -> CenterMessage(stringResource(R.string.timeline_failed, detail.message))
                    is DetailState.Loaded ->
                        if (detail.entries.isEmpty()) {
                            CenterMessage(stringResource(R.string.timeline_detail_empty))
                        } else {
                            EntryList(detail.summary.day, detail.entries, selectedEntry, onEntryClick, offers = detail.offers, myPhotos = detail.myPhotos)
                        }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(summary: DaySummary?, today: LocalDate, onBack: (() -> Unit)?, loaded: Boolean, onShare: (() -> Unit)? = null) {
    val c = EodiTheme.colors
    val space = EodiTheme.space
    Column(Modifier.fillMaxWidth().padding(start = space.xl, end = space.xl, top = 28.dp, bottom = space.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                RoundIconButton(EodiIcons.Back, stringResource(R.string.timeline_back_description), onBack, iconSize = 36.dp)
                Spacer(Modifier.width(20.dp))
            }
            if (summary != null) {
                // 제목이 있으면 큰 글씨는 제목, 날짜는 위 작은 줄로. 없으면 날짜가 큰 글씨.
                val title = summary.title?.takeIf { it.isNotBlank() }
                val relative = relativeDayName(summary.day, today)
                Column(Modifier.weight(1f)) {
                    if (title != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (relative != null) {
                                Text(relative, style = EodiTheme.type.eyebrow, color = c.accent)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(dayTitle(summary.day, today), style = EodiTheme.type.label, color = c.textTertiary, maxLines = 1)
                        }
                    } else if (relative != null) {
                        Text(relative, style = EodiTheme.type.label, color = c.accent)
                    }
                    // 제목은 버튼과 줄을 나눠 폭을 다 쓰고, 두 줄까지 보인다
                    Text(
                        title ?: dayTitle(summary.day, today),
                        style = EodiTheme.type.hero,
                        color = c.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
        }
        if (summary != null) {
            Spacer(Modifier.height(16.dp))
            DayActions(summary, loaded, onShare)
            if (summary.tags.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                TagRow(summary.tags)
            }
            Spacer(Modifier.height(20.dp))
            StatRow(summary)
        }
    }
}

/**
 * 하루 상세 머리 버튼: 자리는 날마다 같다 — 그날로 돌아가기 · 올리기 · 공유 · 더보기.
 * 못 쓰는 날은 숨기지 않고 흐리게 두고, 누르면 버튼 아래 한 줄로 이유를 잠깐 보여 준다 (이 화면에서는 토스트가 안 보인다).
 * [loaded]가 false(불러오는 중)면 올리기는 흐리게만 두고 이유는 보이지 않는다.
 */
@Composable
internal fun DayActions(summary: DaySummary, loaded: Boolean, onShare: (() -> Unit)?) {
    val c = EodiTheme.colors
    var reason by remember(summary.day) { mutableStateOf<Int?>(null) }
    LaunchedEffect(reason) {
        if (reason != null) {
            delay(ReasonMs)
            reason = null
        }
    }
    val openReplay = app.eodiyeotji.ui.replay.LocalOpenReplay.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // 그날로 돌아가기: 다시 볼 것이 있는 날만 (Rust `replayable`)
        DimPill(
            stringResource(R.string.replay_open), app.eodiyeotji.ui.theme.EodiIcons.Play, available = summary.replayable,
            onClick = { openReplay(summary.day) }, onUnavailable = { reason = R.string.timeline_replay_unavailable },
        )
        // 커뮤니티에 올리기: 나들이 · 여행 기록이 있는 날만 (Rust `share_candidates_on`)
        DimIcon(
            stringResource(R.string.timeline_share_short), CommunityIcons.Upload, available = onShare != null,
            onClick = { onShare?.invoke() }, onUnavailable = { if (loaded) reason = R.string.timeline_share_unavailable },
        )
        // 카드로 공유 (차: 연결된 휴대폰으로 → 카톡)
        ShareImageButton(ShareTarget.Day(summary.day), style = ShareButtonStyle.ICON)
        LocalTimelineEdits.current?.let { edits ->
            MoreButton(stringResource(R.string.edit_more_day), { edits.onDay(summary) })
        }
    }
    reason?.let {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(it), style = EodiTheme.type.body, color = c.textSecondary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** 못 쓰는 날은 흐리게(눌러도 동작 대신 [onUnavailable]로 이유를 보인다). 자리는 그대로 지킨다. */
@Composable
private fun DimPill(text: String, icon: ImageVector, available: Boolean, onClick: () -> Unit, onUnavailable: () -> Unit, modifier: Modifier = Modifier) {
    SecondaryPill(
        text, icon,
        onClick = if (available) onClick else onUnavailable,
        modifier = if (available) modifier else modifier.alpha(0.42f),
    )
}

/** [DimPill]의 글자 없는 둥근 버튼 (머리 버튼 줄에서 글자 있는 버튼은 그날로 돌아가기 하나만 둔다). */
@Composable
private fun DimIcon(description: String, icon: ImageVector, available: Boolean, onClick: () -> Unit, onUnavailable: () -> Unit) {
    val c = EodiTheme.colors
    Surface(
        onClick = if (available) onClick else onUnavailable,
        shape = CircleShape,
        color = c.surfaceHigh,
        contentColor = c.textSecondary,
        modifier = Modifier.size(76.dp).alpha(if (available) 1f else 0.42f).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(32.dp)) }
    }
}

/** 하루 태그는 앞에서 이만큼만 (9개가 한 줄에 몰리면 읽히지 않는다). */
private const val MaxTags = 3

/** 이유 한 줄이 떠 있는 시간 (ms). */
private const val ReasonMs = 3_500L

/** 하루 태그 알약 줄 ("#여행 #가족"). 앞에서 [MaxTags]개까지만 보이고, 그래도 넘치면 오른쪽이 잘린다. */
@Composable
private fun TagRow(tags: List<String>) {
    val c = EodiTheme.colors
    Row(Modifier.fillMaxWidth().clipToBounds(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (tag in tags.take(MaxTags)) {
            Text(
                stringResource(R.string.day_tag, tag),
                style = EodiTheme.type.caption,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.background(c.surfaceHigh, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * 하루 통계: 기본은 3칸(머문 곳 · 거리 · 운전 시간)만. 누르면 주행 횟수 · 탑승 인원 · 배터리 · 날씨까지 편다
 * (7칸이 한 줄에 다 보이면 읽히지 않는다). 값은 크게, 이름은 작게.
 */
@Composable
private fun StatRow(s: DaySummary) {
    val c = EodiTheme.colors
    var all by remember(s.day) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(EodiTheme.space.radiusM))
            .background(c.surfaceHigh)
            .clickable(onClickLabel = stringResource(if (all) R.string.stat_less else R.string.stat_more)) { all = !all }
            .padding(vertical = 16.dp),
    ) {
        if (all) {
            StatCell(stringResource(R.string.stat_trips_value, s.tripCount), stringResource(R.string.stat_trips_label), c.trip, weight = CountWeight)
            StatDivider()
        }
        StatCell(stringResource(R.string.stat_stays_value, s.stayCount), stringResource(R.string.stat_stays_label), c.stay, weight = CountWeight)
        StatDivider()
        StatCell(distanceText(s.distanceM), stringResource(R.string.stat_distance_label), null, weight = DistanceWeight)
        StatDivider()
        StatCell(durationText(s.driveMillis), stringResource(R.string.stat_drive_label), null, weight = DriveWeight)
        // 좌석 센서로 센 탑승 인원 (차만, 모르면 칸 없음)
        if (all && s.people > 0) {
            StatDivider()
            StatCell(stringResource(R.string.stat_people_value, s.people), stringResource(R.string.stat_people_label), null, weight = CountWeight)
        }
        // 배터리 · 연료 (차만): 쓴 양 %p. 이름 줄은 칸이 좁아 "쓴 배터리" 한 가지로 (충전 횟수를 붙이면 잘려 뜻이 바뀐다)
        s.energy?.takeIf { all }?.let { e ->
            StatDivider()
            val label = stringResource(if (e.ev) R.string.stat_energy_ev_label else R.string.stat_energy_fuel_label)
            StatCell(stringResource(R.string.stat_energy_value, kotlin.math.round(e.usedPct).toInt()), label, null)
        }
        // 비 온 날 (차 와이퍼 기록)
        if (all && s.rain) {
            StatDivider()
            StatCell(stringResource(R.string.stat_rain_value), stringResource(R.string.stat_rain_label), null)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCell(value: String, label: String, dot: Color?, weight: Float = 1f) {
    val c = EodiTheme.colors
    Column(
        Modifier.weight(weight).padding(horizontal = 4.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = EodiTheme.type.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(10.dp).background(dot, CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1)
        }
    }
}

/**
 * 통계 칸 폭 비율: 숫자 한두 글자인 칸(주행 · 머문 곳 · 인원)은 좁게, 운전 시간("1시간 21분")은 넓게 — 시간이 잘리지 않게.
 */
private const val CountWeight = 0.7f
private const val DistanceWeight = 1.1f
private const val DriveWeight = 1.7f

@Composable
private fun StatDivider() {
    Box(Modifier.width(2.dp).fillMaxHeight().padding(vertical = 8.dp).background(EodiTheme.colors.outline))
}

/**
 * 고른 항목이 화면 밖이면(지도에서 고른 경우) 보이게 굴린다. 이미 다 보이면 그대로 둔다.
 * [header]: 항목 위에 같이 굴러가는 머리 줄 ([headerCount]개, 폰 하루 상세의 통계·올리기).
 * 스크롤 위치는 날([day])마다 새로 잡는다 — 다른 날로 바꾸면 그날의 첫 출발부터 보인다.
 */
@Composable
internal fun EntryList(
    day: String,
    entries: List<DayEntry>,
    selectedKey: String?,
    onClick: (String) -> Unit,
    header: (LazyListScope.() -> Unit)? = null,
    headerCount: Int = 0,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = EodiTheme.space.page),
    offers: Map<String, List<PartnerOffer>> = emptyMap(),
    myPhotos: Map<String, StayMyPhotos> = emptyMap(),
) {
    val listState = remember(day) { LazyListState() }
    LaunchedEffect(selectedKey, entries) {
        val key = selectedKey ?: return@LaunchedEffect
        val info = listState.layoutInfo
        val shown = info.visibleItemsInfo.firstOrNull { it.key == key }
        val fullyShown = shown != null &&
            shown.offset >= info.viewportStartOffset &&
            shown.offset + shown.size <= info.viewportEndOffset - info.afterContentPadding
        if (!fullyShown) {
            val index = entries.indexOfFirst { it.key == key }
            if (index >= 0) listState.animateScrollToItem(index + headerCount)
        }
    }
    val last = entries.lastIndex
    val edits = LocalTimelineEdits.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
    ) {
        header?.invoke(this)
        itemsIndexed(
            entries,
            key = { _, e -> e.key },
            contentType = { _, e -> if (e is DayEntry.Trip) "trip" else "stay" },
        ) { i, e ->
            val key = e.key
            val selected = key == selectedKey
            val rail = Rail(first = i == 0, last = i == last)
            val onMore = edits?.let { ed -> { ed.onEntry(e) } }
            when (e) {
                is DayEntry.Trip -> TripRow(e.trip, e.repeat, rail, selected, onClick = { onClick(key) }, onMore = onMore)
                is DayEntry.Stay -> StayRow(e.stay, rail, selected, onClick = { onClick(key) }, onMore = onMore, offers = offers[key].orEmpty(), myPhotos = myPhotos[key])
            }
        }
    }
}

/** 세로 시간선에서 이 행의 위치 (첫 행은 위 선, 마지막 행은 아래 선을 그리지 않는다). */
private data class Rail(val first: Boolean, val last: Boolean)

/**
 * 주행: 제목은 "출발 → 도착" 이름, 둘째 줄은 거리 · 시간. 이름을 하나도 모르면 제목이 거리 · 시간이 된다.
 * [repeat] ≥ 2면 같은 길을 이어 달린 주행들을 Rust가 이 한 줄로 묶은 것 → 둘째 줄 끝에 "같은 길 3번".
 */
@Composable
private fun TripRow(trip: TripSummary, repeat: Int, rail: Rail, selected: Boolean, onClick: () -> Unit, onMore: (() -> Unit)? = null) {
    val c = EodiTheme.colors
    val places = tripPlacesText(trip)
    val repeatText = if (repeat > 1) " · " + stringResource(R.string.timeline_trip_repeat, repeat) else ""
    EntryRow(
        kind = EntryKind.Trip,
        rail = rail,
        time = clockText(trip.startMs),
        timeSub = stringResource(R.string.entry_until, clockText(trip.endMs)),
        timeSubColor = c.textTertiary,
        main = places ?: stringResource(
            R.string.timeline_trip_line,
            distanceText(trip.distanceM),
            durationText(trip.endMs - trip.startMs),
        ) + repeatText,
        sub = if (places != null) tripStatsText(trip) + repeatText else stringResource(R.string.place_pending),
        subPending = places == null,
        navi = trip.hasNaviPlace,
        here = false,
        selected = selected,
        onClick = onClick,
        onMore = onMore,
    )
}

/** 머문 곳: 제목은 장소 이름, 둘째 줄은 분류 · 머문 시간. 아직 머무는 곳은 초록 "지금 여기" 표시. */
@Composable
private fun StayRow(
    stay: StaySummary,
    rail: Rail,
    selected: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    offers: List<PartnerOffer> = emptyList(),
    myPhotos: StayMyPhotos? = null,
) {
    val c = EodiTheme.colors
    val place = stay.place?.takeIf { it.name.isNotBlank() }
    var viewing by remember(stay.arriveMs) { mutableStateOf<Int?>(null) }
    // 그날 맨 처음 · 맨 끝에 머문 곳(대개 집)은 밤새 · 며칠 머문 시간까지 세져 "아파트 · 63시간 46분 / –08:11(사흘 뒤)"처럼 보인다
    // → 지도 말풍선(`mapdata.rs stay_feature`)처럼 머문 시간을 빼고, 마지막 곳은 떠난 시각도 뺀다.
    val overnight = !stay.isOpen && (rail.first || rail.last)
    val main: String
    val sub: String?
    when {
        place != null -> {
            main = place.name
            sub = stayDetailText(stay, place, showDuration = !overnight)
        }
        stay.isOpen -> {
            main = stringResource(R.string.timeline_stay_open)
            sub = stringResource(R.string.place_pending)
        }
        else -> {
            main = stringResource(R.string.timeline_stay_line, durationText(stay.leaveMs - stay.arriveMs))
            sub = stringResource(R.string.place_pending)
        }
    }
    EntryRow(
        kind = if (stay.isOpen) EntryKind.Here else EntryKind.Stay,
        rail = rail,
        time = clockText(stay.arriveMs),
        timeSub = when {
            stay.isOpen -> stringResource(R.string.entry_now)
            overnight && rail.last -> ""
            else -> stringResource(R.string.entry_until, clockText(stay.leaveMs))
        },
        timeSubColor = if (stay.isOpen) c.here else c.textTertiary,
        main = main,
        sub = sub,
        subPending = place == null,
        navi = place?.source == PlaceSource.NAVI,
        here = stay.isOpen,
        selected = selected,
        onClick = onClick,
        onMore = onMore,
        badge = if (offers.isNotEmpty()) ({ PlaceCouponBadge(offers) }) else null,
        // 그곳에서 찍은 내 사진 작은 카드 (누르면 크게 보기)
        below = myPhotos?.let { mp ->
            { MyPhotoStrip(mp.photos.map { it.uri }, mp.total, onOpen = { viewing = it }, size = MyPhotoThumb) }
        },
        trailing = place?.let { p ->
            {
                // 정차한 곳 대표 사진 (구글 지도 사진): 앨범에 붙인 사진처럼 흰 테두리 + 살짝 기울임(행마다 번갈아).
                // 누르면 장소 상세 창 (큰 사진 · 별점 · 리뷰 · 안내)
                val openPlace = LocalOpenPlace.current
                AlbumPhoto(
                    Modifier.size(StayThumb).clickable(onClickLabel = placeDetailOpenLabel()) {
                        openPlace(StopFact(p.name, p.category, p.address, stay.arriveMs, stay.leaveMs, stay.center.lat, stay.center.lon, false))
                    },
                    tilt = if ((stay.arriveMs / 60_000) % 2 == 0L) -2.5f else 2.5f,
                ) {
                    StopPhotoBox(p.name, p.address, stay.center.lat, stay.center.lon, null, Modifier.fillMaxSize(), radius = 0.dp, iconSize = 32.dp)
                }
            }
        },
    )
    val open = viewing
    if (open != null && myPhotos != null) {
        MyPhotoViewer(myPhotos.photos.map { it.uri }, open, place?.name, onDismiss = { viewing = null })
    }
}

private val StayThumb = 96.dp
private val MyPhotoThumb = 96.dp

/** 떠 있는 "오늘로" 버튼(64dp + 아래 20dp)을 비켜 목록 끝을 올리는 높이 */
private val BackToTodayClearance = 96.dp

private enum class EntryKind { Trip, Stay, Here }

private val TimeColumn = 92.dp
private val RailColumn = 64.dp
/** 첫 줄 높이: 시각 · 노드 · 제목이 이 줄 가운데에 나란히 선다. */
private val FirstLine = 44.dp
private val RowPadV = 16.dp

/**
 * 타임라인 한 줄: 시각 열 | 세로 선 + 노드 | 제목 · 둘째 줄.
 * 선은 주행 구간에서 진한 파랑 실선, 머문 구간에서 회색 점선 → 언제 달렸고 언제 서 있었는지 한눈에 보인다.
 * 고른 항목은 옅은 파랑 바탕 + 파랑 테두리, 노드에 후광. 행 전체가 76dp 이상 터치 영역이다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    kind: EntryKind,
    rail: Rail,
    time: String,
    timeSub: String,
    timeSubColor: Color,
    main: String,
    sub: String?,
    subPending: Boolean,
    navi: Boolean,
    here: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val bg by animateColorAsState(if (selected) c.accentSoft else Color.Transparent, tween(SelectMs), label = "entry-bg")
    val edge by animateColorAsState(if (selected) c.accent else Color.Transparent, tween(SelectMs), label = "entry-edge")
    val shape = RoundedCornerShape(EodiTheme.space.radiusM)
    // 누르기 = 고르기, 길게 누르기 = ⋯ 메뉴 (기록 고치기)
    Surface(
        color = bg,
        contentColor = c.textPrimary,
        shape = shape,
        modifier = Modifier.fillMaxWidth().border(2.dp, edge, shape).clip(shape).combinedClickable(onLongClick = onMore, onClick = onClick),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = EodiTheme.space.touch)) {
            // 시각 열
            Column(
                Modifier.width(TimeColumn).padding(top = RowPadV, bottom = RowPadV, start = 12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Box(Modifier.height(FirstLine), contentAlignment = Alignment.CenterStart) {
                    Text(time, style = type.time, color = c.textPrimary, maxLines = 1)
                }
                Text(timeSub, style = type.caption, color = timeSubColor, maxLines = 1)
            }
            // 선 + 노드
            RailCell(kind, rail, selected, Modifier.width(RailColumn).fillMaxHeight())
            // 내용
            Column(
                Modifier.weight(1f).padding(top = RowPadV, bottom = RowPadV, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.heightIn(min = FirstLine), contentAlignment = Alignment.CenterStart) {
                    Text(
                        main,
                        style = if (kind == EntryKind.Trip) type.bodyStrong else type.title,
                        color = c.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (here) {
                        Tag(stringResource(R.string.entry_here), c.here, c.hereSoft, null)
                        Spacer(Modifier.width(10.dp))
                    }
                    if (sub != null) {
                        Text(
                            sub,
                            style = type.meta,
                            color = if (subPending) c.textTertiary else c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (navi) {
                        Spacer(Modifier.width(10.dp))
                        NaviTag()
                    }
                }
                badge?.invoke()
                below?.invoke()
            }
            if (trailing != null) {
                Box(Modifier.padding(top = 12.dp, bottom = 12.dp, end = 16.dp)) { trailing() }
            }
            if (onMore != null) {
                Box(Modifier.fillMaxHeight().padding(end = 8.dp), contentAlignment = Alignment.Center) {
                    // 누르는 칸은 76dp (점 아이콘은 그대로 작게) — Pleos 터치 크기
                    MoreButton(stringResource(R.string.edit_more_entry), onMore, size = 76.dp, filled = false)
                }
            }
        }
    }
}

/** 세로 선과 노드를 한 번에 그린다 (Canvas 하나 → 행마다 레이아웃 노드를 늘리지 않는다). */
@Composable
private fun RailCell(kind: EntryKind, rail: Rail, selected: Boolean, modifier: Modifier) {
    val c = EodiTheme.colors
    val nodeColor = when (kind) {
        EntryKind.Trip -> c.trip
        EntryKind.Stay -> c.stay
        EntryKind.Here -> c.here
    }
    val halo = when (kind) {
        EntryKind.Trip -> c.tripSoft
        EntryKind.Stay -> c.staySoft
        EntryKind.Here -> c.hereSoft
    }
    val lineColor = if (kind == EntryKind.Trip) c.trip else c.outline
    val ringFill = c.surface
    val selectedHalo = if (selected) nodeColor.copy(alpha = 0.22f) else Color.Transparent
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = (RowPadV + FirstLine / 2).toPx()
            val lw = 5.dp.toPx()
            val dashed = if (kind == EntryKind.Trip) null else PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx()))
            val cap = if (kind == EntryKind.Trip) StrokeCap.Butt else StrokeCap.Round
            if (!rail.first) drawLine(lineColor, Offset(cx, 0f), Offset(cx, cy), lw, cap, dashed)
            if (!rail.last) drawLine(lineColor, Offset(cx, cy), Offset(cx, size.height), lw, cap, dashed)
            val big = kind != EntryKind.Trip
            if (selected) drawCircle(selectedHalo, (if (big) 30.dp else 22.dp).toPx(), Offset(cx, cy))
            if (kind == EntryKind.Here) drawCircle(halo, 27.dp.toPx(), Offset(cx, cy))
            if (big) {
                drawCircle(ringFill, 21.dp.toPx(), Offset(cx, cy))
                drawCircle(nodeColor, 18.dp.toPx(), Offset(cx, cy))
            } else {
                drawCircle(ringFill, 12.dp.toPx(), Offset(cx, cy))
                drawCircle(nodeColor, 10.dp.toPx(), Offset(cx, cy))
                drawCircle(ringFill, 5.dp.toPx(), Offset(cx, cy))
            }
        }
        if (kind != EntryKind.Trip) {
            // 머문 곳 노드 안 흰 핀 (첫 줄 가운데)
            Icon(
                EodiIcons.Pin,
                contentDescription = null,
                tint = c.onMarker,
                modifier = Modifier.padding(top = RowPadV + FirstLine / 2 - 11.dp).size(22.dp),
            )
        }
    }
}

/** 작은 알약 표시 (지금 여기 · 내비). 눌리지 않는 장식이라 터치 크기 기준과 무관하다. */
@Composable
private fun Tag(text: String, fg: Color, bg: Color, icon: ImageVector?, modifier: Modifier = Modifier) {
    Row(
        modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = EodiTheme.type.caption, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/** 이름 출처가 차량 내비일 때 붙는 작은 표시. */
@Composable
private fun NaviTag() {
    val c = EodiTheme.colors
    val description = stringResource(R.string.place_source_navi_description)
    Tag(
        stringResource(R.string.place_source_navi),
        fg = c.accent,
        bg = c.accentSoft,
        icon = EodiIcons.Navi,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    )
}

// ---------------------------------------------------------------- 상태 화면

/**
 * 기록이 없을 때. 기록 시작 버튼은 일부러 없다 → 시동을 걸면 저절로 기록된다고만 알려 준다.
 * 아래 작은 "시연 기록 넣기"는 처음 설치한 사람(심사위원)이 1년치 예시 기록으로 바로 둘러보게 한다.
 */
@Composable
private fun EmptyPane(modifier: Modifier = Modifier, demo: DemoSeedViewModel = hiltViewModel()) {
    val busy by demo.busy.collectAsStateWithLifecycle()
    StatePane(modifier) {
        EmptyRoadArt()
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(R.string.timeline_empty_title),
            style = EodiTheme.type.hero,
            color = EodiTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.timeline_empty_body),
            style = EodiTheme.type.body,
            color = EodiTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Spacer(Modifier.height(40.dp))
        SecondaryPill(
            stringResource(if (busy) R.string.timeline_demo_seeding else R.string.timeline_demo_seed),
            EodiIcons.Sparkle,
            onClick = demo::seed,
            enabled = !busy,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.timeline_demo_hint), style = EodiTheme.type.meta, color = EodiTheme.colors.textTertiary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatePane(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.padding(EodiTheme.space.page),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(EodiTheme.space.page), contentAlignment = Alignment.Center) {
        Text(text, style = EodiTheme.type.body, color = EodiTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}
