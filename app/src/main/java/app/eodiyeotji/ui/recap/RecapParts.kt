package app.eodiyeotji.ui.recap

import app.eodiyeotji.ui.community.SectionTitle
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.graphics.Brush

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.core.tween

import androidx.compose.animation.core.LinearEasing

import androidx.compose.animation.core.Animatable

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
import app.eodiyeotji.ui.community.StopPhotoBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.OnThisDay
import app.eodiyeotji.core.RecapDay
import app.eodiyeotji.core.RoutineNudge
import app.eodiyeotji.recap.RecallCenter
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.ui.agent.memoryDateText
import app.eodiyeotji.ui.place.PhotoLoadingPlaceholder
import app.eodiyeotji.ui.place.PlaceThumbnail
import app.eodiyeotji.ui.replay.LocalOpenReplay
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.places.PlacesIcons
import androidx.compose.ui.graphics.vector.ImageVector
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
fun RecapListTop(
    viewModel: RecapViewModel,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenBiz: (() -> Unit)? = null,
    onOpenTrips: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 입구: 한 줄 한 띠에 글자만 (아이콘 · 테두리 알약 네 개가 두 줄을 차지하던 것을 줄였다). 주행 중에는 요약 · 결산만.
        val month = stringResource(R.string.recap_month_entry, state.today.monthValue)
        val year = stringResource(R.string.recap_year_entry)
        val entries = buildList<Pair<String, () -> Unit>> {
            // 여행 간 날만 모아 보기 (검색의 종류 칩 "여행"을 켠 채로 연다)
            if (onOpenTrips != null && !state.driving) add(stringResource(R.string.trips_entry) to onOpenTrips)
            add(month to { viewModel.openRecap(RecapMode.MONTH) })
            add(year to { viewModel.openRecap(RecapMode.YEAR) })
            if (!state.driving) {
                add(stringResource(R.string.memory_map_entry) to { viewModel.openMemoryMap() })
                if (onOpenBiz != null) add(stringResource(R.string.biz_entry) to onOpenBiz)
            }
        }
        EntryStrip(entries)
        // 다가오는 날(늘 가던 날)이 지난 날(N년 전 오늘)보다 먼저. 카드는 지우지 않고 한 줄로 접어 둘 수 있다 (누르면 다시 편다)
        val routine = state.routine?.takeUnless { state.driving }
        val folded = state.routineFolded
        if (routine != null) {
            if (folded) RoutineChip(routine, onExpand = { viewModel.foldRoutine(false) })
            else RoutineCard(routine, onNavigate = viewModel::navigateRoutine, onFold = { viewModel.foldRoutine(true) })
        }
        // 큰 카드는 한 번에 하나만 (둘이 같이 뜨면 화면을 다 가린다): 늘 가던 날을 접으면 N년 전 오늘이 나온다
        val otd = state.onThisDay.takeIf { routine == null || folded }
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

/** 요약 · 결산 · 기억 지도 · 출장 관리 입구 (같은 테두리). [ai]면 아이콘을 금색(AI가 쓴 요약), 아니면 파랑. */
@Composable
internal fun EntryPill(text: String, icon: ImageVector, ai: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Row(
        modifier
            .heightIn(min = 76.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(2.dp, c.outline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (ai) c.memoryInk else c.accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 날짜 목록 맨 위 입구 띠: 같은 너비 글자 버튼을 가는 선으로 나눈 한 줄. */
@Composable
private fun EntryStrip(entries: List<Pair<String, () -> Unit>>) {
    val c = EodiTheme.colors
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(EodiTheme.space.radiusM)).background(c.surfaceHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEachIndexed { i, (text, onClick) ->
            if (i > 0) Box(Modifier.width(2.dp).fillMaxHeight().padding(vertical = 18.dp).background(c.outline))
            Box(
                Modifier.weight(1f).heightIn(min = 76.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 접어 둔 "늘 가던 날": 금색 도장 + 그곳 이름 한 줄. 누르면 다시 편다 (카드는 지우지 않고 접기만 한다). */
@Composable
private fun RoutineChip(nudge: RoutineNudge, onExpand: () -> Unit) {
    val c = EodiTheme.colors
    val shape = RoundedCornerShape(EodiTheme.space.radiusM)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(shape).background(c.surface).border(2.dp, c.memory, shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.routine_expand), onClick = onExpand)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemoryStamp(stringResource(R.string.routine_stamp, nudge.whenText))
        Spacer(Modifier.width(12.dp))
        Text(nudge.place.name, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(EodiIcons.Chevron, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(28.dp).rotate(90f))
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
 * "늘 가던 날" 카드: 금색 도장("늘 가던 날 · 내일") · 그곳 사진과 이름 · 한 줄("매달 둘째 목요일마다 …에 갔어요. 내일이 그날이에요.") ·
 * 덧붙임(횟수 · 보통 도착 시각) · [장소 보기] [여기로 안내] · 접어 두기(∧). 고르기 · 문장은 Rust(`routine.rs`).
 */
@Composable
fun RoutineCard(nudge: RoutineNudge, onNavigate: () -> Unit, onFold: () -> Unit, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val openPlace = LocalOpenPlace.current
    val p = nudge.place
    Surface(
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        color = c.surface,
        modifier = modifier.fillMaxWidth().border(2.dp, c.memory, RoundedCornerShape(EodiTheme.space.radiusL)),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryStamp(stringResource(R.string.routine_stamp, nudge.whenText), large = true)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(c.surfaceHigh).clickable(role = Role.Button, onClick = onFold),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(EodiIcons.Chevron, contentDescription = stringResource(R.string.routine_fold), tint = c.textSecondary, modifier = Modifier.size(32.dp).rotate(-90f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                StopPhotoBox(p.name, p.address, p.lat, p.lon, null, Modifier.size(132.dp), radius = 20.dp, iconSize = 36.dp, loadingColor = c.surfaceHigh, onClick = { openPlace(p) })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = EodiTheme.type.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(nudge.detail, style = EodiTheme.type.meta, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(nudge.line, style = EodiTheme.type.body, color = c.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = { openPlace(p) }, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 76.dp)) {
                    Text(stringResource(R.string.routine_open_place), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    onClick = onNavigate,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    modifier = Modifier.weight(1f).heightIn(min = 76.dp),
                ) {
                    Icon(EodiIcons.Navi, contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.routine_navigate), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
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
                    Icon(EodiIcons.Close, contentDescription = stringResource(R.string.recap_dismiss), tint = c.textSecondary, modifier = Modifier.size(32.dp))
                }
            }
            Text(otd.title, style = EodiTheme.type.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    memoryDateText(otd.day),
                    otd.region.takeIf { it.isNotBlank() },
                    distanceText(otd.distanceM),
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
                    val openPlace = LocalOpenPlace.current
                    otd.photos.forEach { s -> StopPhotoBox(s.name, s.address, s.lat, s.lon, null, Modifier.weight(1f).height(150.dp), radius = 20.dp, iconSize = 36.dp, loadingColor = c.surfaceHigh, showName = true, onClick = { openPlace(s) }) }
                    // 사진이 1~2장이면 빈 칸으로 너비를 맞춘다 (사진이 너무 커지지 않게)
                    repeat(3 - otd.photos.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // 첫 버튼: 그날로 돌아가기 (사진 · 지도로 그날을 다시 본다). 다시 볼 것이 있는 날만 (Rust `replayable`)
            val openReplay = LocalOpenReplay.current
            if (otd.replayable) Button(
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
                    Icon(EodiIcons.Navi, contentDescription = null, modifier = Modifier.size(30.dp))
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
 * 운전 중 지나가며 떠오른 기억 (차 주행 화면, 마이크 위 큰 카드). 그날 사진이 카드를 가득 채우고, 아래쪽 어두운 그라데이션 위에
 * 금색 도장("1년 전 가을") · 그곳 이름 · 읽어 주는 한 줄 · 그날 제목. 사진은 천천히 다가온다 (기억이 떠오르듯).
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
    val shape = RoundedCornerShape(32.dp)
    // 사진이 1.0 → 1.08배로 천천히 다가온다 (이 회상이 떠 있는 동안 한 번)
    val zoom = remember(r.key) { Animatable(1f) }
    LaunchedEffect(r.key) { zoom.animateTo(1.08f, tween(durationMillis = 9_000, easing = LinearEasing)) }
    Box(modifier.fillMaxWidth().clip(shape).background(c.staySoft, shape)) {
        PlaceThumbnail(
            r.place.name, r.place.address, r.place.lat, r.place.lon,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value },
            fallback = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(EodiIcons.Pin, contentDescription = null, tint = c.stay, modifier = Modifier.size(64.dp))
                }
            },
            loading = { PhotoLoadingPlaceholder(c.surfaceHigh) },
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .padding(start = 36.dp, end = 36.dp, top = 72.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MemoryStamp(r.whenText, large = true)
            Text(r.place.name, style = EodiTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.line, style = EodiTheme.type.bodyStrong, color = Color.White.copy(alpha = 0.92f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (r.dayTitle.isNotBlank()) {
                Text(r.dayTitle, style = EodiTheme.type.meta, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(role = Role.Button, onClick = center::dismissCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EodiIcons.Close, contentDescription = stringResource(R.string.recap_recall_close), tint = Color.White, modifier = Modifier.size(36.dp))
        }
        // 그날로 돌아가기: 정차 중에만 (주행 중에는 큰 사진 화면을 열지 않는다)
        if (!driving) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 116.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(c.memory)
                    .clickable(role = Role.Button) { openReplay(r.day) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(EodiIcons.Play, contentDescription = stringResource(R.string.replay_open), tint = c.onMemory, modifier = Modifier.size(36.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- 공통 조각

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
            val openPlace = LocalOpenPlace.current
            StopPhotoBox(photo.name, photo.address, photo.lat, photo.lon, null, Modifier.size(140.dp), radius = 20.dp, iconSize = 36.dp, loadingColor = c.surfaceHigh, onClick = { openPlace(photo) })
            Spacer(Modifier.width(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryStamp(stamp)
            Text(day.title, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val span = if (day.spanDays > 1) stringResource(R.string.recap_span, day.spanDays, distanceText(day.distanceM)) else distanceText(day.distanceM)
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
