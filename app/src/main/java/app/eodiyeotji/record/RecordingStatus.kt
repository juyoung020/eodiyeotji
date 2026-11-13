package app.eodiyeotji.record

import androidx.compose.ui.graphics.graphicsLayer
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.platform.DriveSignalSource
import app.eodiyeotji.platform.RecordingIssue
import app.eodiyeotji.platform.RecordingPlatform
import app.eodiyeotji.ui.community.LocalOverlayPresenter
import app.eodiyeotji.ui.community.OverlayCard
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.places.PlacesIcons
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** 머리 상태 표시 한 줄이 무엇을 말하나 (중요한 것 하나). */
enum class StatusHeadline { DRIVING, WAITING, OFF, LOCATION_OFF, BACKGROUND_OFF, NOTIFICATIONS_OFF, BATTERY_OPTIMIZED, NO_VEHICLE_SIGNAL }

/**
 * 기록 상태 (머리의 작은 표시 + 누르면 설명 시트).
 * [phase] = 기록 서비스([RecordingMonitor]), [issues] = 지금 걸린 설정, [checked] = 이 판이 확인하는 설정,
 * [vehicleLinked] = 차량 신호(차만, 폰은 null).
 */
@Immutable
data class RecordingStatusUi(
    val phase: RecordingPhase = RecordingPhase.WAITING,
    val issues: List<RecordingIssue> = emptyList(),
    val checked: List<RecordingIssue> = emptyList(),
    val vehicleLinked: Boolean? = null,
) {
    val headline: StatusHeadline
        get() = when {
            RecordingIssue.LOCATION_OFF in issues -> StatusHeadline.LOCATION_OFF
            phase == RecordingPhase.DRIVING -> StatusHeadline.DRIVING
            phase == RecordingPhase.OFF -> StatusHeadline.OFF
            RecordingIssue.BACKGROUND_OFF in issues -> StatusHeadline.BACKGROUND_OFF
            RecordingIssue.NOTIFICATIONS_OFF in issues -> StatusHeadline.NOTIFICATIONS_OFF
            RecordingIssue.BATTERY_OPTIMIZED in issues -> StatusHeadline.BATTERY_OPTIMIZED
            vehicleLinked == false -> StatusHeadline.NO_VEHICLE_SIGNAL
            else -> StatusHeadline.WAITING
        }
}

/**
 * 기록 상태를 모은다. 계산은 없다 — 기록 서비스 상태·차량 신호(StateFlow)를 그대로 잇고, 권한·전원 설정은
 * 화면으로 돌아올 때마다([refresh]) 백그라운드에서 다시 읽는다.
 * 서비스가 막 켜지는 순간이나 차량 서비스가 연결되는 동안 "꺼짐"이 깜빡이지 않게, 나쁜 상태는 [SETTLE_MS] 이어질 때만 보인다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordingStatusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platform: RecordingPlatform,
    monitor: RecordingMonitor,
    drive: DriveSignalSource,
    drivingState: DrivingStatePort,
    private val dataWipe: DataWipe,
) : ViewModel() {
    /** 주행 중이면 "기록 모두 지우기"를 막는다 (긴 확인 창 · 되돌릴 수 없는 일은 정차 중에만). */
    val driving: StateFlow<Boolean> = drivingState.isDriving

    /** 기록 모두 지우는 중 (버튼을 다시 못 누르게). */
    val wiping = MutableStateFlow(false)

    private val issues = MutableStateFlow<List<RecordingIssue>>(emptyList())

    private val phase: Flow<RecordingPhase> = monitor.phase.transformLatest {
        if (it == RecordingPhase.OFF) delay(SETTLE_MS)
        emit(it)
    }

    private val linked: Flow<Boolean?> = (drive.linked ?: flowOf(null)).transformLatest {
        if (it == false) delay(SETTLE_MS)
        emit(it)
    }

    val state: StateFlow<RecordingStatusUi> = combine(phase, issues, linked) { p, i, l ->
        RecordingStatusUi(p, i, platform.checkedIssues, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingStatusUi(checked = platform.checkedIssues))

    init {
        refresh()
        // 주행이 시작·끝날 때도 권한을 다시 본다 (그사이 설정 앱에서 바꿨을 수 있다)
        viewModelScope.launch { monitor.phase.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            issues.value = runCatching { platform.issues() }.getOrDefault(emptyList())
        }
    }

    /** 앱 안에서 바로 물을 권한 (비면 설정 화면). */
    fun permissionsFor(issue: RecordingIssue): List<String> = platform.permissionsFor(issue)

    /** 권한을 받은 뒤·"다시 켜기": 기록 서비스를 켜고(여러 번 불러도 하나) 상태를 다시 읽는다. */
    fun restart() {
        viewModelScope.launch(Dispatchers.IO) {
            RecordingController.ensureStarted(context)
            refresh()
        }
    }

    /** 기록 모두 지우기 (동의 철회). 주행 중이면 아무것도 하지 않는다. 끝나면 앱을 첫 화면부터 다시 띄운다. */
    fun wipeAll() {
        if (driving.value || !wiping.compareAndSet(false, true)) return
        viewModelScope.launch {
            val ok = runCatching { dataWipe.wipeAll() }.isSuccess
            if (ok) dataWipe.restartApp() else wiping.value = false
        }
    }

    private companion object {
        const val SETTLE_MS = 3_000L
    }
}

/**
 * 머리에 두는 작은 기록 상태 표시: 점 + 짧은 말 ("기록 중"은 점이 천천히 숨 쉰다). 누르면 설명 시트.
 * 차 화면 · 폰 화면 머리 오른쪽에 둔다. 차는 글자 20sp(caption)로 작게, 누르는 자리는 76dp를 지킨다.
 */
@Composable
fun RecordingStatusChip(
    modifier: Modifier = Modifier,
    /** false면 누를 수 없는 표시만, 글자는 크게 (주행 중 화면: 한눈에 보이고 자세한 상태 창은 열지 않는다) */
    interactive: Boolean = true,
    /** true면 평소(시동을 기다리는 중)에는 아무것도 그리지 않는다: 기록 중이거나 설정이 꺼져 있을 때만 보인다 */
    quietWhenWaiting: Boolean = false,
    /** true면 경고가 아닐 때 점만 (좁은 창 머리: 글자가 검색 칸 자리를 먹었다). 읽어 주기 설명은 그대로 */
    dotOnly: Boolean = false,
    viewModel: RecordingStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val headline = state.headline
    if (quietWhenWaiting && headline == StatusHeadline.WAITING) return
    val label = stringResource(headline.short())
    val c = EodiTheme.colors
    val warn = headline.isWarning()
    val a11y = stringResource(R.string.status_a11y, label)
    Box(
        modifier
            .heightIn(min = EodiTheme.space.touch)
            .widthIn(min = EodiTheme.space.touch)
            .clip(CircleShape)
            .then(if (interactive) Modifier.clickable(role = Role.Button) { open = true } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                // 머리의 상태 표시는 버튼처럼 보이지 않게 점 + 글자만 (경고일 때만 옅은 바탕). 누르면 설명은 그대로 열린다.
                .background(if (warn) c.staySoft else if (interactive) Color.Transparent else c.accentSoft, CircleShape)
                .padding(
                    start = if (interactive) 16.dp else 24.dp,
                    end = if (interactive) 20.dp else 30.dp,
                    top = if (interactive) 10.dp else 16.dp,
                    bottom = if (interactive) 10.dp else 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 주행 화면(누를 수 없는 표시)에서는 숨 쉬는 점을 멈춘다: 주행 중 반복 애니메이션 최소화 (Pleos 주행 안전 설계 가이드)
            StatusDot(headline, if (interactive) 14.dp else 20.dp, pulse = interactive)
            if (!(dotOnly && !warn)) {
            Spacer(Modifier.width(if (interactive) 10.dp else 14.dp))
            Text(
                label,
                // 주행 중 표시는 팔 길이에서 한눈에 (28sp 굵게, 파란 글자)
                style = if (interactive) EodiTheme.type.caption else EodiTheme.type.bodyStrong,
                fontWeight = if (warn || !interactive) FontWeight.Bold else FontWeight.SemiBold,
                color = when {
                    warn -> c.textPrimary
                    !interactive -> c.accent
                    else -> c.textSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            }
        }
    }
    if (open) {
        RecordingStatusSheet(state, viewModel, onClose = { open = false })
    }
}

/** 상태 점: 기록 중 = 파랑(길 색)이 숨 쉼, 대기 = 초록(지금), 경고 = 코랄. */
@Composable
private fun StatusDot(headline: StatusHeadline, size: androidx.compose.ui.unit.Dp = 14.dp, pulse: Boolean = true) {
    val c = EodiTheme.colors
    val color = when {
        headline == StatusHeadline.DRIVING -> c.accent
        headline.isWarning() -> c.stay
        else -> c.here
    }
    if (pulse && headline == StatusHeadline.DRIVING) {
        // 값은 그리기 단계에서만 읽는다 (graphicsLayer) → 매 프레임 재구성 없이 투명도만 바뀐다
        val pulse = rememberInfiniteTransition(label = "rec").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "rec-dot",
        )
        Box(Modifier.size(size).graphicsLayer { alpha = pulse.value }.background(color, CircleShape))
    } else {
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

/**
 * 설명 시트: 지금 상태 한 줄 + 왜 그런지 + 확인 항목(켜짐/꺼짐) + 고치기 버튼 하나.
 * 폰은 아래에서 올라오는 시트(LocalOverlayPresenter), 차는 화면 가운데 카드(대화상자 창 — 머리 안에서 불러도 화면 전체를 덮게).
 */
@Composable
private fun RecordingStatusSheet(state: RecordingStatusUi, viewModel: RecordingStatusViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val headline = state.headline
    val issue = headline.issue()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // 다시 묻지 않음을 고른 경우 등: 창이 안 뜨고 바로 거절로 온다 → 설정 화면으로
        if (result.isNotEmpty() && result.values.none { it } && issue != null) context.openFixSettings(issue)
        viewModel.restart()
    }
    val fix: Pair<Int, () -> Unit>? = when {
        headline == StatusHeadline.OFF -> R.string.status_fix_restart to { viewModel.restart() }
        issue == null -> null
        issue == RecordingIssue.BATTERY_OPTIMIZED -> R.string.status_fix_battery to { context.openFixSettings(issue) }
        viewModel.permissionsFor(issue).isNotEmpty() ->
            R.string.status_fix_permission to { launcher.launch(viewModel.permissionsFor(issue).toTypedArray()) }
        else -> R.string.status_fix_settings to { context.openFixSettings(issue) }
    }
    val driving by viewModel.driving.collectAsStateWithLifecycle()
    val wiping by viewModel.wiping.collectAsStateWithLifecycle()
    var confirmWipe by remember { mutableStateOf(false) }
    val content: @Composable BoxScope.() -> Unit = {
        StatusSheetContent(state, fix, onClose, wipeEnabled = !driving && !wiping, onWipe = { confirmWipe = true })
    }
    if (confirmWipe && !driving) {
        ConfirmWipe(wiping = wiping, onWipe = viewModel::wipeAll, onCancel = { confirmWipe = false })
    }
    if (LocalOverlayPresenter.current != null) {
        OverlayCard(onDismiss = onClose, content = content)
    } else {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = RoundedCornerShape(EodiTheme.space.radiusL),
                color = EodiTheme.colors.surface,
                contentColor = EodiTheme.colors.textPrimary,
                modifier = Modifier.widthIn(max = 960.dp).padding(32.dp),
            ) {
                Box(content = content)
            }
        }
    }
}

@Composable
private fun StatusSheetContent(
    state: RecordingStatusUi,
    fix: Pair<Int, () -> Unit>?,
    onClose: () -> Unit,
    wipeEnabled: Boolean,
    onWipe: () -> Unit,
) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val headline = state.headline
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(headline)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(headline.title()), style = type.title, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Text(stringResource(headline.body()), style = type.body, color = c.textSecondary)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(EodiTheme.space.radiusM)).background(c.surfaceHigh).padding(vertical = 8.dp)) {
            CheckRow(
                stringResource(R.string.status_check_service),
                stringResource(
                    when (state.phase) {
                        RecordingPhase.DRIVING -> R.string.status_driving
                        RecordingPhase.WAITING -> R.string.status_waiting
                        RecordingPhase.OFF -> R.string.status_off
                    },
                ),
                ok = state.phase != RecordingPhase.OFF,
            )
            state.vehicleLinked?.let { linked ->
                CheckRow(
                    stringResource(R.string.status_check_vehicle),
                    stringResource(if (linked) R.string.status_vehicle_ok else R.string.status_vehicle_wait),
                    ok = linked,
                )
            }
            state.checked.forEach { i ->
                val ok = i !in state.issues
                CheckRow(stringResource(i.checkLabel()), stringResource(if (ok) R.string.status_ok else R.string.status_bad), ok)
            }
        }
        // 개인정보 · 데이터 활용 원칙 (Pleos 보안 · 개인정보 가이드: 익명화 · 마케팅 금지). README "개인정보" 절과 같은 내용
        Text(stringResource(R.string.status_privacy_title), style = type.label, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.status_privacy_body), style = type.body, color = c.textSecondary)
        Spacer(Modifier.size(4.dp))
        if (fix != null) {
            PrimaryPill(stringResource(fix.first), null, fix.second, Modifier.fillMaxWidth())
        }
        SecondaryPill(stringResource(R.string.status_close), null, onClose, Modifier.fillMaxWidth())
        // 동의 철회 = 기록 모두 지우기. 되돌릴 수 없어 맨 아래에 빨간 글자로 두고, 주행 중에는 막는다
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.wipe_section), style = type.label, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(if (wipeEnabled) R.string.wipe_hint else R.string.wipe_hint_driving),
            style = type.body,
            color = c.textSecondary,
        )
        PressSurface(
            onClick = onWipe,
            enabled = wipeEnabled,
            shape = CircleShape,
            color = c.error.copy(alpha = if (wipeEnabled) 0.12f else 0.05f),
            contentColor = c.error.copy(alpha = if (wipeEnabled) 1f else 0.4f),
            modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.wipe_button), style = type.bodyStrong, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "기록 모두 지우기" 확인 창 (지우기 확인과 같은 모양: 빨간 원 아이콘 + 제목 + 설명 + [취소][지우기]). */
@Composable
private fun ConfirmWipe(wiping: Boolean, onWipe: () -> Unit, onCancel: () -> Unit) {
    val c = EodiTheme.colors
    OverlayCard(onDismiss = { if (!wiping) onCancel() }, maxWidth = 900.dp) {
        Column(Modifier.padding(32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).background(c.error.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(PlacesIcons.Trash, null, tint = c.error, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(20.dp))
                Text(stringResource(R.string.wipe_title), style = EodiTheme.type.title, modifier = Modifier.weight(1f).semantics { heading() })
            }
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.wipe_body), style = EodiTheme.type.body, color = c.textSecondary)
            Spacer(Modifier.size(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryPill(stringResource(R.string.wipe_cancel), null, onCancel, Modifier.weight(1f), enabled = !wiping)
                PressSurface(
                    onClick = onWipe,
                    enabled = !wiping,
                    shape = CircleShape,
                    color = c.error,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f).heightIn(min = 76.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (wiping) R.string.wipe_running else R.string.wipe_confirm),
                            style = EodiTheme.type.bodyStrong,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, value: String, ok: Boolean) {
    val c = EodiTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = EodiTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = if (ok) c.here else c.stay, maxLines = 1)
    }
}

private fun StatusHeadline.isWarning(): Boolean = this != StatusHeadline.DRIVING && this != StatusHeadline.WAITING

private fun StatusHeadline.issue(): RecordingIssue? = when (this) {
    StatusHeadline.LOCATION_OFF -> RecordingIssue.LOCATION_OFF
    StatusHeadline.BACKGROUND_OFF -> RecordingIssue.BACKGROUND_OFF
    StatusHeadline.NOTIFICATIONS_OFF -> RecordingIssue.NOTIFICATIONS_OFF
    StatusHeadline.BATTERY_OPTIMIZED -> RecordingIssue.BATTERY_OPTIMIZED
    else -> null
}

private fun StatusHeadline.short(): Int = when (this) {
    StatusHeadline.DRIVING -> R.string.status_driving
    StatusHeadline.WAITING -> R.string.status_waiting
    StatusHeadline.OFF -> R.string.status_off
    StatusHeadline.LOCATION_OFF -> R.string.status_location_off
    StatusHeadline.BACKGROUND_OFF -> R.string.status_background_off
    StatusHeadline.NOTIFICATIONS_OFF -> R.string.status_notifications_off
    StatusHeadline.BATTERY_OPTIMIZED -> R.string.status_battery
    StatusHeadline.NO_VEHICLE_SIGNAL -> R.string.status_no_vehicle
}

private fun StatusHeadline.title(): Int = when (this) {
    StatusHeadline.DRIVING -> R.string.status_title_driving
    StatusHeadline.WAITING -> R.string.status_title_waiting
    StatusHeadline.OFF -> R.string.status_title_off
    StatusHeadline.LOCATION_OFF -> R.string.status_title_location_off
    StatusHeadline.BACKGROUND_OFF -> R.string.status_title_background_off
    StatusHeadline.NOTIFICATIONS_OFF -> R.string.status_title_notifications_off
    StatusHeadline.BATTERY_OPTIMIZED -> R.string.status_title_battery
    StatusHeadline.NO_VEHICLE_SIGNAL -> R.string.status_title_no_vehicle
}

private fun StatusHeadline.body(): Int = when (this) {
    StatusHeadline.DRIVING -> R.string.status_body_driving
    StatusHeadline.WAITING -> R.string.status_body_waiting
    StatusHeadline.OFF -> R.string.status_body_off
    StatusHeadline.LOCATION_OFF -> R.string.status_body_location_off
    StatusHeadline.BACKGROUND_OFF -> R.string.status_body_background_off
    StatusHeadline.NOTIFICATIONS_OFF -> R.string.status_body_notifications_off
    StatusHeadline.BATTERY_OPTIMIZED -> R.string.status_body_battery
    StatusHeadline.NO_VEHICLE_SIGNAL -> R.string.status_body_no_vehicle
}

private fun RecordingIssue.checkLabel(): Int = when (this) {
    RecordingIssue.LOCATION_OFF -> R.string.status_check_location
    RecordingIssue.BACKGROUND_OFF -> R.string.status_check_background
    RecordingIssue.NOTIFICATIONS_OFF -> R.string.status_check_notifications
    RecordingIssue.BATTERY_OPTIMIZED -> R.string.status_check_battery
}

/** 고칠 수 있는 시스템 화면: 배터리 → 최적화 예외 요청 창(안 되면 목록), 알림 → 앱 알림 설정, 그 밖 → 앱 정보(권한). */
private fun Context.openFixSettings(issue: RecordingIssue) {
    val pkg = Uri.fromParts("package", packageName, null)
    val tries = when (issue) {
        RecordingIssue.BATTERY_OPTIMIZED -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        RecordingIssue.NOTIFICATIONS_OFF -> listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
        )
        else -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
    }
    for (intent in tries) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
        } catch (e: SecurityException) {
        }
    }
}

private const val PULSE_MS = 1_100
