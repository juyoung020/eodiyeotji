package app.eodiyeotji.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.DriveCourse
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.SharePreview
import app.eodiyeotji.core.Visit
import app.eodiyeotji.core.shareCandidates
import app.eodiyeotji.core.shareCourseWith
import app.eodiyeotji.core.sharePreview
import app.eodiyeotji.core.weatherFor
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.distanceText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * 내 기록에서 코스 올리기 (다른 화면에서도 다시 쓴다: 타임라인 하루 상세, 에이전트 추천 카드).
 * - ShareEpisodePicker: 내 최근 나들이·여행 목록 → 하나 고르기
 * - ShareCourseSheet(episodeId, onDone): 미리보기 → [올리기] 한 번
 * 집·회사 근처 곳 거르기·거리·계절·날씨 주소는 Rust(share_preview)가 정하고, 그날 날씨는 열리자마자 한 번 받아 둔다.
 */

// ---------------------------------------------------------------- 공유 시트

@Immutable
data class ShareUiState(
    val episodeId: Long = 0,
    val preview: SharePreview? = null,
    val error: String? = null,
    val title: String = "",
    val body: String = "",
    /** 체크를 푼 곳의 도착 시각 */
    val excluded: Set<Long> = emptySet(),
    val weather: String? = null,
    val weatherLoading: Boolean = false,
    val submitting: Boolean = false,
    val submitError: String? = null,
) {
    val keptCount: Int get() = preview?.stops?.count { it.arriveMs !in excluded } ?: 0
    val canSubmit: Boolean get() = !submitting && title.isNotBlank() && keptCount > 0
}

@HiltViewModel
class ShareCourseViewModel @Inject constructor(private val core: EodiCore) : ViewModel() {
    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** 시트가 열릴 때마다 새로 읽는다 (같은 에피소드를 다시 열어도 처음 상태로). */
    fun start(episodeId: Long) {
        job?.cancel()
        _state.value = ShareUiState(episodeId = episodeId)
        job = viewModelScope.launch {
            val p = try {
                core.sharePreview(episodeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
                return@launch
            }
            _state.update { it.copy(preview = p, title = p.title, weatherLoading = true) }
            val w = core.weatherFor(p)
            _state.update { it.copy(weather = w, weatherLoading = false) }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v.take(100)) }
    fun setBody(v: String) = _state.update { it.copy(body = v.take(1000)) }

    fun toggleStop(arriveMs: Long) = _state.update {
        it.copy(excluded = if (arriveMs in it.excluded) it.excluded - arriveMs else it.excluded + arriveMs)
    }

    fun submit(onDone: (DriveCourse) -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            try {
                val post = core.shareCourseWith(s.episodeId, s.title, s.body, s.excluded.toList(), s.weather)
                _state.update { it.copy(submitting = false) }
                onDone(post)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, submitError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }
}

/**
 * 코스 올리기 미리보기 시트. 제목은 에피소드·하루 제목으로 미리 채우고, 들른 곳은 사진 칩으로 보여 준다
 * (눌러서 빼기). 집·회사 근처는 Rust가 이미 뺐고 그 수를 알려 준다. [onDone]: 올렸으면 그 코스, 닫았으면 null.
 */
@Composable
fun ShareCourseSheet(episodeId: Long, onDone: (DriveCourse?) -> Unit, viewModel: ShareCourseViewModel = hiltViewModel(key = "share-course")) {
    LaunchedEffect(episodeId) { viewModel.start(episodeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    OverlayCard(onDismiss = { if (!state.submitting) onDone(null) }, maxWidth = 1200.dp) {
        val p = state.preview
        when {
            state.error != null -> CenterMessage(stringResource(R.string.community_failed, state.error ?: ""), Modifier.heightIn(max = 400.dp)) {
                SecondaryPill(stringResource(R.string.community_close), CommunityIcons.Close, { onDone(null) })
            }
            p == null || state.episodeId != episodeId -> Box(Modifier.fillMaxWidth().height(360.dp)) { LoadingBox() }
            else -> ShareSheetBody(
                state = state,
                preview = p,
                onTitle = viewModel::setTitle,
                onBody = viewModel::setBody,
                onToggleStop = viewModel::toggleStop,
                onSubmit = { viewModel.submit { onDone(it) } },
                onClose = { onDone(null) },
            )
        }
    }
}

@Composable
private fun ShareSheetBody(
    state: ShareUiState,
    preview: SharePreview,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onToggleStop: (Long) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.share_sheet_title), style = type.title, modifier = Modifier.weight(1f))
            RoundIconButton(CommunityIcons.Close, stringResource(R.string.community_close), onClose)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            VisitLine(Visit(preview.day, preview.season, state.weather), Modifier.weight(1f, fill = false))
            if (state.weatherLoading) {
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.share_weather_loading), style = type.caption, color = c.textTertiary)
            }
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitle,
            label = { Text(stringResource(R.string.share_title_label), style = type.caption) },
            textStyle = type.bodyStrong,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.body,
            onValueChange = onBody,
            label = { Text(stringResource(R.string.share_body_label), style = type.caption) },
            textStyle = type.body,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.share_stops_hint), style = type.label, color = c.textTertiary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(preview.stops, key = { _, s -> s.arriveMs }) { i, s ->
                val on = s.arriveMs !in state.excluded
                Column(
                    Modifier
                        .width(156.dp)
                        .clickable(role = Role.Checkbox) { onToggleStop(s.arriveMs) }
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.alpha(if (on) 1f else 0.35f)) {
                        StopPhoto(s.name, s.address, s.lat, s.lon, order = i + 1, size = 156.dp)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(44.dp)
                                .background(if (on) c.accent else c.surface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(CommunityIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(s.name, style = type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) c.textPrimary else c.textTertiary)
                }
            }
        }
        val kept = preview.stops.filter { it.arriveMs !in state.excluded }
        Text(
            listOf(stringResource(R.string.community_stops_count, kept.size), distanceText(preview.distanceM)).joinToString(" · "),
            style = type.meta,
            color = c.textSecondary,
        )
        if (preview.hiddenCount > 0) PrivacyNote(stringResource(R.string.share_hidden, preview.hiddenCount))
        if (preview.unnamedCount > 0) PrivacyNote(stringResource(R.string.share_unnamed, preview.unnamedCount))
        if (preview.shared) Text(stringResource(R.string.share_reshare_note), style = type.caption, color = c.textTertiary)
        state.submitError?.let { Text(stringResource(R.string.share_failed, it), style = type.caption, color = c.error) }
        if (state.keptCount == 0) Text(stringResource(R.string.share_need_stop), style = type.caption, color = c.error)
        PrimaryPill(
            stringResource(if (state.submitting) R.string.share_submitting else R.string.share_submit),
            CommunityIcons.Upload,
            onSubmit,
            Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
        )
    }
}

@Composable
private fun PrivacyNote(text: String) {
    val c = EodiTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.hereSoft, RoundedCornerShape(EodiTheme.space.radiusS)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CommunityIcons.Check, null, tint = c.here, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = EodiTheme.type.label, color = c.textPrimary)
    }
}

// ---------------------------------------------------------------- 기록 고르기

@Immutable
data class PickerUiState(val list: Load<SharePreview> = Load.Loading)

@HiltViewModel
class SharePickerViewModel @Inject constructor(private val core: EodiCore) : ViewModel() {
    private val _state = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = PickerUiState(
                try {
                    Load.Loaded(core.shareCandidates())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Load.Failed(e.message ?: e.javaClass.simpleName)
                },
            )
        }
    }
}

/** 내 최근 나들이·여행 목록 (첫 곳 사진, 날짜·계절, 제목, 들른 곳 수). 고르면 [onPick]. */
@Composable
fun ShareEpisodePicker(onPick: (Long) -> Unit, onDismiss: () -> Unit, viewModel: SharePickerViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = EodiTheme.colors
    OverlayCard(onDismiss = onDismiss, maxWidth = 1100.dp) {
        Column(Modifier.padding(32.dp).heightIn(max = 1000.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.share_pick_title), style = EodiTheme.type.title, modifier = Modifier.weight(1f))
                RoundIconButton(CommunityIcons.Close, stringResource(R.string.community_close), onDismiss)
            }
            Spacer(Modifier.height(16.dp))
            when (val l = state.list) {
                Load.Loading -> Box(Modifier.fillMaxWidth().height(300.dp)) { LoadingBox() }
                is Load.Failed -> Text(stringResource(R.string.community_failed, l.message), style = EodiTheme.type.body, color = c.error)
                is Load.Loaded ->
                    if (l.items.isEmpty()) {
                        Text(stringResource(R.string.share_pick_empty), style = EodiTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(vertical = 40.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(l.items, key = { it.episodeId }) { p -> CandidateRow(p) { onPick(p.episodeId) } }
                        }
                    }
            }
        }
    }
}

@Composable
private fun CandidateRow(p: SharePreview, onClick: () -> Unit) {
    val c = EodiTheme.colors
    val first = p.stops.first()
    Surface(onClick = onClick, color = c.surfaceHigh, shape = RoundedCornerShape(EodiTheme.space.radiusM), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StopPhoto(first.name, first.address, first.lat, first.lon, order = null, size = 112.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(visitText(Visit(p.day, p.season, null)), style = EodiTheme.type.caption, color = c.textTertiary)
                Text(p.title, style = EodiTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(stringResource(R.string.community_stops_count, p.stops.size), p.stops.joinToString(" · ") { it.name })
                        .joinToString(" · "),
                    style = EodiTheme.type.meta,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (p.shared) {
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.share_already),
                    style = EodiTheme.type.caption,
                    color = c.accent,
                    modifier = Modifier.background(c.accentSoft, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}
