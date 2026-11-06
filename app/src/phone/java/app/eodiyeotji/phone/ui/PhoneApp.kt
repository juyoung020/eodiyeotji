package app.eodiyeotji.phone.ui

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
import androidx.compose.foundation.layout.offset
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
import app.eodiyeotji.ui.agent.AgentIcons
import app.eodiyeotji.ui.agent.AgentPanel
import app.eodiyeotji.ui.agent.AgentViewModel
import app.eodiyeotji.ui.community.BoardViewModel
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.CommunityRoute
import app.eodiyeotji.ui.community.CourseViewModel
import app.eodiyeotji.ui.community.LocalOverlayPresenter
import app.eodiyeotji.ui.home.CourseGuideBanner
import app.eodiyeotji.ui.home.CourseGuideViewModel
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.SparkleBadge
import app.eodiyeotji.ui.timeline.TimelineRoute
import app.eodiyeotji.ui.timeline.TimelineViewModel

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
        // 시트(OverlayCard 자리)는 폰에서 아래에서 올라온다
        val sheets = remember { PhoneSheetHostState() }
        CompositionLocalProvider(
            LocalOverlayPresenter provides { onDismiss, content -> PhoneSheet(onDismiss, content) },
            LocalPhoneSheetHost provides sheets,
        ) {
            Box(Modifier.fillMaxSize().background(EodiTheme.colors.background)) {
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
                // 시트는 맨 위에 (아래 탭 · 운전 중 띠까지 덮는다)
                PhoneSheetHost(sheets)
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
    val agent: AgentViewModel = hiltViewModel()
    val guide: CourseGuideViewModel = hiltViewModel()
    val moving by shell.moving.collectAsStateWithLifecycle()
    val passenger by shell.passenger.collectAsStateWithLifecycle()
    val permissions by shell.permissions.collectAsStateWithLifecycle()
    val noMap by shell.noMapApp.collectAsStateWithLifecycle()
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

    // 상세 화면이 열려 있는지 (아래 탭을 감춘다). 같은 ViewModel 인스턴스를 각 화면과 같이 본다.
    val detailOpen = when (tab) {
        PhoneTab.RECORDS -> {
            val tl: TimelineViewModel = hiltViewModel()
            val s by tl.state.collectAsStateWithLifecycle()
            s.selectedDay != null
        }
        PhoneTab.COMMUNITY -> {
            val course: CourseViewModel = hiltViewModel()
            val board: BoardViewModel = hiltViewModel()
            val cs by course.state.collectAsStateWithLifecycle()
            val bs by board.state.collectAsStateWithLifecycle()
            cs.detail != null || bs.thread != null
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
                    PhoneTab.RECORDS -> TimelineRoute(
                        compact = true,
                        headerAction = { HeaderIconButton(PhoneIcons.Tune, stringResource(R.string.phone_settings)) { settingsOpen = true } },
                        headerBanner = if (!permissions.location) {
                            { PermissionWarning(onFix = shell::openAppSettings) }
                        } else {
                            null
                        },
                    )
                    PhoneTab.ASK -> AgentPanel(onClose = null, viewModel = agent, compact = true)
                    PhoneTab.COMMUNITY -> CommunityRoute(headerAction = null, compact = true)
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

    if (settingsOpen) {
        val prefs by shell.prefs.collectAsStateWithLifecycle()
        val installed by shell.installed.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { shell.refreshInstalled() }
        PhoneSettingsSheet(
            mapApp = prefs.mapApp,
            installed = installed,
            permissions = permissions,
            onMapApp = shell::setMapApp,
            onOpenSystemSettings = shell::openAppSettings,
            onClose = { settingsOpen = false },
        )
    }
    noMap?.let { req ->
        NoMapAppSheet(
            request = req,
            onWeb = { shell.answerNoMapApp(openWeb = true) },
            onInstall = shell::installMapApp,
            onClose = { shell.answerNoMapApp(openWeb = false) },
        )
    }
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

/** 가운데 물어보기: 막대 위로 살짝 떠 있는 AI 동그라미(파랑 + 금색 반짝임, 운전 중에는 마이크). 이름 줄은 양옆 탭 이름과 같은 높이. */
@Composable
private fun AskItem(label: String, selected: Boolean, driving: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .offset(y = (-28).dp)
                .clip(RoundedCornerShape(EodiTheme.space.radiusM))
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (driving) {
                // 운전 중: 말로 묻기가 먼저 → 마이크
                Surface(shape = CircleShape, color = c.accent, contentColor = c.onAccent, shadowElevation = 6.dp, modifier = Modifier.size(104.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AgentIcons.Mic, contentDescription = null, modifier = Modifier.size(50.dp))
                    }
                }
            } else {
                // AI 동그라미 (파랑 + 금색 반짝임, 디자인 무늬 SparkleBadge) — 기억 에이전트 입구
                Surface(shape = CircleShape, color = c.accent, shadowElevation = 6.dp, modifier = Modifier.size(104.dp)) {
                    SparkleBadge(size = 104.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = EodiTheme.type.caption,
                fontWeight = FontWeight.Bold,
                color = if (selected) c.accent else c.textPrimary,
                maxLines = 1,
            )
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
        Surface(onClick = onListen, color = c.accent, contentColor = c.onAccent, shape = RoundedCornerShape(EodiTheme.space.radiusL), shadowElevation = 4.dp, modifier = modifier) {
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
    Surface(
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

/** 머리 오른쪽 둥근 아이콘 버튼 (설정). */
@Composable
private fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = c.surface,
        contentColor = c.textSecondary,
        modifier = Modifier.size(EodiTheme.space.touch).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp)) }
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
