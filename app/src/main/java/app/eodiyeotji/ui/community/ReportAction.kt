package app.eodiyeotji.ui.community

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.ReportReason
import app.eodiyeotji.core.ReportTarget
import app.eodiyeotji.core.reportCommunityItem
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 신고 · 숨기기 (Pleos 앱 심사 가이드: 사용자 · AI가 만든 콘텐츠는 거르기 + 신고 수단).
 * 신고한 글은 Rust 저장소(`hidden_item`)에 남아 모든 피드에서 바로 빠진다. 서버(relay)에 커뮤니티 주소가 아직 없어 기기 안에만 남는다.
 * 정차 중에만 누를 수 있다 (주행 중에는 버튼이 꺼진다 — 커뮤니티 화면 자체도 주행 중엔 감춰진다).
 */
@HiltViewModel
class ReportViewModel @Inject constructor(private val core: EodiCore, driving: DrivingStatePort) : ViewModel() {
    val isDriving: StateFlow<Boolean> = driving.isDriving

    fun report(target: ReportTarget, id: String, reason: ReportReason, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                core.reportCommunityItem(target, id, reason)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("EodiReport", "신고 실패", e)
            }
        }
    }
}

/** 깃발 버튼 (터치 76dp = Pleos 터치 크기) + 누르면 이유 고르는 확인 창. 신고가 끝나면 [onHidden] (피드 다시 읽기 · 상세 닫기). */
@Composable
fun ReportButton(target: ReportTarget, id: String, onHidden: () -> Unit, modifier: Modifier = Modifier, vm: ReportViewModel = hiltViewModel()) {
    val driving by vm.isDriving.collectAsStateWithLifecycle()
    var open by rememberSaveable(id) { mutableStateOf(false) }
    RoundIconButton(
        CommunityIcons.Flag,
        stringResource(R.string.report_action),
        { open = true },
        modifier,
        enabled = !driving,
        iconSize = 28.dp,
    )
    // 확인 창을 연 채로 출발하면 닫는다
    LaunchedEffect(driving) { if (driving) open = false }
    if (open) {
        // 버튼이 목록(LazyColumn) 안에 있어 그 자리에 바로 그리면 높이가 무한이 된다 → 화면 전체 창(Dialog)에 띄운다
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            ReportSheet(
                onReport = { reason -> vm.report(target, id, reason) { open = false; onHidden() } },
                onDismiss = { open = false },
            )
        }
    }
}

@Composable
private fun ReportSheet(onReport: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    val c = EodiTheme.colors
    OverlayCard(onDismiss = onDismiss, maxWidth = 760.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.report_title),
                    style = EodiTheme.type.title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                RoundIconButton(EodiIcons.Close, stringResource(R.string.community_close), onDismiss)
            }
            Text(stringResource(R.string.report_body), style = EodiTheme.type.body, color = c.textSecondary)
            listOf(
                ReportReason.INAPPROPRIATE to R.string.report_reason_inappropriate,
                ReportReason.SPAM to R.string.report_reason_spam,
                ReportReason.PRIVACY to R.string.report_reason_privacy,
                ReportReason.OTHER to R.string.report_reason_other,
            ).forEach { (r, label) ->
                TogglePill(stringResource(label), reason == r, { reason = r }, Modifier.fillMaxWidth(), role = Role.RadioButton, minHeight = EodiTheme.space.touch)
            }
            PrimaryPill(
                stringResource(R.string.report_submit),
                CommunityIcons.Flag,
                { reason?.let(onReport) },
                Modifier.fillMaxWidth(),
                enabled = reason != null,
                large = true,
            )
        }
    }
}
