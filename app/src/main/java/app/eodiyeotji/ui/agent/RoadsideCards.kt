package app.eodiyeotji.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** 정차 중: 3개까지 나란히 (번호 · 종류 · 이름 · 앞쪽 거리). 고르기는 답 아래 버튼 / 말로. */
@Composable
internal fun RoadsideOptionsBlock(card: RoadsideCard, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        card.options.take(3).forEachIndexed { i, o -> RoadsideRow(i, o) }
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
private fun RoadsideRow(index: Int, o: RoadsideOption) {
    val c = EodiTheme.colors
    Surface(shape = RoundedCornerShape(24.dp), color = if (index == 0) c.accentSoft else c.surface, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text("${index + 1}", style = EodiTheme.type.titleSmall, color = if (index == 0) c.accent else c.textTertiary)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(o.name, style = EodiTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(o.kind, style = EodiTheme.type.meta, color = c.textSecondary)
            }
            Text(o.ahead, style = EodiTheme.type.label, color = c.accent)
        }
    }
}
