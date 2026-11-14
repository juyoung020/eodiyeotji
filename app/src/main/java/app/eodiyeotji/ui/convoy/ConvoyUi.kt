package app.eodiyeotji.ui.convoy

import app.eodiyeotji.ui.common.PressSurface
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import app.eodiyeotji.core.rust.ConvoyEnergy
import app.eodiyeotji.ui.share.ShareIcon
import app.eodiyeotji.ui.theme.EodiIcons
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.convoy.ConvoyPhase
import app.eodiyeotji.convoy.ConvoyRepository
import app.eodiyeotji.convoy.ConvoyUiState
import app.eodiyeotji.core.rust.ConvoyMemberView
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** 이보다 좁은 창(dp)은 세로로 쌓는다 (세로 화면 · 나눈 화면). */
private const val NARROW_DP = 1100

@HiltViewModel
class ConvoyViewModel @Inject constructor(val repo: ConvoyRepository) : ViewModel()

/**
 * 머리의 작은 표시: 같이 가기 전에는 "같이 가기"(누르면 창), 같이 가는 중에는 "위치 공유 중 · N대"(점 하나 + 누르면 창).
 */
@Composable
fun ConvoyEntryChip(modifier: Modifier = Modifier, vm: ConvoyViewModel = hiltViewModel()) {
    val s by vm.repo.state.collectAsStateWithLifecycle()
    val c = EodiTheme.colors
    val active = s.phase == ConvoyPhase.ACTIVE
    // 위치가 끊긴 차는 "공유 중" 대수에서 뺀다 (가짜 차를 멈춘 뒤에도 "3대"로 남았다)
    val text = if (active) stringResource(R.string.convoy_sharing_count, s.members.count { !it.stale } + 1) else stringResource(R.string.convoy_title)
    Row(
        modifier
            .heightIn(min = 64.dp)
            .background(if (active) c.accentSoft else c.surface, RoundedCornerShape(32.dp))
            .clickable(onClick = vm.repo::openSheet)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Box(Modifier.size(12.dp).background(c.accent, CircleShape))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = EodiTheme.type.label, color = if (active) c.accent else c.textPrimary, maxLines = 1)
    }
}

/**
 * 주행 중 큰 카드 1장: 같이 가는 차 모두를 한 줄에 (아바타 · 간격 · 작은 잔량 막대). 부족한 차는 빨강.
 * 누가 부족하면 오른쪽에 큰 버튼 하나 ("같이 충전소 들르기"). 같이 가는 중이 아니면 아무것도 그리지 않는다.
 * 대열이 떨어지면 차 줄 대신 제안 한 줄 + 큰 버튼 "○○휴게소에서 기다리기" (Rust가 고른 앞 휴게소, 음성으로도 묻는다).
 */
@Composable
fun ConvoyDrivingCard(modifier: Modifier = Modifier, vm: ConvoyViewModel = hiltViewModel()) {
    val s by vm.repo.state.collectAsStateWithLifecycle()
    if (s.phase != ConvoyPhase.ACTIVE) return
    val c = EodiTheme.colors
    val narrow = LocalConfiguration.current.screenWidthDp < NARROW_DP
    val scope = rememberCoroutineScope()
    val wait = s.wait
    val showStop = wait == null && s.low != null && s.waypoint == null
    val live = s.members.count { !it.stale } + 1
    Surface(color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = modifier) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.convoy_title) + " · " + stringResource(R.string.convoy_together_count, live),
                    style = EodiTheme.type.label,
                    color = c.accent,
                    modifier = Modifier.weight(1f),
                )
                // 모두가 들를 휴게소 · 충전소를 한눈에 (같이 가기 핵심: 어디서 쉬는지 공유)
                s.waypoint?.let { wp ->
                    Text(
                        stringResource(R.string.convoy_drive_stop, wp.name),
                        style = EodiTheme.type.label,
                        fontWeight = FontWeight.Bold,
                        color = c.onAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.background(c.accent, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            if (wait != null) {
                WaitRow(wait.card, wait.action, narrow, onWait = { scope.launch { vm.repo.waitAtRestArea() } }, onDismiss = vm.repo::dismissWait)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (s.members.isEmpty()) {
                    Text(stringResource(R.string.convoy_waiting), style = EodiTheme.type.bodyStrong, color = c.textSecondary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    s.members.take(if (narrow) 2 else 4).forEach { m -> DriveMember(m, Modifier.weight(1f)) }
                }
                if (showStop && !narrow) {
                    BigButton(stringResource(R.string.convoy_stop_together), primary = true, modifier = Modifier.width(300.dp)) {
                        scope.launch { vm.repo.stopTogether() }
                    }
                }
            }
            // 좁은 창(세로 · 나눈 화면)에서는 버튼을 아래 한 줄로
            if (showStop && narrow) {
                BigButton(stringResource(R.string.convoy_stop_together), primary = true, modifier = Modifier.fillMaxWidth()) {
                    scope.launch { vm.repo.stopTogether() }
                }
            }
        }
    }
}

/**
 * 휴게소 대기 제안: 한 줄 ("지현 차와 4.6km 멀어졌어요 · 7.2km 앞 기흥휴게소") + 큰 주 버튼 + "괜찮아요".
 * 넓은 창은 [글 | 괜찮아요 | 기다리기], 좁은 창은 글 아래 버튼 줄.
 */
@Composable
private fun WaitRow(card: String, action: String, narrow: Boolean, onWait: () -> Unit, onDismiss: () -> Unit) {
    val c = EodiTheme.colors
    val text = @Composable { m: Modifier ->
        Text(card, style = EodiTheme.type.drivingBody, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = m)
    }
    if (narrow) {
        text(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TonalButton(stringResource(R.string.convoy_wait_dismiss), modifier = Modifier.weight(0.6f), onClick = onDismiss)
            BigButton(action, primary = true, modifier = Modifier.weight(1f), onClick = onWait)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            text(Modifier.weight(1f))
            TonalButton(stringResource(R.string.convoy_wait_dismiss), onClick = onDismiss)
            BigButton(action, primary = true, modifier = Modifier.widthIn(min = 320.dp, max = 420.dp), onClick = onWait)
        }
    }
}

/** 주행 카드의 차 한 대: 아바타 + 간격(큰 글) + 작은 잔량 막대. 부족 = 옅은 빨강 판, 끊김 = 흐림. */
@Composable
private fun DriveMember(m: ConvoyMemberView, modifier: Modifier) {
    val c = EodiTheme.colors
    val low = m.low && !m.stale
    Row(
        modifier
            .background(if (low) c.error.copy(alpha = 0.10f) else c.background, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(m.initial, memberColor(m), 64, dim = m.stale, warn = low)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when {
                    m.stale -> stringResource(R.string.convoy_stale_min, m.staleMin.toInt())
                    m.rel.isNotEmpty() -> m.rel
                    else -> m.name
                },
                style = EodiTheme.type.drivingBody,
                color = when { m.stale -> c.textTertiary; low -> c.error; else -> c.textPrimary },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            EnergyBar(m.gauge, low = low, dim = m.stale, color = memberColor(m), compact = true)
        }
    }
}

/** 같이 가기 창 (정차 중에만, 화면 위에 덮는다): 만들기 · 참여 / 지도 + 차 목록 + 코드 · 안내 · 나가기. */
@Composable
fun ConvoyHost(driving: Boolean, vm: ConvoyViewModel = hiltViewModel()) {
    val s by vm.repo.state.collectAsStateWithLifecycle()
    if (!s.sheetOpen || driving) return
    val c = EodiTheme.colors
    BackHandler(onBack = vm.repo::closeSheet)
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = vm.repo::closeSheet),
    ) {
        Surface(
            color = c.surface,
            shape = RoundedCornerShape(EodiTheme.space.radiusL),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(if (LocalConfiguration.current.screenWidthDp < NARROW_DP) 16.dp else 32.dp)
                .fillMaxWidth()
                // 만들기 · 참여 단계는 내용 높이만 (화면 전체 높이면 아래 3/4이 빈 흰 판이었다). 지도가 있는 같이 가는 중은 전체
                .then(if (s.phase == ConvoyPhase.ACTIVE) Modifier.fillMaxHeight() else Modifier)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            when (s.phase) {
                ConvoyPhase.ACTIVE -> ActivePane(s, vm.repo)
                else -> StartPane(s, vm.repo)
            }
        }
    }
}

@Composable
private fun StartPane(s: ConvoyUiState, repo: ConvoyRepository) {
    val c = EodiTheme.colors
    val defaultName = stringResource(R.string.convoy_default_name)
    var name by rememberSaveable { mutableStateOf(s.name.ifEmpty { defaultName }) }
    var code by rememberSaveable { mutableStateOf("") }
    val busy = s.phase == ConvoyPhase.CONNECTING
    // 누른 버튼 안에 로더 (만들기 / 들어가기)
    var joining by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.convoy_title), style = EodiTheme.type.title, color = c.textPrimary, modifier = Modifier.weight(1f))
            Pill(stringResource(R.string.convoy_close), onClick = repo::closeSheet)
        }
        Text(
            s.dest?.let { stringResource(R.string.convoy_dest, it.name) } ?: stringResource(R.string.convoy_dest_none),
            style = EodiTheme.type.body,
            color = c.textSecondary,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(12) },
            label = { Text(stringResource(R.string.convoy_name_label)) },
            singleLine = true,
            textStyle = EodiTheme.type.body,
            modifier = Modifier.fillMaxWidth(),
        )
        val narrow = LocalConfiguration.current.screenWidthDp < NARROW_DP
        // 참여: 버튼과 키보드 "이동"이 같이 쓴다
        val join = {
            if (!busy && code.length == 6) {
                joining = true
                repo.join(code, name.trim())
            }
        }
        if (narrow) {
            // 좁은 창: [새로 만들기] 한 줄, 아래 [코드 칸 | 코드로 참여]
            BigButton(stringResource(R.string.convoy_create), primary = true, enabled = !busy || !joining, loading = busy && !joining, modifier = Modifier.fillMaxWidth()) {
                joining = false
                repo.create(name.trim(), s.dest)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!narrow) BigButton(stringResource(R.string.convoy_create), primary = true, enabled = !busy || !joining, loading = busy && !joining, modifier = Modifier.weight(1f)) {
                joining = false
                repo.create(name.trim(), s.dest)
            }
            // 코드는 숫자 6자리 → 숫자 키보드 (한글 입력기와 섞이지 않게)
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.convoy_code_label)) },
                singleLine = true,
                textStyle = EodiTheme.type.titleSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
                // 키보드의 "이동"으로도 참여 (전에는 눌러도 아무 일이 없었다)
                keyboardActions = KeyboardActions(onGo = { join() }),
                modifier = Modifier.weight(1f),
            )
            BigButton(stringResource(R.string.convoy_join), enabled = (busy && joining) || (!busy && code.length == 6), loading = busy && joining, modifier = Modifier.weight(0.7f), onClick = join)
        }
        when {
            busy -> Text(stringResource(R.string.convoy_connecting), style = EodiTheme.type.body, color = c.textSecondary)
            s.error != null -> Text(stringResource(s.error), style = EodiTheme.type.body, color = c.error)
        }
        Text(stringResource(R.string.convoy_privacy), style = EodiTheme.type.meta, color = c.textTertiary)
        if (s.local) Text(stringResource(R.string.convoy_local), style = EodiTheme.type.caption, color = c.textTertiary)
    }
}

@Composable
private fun ActivePane(s: ConvoyUiState, repo: ConvoyRepository) {
    val c = EodiTheme.colors
    val mapDesc = stringResource(R.string.convoy_map_description)
    // 넓은 창: [지도 | 정보]. 좁은 창(세로 · 나눈 화면): 위 지도 · 아래 정보
    val narrow = LocalConfiguration.current.screenWidthDp < NARROW_DP
    val r = EodiTheme.space.radiusL
    if (narrow) {
        Column(Modifier.fillMaxSize()) {
            ConvoyMap(
                s.geojson,
                s.bounds,
                s.members,
                Modifier.fillMaxWidth().fillMaxHeight(0.32f)
                    .background(c.surfaceHigh, RoundedCornerShape(topStart = r, topEnd = r))
                    .semantics { contentDescription = mapDesc },
            )
            ActiveInfo(s, repo, Modifier.weight(1f).fillMaxWidth().padding(20.dp))
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            ConvoyMap(
                s.geojson,
                s.bounds,
                s.members,
                Modifier.weight(1.35f).fillMaxHeight()
                    .background(c.surfaceHigh, RoundedCornerShape(topStart = r, bottomStart = r))
                    .semantics { contentDescription = mapDesc },
            )
            ActiveInfo(s, repo, Modifier.weight(1f).fillMaxHeight().padding(28.dp))
        }
    }
}

@Composable
private fun ActiveInfo(s: ConvoyUiState, repo: ConvoyRepository, modifier: Modifier) {
    val c = EodiTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var navi by remember { mutableStateOf<Boolean?>(null) }
    val shareText = stringResource(R.string.convoy_share_text, s.code, s.dest?.name ?: "-")
    val chooser = stringResource(R.string.convoy_share)
    val share: () -> Unit = {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
        runCatching { context.startActivity(Intent.createChooser(send, chooser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val navigate: () -> Unit = { scope.launch { navi = repo.navigate() } }
    val stop: () -> Unit = { scope.launch { navi = repo.stopTogether() } }
    val waitAt: () -> Unit = { scope.launch { navi = repo.waitAtRestArea() } }
    val wait = s.wait
    val lowCase = wait == null && s.low != null && s.waypoint == null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 머리: 제목 + 살아 있는 점 "N대 함께" · 코드 칩 · 닫기
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.convoy_title), style = EodiTheme.type.title, color = c.textPrimary, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot()
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.convoy_together_count, s.members.count { !it.stale } + 1), style = EodiTheme.type.label, color = c.accent, maxLines = 1)
                }
            }
            CodeChip(s.code, onClick = share)
            Spacer(Modifier.width(12.dp))
            RoundIcon(EodiIcons.Close, stringResource(R.string.convoy_close), onClick = repo::closeSheet)
        }
        // 잠깐 보일 안내 (한 줄씩)
        if (s.offline) Note(stringResource(R.string.convoy_offline), c.error)
        s.stopNotice?.let { Note(stringResource(it), c.textSecondary) }
        navi?.let { Note(stringResource(if (it) R.string.convoy_navi_ok else R.string.convoy_navi_fail), if (it) c.textSecondary else c.error) }
        if (s.local) Note(stringResource(R.string.convoy_local), c.textTertiary)
        wait?.let { Text(it.card, style = EodiTheme.type.bodyStrong, color = c.accent, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        // 차 카드: 나 먼저, 그다음 같이 가는 차들. 남는 높이를 채우고 넘치면 스크롤
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "me") { MeCard(s.myEnergy) }
            if (s.members.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.convoy_waiting), style = EodiTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                }
            }
            items(s.members, key = { it.id }) { m -> MemberCard(m) }
        }
        DestRow(s, onPick = repo::closeSheet)
        // 주 버튼 하나 (상황에 맞게) + 아래 옅은 알약 버튼 줄
        if (wait != null) {
            BigButton(wait.action, primary = true, modifier = Modifier.fillMaxWidth(), onClick = waitAt)
        } else if (lowCase) {
            BigButton(stringResource(R.string.convoy_stop_together), primary = true, modifier = Modifier.fillMaxWidth(), onClick = stop)
        } else {
            BigButton(stringResource(R.string.convoy_navigate), primary = true, enabled = s.dest != null, modifier = Modifier.fillMaxWidth(), onClick = navigate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (wait != null) TonalButton(stringResource(R.string.convoy_wait_dismiss), modifier = Modifier.weight(1f), onClick = repo::dismissWait)
            if (lowCase || wait != null) TonalButton(stringResource(R.string.convoy_navigate_short), enabled = s.dest != null, modifier = Modifier.weight(1f), onClick = navigate)
            TonalButton(chooser, icon = ShareIcon, modifier = Modifier.weight(1f), onClick = share)
            TonalButton(stringResource(R.string.convoy_leave), modifier = Modifier.weight(1f), onClick = repo::leave)
        }
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, style = EodiTheme.type.caption, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 목적지 한 줄: 없으면 짧은 안내 + "목적지 정하기", 있으면 이름 (+ 함께 들를 곳). */
@Composable
private fun DestRow(s: ConvoyUiState, onPick: () -> Unit) {
    val c = EodiTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).background(c.background, RoundedCornerShape(24.dp)).padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EodiIcons.Pin, null, tint = if (s.dest != null) c.accent else c.textTertiary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        val d = s.dest
        if (d == null) {
            Text(stringResource(R.string.convoy_dest_empty), style = EodiTheme.type.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(
                Modifier.heightIn(min = 76.dp).clip(RoundedCornerShape(38.dp)).clickable(onClick = onPick).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.convoy_dest_pick), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.accent, maxLines = 1)
            }
        } else {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(d.name, style = EodiTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 다른 차가 휴게소를 올렸으면 "선두가 ○○휴게소에서 기다려요" (Rust convoy_waypoint_note), 아니면 "함께 들를 곳 · ○○"
                s.waypoint?.let {
                    Text(
                        s.waypointNote ?: stringResource(R.string.convoy_waypoint, it.name),
                        style = EodiTheme.type.meta,
                        color = if (s.waypointNote != null) c.accent else c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 내 차 카드 (맨 위, 앱 파랑). */
@Composable
private fun MeCard(e: ConvoyEnergy?) {
    val c = EodiTheme.colors
    CardShell(low = false) {
        Avatar(stringResource(R.string.convoy_me), c.accent, 68)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.convoy_my_car), style = EodiTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1)
            EnergyBar(e, low = false, dim = false, color = c.accent)
        }
    }
}

/** 같이 가는 차 카드: 아바타 · 이름 · 앞뒤 간격 · 잔량 막대. 부족 = 빨강 + "부족", 끊김 = 흐림 + "N분 전". */
@Composable
private fun MemberCard(m: ConvoyMemberView) {
    val c = EodiTheme.colors
    val low = m.low && !m.stale
    CardShell(low = low) {
        Avatar(m.initial, memberColor(m), 68, dim = m.stale, warn = low)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, style = EodiTheme.type.bodyStrong, color = if (m.stale) c.textTertiary else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                when {
                    m.stale -> Text(stringResource(R.string.convoy_stale_min, m.staleMin.toInt()), style = EodiTheme.type.meta, color = c.textTertiary, maxLines = 1)
                    m.rel.isNotEmpty() -> Text(m.rel, style = EodiTheme.type.time, color = if (low) c.error else c.textPrimary, maxLines = 1)
                }
            }
            EnergyBar(m.gauge, low = low, dim = m.stale, color = memberColor(m))
        }
    }
}

@Composable
private fun CardShell(low: Boolean, content: @Composable RowScope.() -> Unit) {
    val c = EodiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp)
            .background(if (low) c.error.copy(alpha = 0.08f) else c.background, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 잔량 막대: 번개/주유기 아이콘 · 가로 막대 · "30km". 부족이면 빨강 막대 + "부족" 알약. */
@Composable
private fun EnergyBar(g: ConvoyEnergy?, low: Boolean, dim: Boolean, color: Color, compact: Boolean = false) {
    val c = EodiTheme.colors
    val frac = g?.frac ?: -1.0
    val known = frac >= 0.0
    val ev = g?.ev ?: true
    val tint = when { dim || !known -> c.textTertiary; low -> c.error; else -> color }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ev) BoltIcon else FuelIcon, stringResource(if (ev) R.string.convoy_battery else R.string.convoy_fuel), tint = tint, modifier = Modifier.size(if (compact) 24.dp else 26.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(if (compact) 10.dp else 12.dp).clip(CircleShape).background(c.outline)) {
            if (known) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac.toFloat().coerceIn(0.04f, 1f)).clip(CircleShape).background(tint.copy(alpha = if (dim) 0.5f else 1f)))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            g?.text?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.convoy_energy_unknown),
            style = EodiTheme.type.meta,
            fontWeight = FontWeight.SemiBold,
            color = when { dim -> c.textTertiary; low -> c.error; else -> c.textSecondary },
            maxLines = 1,
        )
        if (low && !compact) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.convoy_low),
                style = EodiTheme.type.caption,
                fontWeight = FontWeight.Bold,
                color = c.onAccent,
                modifier = Modifier.background(c.error, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** 차 아바타: 색 원 + 흰 테두리 + 첫 글자. 부족이면 빨간 테두리, 끊기면 흐림. */
@Composable
private fun Avatar(initial: String, color: Color, sizeDp: Int, dim: Boolean = false, warn: Boolean = false) {
    val c = EodiTheme.colors
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(if (warn) c.error else c.surface, CircleShape)
            .padding(if (warn) 4.dp else 3.dp)
            .background(color.copy(alpha = if (dim) 0.4f else 1f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = if (sizeDp >= 64) EodiTheme.type.titleSmall else EodiTheme.type.bodyStrong, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun memberColor(m: ConvoyMemberView) = Color(ConvoyColors.of(m.color.toInt()))

/** 살아 있음 점 (파랑 점 + 옅은 테두리). */
@Composable
private fun LiveDot() {
    val c = EodiTheme.colors
    Box(Modifier.size(18.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).background(c.accent, CircleShape))
    }
}

/** 참여 코드 칩: "코드 112645" + 공유 아이콘. 누르면 코드 보내기. */
@Composable
private fun CodeChip(code: String, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Row(
        Modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(c.accentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.convoy_code_chip, code), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.accent, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Icon(ShareIcon, stringResource(R.string.convoy_share), tint = c.accent, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = c.textPrimary, modifier = Modifier.size(30.dp)) }
}

@Composable
private fun BigButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val c = EodiTheme.colors
    PressSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        // 꺼진 주 버튼은 회색 바탕 (파랑 위 흐린 글자는 읽히지 않았다)
        color = if (primary && enabled) c.accent else c.surfaceHigh,
        contentColor = if (primary && enabled) c.onAccent else c.textPrimary,
        loading = loading,
        modifier = modifier.heightIn(min = 88.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp).height(88.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                app.eodiyeotji.ui.common.EodiLoader(size = 36.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = EodiTheme.type.label,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.Unspecified else c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 옅은 알약 버튼 (보조 동작). */
@Composable
private fun TonalButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = EodiTheme.colors
    PressSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(38.dp),
        color = c.surfaceHigh,
        contentColor = if (enabled) c.textPrimary else c.textTertiary,
        modifier = modifier.heightIn(min = 76.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp).height(76.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = EodiTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Text(
        text,
        style = EodiTheme.type.label,
        color = c.textPrimary,
        modifier = Modifier
            .heightIn(min = 64.dp)
            .background(c.surfaceHigh, RoundedCornerShape(32.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    )
}

private val BoltIcon: ImageVector by lazy {
    ImageVector.Builder("Bolt", 24.dp, 24.dp, 24f, 24f).addPath(
        addPathNodes("M13.5 2L4.5 13.5H11L10 22L19.5 10H13L13.5 2Z"),
        fill = SolidColor(Color.Black),
    ).build()
}

private val FuelIcon: ImageVector by lazy {
    ImageVector.Builder("Fuel", 24.dp, 24.dp, 24f, 24f).addPath(
        addPathNodes(
            "M4 21V5C4 3.9 4.9 3 6 3H13C14.1 3 15 3.9 15 5V21H4ZM7 6V10.5H12V6H7Z" +
                "M16 7.5L17.4 6.1L20.4 9.1C20.8 9.5 21 10 21 10.5V18C21 19.1 20.1 20 19 20C17.9 20 17 19.1 17 18V14.5H16V12.5H17C18.1 12.5 19 13.4 19 14.5V18H19V10.9L16 7.5Z",
        ),
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
    ).build()
}
