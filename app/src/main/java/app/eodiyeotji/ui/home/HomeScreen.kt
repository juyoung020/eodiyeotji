package app.eodiyeotji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.record.RecordingStatusChip
import app.eodiyeotji.ui.agent.AgentEntryButton
import app.eodiyeotji.ui.agent.AgentPanel
import app.eodiyeotji.ui.agent.AgentViewModel
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.BoardViewModel
import app.eodiyeotji.ui.community.CommunityRoute
import app.eodiyeotji.ui.community.CommunityTab
import app.eodiyeotji.ui.community.CourseViewModel
import app.eodiyeotji.core.SearchScope
import app.eodiyeotji.ui.review.ReviewViewModel
import app.eodiyeotji.ui.search.SearchActions
import app.eodiyeotji.ui.search.SearchViewModel
import app.eodiyeotji.ui.places.PlacesRoute
import app.eodiyeotji.ui.search.rememberSearchSlot
import app.eodiyeotji.ui.search.searchHint
import app.eodiyeotji.ui.search.searchScope
import app.eodiyeotji.ui.timeline.TimelineViewModel
import androidx.compose.foundation.layout.widthIn
import app.eodiyeotji.ui.theme.BrandMark
import app.eodiyeotji.ui.common.enterRise
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.TimelineRoute
import java.util.Locale

/** 왼쪽 레일 목적지. */
enum class HomeTab { TIMELINE, COMMUNITY, PLACES }

/**
 * 앱 첫 화면 (MainActivity가 부른다).
 * - 정차 중: 왼쪽 레일 [내 기록 | 커뮤니티 | 내 장소] + 고른 화면. 머리에는 검색 칸(손으로 · 말로 찾기, ui/search)과
 *   "어디였지? 물어보세요"(누르면 기억 에이전트가 위에 덮인다). 검색 결과를 열면 그날 · 코스 · 리뷰 · 글로 간다.
 * - 주행 중: 레일·타임라인·커뮤니티(긴 목록·피드)를 모두 감추고 기억 에이전트 주행 화면(음성 + 큰 카드 1장)만 보인다.
 */
@Composable
fun HomeRoute(agentViewModel: AgentViewModel = hiltViewModel(), guideViewModel: CourseGuideViewModel = hiltViewModel()) {
    // 코스 안내 이어 가기(CourseGuide)는 주행 중에도 살아 있어야 한다 → 화면 갈래와 상관없이 여기서 만든다
    // (운전 중 "응, 안내해 줘"로 시작한 에이전트 코스도 도착하면 다음 구간으로 이어진다)
    val agentState by agentViewModel.state.collectAsStateWithLifecycle()
    var agentOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.TIMELINE) }
    Box(Modifier.fillMaxSize()) {
        if (agentState.isDriving && agentViewModel.drivingLocksScreen) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { AgentPanel(onClose = null, viewModel = agentViewModel) }
                // 주행 중에는 버튼 없이 진행 상황 한 줄만 (몇 번째 구간 · 다음 들를 곳)
                CourseGuideBanner(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                    viewModel = guideViewModel,
                    interactive = false,
                )
            }
        } else {
            // 머리: [검색 칸 (손으로 · 말로 찾기)] + [✦ 물어보기 (기억 에이전트)]. 마이크는 검색 칸 안에 하나만 둔다.
            // 검색 칸이 글로 찾기·묻기의 한 곳: 문장으로 쓰면 결과 맨 위가 "AI에게 물어보기"(Rust search_ui의 question 판정).
            // 내 기록 머리 오른쪽: 작은 기록 상태(기록 중 · 시동 걸면 자동 기록 · 권한 꺼짐, 누르면 설명) + 물어보기(바로 대화로)
            val recordsSearchVm: SearchViewModel = hiltViewModel(key = "search-records")
            val communitySearchVm: SearchViewModel = hiltViewModel(key = "search-community")
            // 검색 칸에 글을 쓴 채 물어보기를 누르면 그 글을 바로 묻는다 (빈 입력창으로 열리지 않게)
            val openAgent = { search: SearchViewModel ->
                val typed = search.state.value.query.trim()
                if (typed.isNotEmpty()) agentViewModel.ask(typed)
                agentOpen = true
            }
            val entry: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecordingStatusChip()
                    Spacer(Modifier.width(12.dp))
                    AgentEntryButton(onClick = { openAgent(recordsSearchVm) }, mic = false)
                }
            }
            val compactEntry: @Composable () -> Unit = {
                AgentEntryButton(onClick = { openAgent(communitySearchVm) }, mic = false, modifier = Modifier.widthIn(max = 300.dp))
            }
            var communityTab by rememberSaveable { mutableStateOf(CommunityTab.COURSES) }
            val timeline: TimelineViewModel = hiltViewModel()
            val courses: CourseViewModel = hiltViewModel()
            val reviews: ReviewViewModel = hiltViewModel()
            val board: BoardViewModel = hiltViewModel()
            val actions = SearchActions(
                openDay = { day, entryKey ->
                    tab = HomeTab.TIMELINE
                    timeline.openDay(day, entryKey)
                },
                openCourse = { c ->
                    // 여러 곳을 차례로 다녀온 코스 글은 여행 리뷰 피드에 있다 (상세를 바로 연다)
                    tab = HomeTab.COMMUNITY
                    communityTab = CommunityTab.REVIEWS
                    courses.open(c)
                },
                openReview = { p ->
                    tab = HomeTab.COMMUNITY
                    communityTab = CommunityTab.REVIEWS
                    reviews.openPlace(p)
                },
                openPost = { p ->
                    tab = HomeTab.COMMUNITY
                    communityTab = CommunityTab.BOARD
                    board.open(p)
                },
                askAi = { text ->
                    agentViewModel.ask(text)
                    agentOpen = true
                },
            )
            val recordsSearch = rememberSearchSlot(
                viewModel = recordsSearchVm,
                scope = SearchScope.RECORDS,
                agent = agentViewModel,
                actions = actions,
                hint = stringResource(R.string.search_hint_records),
            )
            val communitySearch = rememberSearchSlot(
                viewModel = communitySearchVm,
                scope = communityTab.searchScope(),
                agent = agentViewModel,
                actions = actions,
                hint = stringResource(communityTab.searchHint()),
            )
            Row(Modifier.fillMaxSize().background(EodiTheme.colors.background)) {
                NavRail(tab, onSelect = { tab = it })
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // 탭을 바꾸면 새 화면이 살짝 떠오르며 들어온다 (한 번만 · 그리기 단계만 바뀜, 지도 두 장을 겹쳐 그리지 않는다)
                    Box(Modifier.weight(1f).fillMaxWidth().enterRise(tab)) {
                        when (tab) {
                            HomeTab.TIMELINE -> TimelineRoute(headerAction = entry, search = recordsSearch)
                            HomeTab.COMMUNITY -> CommunityRoute(
                                headerAction = compactEntry,
                                search = communitySearch,
                                selectedTab = communityTab,
                                onTabChange = { communityTab = it },
                            )
                            // 저장한 곳 · 자주 가는 곳 · 집 · 회사 · 숨긴 기록. "기록 보기"는 그 이름으로 내 기록 검색.
                            HomeTab.PLACES -> PlacesRoute(onOpenRecords = { name ->
                                tab = HomeTab.TIMELINE
                                recordsSearchVm.submit(name)
                            })
                        }
                    }
                    // 코스 안내 중이면 어느 탭에서든 맨 아래에 진행 카드 (내비에서 돌아왔을 때 몇 번째 구간인지 바로 보이게).
                    // 화면을 덮지 않고 그만큼 위 내용이 줄어든다.
                    CourseGuideBanner(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp), viewModel = guideViewModel)
                }
            }
            if (agentOpen) AgentPanel(onClose = { agentOpen = false; agentViewModel.clear() }, viewModel = agentViewModel) // 닫으면 대화를 비워 다음엔 새 질문부터
        }
    }
}

/** 왼쪽 레일: 앱 표시(BrandMark) + 목적지 3개 (내 기록 · 커뮤니티 · 내 장소) (한 칸 112dp, 아이콘 + 이름). 좁은 창(약 840dp)에서도 폭 120dp만 쓴다. */
@Composable
private fun NavRail(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val c = EodiTheme.colors
    Column(
        Modifier
            .fillMaxHeight()
            .width(120.dp)
            .background(c.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .padding(vertical = 24.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 앱 표시 (앱 아이콘을 줄인 모양): 어느 탭에서든 맨 위에
        BrandMark(Modifier.padding(top = 4.dp, bottom = 20.dp), size = 64.dp)
        RailItem(CommunityIcons.Clock, stringResource(R.string.nav_timeline), selected == HomeTab.TIMELINE) { onSelect(HomeTab.TIMELINE) }
        RailItem(CommunityIcons.People, stringResource(R.string.nav_community), selected == HomeTab.COMMUNITY) { onSelect(HomeTab.COMMUNITY) }
        // 받은 쿠폰이 있으면 내 장소 칸에 장 수 배지 (쿠폰함이 내 장소 안에 있다는 걸 보이게)
        val coupons = app.eodiyeotji.ui.offers.LocalCoupons.current?.claimed?.collectAsStateWithLifecycle()?.value?.size ?: 0
        RailItem(CommunityIcons.Heart, stringResource(R.string.nav_places), selected == HomeTab.PLACES, badge = coupons) { onSelect(HomeTab.PLACES) }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    val c = EodiTheme.colors
    val badgeText = if (badge > 0) stringResource(R.string.coupon_rail_badge, badge) else null
    val motion = EodiTheme.motion
    val bg by animateColorAsState(if (selected) c.accentSoft else c.accentSoft.copy(alpha = 0f), tween(motion.medium), label = "rail-bg")
    val ink by animateColorAsState(if (selected) c.accent else c.textSecondary, tween(motion.medium), label = "rail-ink")
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(EodiTheme.space.radiusM))
            .background(bg)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .then(if (badgeText != null) Modifier.semantics { stateDescription = badgeText } else Modifier)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(40.dp))
            if (badge > 0) {
                Text(
                    if (badge > 9) "9+" else badge.toString(),
                    style = EodiTheme.type.caption,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 14.dp, y = (-8).dp)
                        .background(c.coupon, CircleShape)
                        .widthIn(min = 30.dp)
                        .padding(horizontal = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = EodiTheme.type.caption,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** 개발용 Rust 코어 상태 한 줄: 버전 + 하루 경로(점 1만 개) 단순화 시간. 디버그 빌드에서만 머리 구석에 작게 둔다. */
@Composable
fun CoreStatusLine(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CoreStatusText(state, modifier)
}

@Composable
fun CoreStatusText(state: CoreUiState, modifier: Modifier = Modifier) {
    val text = when (state) {
        CoreUiState.Checking -> stringResource(R.string.core_checking)
        is CoreUiState.Failed -> stringResource(R.string.core_failed, state.message)
        is CoreUiState.Ready -> stringResource(
            R.string.core_status_line,
            state.status.version,
            String.format(Locale.ROOT, "%.2f", state.status.elapsedMicros / 1000.0),
        )
    }
    val c = EodiTheme.colors
    Text(
        text,
        style = EodiTheme.type.caption,
        color = if (state is CoreUiState.Failed) c.error else c.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(c.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
