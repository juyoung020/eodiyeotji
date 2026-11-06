package app.eodiyeotji.ui.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.ui.review.ReviewMode
import app.eodiyeotji.ui.review.ReviewPane
import app.eodiyeotji.ui.review.ReviewViewModel
import app.eodiyeotji.ui.search.SearchSlot
import app.eodiyeotji.ui.theme.EodiTheme

enum class CommunityTab { BOARD, COURSES, REVIEWS }

/** 이 폭 이상이면 머리 한 줄에 제목 · 하위 탭 · 동작 버튼을 다 둔다 (좁으면 하위 탭이 둘째 줄). */
private val OneLineHeader = 1400.dp

/**
 * 커뮤니티 (정차 중에만 보인다 — 주행 중에는 HomeRoute가 에이전트 주행 화면만 띄운다).
 * 위쪽 하위 탭 [게시판 · 드라이브 코스(경치 길) · 여행 리뷰(장소 리뷰 · 여행 코스 후기)]. 각 탭은 자기 ViewModel을 가져 탭을 오가도 목록·정렬이 남는다.
 * [headerAction] 자리는 타임라인과 같은 대표 동작 하나(에이전트 "어디였지? 물어보세요").
 * [compact]: 폰 세로 화면 (상세를 열면 머리를 감춘다).
 * [search]: 머리의 검색 칸 · 검색 중이면 하위 탭 화면 대신 결과 (지금 하위 탭 안에서 찾는다).
 * [selectedTab]·[onTabChange]: 하위 탭을 밖에서 정할 때 (내 기록 검색 결과에서 코스 · 리뷰를 열 때). 없으면 이 화면이 기억한다.
 */
@Composable
fun CommunityRoute(
    headerAction: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    search: SearchSlot? = null,
    selectedTab: CommunityTab? = null,
    onTabChange: ((CommunityTab) -> Unit)? = null,
) {
    var ownTab by rememberSaveable { mutableStateOf(CommunityTab.COURSES) }
    val tab = selectedTab ?: ownTab
    val selectTab: (CommunityTab) -> Unit = { t -> ownTab = t; onTabChange?.invoke(t) }
    // 폰 세로 화면: 코스 상세 · 게시글을 열면 머리(제목 · 하위 탭)를 감춰 상세가 화면을 다 쓴다. 차 화면은 그대로.
    val detailOpen = if (!compact) {
        false
    } else {
        when (tab) {
            CommunityTab.COURSES -> hiltViewModel<DriveRouteViewModel>().state.collectAsStateWithLifecycle().value.detail != null
            CommunityTab.BOARD -> hiltViewModel<BoardViewModel>().state.collectAsStateWithLifecycle().value.thread != null
            // 여행 리뷰 > 여행 코스 후기의 코스 상세
            CommunityTab.REVIEWS ->
                hiltViewModel<ReviewViewModel>().state.collectAsStateWithLifecycle().value.mode == ReviewMode.COURSES &&
                    hiltViewModel<CourseViewModel>().state.collectAsStateWithLifecycle().value.detail != null
        }
    }
    val c = EodiTheme.colors
    Surface(Modifier.fillMaxSize(), color = c.background, contentColor = c.textPrimary) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val oneLine = maxWidth >= OneLineHeader
            Column(Modifier.fillMaxSize()) {
                val tabs: @Composable () -> Unit = {
                    ChoiceRow(
                        options = listOf(
                            CommunityTab.BOARD to stringResource(R.string.community_tab_board),
                            CommunityTab.COURSES to stringResource(R.string.community_tab_courses),
                            CommunityTab.REVIEWS to stringResource(R.string.community_tab_reviews),
                        ),
                        selected = tab,
                        onSelect = selectTab,
                        big = true,
                    )
                }
                val searching = search?.active == true
                if (compact && search != null) {
                    // 폰: 제목 줄 · 하위 탭 아래에 검색 칸 한 줄 (검색 중에는 검색 칸 · 하위 탭만 위에 남는다)
                    if (!detailOpen && !searching) Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.community_title),
                            style = EodiTheme.type.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.weight(1f))
                        if (headerAction != null) headerAction()
                    }
                    if (!detailOpen) {
                        search.bar(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = if (searching) 16.dp else 0.dp, bottom = 12.dp))
                        Box(Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp)) { tabs() }
                    }
                } else {
                    if (!detailOpen) Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.community_title),
                            style = EodiTheme.type.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (oneLine) {
                            Spacer(Modifier.width(32.dp))
                            tabs()
                        }
                        if (search != null) {
                            // 하위 탭과 물어보기 버튼 사이를 검색 칸이 채운다
                            Spacer(Modifier.width(24.dp))
                            search.bar(Modifier.weight(1f))
                            Spacer(Modifier.width(16.dp))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (headerAction != null) headerAction()
                    }
                    if (!oneLine && !detailOpen) {
                        Box(Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp)) { tabs() }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    if (searching) {
                        search!!.results(Modifier.fillMaxSize().padding(horizontal = 32.dp))
                    } else {
                        when (tab) {
                            CommunityTab.BOARD -> BoardPane()
                            CommunityTab.COURSES -> DriveRoutePane()
                            CommunityTab.REVIEWS -> ReviewPane()
                        }
                    }
                }
            }
        }
    }
}
