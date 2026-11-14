package app.eodiyeotji.phone.ui

import app.eodiyeotji.ui.common.eodiShadow
import app.eodiyeotji.ui.nav.NavScreen
import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.community.RoundIconButton
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.record.RecordingStatusChip
import app.eodiyeotji.ui.agent.AgentIcons
import app.eodiyeotji.ui.agent.AgentPanel
import app.eodiyeotji.ui.agent.AgentViewModel
import app.eodiyeotji.ui.community.BoardViewModel
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.CommunityRoute
import app.eodiyeotji.ui.community.CommunityTab
import app.eodiyeotji.core.EpisodeKind
import app.eodiyeotji.core.SearchScope
import app.eodiyeotji.ui.review.ReviewViewModel
import app.eodiyeotji.ui.search.SearchActions
import app.eodiyeotji.ui.search.SearchViewModel
import app.eodiyeotji.ui.search.rememberSearchSlot
import app.eodiyeotji.ui.search.searchHint
import app.eodiyeotji.ui.search.searchScope
import app.eodiyeotji.ui.community.CourseViewModel
import app.eodiyeotji.ui.community.LocalOverlayPresenter
import app.eodiyeotji.ui.home.CourseGuideBanner
import app.eodiyeotji.ui.home.CourseGuideViewModel
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.timeline.TimelineRoute
import app.eodiyeotji.ui.places.PlacesRoute
import app.eodiyeotji.ui.place.PlaceDetailHost
import app.eodiyeotji.ui.agent.SpokenCaption
import app.eodiyeotji.ui.agent.ProactiveCard
import app.eodiyeotji.ui.timeline.TimelineViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** 아래 탭. 가운데 [ASK](기억 에이전트)는 한 번에 닿게 크게 둔다. */
enum class PhoneTab { RECORDS, ASK, COMMUNITY }

/**
 * 폰 판 화면 뼈대 (src/phone/AppRoot.kt가 부른다). 전체를 [PhoneScale] 비율로 그린다.
 * 1) 설정 파일을 읽을 때까지는 바탕만 → 2) 첫 실행이면 권한 안내([PhoneOnboarding]) → 3) 아래 탭 화면([PhoneHome]).
 */
@Composable
fun PhoneApp(shell: PhoneShellViewModel = hiltViewModel()) {
    PhoneScale {
        val prefs by shell.prefs.collectAsStateWithLifecycle()
        val navigating by shell.navigating.collectAsStateWithLifecycle()
        // 시트(OverlayCard 자리)는 폰에서 아래에서 올라온다
        val sheets = remember { PhoneSheetHostState() }
        CompositionLocalProvider(
            LocalOverlayPresenter provides { onDismiss, content -> PhoneSheet(onDismiss, content) },
            LocalPhoneSheetHost provides sheets,
        ) {
            Box(Modifier.fillMaxSize().background(EodiTheme.colors.background)) {
                if (prefs.onboarded) PhoneHandoffHost()
                when {
                    !prefs.loaded -> Unit
                    !prefs.onboarded -> PhoneOnboarding(
                        backgroundAsked = prefs.backgroundAsked,
                        onLocationGranted = shell::startRecording,
                        onBackgroundAsked = shell::markBackgroundAsked,
                        onDone = shell::finishOnboarding,
                    )
                    else -> {
                        LaunchedEffect(Unit) { shell.startRecording() }
                        PhoneHome(shell)
                    }
                }
                // 장소 상세 창(별점 · 리뷰)은 어느 탭에서든 아래 시트로 뜬다
                PlaceDetailHost()
                // 앱이 소리 내어 읽는 말 (답 · 회상)을 위쪽에 자막으로
                // 길 안내 중에는 위쪽 다음 안내 배너를 가리지 않게 아래 막대 바로 위에
                SpokenCaption(bottomInset = if (navigating) 120.dp else null)
                // 에이전트가 먼저 거는 말 한 장 (ui/agent/ProactiveCard.kt)
                ProactiveCard()
                // 시트는 맨 위에 (아래 탭 · 운전 중 띠까지 덮는다)
                PhoneSheetHost(sheets)
                // 공유 결과 같은 앱 안 알림은 시트 위, 아래 탭을 피해서 (ui/share/ShareNotice.kt)
                app.eodiyeotji.ui.share.ShareNoticeHost(bottom = 160.dp)
            }
        }
    }
}

/**
 * 아래 탭 화면: [내 기록 | (물어보기) | 커뮤니티].
 * - 상세(하루 상세 · 코스 상세 · 게시글)를 열거나 키보드가 올라오면 아래 탭을 감춰 화면을 다 쓴다.
 * - 뒤로 가기: 상세 → 목록(각 화면이 맡는다), 물어보기 → 그 전 탭, 커뮤니티 → 내 기록, 내 기록 → 앱 닫기.
 * - 운전 중(위치로 판단)이면 맨 위에 "운전 중 모드" 띠: 마이크 한 번으로 말로 묻기. 화면은 막지 않는다 (동승자일 수 있다).
 * - 코스 안내 중이면 아래 탭 위에 진행 카드 (지도 앱은 도착을 알려 주지 않아 "다음 구간"을 손으로 누른다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneHome(shell: PhoneShellViewModel) {
    var tab by rememberSaveable { mutableStateOf(PhoneTab.RECORDS) }
    var previousTab by rememberSaveable { mutableStateOf(PhoneTab.RECORDS) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // 내 장소 (저장한 곳 · 자주 가는 곳 · 집 · 회사 · 숨긴 기록): 내 기록 머리의 ♥ 버튼으로 연다
    var placesOpen by rememberSaveable { mutableStateOf(false) }
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    val agent: AgentViewModel = hiltViewModel()
    val guide: CourseGuideViewModel = hiltViewModel()
    val moving by shell.moving.collectAsStateWithLifecycle()
    val passenger by shell.passenger.collectAsStateWithLifecycle()
    val permissions by shell.permissions.collectAsStateWithLifecycle()
    val parked by shell.parked.collectAsStateWithLifecycle()
    val parkedMemo by shell.parkedMemo.collectAsStateWithLifecycle()
    var walk by remember { mutableStateOf<WalkTarget?>(null) }
    val guideProgress by guide.progress.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        shell.refreshPermissions()
        onPauseOrDispose { }
    }

    fun select(t: PhoneTab) {
        if (t == tab) return
        if (t == PhoneTab.ASK) previousTab = tab
        tab = t
    }

    // 운전 중 띠의 마이크: 물어보기 탭으로 가서 바로 듣기 (마이크 권한이 없으면 먼저 묻는다)
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) agent.voice() else agent.micPermissionDenied()
    }
    val listenNow = {
        select(PhoneTab.ASK)
        if (agent.needsMicPermission()) micPermission.launch(Manifest.permission.RECORD_AUDIO) else agent.voice()
    }

    // 검색: 내 기록 · 커뮤니티 맨 위 검색 칸 (손으로 · 말로). 결과를 열면 그 화면으로 간다.
    var communityTab by rememberSaveable { mutableStateOf(CommunityTab.COURSES) }
    val timeline: TimelineViewModel = hiltViewModel()
    val courseVm: CourseViewModel = hiltViewModel()
    val boardVm: BoardViewModel = hiltViewModel()
    val reviewVm: ReviewViewModel = hiltViewModel()
    val recordsSearchVm: SearchViewModel = hiltViewModel(key = "search-records")
    val communitySearchVm: SearchViewModel = hiltViewModel(key = "search-community")
    // 한 번만 만든다: 매번 새로 만들면 검색 칸 둘이 홈이 다시 그려질 때마다 같이 다시 그려진다 (탭은 상태로 읽으므로 기억해도 된다)
    val actions = remember(timeline, courseVm, reviewVm, boardVm, agent) { SearchActions(
        openDay = { day, entryKey ->
            select(PhoneTab.RECORDS)
            timeline.openDay(day, entryKey)
        },
        openCourse = { c ->
            // 여러 곳을 차례로 다녀온 코스 글은 여행 리뷰 피드에 있다 (상세를 바로 연다)
            select(PhoneTab.COMMUNITY)
            communityTab = CommunityTab.REVIEWS
            courseVm.open(c)
        },
        openReview = { p ->
            select(PhoneTab.COMMUNITY)
            communityTab = CommunityTab.REVIEWS
            reviewVm.openPlace(p)
        },
        openPost = { p ->
            select(PhoneTab.COMMUNITY)
            communityTab = CommunityTab.BOARD
            boardVm.open(p)
        },
        askAi = { text ->
            select(PhoneTab.ASK)
            agent.ask(text)
        },
    ) }
    // 운전 중(동승자 아님)이면 결과 맨 위에 말로 찾기를 권한다 (막지는 않는다)
    val driverMoving = moving && !passenger
    val recordsSearch = rememberSearchSlot(
        viewModel = recordsSearchVm,
        scope = SearchScope.RECORDS,
        agent = agent,
        actions = actions,
        hint = stringResource(R.string.search_hint_records),
        drivingHint = driverMoving,
    )
    val communitySearch = rememberSearchSlot(
        viewModel = communitySearchVm,
        scope = communityTab.searchScope(),
        agent = agent,
        actions = actions,
        hint = stringResource(communityTab.searchHint()),
        drivingHint = driverMoving,
    )

    // 상세 화면이나 검색 결과가 열려 있는지 (아래 탭을 감춰 화면을 다 쓴다). 같은 ViewModel 인스턴스를 각 화면과 같이 본다.
    // 열렸는지 여부만 골라 읽는다: 목록이 바뀌거나 불러오는 중일 때마다 홈 전체를 다시 그리지 않게.
    val detailOpen = when (tab) {
        PhoneTab.RECORDS -> {
            val dayOpen by remember(timeline) { timeline.state.map { it.selectedDay != null }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(timeline.state.value.selectedDay != null)
            dayOpen || recordsSearch.active || placesOpen
        }
        PhoneTab.COMMUNITY -> {
            val courseOpen by remember(courseVm) { courseVm.state.map { it.detail != null }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(courseVm.state.value.detail != null)
            val threadOpen by remember(boardVm) { boardVm.state.map { it.thread != null }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(boardVm.state.value.thread != null)
            courseOpen || threadOpen || communitySearch.active
        }
        PhoneTab.ASK -> false
    }
    val imeOpen = WindowInsets.isImeVisible
    val showBar = !detailOpen && !imeOpen
    val guideShown = guideProgress != null && !imeOpen
    val drivingBanner = moving

    // 뒤로 가기 (각 상세 화면의 BackHandler가 나중에 등록돼 먼저 받는다)
    BackHandler(enabled = tab == PhoneTab.ASK) { tab = previousTab.takeIf { it != PhoneTab.ASK } ?: PhoneTab.RECORDS }
    BackHandler(enabled = tab == PhoneTab.COMMUNITY) { tab = PhoneTab.RECORDS }

    Column(Modifier.fillMaxSize().background(EodiTheme.colors.background)) {
        AnimatedVisibility(visible = drivingBanner, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            DrivingBanner(
                passenger = passenger,
                onListen = listenNow,
                onPassenger = { shell.setPassenger(true) },
                onDriver = { shell.setPassenger(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // 띠·아래 탭이 이미 맡은 시스템 막대 자리는 화면 안에서 다시 비우지 않는다
                .then(if (drivingBanner) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else Modifier)
                .then(if (showBar || guideShown) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            Crossfade(targetState = tab, label = "phone-tab") { t ->
                when (t) {
                    PhoneTab.RECORDS -> if (placesOpen) {
                        PlacesRoute(
                            compact = true,
                            onBack = { placesOpen = false },
                            onOpenRecords = { name ->
                                placesOpen = false
                                recordsSearchVm.submit(name)
                            },
                        )
                    } else TimelineRoute(
                        compact = true,
                        search = recordsSearch,
                        onOpenTrips = { recordsSearchVm.openKind(EpisodeKind.TRIP) },
                        headerAction = {
                            // 기록 상태(기록 중 · 권한/알림/배터리 경고, 누르면 설명 시트. 대기 중에는 숨긴다) + 내 장소 + 설정
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 경고(권한 · 배터리 최적화)는 머리에서 잘려 "배터리 최적화…"로 보였다 → 머리에는 기록 중 표시만, 경고는 목록 위 한 줄 띠
                                RecordingStatusChip(Modifier.weight(1f, fill = false), quietWhenWaiting = true, hideWarning = true)
                                Spacer(Modifier.width(12.dp))
                                // 날짜로 찾기: 몇 달 전 날도 달력에서 바로 (기록 있는 날만 누를 수 있다)
                                RoundIconButton(app.eodiyeotji.ui.theme.EodiIcons.Calendar, stringResource(R.string.phone_calendar_open), { calendarOpen = true }, containerColor = EodiTheme.colors.surface, contentColor = EodiTheme.colors.textSecondary, iconSize = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                RoundIconButton(CommunityIcons.Heart, stringResource(R.string.nav_places), { placesOpen = true }, containerColor = EodiTheme.colors.surface, contentColor = EodiTheme.colors.textSecondary, iconSize = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                RoundIconButton(PhoneIcons.Tune, stringResource(R.string.phone_settings), { settingsOpen = true }, containerColor = EodiTheme.colors.surface, contentColor = EodiTheme.colors.textSecondary, iconSize = 36.dp)
                            }
                        },
                        headerBanner = when {
                            !permissions.location -> {
                                { PermissionWarning(onFix = shell::openAppSettings) }
                            }
                            // 차를 세워 둔 동안: 내 차 카드 (가게까지 걷기 · 내 차 찾기). 기록 경고 띠가 있으면 그 아래
                            parked != null && !moving -> {
                                {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RecordingStatusChip(banner = true)
                                        parked?.let { ParkedCarCard(it, parkedMemo, onMemo = shell::setParkedMemo, onWalk = { t -> walk = t }, onDismiss = shell::dismissParked) }
                                    }
                                }
                            }
                            else -> {
                                { RecordingStatusChip(banner = true) }
                            }
                        },
                    )
                    PhoneTab.ASK -> AgentPanel(onClose = null, viewModel = agent, compact = true)
                    PhoneTab.COMMUNITY -> CommunityRoute(
                        headerAction = null,
                        compact = true,
                        search = communitySearch,
                        selectedTab = communityTab,
                        onTabChange = { communityTab = it },
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().then(if (!showBar && guideShown) Modifier.navigationBarsPadding() else Modifier)) {
            if (!imeOpen) {
                CourseGuideBanner(
                    // 아래 탭 가운데 동그라미가 위로 떠 있어서 그만큼 띄운다
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = if (showBar) 40.dp else 12.dp),
                    viewModel = guide,
                    compact = true,
                )
            }
            AnimatedVisibility(visible = showBar, enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PhoneBottomBar(tab, driving = moving && !passenger, onSelect = ::select)
            }
        }
    }

    if (calendarOpen) {
        val ts by timeline.state.collectAsStateWithLifecycle()
        val loaded = ts.days as? app.eodiyeotji.ui.timeline.DaysState.Loaded
        if (loaded != null) PhoneDateCalendarSheet(
            days = loaded.days,
            today = loaded.today,
            selected = ts.selectedDay,
            onPick = { day -> timeline.openDay(day, null) },
            onDismiss = { calendarOpen = false },
        )
    }
    if (settingsOpen) {
        PhoneSettingsSheet(
            permissions = permissions,
            onOpenSystemSettings = shell::openAppSettings,
            onClose = { settingsOpen = false },
        )
    }
    // 앱 안 길 안내: 모든 화면 위에 꽉 차게
    NavOverlay(shell)
    // 걸어서 찾아가기: 가게까지 · 내 차로
    walk?.let { PhoneWalkScreen(it, onClose = { walk = null }, onFoundCar = shell::dismissParked) }
}

/** 앱 안 길 안내 화면. 안내 상태는 위치가 올 때마다(1초) 바뀌므로 여기서만 읽어, 그 아래 깔린 홈 화면까지 다시 그리지 않게 한다. */
@Composable
private fun NavOverlay(shell: PhoneShellViewModel) {
    val nav by shell.nav.collectAsStateWithLifecycle()
    nav?.let { NavScreen(it, onEnd = shell::endNav, onNext = shell::nextStop) }
}

/**
 * 아래 탭 막대: 왼쪽 내 기록 · 가운데 큰 파란 동그라미(물어보기, 금색 반짝임) · 오른쪽 커뮤니티.
 * 운전 중이면 가운데 이름이 "말로 묻기"로 바뀐다.
 */
@Composable
private fun PhoneBottomBar(selected: PhoneTab, driving: Boolean, onSelect: (PhoneTab) -> Unit) {
    val c = EodiTheme.colors
    // Surface는 안을 잘라 내서 가운데 동그라미가 막대 위로 뜰 수 없다 → 바탕·윗선만 직접 그린다
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.outline))
        Row(
            Modifier.fillMaxWidth().height(128.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem(CommunityIcons.Clock, stringResource(R.string.nav_timeline), selected == PhoneTab.RECORDS, Modifier.weight(1f)) {
                onSelect(PhoneTab.RECORDS)
            }
            AskItem(
                label = stringResource(if (driving) R.string.phone_tab_ask_driving else R.string.phone_tab_ask),
                selected = selected == PhoneTab.ASK,
                driving = driving,
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
            ) { onSelect(PhoneTab.ASK) }
            BarItem(CommunityIcons.People, stringResource(R.string.nav_community), selected == PhoneTab.COMMUNITY, Modifier.weight(1f)) {
                onSelect(PhoneTab.COMMUNITY)
            }
        }
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Column(
        modifier
            .heightIn(min = EodiTheme.space.touch)
            .clip(RoundedCornerShape(EodiTheme.space.radiusM))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 96.dp, height = 52.dp).background(if (selected) c.accentSoft else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) c.accent else c.textSecondary, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = EodiTheme.type.caption,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) c.accent else c.textSecondary,
            maxLines = 1,
        )
    }
}

/** 가운데 물어보기: 막대 안의 진한 알약 (아이콘 + 이름, 운전 중에는 마이크). 양옆 탭과 같은 줄에 납작하게 둔다. */
@Composable
private fun AskItem(label: String, selected: Boolean, driving: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = if (selected) c.accent else c.ink,
            contentColor = if (selected) c.onAccent else c.onInk,
            modifier = Modifier.heightIn(min = EodiTheme.space.touch).clip(CircleShape).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        ) {
            Row(Modifier.padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                // 운전 중: 말로 묻기가 먼저 → 마이크
                Icon(if (driving) AgentIcons.Mic else EodiIcons.Sparkle, contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(label, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/**
 * 운전 중 띠 (화면을 막지 않는다).
 * - 운전 중: 파란 띠 = 큰 마이크 + "화면 대신 말로 물어보세요" + "동승자예요".
 * - 동승자 모드: 옅은 띠 = "차가 달리는 중이에요" + "운전 중 모드로".
 */
@Composable
private fun DrivingBanner(passenger: Boolean, onListen: () -> Unit, onPassenger: () -> Unit, onDriver: () -> Unit, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    if (!passenger) {
        PressSurface(onClick = onListen, color = c.accent, contentColor = c.onAccent, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = modifier.eodiShadow(EodiTheme.elevation.level1, RoundedCornerShape(EodiTheme.space.radiusL))) {
            Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(96.dp).background(c.onAccent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AgentIcons.Mic, contentDescription = stringResource(R.string.agent_mic), tint = c.accent, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.phone_driving_title), style = type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(stringResource(R.string.phone_driving_body), style = type.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                BannerPill(stringResource(R.string.phone_driving_passenger), onPassenger, onInk = true)
            }
        }
    } else {
        Surface(color = c.surfaceHigh, contentColor = c.textPrimary, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = modifier) {
            Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.phone_passenger_title), style = type.label, fontWeight = FontWeight.Bold, color = c.accent)
                    Text(stringResource(R.string.phone_passenger_body), style = type.caption, color = c.textSecondary, maxLines = 2)
                }
                Spacer(Modifier.width(12.dp))
                BannerPill(stringResource(R.string.phone_passenger_off), onDriver, onInk = false)
            }
        }
    }
}

@Composable
private fun BannerPill(text: String, onClick: () -> Unit, onInk: Boolean) {
    val c = EodiTheme.colors
    PressSurface(
        onClick = onClick,
        shape = CircleShape,
        color = if (onInk) c.onAccent.copy(alpha = 0.18f) else c.surface,
        contentColor = if (onInk) c.onAccent else c.accent,
        modifier = Modifier.heightIn(min = EodiTheme.space.touch),
    ) {
        Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(text, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** 위치 권한이 꺼져 기록하지 않는 중일 때 기록 화면 맨 위 한 줄 (다시 묻지 않고 설정 화면으로). */
@Composable
private fun PermissionWarning(onFix: () -> Unit) {
    val c = EodiTheme.colors
    Surface(color = c.staySoft, contentColor = c.textPrimary, shape = RoundedCornerShape(EodiTheme.space.radiusM)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PhoneIcons.Locate, contentDescription = null, tint = c.stay, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.phone_no_permission), style = EodiTheme.type.label, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            BannerPill(stringResource(R.string.phone_no_permission_action), onFix, onInk = false)
        }
    }
}
