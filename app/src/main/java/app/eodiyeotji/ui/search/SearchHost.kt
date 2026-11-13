package app.eodiyeotji.ui.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.core.SearchScope
import app.eodiyeotji.ui.agent.AgentViewModel
import app.eodiyeotji.ui.agent.VoiceUi
import app.eodiyeotji.ui.community.CommunityTab
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 화면(내 기록 · 커뮤니티)에 끼울 검색 자리를 만든다: 검색 칸 + 결과 + 말로 찾기.
 * 마이크는 기억 에이전트의 음성 길([AgentViewModel.dictate])을 같이 쓰고, 알아들은 말은 이 검색 칸에 들어간다
 * (마이크를 누른 검색 칸만 받는다 — 내 기록 · 커뮤니티 칸이 같이 받지 않게).
 */
@Composable
fun rememberSearchSlot(
    viewModel: SearchViewModel,
    scope: SearchScope,
    agent: AgentViewModel,
    actions: SearchActions,
    hint: String,
    drivingHint: Boolean = false,
    barHeight: Dp = 88.dp,
    resultsPadding: PaddingValues = PaddingValues(bottom = 24.dp),
): SearchSlot {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 에이전트 상태 전체를 읽으면 듣는 소리 크기 · 답 글자가 바뀔 때마다 이 칸을 부른 홈 화면 전체가 다시 그려진다
    // → 받아쓰기 중인지만 따로 보고, 목소리 상태는 이 칸에서 받아쓰는 동안만 읽는다.
    val dictatingNow by remember(agent) { agent.state.map { it.voice.dictating }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
    var dictatingHere by remember { mutableStateOf(false) }
    LaunchedEffect(scope) { viewModel.setScope(scope) }
    val vm by rememberUpdatedState(viewModel)
    LaunchedEffect(agent) {
        agent.dictated.collect { text ->
            if (dictatingHere) {
                dictatingHere = false
                vm.submit(text)
            }
        }
    }
    // 받아쓰기가 끝났는데 말이 없었으면 (못 들음 · 취소) 이 칸의 받아쓰기 표시도 끝낸다 (말이 있었으면 위에서 이미 끝남)
    var sawDictating by remember { mutableStateOf(false) }
    LaunchedEffect(dictatingNow) {
        if (dictatingNow) {
            sawDictating = true
        } else if (sawDictating) {
            sawDictating = false
            kotlinx.coroutines.delay(300)
            dictatingHere = false
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) agent.dictate() else {
            dictatingHere = false
            agent.micPermissionDenied()
        }
    }
    val onMic: () -> Unit = {
        dictatingHere = true
        viewModel.open()
        if (agent.needsMicPermission()) micPermission.launch(Manifest.permission.RECORD_AUDIO) else agent.dictate()
    }
    val voice = if (dictatingHere) {
        remember(agent) { agent.state.map { it.voice }.distinctUntilChanged() }.collectAsStateWithLifecycle(VoiceUi()).value
    } else {
        VoiceUi()
    }
    return SearchSlot(
        active = state.active,
        bar = { m ->
            SearchField(
                query = state.query,
                active = state.active,
                hint = hint,
                voice = voice,
                onOpen = viewModel::open,
                onQuery = viewModel::setQuery,
                onSubmit = viewModel::submit,
                onMic = onMic,
                onClose = viewModel::close,
                modifier = m,
                height = barHeight,
            )
        },
        results = { m ->
            SearchResults(
                state = state,
                actions = actions,
                viewModel = viewModel,
                onMic = onMic,
                modifier = m,
                drivingHint = drivingHint,
                contentPadding = resultsPadding,
            )
        },
    )
}

/** 커뮤니티 하위 탭 → 검색 범위. */
fun CommunityTab.searchScope(): SearchScope = when (this) {
    CommunityTab.BOARD -> SearchScope.BOARD
    CommunityTab.COURSES -> SearchScope.COURSES
    CommunityTab.REVIEWS, CommunityTab.REGION -> SearchScope.REVIEWS
}

/** 커뮤니티 하위 탭의 검색 칸 안내 글. */
fun CommunityTab.searchHint(): Int = when (this) {
    CommunityTab.BOARD -> app.eodiyeotji.R.string.search_hint_board
    CommunityTab.COURSES -> app.eodiyeotji.R.string.search_hint_courses
    CommunityTab.REVIEWS, CommunityTab.REGION -> app.eodiyeotji.R.string.search_hint_reviews
}
