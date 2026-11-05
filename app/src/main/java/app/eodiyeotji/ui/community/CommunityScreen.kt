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
import app.eodiyeotji.R
import app.eodiyeotji.ui.review.ReviewPane
import app.eodiyeotji.ui.theme.EodiTheme

enum class CommunityTab { BOARD, COURSES, REVIEWS }

/** 이 폭 이상이면 머리 한 줄에 제목 · 하위 탭 · 동작 버튼을 다 둔다 (좁으면 하위 탭이 둘째 줄). */
private val OneLineHeader = 1400.dp

/**
 * 커뮤니티 (정차 중에만 보인다 — 주행 중에는 HomeRoute가 에이전트 주행 화면만 띄운다).
 * 위쪽 하위 탭 [게시판 · 드라이브 코스 · 여행 리뷰]. 각 탭은 자기 ViewModel을 가져 탭을 오가도 목록·정렬이 남는다.
 * [headerAction] 자리는 타임라인과 같은 대표 동작 하나(에이전트 "어디였지? 물어보세요").
 */
@Composable
fun CommunityRoute(headerAction: (@Composable () -> Unit)? = null) {
    var tab by rememberSaveable { mutableStateOf(CommunityTab.COURSES) }
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
                        onSelect = { tab = it },
                        big = true,
                    )
                }
                Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.weight(1f))
                    if (headerAction != null) headerAction()
                }
                if (!oneLine) {
                    Box(Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp)) { tabs() }
                }
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        CommunityTab.BOARD -> BoardPane()
                        CommunityTab.COURSES -> CoursePane()
                        CommunityTab.REVIEWS -> ReviewPane()
                    }
                }
            }
        }
    }
}
