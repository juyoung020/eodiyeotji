package app.eodiyeotji.ui.biz

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.rust.BizMonth
import app.eodiyeotji.core.rust.BizClient
import app.eodiyeotji.core.rust.BizTrip
import androidx.compose.foundation.horizontalScroll
import app.eodiyeotji.ui.common.durationText
import app.eodiyeotji.ui.community.CenterMessage
import app.eodiyeotji.ui.community.LoadingBox
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.recap.Load
import app.eodiyeotji.ui.replay.LocalOpenReplay
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 출장 관리 (열려 있을 때만 그린다): 머리(뒤로 · 출장 관리 · ‹ 달 › · 정산서 내보내기) → 한 달 요약 칸 → 출장 카드 목록.
 * 출장 찾기 · 거리 · 에너지 · 경비 · CSV는 Rust(`biztrip.rs`)가 하고, 여기서는 그리기만 한다.
 */
@Composable
fun BizOverlay(viewModel: BizViewModel, onOpenDay: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.open) return
    BackHandler(onBack = viewModel::close)
    val c = EodiTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 정산서 파일 공유는 공유용 FileProvider가 있는 판(폰)에서만
    val canExport = remember(context) {
        runCatching { context.packageManager.resolveContentProvider("${context.packageName}.share", 0) != null }.getOrDefault(false)
    }
    Surface(color = c.background, contentColor = c.textPrimary, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = EodiTheme.space.page, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(EodiIcons.Back, stringResource(R.string.biz_back), viewModel::close, containerColor = c.surface, iconSize = 36.dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.biz_title), style = EodiTheme.type.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val ready = (state.month as? Load.Ready)?.value
                if (canExport && ready != null && ready.trips.isNotEmpty()) {
                    SecondaryPill(stringResource(R.string.biz_export), EodiIcons.Calendar, { scope.launch { share(context, viewModel, ready) } })
                    Spacer(Modifier.width(16.dp))
                }
                RoundIconButton(EodiIcons.Chevron, stringResource(R.string.biz_prev), { viewModel.shift(-1) }, containerColor = c.surface, iconModifier = Modifier.rotate(180f))
                Text(
                    stringResource(R.string.biz_month, state.ym.year, state.ym.monthValue),
                    style = EodiTheme.type.bodyStrong,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 150.dp).padding(horizontal = 8.dp),
                )
                // 이번 달 다음으로는 못 넘어간다 → 흐리게
                val canNext = state.ym < YearMonth.now()
                RoundIconButton(EodiIcons.Chevron, stringResource(R.string.biz_next), { viewModel.shift(1) }, Modifier.alpha(if (canNext) 1f else 0.35f), enabled = canNext, containerColor = c.surface)
            }
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = EodiTheme.space.page, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                // 차 화면(2560dp)에서 1280dp로 묶으면 오른쪽 절반이 비어 보인다 → 머리 줄과 같은 폭까지 쓴다
                val inner = Modifier.widthIn(max = 2000.dp).fillMaxWidth().padding(bottom = EodiTheme.space.page)
                when (val m = state.month) {
                    Load.Loading -> LoadingBox(Modifier.padding(top = 80.dp))
                    Load.Failed -> CenterMessage(stringResource(R.string.biz_failed), Modifier.padding(top = 48.dp))
                    is Load.Ready -> Column(inner, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.biz_hint), style = EodiTheme.type.meta, color = c.textSecondary)
                        SummaryRow(m.value)
                        ClientSection(state.clients, state.suggestions, viewModel::setClient)
                        if (m.value.trips.isEmpty()) {
                            CenterMessage(stringResource(R.string.biz_empty), Modifier.padding(top = 32.dp))
                        }
                        val openReplay = LocalOpenReplay.current
                        m.value.trips.forEach { t ->
                            TripCard(
                                t,
                                onOpenDay = { viewModel.close(); onOpenDay(t.day) },
                                onReplay = { openReplay(t.day) },
                                onNotBiz = { viewModel.markNotBiz(t.day) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(m: BizMonth) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Tile(stringResource(R.string.biz_count, m.trips.size), stringResource(R.string.biz_count_label), Modifier.weight(1f))
        // 아래 줄("계기판 89.8km")과 같은 숫자 · 같은 기준을 적는다 (하루 상세 거리는 GPS · 하루 전체라 다르다)
        Tile(
            stringResource(R.string.biz_km_value, m.totalKm),
            stringResource(if (m.totalHasGps) R.string.biz_km_label_mixed else R.string.biz_km_label),
            Modifier.weight(1f),
        )
        Tile(durationText(m.totalMin * 60_000), stringResource(R.string.biz_time_label), Modifier.weight(1f))
        Tile(wonText(m.totalWon), stringResource(R.string.biz_cost_label), Modifier.weight(1f), accent = true)
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier, accent: Boolean = false) {
    val c = EodiTheme.colors
    Surface(shape = RoundedCornerShape(24.dp), color = if (accent) c.accentSoft else c.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(value, style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = if (accent) c.accent else c.textPrimary, maxLines = 1)
            Text(label, style = EodiTheme.type.meta, color = c.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun TripCard(t: BizTrip, onOpenDay: () -> Unit, onReplay: () -> Unit, onNotBiz: () -> Unit) {
    val c = EodiTheme.colors
    // 카드를 누르면 그날 기록, 버튼은 다른 화면과 같은 이름의 "그날로 돌아가기"(사진 · 지도로 다시 보기)
    Surface(onClick = onOpenDay, shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // 날짜 칸 (회사 · 연구소는 장소 사진이 엉뚱한 경우가 많아 사진 대신 날짜로)
            val dd = java.time.LocalDate.parse(t.day)
            Column(
                Modifier.size(120.dp).clip(RoundedCornerShape(24.dp)).background(c.accentSoft),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("${dd.dayOfMonth}", style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = c.accent)
                Text(t.weekday, style = EodiTheme.type.meta, color = c.accent)
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val d = java.time.LocalDate.parse(t.day)
                Text(
                    stringResource(R.string.biz_date, d.monthValue, d.dayOfMonth, t.weekday),
                    style = EodiTheme.type.meta,
                    color = c.accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(t.title, style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    t.stops.map { s -> "${s.name} (${durationText(s.leaveMs - s.arriveMs)})" }.joinToString(" · "),
                    style = EodiTheme.type.meta,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val km = stringResource(if (t.gpsDistance) R.string.biz_km_gps else R.string.biz_km_odo, t.distanceKm)
                val drive = stringResource(R.string.biz_drive, durationText(t.driveMin * 60_000))
                val energy = t.energyPct?.let { stringResource(if (t.ev) R.string.biz_battery else R.string.biz_fuel, it.toInt()) }
                val parts = listOfNotNull(km, drive, energy)
                Text(parts.joinToString("  ·  "), style = EodiTheme.type.meta, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(wonText(t.costWon), style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = c.accent)
                Text(
                    stringResource(if (t.marked) R.string.biz_marked else if (t.client) R.string.biz_client_visit else R.string.biz_auto),
                    style = EodiTheme.type.meta,
                    color = c.textTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryPill(stringResource(R.string.replay_open), EodiIcons.Play, onReplay)
                    SecondaryPill(stringResource(R.string.biz_not_biz), EodiIcons.Close, onNotBiz)
                }
            }
        }
    }
}

/**
 * 거래처 줄: 정해 둔 거래처(누르면 뺀다)와 권하는 곳(평일에 자주 간 곳, 누르면 거래처로). 고르기 · 날 수는 Rust(`bizclient.rs`).
 * 거래처에 들른 주행은 요일 · 시각 · 거리와 상관없이 출장으로 묶인다 (근무 시간이 다른 사람 · 영업직).
 */
@Composable
private fun ClientSection(clients: List<BizClient>, suggestions: List<BizClient>, onSet: (BizClient, Boolean) -> Unit) {
    val c = EodiTheme.colors
    Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.biz_clients_title), style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.biz_clients_hint), style = EodiTheme.type.meta, color = c.textSecondary)
            if (clients.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    clients.forEach { cl ->
                        SecondaryPill(stringResource(R.string.biz_client_chip, cl.name.ifBlank { stringResource(R.string.biz_client_unnamed) }, cl.visitDays.toInt()), EodiIcons.Close, { onSet(cl, false) })
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                Text(stringResource(R.string.biz_clients_suggest), style = EodiTheme.type.meta, color = c.textSecondary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    suggestions.forEach { s ->
                        SecondaryPill(stringResource(R.string.biz_client_suggest_chip, s.name, s.visitDays.toInt()), EodiIcons.Pin, { onSet(s, true) })
                    }
                }
            }
        }
    }
}

/** 4300 → "약 4,300원", 12300 → "약 1만 2,300원". */
@Composable
private fun wonText(won: Long): String {
    val man = won / 10_000
    val rest = won % 10_000
    val body = when {
        man == 0L -> "%,d원".format(rest)
        rest == 0L -> "${man}만 원"
        else -> "${man}만 %,d원".format(rest)
    }
    return stringResource(R.string.biz_about, body)
}

/** 정산서 CSV를 캐시 share/에 저장하고 공유 시트(메일 · 드라이브 · 카톡)로 넘긴다. */
private suspend fun share(context: Context, viewModel: BizViewModel, m: BizMonth) {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "출장정산서-${m.month}.csv")
            file.writeText(viewModel.csv(m), Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        }.onFailure { Log.w("EodiBiz", "정산서 저장 실패", it) }.getOrNull()
    } ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.biz_subject, m.month))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.biz_export)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
