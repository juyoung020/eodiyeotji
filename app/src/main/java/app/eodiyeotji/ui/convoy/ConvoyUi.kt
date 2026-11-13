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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val text = if (active) stringResource(R.string.convoy_sharing_count, s.members.size + 1) else stringResource(R.string.convoy_title)
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

/** 주행 중 큰 카드 1장: "민수 차 · 2.1km 뒤 · 도착 5분 차이" + 위치 공유 중. 같이 가는 중이 아니면 아무것도 그리지 않는다. */
@Composable
fun ConvoyDrivingCard(modifier: Modifier = Modifier, vm: ConvoyViewModel = hiltViewModel()) {
    val s by vm.repo.state.collectAsStateWithLifecycle()
    if (s.phase != ConvoyPhase.ACTIVE) return
    val c = EodiTheme.colors
    val f = s.focus
    val narrow = LocalConfiguration.current.screenWidthDp < NARROW_DP
    val scope = rememberCoroutineScope()
    val showStop = s.low != null && s.waypoint == null
    Surface(color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = modifier) {
      Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (f != null) {
                Puck(f, 72)
                Spacer(Modifier.width(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    f?.card ?: stringResource(R.string.convoy_waiting),
                    style = EodiTheme.type.drivingBody,
                    // 잔량 부족 · 끊김은 한눈에 (빨강 = 부족, 흐림 = 소식 끊김)
                    color = when {
                        f?.low == true && !f.stale -> c.error
                        f?.stale == true -> c.textSecondary
                        else -> c.textPrimary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.convoy_sharing_count, s.members.size + 1),
                    style = EodiTheme.type.label,
                    color = c.accent,
                )
            }
            // 누가 잔량이 부족하면 한 번 누르기로 모두 같이 들를 곳을 정한다 (주행 중에도 큰 버튼 하나만)
            if (showStop && !narrow) {
                Spacer(Modifier.width(24.dp))
                BigButton(stringResource(R.string.convoy_stop_together), primary = true, modifier = Modifier.width(320.dp)) {
                    scope.launch { vm.repo.stopTogether() }
                }
            }
        }
        // 좁은 창(세로 · 나눈 화면)에서는 버튼을 글 아래 한 줄로 (옆에 두면 글이 잘렸다)
        if (showStop && narrow) {
            BigButton(stringResource(R.string.convoy_stop_together), primary = true, modifier = Modifier.fillMaxWidth()) {
                scope.launch { vm.repo.stopTogether() }
            }
        }
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
                .fillMaxHeight()
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
                modifier = Modifier.weight(1f),
            )
            BigButton(stringResource(R.string.convoy_join), enabled = (busy && joining) || (!busy && code.length == 6), loading = busy && joining, modifier = Modifier.weight(0.7f)) {
                joining = true
                repo.join(code, name.trim())
            }
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
    // 넓은 창: [지도 | 정보]. 좁은 창(세로 · 나눈 화면): 위 지도 · 아래 정보 (옆으로 두면 정보 칸이 잘렸다)
    val narrow = LocalConfiguration.current.screenWidthDp < NARROW_DP
    val r = EodiTheme.space.radiusL
    if (narrow) {
        Column(Modifier.fillMaxSize()) {
            ConvoyMap(
                s.geojson,
                s.bounds,
                Modifier.fillMaxWidth().fillMaxHeight(0.32f)
                    .background(c.surfaceHigh, RoundedCornerShape(topStart = r, topEnd = r))
                    .semantics { contentDescription = mapDesc },
            )
            ActiveInfo(s, repo, Modifier.weight(1f).fillMaxWidth().padding(24.dp), compact = true)
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            ConvoyMap(
                s.geojson,
                s.bounds,
                Modifier.weight(1.4f).fillMaxHeight()
                    .background(c.surfaceHigh, RoundedCornerShape(topStart = r, bottomStart = r))
                    .semantics { contentDescription = mapDesc },
            )
            ActiveInfo(s, repo, Modifier.weight(1f).fillMaxHeight().padding(32.dp))
        }
    }
}

@Composable
private fun ActiveInfo(s: ConvoyUiState, repo: ConvoyRepository, modifier: Modifier, compact: Boolean = false) {
    val c = EodiTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var navi by remember { mutableStateOf<Boolean?>(null) }
    run {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(c.accent, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.convoy_sharing), style = EodiTheme.type.label, color = c.accent, modifier = Modifier.weight(1f))
                // 좁은 창: 참여 코드를 머리 줄에 (큰 코드 글자가 차 목록 자리를 먹어 두 번째 차부터 잘렸다)
                if (compact) {
                    Text(stringResource(R.string.convoy_code), style = EodiTheme.type.meta, color = c.textTertiary)
                    Spacer(Modifier.width(8.dp))
                    Text(s.code, style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1)
                    Spacer(Modifier.width(16.dp))
                }
                Pill(stringResource(R.string.convoy_close), onClick = repo::closeSheet)
            }
            if (!compact) {
                Text(stringResource(R.string.convoy_code), style = EodiTheme.type.eyebrow, color = c.textTertiary)
                Text(s.code, style = EodiTheme.type.display, color = c.textPrimary, maxLines = 1)
            }
            Text(
                s.dest?.let { stringResource(R.string.convoy_dest, it.name) } ?: stringResource(R.string.convoy_dest_none),
                style = EodiTheme.type.body,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            s.waypoint?.let { Text(stringResource(R.string.convoy_waypoint, it.name), style = EodiTheme.type.body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            s.stopNotice?.let { Text(stringResource(it), style = EodiTheme.type.caption, color = c.textSecondary) }
            if (s.offline) Text(stringResource(R.string.convoy_offline), style = EodiTheme.type.meta, color = c.error)
            if (s.local) Text(stringResource(R.string.convoy_local), style = EodiTheme.type.caption, color = c.textTertiary)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.members.isEmpty()) {
                    item(key = "empty") { Text(stringResource(R.string.convoy_waiting), style = EodiTheme.type.body, color = c.textSecondary) }
                }
                items(s.members, key = { it.id }) { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Puck(m, 52)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            // 끊긴 차는 흐리게 (잔량 부족이어도 지금 값이 아니므로 빨강 대신 흐림 — 주행 카드와 같은 규칙)
                            Text(m.card, style = EodiTheme.type.bodyStrong, color = when { m.stale -> c.textSecondary; m.low -> c.error; else -> c.textPrimary }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            // 정차 중에는 모든 차의 잔량을 보인다 (주행 중 · 지도에는 부족할 때만)
                            if (m.energy.isNotEmpty()) Text(m.energy, style = EodiTheme.type.meta, color = if (m.low) c.error else c.textTertiary)
                        }
                    }
                }
            }
            navi?.let {
                Text(
                    stringResource(if (it) R.string.convoy_navi_ok else R.string.convoy_navi_fail),
                    style = EodiTheme.type.caption,
                    color = if (it) c.textSecondary else c.error,
                )
            }
            if (s.low != null && s.waypoint == null) {
                BigButton(stringResource(R.string.convoy_stop_together), modifier = Modifier.fillMaxWidth()) {
                    scope.launch { navi = repo.stopTogether() }
                }
            }
            BigButton(stringResource(R.string.convoy_navigate), primary = true, enabled = s.dest != null, modifier = Modifier.fillMaxWidth()) {
                scope.launch { navi = repo.navigate() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val shareText = stringResource(R.string.convoy_share_text, s.code, s.dest?.name ?: "-")
                val chooser = stringResource(R.string.convoy_share)
                BigButton(chooser, modifier = Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    runCatching { context.startActivity(Intent.createChooser(send, chooser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                BigButton(stringResource(R.string.convoy_leave), modifier = Modifier.weight(1f), onClick = repo::leave)
            }
        }
    }
}

/** 다른 차 점: 색 원 + 이름 첫 글자 (지도와 같은 색). 끊긴 차는 흐리게. */
@Composable
private fun Puck(m: ConvoyMemberView, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(Color(ConvoyColors.of(m.color.toInt())).copy(alpha = if (m.stale) 0.45f else 1f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(m.initial, style = if (sizeDp >= 64) EodiTheme.type.drivingBody else EodiTheme.type.bodyStrong, color = Color.White, fontWeight = FontWeight.Bold)
    }
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
                color = if (enabled) Color.Unspecified else c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
