package app.eodiyeotji.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.eodiyeotji.ui.place.LocalOpenPlace
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.OnThisDay
import app.eodiyeotji.core.RecapDay
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.recap.RecallCenter
import app.eodiyeotji.ui.agent.AgentIcons
import app.eodiyeotji.ui.agent.kmText
import app.eodiyeotji.ui.agent.memoryDateText
import app.eodiyeotji.ui.place.PhotoLoadingPlaceholder
import app.eodiyeotji.ui.place.PlaceThumbnail
import app.eodiyeotji.ui.replay.LocalOpenReplay
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.MemoryStamp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// ---------------------------------------------------------------- 날짜 목록 맨 위 (차 · 폰 공통)

/**
 * 내 기록 날짜 목록 맨 위: 요약 입구 두 개("11월 요약" · "올해의 어디였지") + "N년 전 오늘" 카드(있고, 오늘 닫지 않았고, 운전 중이 아닐 때).
 * [onOpenDay]: "그날 다시 보기".
 */
@Composable
fun RecapListTop(viewModel: RecapViewModel, onOpenDay: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            EntryPill(stringResource(R.string.recap_month_entry, state.today.monthValue), gold = true, Modifier.weight(1f)) {
                viewModel.openRecap(RecapMode.MONTH)
            }
            EntryPill(stringResource(R.string.recap_year_entry), gold = false, Modifier.weight(1f)) {
                viewModel.openRecap(RecapMode.YEAR)
            }
        }
        val otd = state.onThisDay
        if (otd != null && !state.driving) {
            OnThisDayCard(
                otd = otd,
                onOpenDay = { onOpenDay(otd.day) },
                onNavigate = viewModel::navigateOnThisDay,
                onDismiss = viewModel::dismissOnThisDay,
            )
        }
        state.note?.let { RecapNote(stringResource(it)) }
    }
}

@Composable
private fun EntryPill(text: String, gold: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Row(
        modifier
            .heightIn(min = 76.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(2.dp, if (gold) c.memory else c.outline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(EodiIcons.Sparkle, contentDescription = null, tint = c.memoryInk, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecapNote(text: String) {
    val c = EodiTheme.colors
    Text(
        text,
        style = EodiTheme.type.label,
        color = c.surface,
        modifier = Modifier.background(c.textPrimary, CircleShape).padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

/**
 * "N년 전 오늘" 카드: 금색 도장 · AI 하루 제목 · 날짜 줄 · 들른 곳 사진(최대 3장) · [그날로 돌아가기] · [그날 기록 보기] [그 코스로 안내] · 오늘은 닫기(×).
 */
@Composable
fun OnThisDayCard(
    otd: OnThisDay,
    onOpenDay: () -> Unit,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = EodiTheme.colors
    Surface(
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        color = c.surface,
        modifier = modifier.fillMaxWidth().border(2.dp, c.memory, RoundedCornerShape(EodiTheme.space.radiusL)),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryStamp(stringResource(R.string.recap_on_this_day_stamp, otd.yearsAgo), large = true)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(c.surfaceHigh).clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AgentIcons.Close, contentDescription = stringResource(R.string.recap_dismiss), tint = c.textSecondary, modifier = Modifier.size(32.dp))
                }
            }
            Text(otd.title, style = EodiTheme.type.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    memoryDateText(otd.day),
                    otd.region.takeIf { it.isNotBlank() },
                    kmText(otd.distanceM),
                    stringResource(R.string.recap_place_count, otd.stops.size).takeIf { otd.stops.isNotEmpty() },
                ).joinToString(" · "),
                style = EodiTheme.type.meta,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (otd.summary.isNotBlank()) {
                Text(otd.summary, style = EodiTheme.type.body, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (otd.photos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    otd.photos.forEach { s -> PhotoTile(s, Modifier.weight(1f).height(150.dp)) }
                    // 사진이 1~2장이면 빈 칸으로 너비를 맞춘다 (사진이 너무 커지지 않게)
                    repeat(3 - otd.photos.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // 첫 버튼: 그날로 돌아가기 (사진 · 지도로 그날을 다시 본다)
            val openReplay = LocalOpenReplay.current
            Button(
                onClick = { openReplay(otd.day) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = c.memory, contentColor = c.onMemory),
                modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
            ) {
                Icon(EodiIcons.Play, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.replay_open), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onOpenDay, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 76.dp)) {
                    Text(stringResource(R.string.replay_open_day_list), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    onClick = onNavigate,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    modifier = Modifier.weight(1f).heightIn(min = 76.dp),
                ) {
                    Icon(AgentIcons.Navigate, contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recap_navigate), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 운전 중 회상 카드 (차 에이전트 주행 화면)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecallEntryPoint {
    fun recallCenter(): RecallCenter
    fun drivingState(): DrivingStatePort
}

/**
 * 운전 중 지나가며 떠오른 기억 (차 주행 화면, 에이전트 처음 화면 위에 작게). 사진 한 장 · 금색 도장 · 이름 · 한 줄.
 * 회상이 없으면 아무것도 그리지 않는다. 몇 분 뒤 또는 주행이 끝나면 저절로 사라진다 (`RecallCenter`).
 */
@Composable
fun RecallDrivingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entry = remember { EntryPointAccessors.fromApplication(context.applicationContext, RecallEntryPoint::class.java) }
    val center = remember(entry) { entry.recallCenter() }
    val recall by center.current.collectAsStateWithLifecycle()
    val driving by remember(entry) { entry.drivingState().isDriving }.collectAsStateWithLifecycle()
    val openReplay = LocalOpenReplay.current
    val r = recall ?: return
    val c = EodiTheme.colors
    Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceHigh, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            PhotoTile(r.place, Modifier.size(120.dp), showName = false)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryStamp(r.whenText, large = true)
                Text(r.line, style = EodiTheme.type.bodyStrong, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // 그날로 돌아가기: 정차 중에만 (주행 중에는 큰 사진 화면을 열지 않는다)
            if (!driving) {
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(c.memory).clickable(role = Role.Button) { openReplay(r.day) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(EodiIcons.Play, contentDescription = stringResource(R.string.replay_open), tint = c.onMemory, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(c.surface).clickable(role = Role.Button, onClick = center::dismissCard),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AgentIcons.Close, contentDescription = stringResource(R.string.recap_recall_close), tint = c.textSecondary, modifier = Modifier.size(36.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- 공통 조각

/** 들른 곳 사진 한 칸 (없으면 코랄 옅은 칸 + 핀). [showName]이면 아래에 이름 받침. */
@Composable
internal fun PhotoTile(stop: StopFact, modifier: Modifier, showName: Boolean = true, radius: Dp = 20.dp) {
    val c = EodiTheme.colors
    val shape = RoundedCornerShape(radius)
    val openPlace = LocalOpenPlace.current
    // 누르면 그곳 장소 창(사진 · 별점 · 리뷰)
    Box(modifier.clip(shape).background(c.staySoft, shape).clickable { openPlace(stop) }) {
        val pin: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(EodiIcons.Pin, contentDescription = null, tint = c.stay, modifier = Modifier.size(36.dp))
            }
        }
        PlaceThumbnail(
            stop.name, stop.address, stop.lat, stop.lon,
            modifier = Modifier.fillMaxSize(),
            fallback = pin,
            loading = { PhotoLoadingPlaceholder(c.surfaceHigh) },
        )
        if (showName) {
            Text(
                stop.name,
                style = EodiTheme.type.caption,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(c.scrim).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 통계 칸 줄 (카드 안 옅은 칸, 칸 사이 세로선). */
@Composable
internal fun StatStrip(cells: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(c.surfaceHigh, RoundedCornerShape(EodiTheme.space.radiusM))
            .padding(vertical = 18.dp),
    ) {
        cells.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(2.dp).fillMaxHeight().padding(vertical = 8.dp).background(c.outline))
            StatCell(value, label)
        }
    }
}

@Composable
private fun RowScope.StatCell(value: String, label: String) {
    val c = EodiTheme.colors
    Column(Modifier.weight(1f).padding(horizontal = 8.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = EodiTheme.type.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1)
    }
}

/** 섹션 머리말 (금색 반짝임 + 글). */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    Row(modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(EodiIcons.Sparkle, contentDescription = null, tint = c.memoryInk, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = EodiTheme.type.eyebrow, color = c.textTertiary)
    }
}

/** 요약의 날 한 줄 (최고의 날 · 가장 긴 여행): 사진 · 도장 · 제목 · 날짜 줄. 누르면 그날. */
@Composable
internal fun RecapDayRow(stamp: String, day: RecapDay, onOpenDay: ((String) -> Unit)?, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(EodiTheme.space.radiusM))
            .background(c.surfaceHigh)
            .then(if (onOpenDay != null) Modifier.clickable { onOpenDay(day.day) } else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = day.photo
        if (photo != null) {
            PhotoTile(photo, Modifier.size(140.dp), showName = false)
            Spacer(Modifier.width(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryStamp(stamp)
            Text(day.title, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val span = if (day.spanDays > 1) stringResource(R.string.recap_span, day.spanDays, kmText(day.distanceM)) else kmText(day.distanceM)
            Text(
                listOfNotNull(memoryDateText(day.day), day.region.takeIf { it.isNotBlank() }, span).joinToString(" · "),
                style = EodiTheme.type.meta,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            photo?.let { Text(it.name, style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (onOpenDay != null) {
            Icon(EodiIcons.Chevron, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(32.dp))
        }
    }
}
