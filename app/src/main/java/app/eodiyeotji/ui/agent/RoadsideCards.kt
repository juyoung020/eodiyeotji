package app.eodiyeotji.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.agent.roadside.RoadsideCard
import app.eodiyeotji.agent.roadside.RoadsideOption
import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.theme.EodiTheme

/**
 * 주행 중 길 위 카드 (큰 카드 1장): 1순위 이름 + "12km · 8분 앞" 크게, 그 아래 다른 선택 한 줄.
 * 목록 · 지도 없이 한눈에 (운전 중 시선 2초 안).
 */
@Composable
internal fun DrivingRoadsideCard(card: RoadsideCard, modifier: Modifier = Modifier) {
    val top = card.options.firstOrNull() ?: return
    val c = EodiTheme.colors
    Surface(shape = RoundedCornerShape(32.dp), color = c.accentSoft, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 40.dp, vertical = 32.dp)) {
            Text(top.kind, style = EodiTheme.type.label, color = c.accent)
            Spacer(Modifier.height(8.dp))
            Text(top.name, style = EodiTheme.type.drivingTitle, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(top.ahead, style = EodiTheme.type.drivingHeadline, color = c.accent)
            card.options.getOrNull(1)?.let { second ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "${stringResource(R.string.roadside_second)} · ${second.name} ${second.ahead}",
                    style = EodiTheme.type.bodyStrong,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PublicDataCredit(card)
        }
    }
}

/**
 * 정차 중: 3개까지 나란히 (번호 · 종류 · 이름 · 앞쪽 거리). 줄을 누르면 그 곳으로 고르고,
 * 고른 줄 바로 아래 Gleo 칩 "그래 · 경유지로" / "아니" (주행 카드와 같은 확인, 말 "응/아니"도 같다).
 * 보내는 중엔 "그래" 칩 안에 로더, 결과는 답 아래 한 줄([ActionStatus]).
 */
@Composable
internal fun RoadsideOptionsBlock(
    card: RoadsideCard,
    proposal: AgentEvent.ActionProposal?,
    action: ActionState,
    onSelect: (Int) -> Unit,
    onYes: (AgentEvent.ActionProposal) -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = action is ActionState.Running
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        card.options.take(3).forEachIndexed { i, o ->
            RoadsideRow(i, o, selected = i == card.selected, enabled = !running, onClick = { onSelect(i) })
            if (i == card.selected && proposal != null && action !is ActionState.Done) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    GleoChip(
                        // 이름은 바로 위 고른 줄에 있다 → 칩은 "그래 · 경유지로" / "그래 · 안내"만 (좁은 창에서도 잘리지 않게)
                        stringResource(if (card.addsWaypoint) R.string.roadside_yes_waypoint else R.string.roadside_yes_navigate),
                        primary = true,
                        loading = running,
                        onClick = { onYes(proposal) },
                        modifier = Modifier.weight(1.6f),
                    )
                    GleoChip(stringResource(R.string.roadside_no), primary = false, loading = false, enabled = !running, onClick = onNo, modifier = Modifier.weight(1f))
                }
            }
        }
        PublicDataCredit(card)
    }
}

/** 공공데이터포털 목록에서 온 곳이 있으면 작은 출처 한 줄 (공공누리 출처 표시). */
@Composable
private fun PublicDataCredit(card: RoadsideCard) {
    if (card.options.none { it.publicData }) return
    Text(stringResource(R.string.roadside_public_credit), style = EodiTheme.type.meta, color = EodiTheme.colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun RoadsideRow(index: Int, o: RoadsideOption, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = EodiTheme.colors
    PressSurface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) c.accentSoft else c.surface,
        border = if (selected) BorderStroke(2.dp, c.accent) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text("${index + 1}", style = EodiTheme.type.titleSmall, color = if (selected) c.accent else c.textTertiary)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(o.name, style = EodiTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(o.kind, style = EodiTheme.type.meta, color = c.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Text(o.ahead, style = EodiTheme.type.label, color = c.accent, maxLines = 1)
        }
    }
}
