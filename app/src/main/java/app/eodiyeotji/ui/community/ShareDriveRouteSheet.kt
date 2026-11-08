package app.eodiyeotji.ui.community

import app.eodiyeotji.ui.theme.EodiIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.BestTime
import app.eodiyeotji.core.DriveRoute
import app.eodiyeotji.core.DriveTripCandidate
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.Visit
import app.eodiyeotji.core.bestTimeOptions
import app.eodiyeotji.core.driveShareCandidates
import app.eodiyeotji.core.shareDriveRoute
import app.eodiyeotji.core.weatherFor
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.ui.common.durationText
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
 * 내 주행 하나를 드라이브 코스(경치 길)로 올리기: ① 최근 주행 고르기 → ② 이름·소개·언제 → [올리기].
 * 올라갈 길(집·회사 근처 1.2km를 지운 선)·거리·시간·이미 올렸는지는 Rust(drive_share_candidates)가 정한다.
 * 그날 날씨는 주행을 고르자마자 한 번 받아 둔다(못 받으면 날씨 없이).
 */

@Immutable
data class ShareDriveUiState(
    val candidates: Load<DriveTripCandidate> = Load.Loading,
    val picked: DriveTripCandidate? = null,
    val bestOptions: List<BestTime> = emptyList(),
    val title: String = "",
    val body: String = "",
    val best: BestTime? = null,
    val weather: String? = null,
    val submitting: Boolean = false,
    val submitError: String? = null,
) {
    val canSubmit: Boolean get() = !submitting && picked != null && title.isNotBlank() && best != null
}

@HiltViewModel
class ShareDriveViewModel @Inject constructor(private val core: EodiCore) : ViewModel() {
    private val _state = MutableStateFlow(ShareDriveUiState())
    val state: StateFlow<ShareDriveUiState> = _state.asStateFlow()
    private var weatherJob: Job? = null

    fun load() {
        _state.value = ShareDriveUiState()
        viewModelScope.launch {
            val options = bestTimeOptions()
            val list = try {
                Load.Loaded(core.driveShareCandidates())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { it.copy(candidates = list, bestOptions = options) }
        }
    }

    /** 주행을 고른다. 제목 기본값은 Rust가 지은 이름 ("A → B 드라이브" 등). */
    fun pick(c: DriveTripCandidate) {
        _state.update { it.copy(picked = c, title = c.defaultTitle, body = "", best = null, weather = null, submitError = null) }
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val w = core.weatherFor(c)
            _state.update { s -> if (s.picked?.tripStartMs == c.tripStartMs) s.copy(weather = w) else s }
        }
    }

    fun back() = _state.update { it.copy(picked = null) }
    fun setTitle(v: String) = _state.update { it.copy(title = v.take(60)) }
    fun setBody(v: String) = _state.update { it.copy(body = v.take(1000)) }
    fun setBest(b: BestTime) = _state.update { it.copy(best = b) }

    fun submit(onDone: (DriveRoute) -> Unit) {
        val s = _state.value
        val c = s.picked ?: return
        val best = s.best ?: return
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            try {
                val r = core.shareDriveRoute(c.tripStartMs, s.title, s.body, best, s.weather)
                _state.update { it.copy(submitting = false) }
                onDone(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, submitError = e.message ?: e.javaClass.simpleName) }
            }
        }
    }
}

/** 내 주행을 드라이브 코스로 올리는 창. [onDone]: 올렸으면 그 길, 닫았으면 null. */
@Composable
fun ShareDriveRouteSheet(onDone: (DriveRoute?) -> Unit, viewModel: ShareDriveViewModel = hiltViewModel(key = "share-drive")) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = EodiTheme.colors
    OverlayCard(onDismiss = { if (!state.submitting) onDone(null) }, maxWidth = 1100.dp) {
        val picked = state.picked
        if (picked == null) {
            Column(Modifier.padding(32.dp).heightIn(max = 1000.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.drive_share_pick_title), style = EodiTheme.type.title, modifier = Modifier.weight(1f))
                    RoundIconButton(EodiIcons.Close, stringResource(R.string.community_close), { onDone(null) })
                }
                Spacer(Modifier.height(16.dp))
                when (val l = state.candidates) {
                    Load.Loading -> Box(Modifier.fillMaxWidth().height(300.dp)) { LoadingBox() }
                    is Load.Failed -> Text(stringResource(R.string.community_failed, l.message), style = EodiTheme.type.body, color = c.error)
                    is Load.Loaded ->
                        if (l.items.isEmpty()) {
                            Text(stringResource(R.string.drive_share_pick_empty), style = EodiTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(vertical = 40.dp))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(l.items, key = { it.tripStartMs }) { t -> TripRow(t) { viewModel.pick(t) } }
                            }
                        }
                }
            }
        } else {
            ShareDriveForm(
                state = state,
                trip = picked,
                onTitle = viewModel::setTitle,
                onBody = viewModel::setBody,
                onBest = viewModel::setBest,
                onSubmit = { viewModel.submit { onDone(it) } },
                onBack = viewModel::back,
            )
        }
    }
}

@Composable
private fun TripRow(t: DriveTripCandidate, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Surface(onClick = onClick, color = c.surfaceHigh, shape = RoundedCornerShape(EodiTheme.space.radiusM), modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(CommunityIcons.Road, null, tint = c.stay, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(visitText(Visit(t.day, "", null)), style = EodiTheme.type.caption, color = c.textTertiary)
                Text(t.defaultTitle, style = EodiTheme.type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(distanceText(t.distanceM), durationText(t.durationMs)).joinToString(" · "), style = EodiTheme.type.meta, color = c.textSecondary)
            }
            if (t.shared) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareDriveForm(
    state: ShareDriveUiState,
    trip: DriveTripCandidate,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onBest: (BestTime) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(app.eodiyeotji.ui.theme.EodiIcons.Back, stringResource(R.string.community_close), onBack)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.drive_share_title), style = type.title, modifier = Modifier.weight(1f))
        }
        VisitLine(Visit(trip.day, "", state.weather))
        Text(
            listOf(stringResource(R.string.drive_from_to, trip.fromName, trip.toName), distanceText(trip.distanceM), durationText(trip.durationMs)).joinToString(" · "),
            style = type.meta,
            color = c.textSecondary,
        )
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitle,
            label = { Text(stringResource(R.string.drive_share_title_label), style = type.caption) },
            textStyle = type.bodyStrong,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.body,
            onValueChange = onBody,
            label = { Text(stringResource(R.string.drive_share_body_label), style = type.caption) },
            textStyle = type.body,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.drive_share_when), style = type.label, color = c.textTertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.bestOptions.forEach { b -> TogglePill(bestTimeLabel(b), state.best == b, { onBest(b) }) }
        }
        if (trip.trimmed) {
            Row(
                Modifier.fillMaxWidth().background(c.hereSoft, RoundedCornerShape(EodiTheme.space.radiusS)).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EodiIcons.Check, null, tint = c.here, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.drive_share_trimmed), style = type.label, color = c.textPrimary)
            }
        }
        if (trip.shared) Text(stringResource(R.string.share_reshare_note), style = type.caption, color = c.textTertiary)
        state.submitError?.let { Text(stringResource(R.string.share_failed, it), style = type.caption, color = c.error) }
        PrimaryPill(
            stringResource(if (state.submitting) R.string.share_submitting else R.string.share_submit),
            CommunityIcons.Upload,
            onSubmit,
            Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
        )
    }
}
