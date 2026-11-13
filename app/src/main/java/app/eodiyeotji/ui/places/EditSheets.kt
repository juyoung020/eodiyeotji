package app.eodiyeotji.ui.places

import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.community.StopPhotoBox
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.AnchorRole
import app.eodiyeotji.core.PlaceCard
import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceSpot
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.Notice
import app.eodiyeotji.ui.community.OverlayCard
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.clockText
import app.eodiyeotji.ui.timeline.dayTitle
import app.eodiyeotji.ui.timeline.tripPlacesText
import java.time.LocalDate

/**
 * 기록 고치기 창들을 그린다: ⋯ 메뉴 · 이름 고치기 · 지우기 확인 · 알림 한 줄.
 * 타임라인 화면 위에 놓는다 (차 = 가운데 카드, 폰 = 아래 시트 — [OverlayCard]가 판마다 고른다).
 */
@Composable
fun PlaceEditHost(vm: PlaceEditViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    s.menu?.let { target ->
        BackHandler { vm.close() }
        EditMenu(target, s.card, vm)
    }
    s.rename?.let { r ->
        BackHandler { vm.cancelRename() }
        RenameSheet(r, onSave = vm::rename, onCancel = vm::cancelRename)
    }
    s.dayTitle?.let { d ->
        BackHandler { vm.cancelDayTitle() }
        DayTitleSheet(d.day, d.title.orEmpty(), onSave = vm::saveDayTitle, onCancel = vm::cancelDayTitle)
    }
    s.confirmDelete?.let { target ->
        BackHandler { vm.cancelDelete() }
        ConfirmDelete(target, onDelete = vm::confirmDelete, onHide = vm::hideInstead, onCancel = vm::cancelDelete)
    }
    s.notice?.let { res ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Notice(if (s.noticeArg != null) stringResource(res, s.noticeArg!!) else stringResource(res))
        }
    }
}

// ---------------------------------------------------------------- ⋯ 메뉴

@Composable
private fun EditMenu(target: EditTarget, card: PlaceCard?, vm: PlaceEditViewModel) {
    OverlayCard(onDismiss = vm::close, maxWidth = 960.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 28.dp)) {
            MenuHeader(target, card, onClose = vm::close)
            Spacer(Modifier.height(24.dp))
            val items: List<MenuItem> = when (target) {
                is EditTarget.Stay -> stayItems(target, card, vm)
                is EditTarget.Trip -> listOf(
                    MenuItem(CommunityIcons.Pen, stringResource(R.string.edit_rename_start)) { vm.startRename(PlaceSpot.tripStart(target.trip)) },
                    MenuItem(CommunityIcons.Pen, stringResource(R.string.edit_rename_end)) { vm.startRename(PlaceSpot.tripEnd(target.trip)) },
                    MenuItem(PlacesIcons.Hide, stringResource(R.string.edit_hide_trip)) { vm.hide(target) },
                    MenuItem(PlacesIcons.Trash, stringResource(R.string.edit_delete_trip), danger = true) { vm.askDelete(target) },
                )
                // 지우기(되돌릴 수 없음)는 항상 맨 끝 줄
                is EditTarget.Day -> listOf(
                    MenuItem(CommunityIcons.Pen, stringResource(R.string.edit_day_title)) { vm.startDayTitle(target.summary) },
                    MenuItem(PlacesIcons.Hide, stringResource(R.string.edit_hide_day)) { vm.hide(target) },
                    MenuItem(PlacesIcons.Trash, stringResource(R.string.edit_delete_day), danger = true) { vm.askDelete(target) },
                )
            }
            MenuGrid(items)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.edit_hide_hint), style = EodiTheme.type.caption, color = EodiTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun stayItems(target: EditTarget.Stay, card: PlaceCard?, vm: PlaceEditViewModel): List<MenuItem> {
    val items = mutableListOf<MenuItem>()
    if (card != null) {
        items += MenuItem(
            if (card.saved) CommunityIcons.HeartFilled else CommunityIcons.Heart,
            stringResource(if (card.saved) R.string.edit_saved else R.string.edit_save),
            on = card.saved,
        ) { vm.toggleSave(card) }
    }
    items += MenuItem(CommunityIcons.Pen, stringResource(R.string.edit_rename)) { vm.startRename(PlaceSpot.stay(target.stay)) }
    if (card != null) {
        items += MenuItem(EodiIcons.Navi, stringResource(R.string.edit_navigate)) { vm.navigate(card) }
        items += if (card.role == AnchorRole.HOME) {
            MenuItem(PlacesIcons.Home, stringResource(R.string.edit_is_home), enabled = false) {}
        } else {
            MenuItem(PlacesIcons.Home, stringResource(R.string.edit_set_home)) { vm.setAnchor(AnchorRole.HOME, card) }
        }
        items += if (card.role == AnchorRole.OFFICE) {
            MenuItem(PlacesIcons.Office, stringResource(R.string.edit_is_office), enabled = false) {}
        } else {
            MenuItem(PlacesIcons.Office, stringResource(R.string.edit_set_office)) { vm.setAnchor(AnchorRole.OFFICE, card) }
        }
    }
    items += MenuItem(PlacesIcons.Hide, stringResource(R.string.edit_hide_stay)) { vm.hide(target) }
    items += MenuItem(PlacesIcons.Trash, stringResource(R.string.edit_delete_stay), danger = true) { vm.askDelete(target) }
    return items
}

/** 메뉴 머리: 머문 곳은 사진 · 이름 · 다녀온 날, 주행은 "출발 → 도착" · 시각, 하루는 날짜 · 제목. */
@Composable
private fun MenuHeader(target: EditTarget, card: PlaceCard?, onClose: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (eyebrow, title, sub) = when (target) {
            is EditTarget.Stay -> {
                val name = target.stay.place?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.edit_unknown_place)
                val visits = card?.let { visitLine(it) }
                Triple(stringResource(R.string.edit_menu_stay), name, listOfNotNull(clockText(target.stay.arriveMs), visits).joinToString(" · "))
            }
            is EditTarget.Trip -> Triple(
                stringResource(R.string.edit_menu_trip),
                tripPlacesText(target.trip) ?: stringResource(R.string.edit_menu_trip),
                "${clockText(target.trip.startMs)} – ${clockText(target.trip.endMs)}",
            )
            is EditTarget.Day -> {
                val date = dayTitle(target.summary.day, LocalDate.now())
                Triple(stringResource(R.string.edit_menu_day), target.summary.title?.takeIf { it.isNotBlank() } ?: date, date)
            }
        }
        if (target is EditTarget.Stay) {
            val p = target.stay.place
            StopPhotoBox(p?.name.orEmpty(), p?.address.orEmpty(), target.stay.center.lat, target.stay.center.lon, null, Modifier.size(104.dp), radius = EodiTheme.space.radiusM, iconSize = 40.dp)
            Spacer(Modifier.width(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = type.eyebrow, color = c.textTertiary)
            Text(title, style = type.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (sub.isNotBlank()) Text(sub, style = type.meta, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(16.dp))
        RoundIconButton(EodiIcons.Close, stringResource(R.string.edit_close), onClose)
    }
}

/** "3일 다녀옴 · 마지막 10월 8일" */
@Composable
internal fun visitLine(card: PlaceCard): String? {
    if (card.visitDays <= 0) return null
    val last = card.lastVisitDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val visits = stringResource(R.string.places_visits, card.visitDays)
    return if (last != null) "$visits · ${stringResource(R.string.places_last_visit, shortDay(last))}" else visits
}

internal fun shortDay(d: LocalDate): String =
    if (d.year == LocalDate.now().year) "${d.monthValue}월 ${d.dayOfMonth}일" else "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"

private data class MenuItem(
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false,
    val on: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** 메뉴 칸: 넓으면 두 줄씩, 좁으면 한 줄씩. 칸 하나 높이 88dp. */
@Composable
private fun MenuGrid(items: List<MenuItem>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= 640.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { MenuCell(it, Modifier.weight(1f)) }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MenuCell(item: MenuItem, modifier: Modifier) {
    val c = EodiTheme.colors
    val fg = when {
        !item.enabled -> c.textTertiary
        item.danger -> c.error
        item.on -> c.heart
        else -> c.textPrimary
    }
    val iconTint = when {
        !item.enabled -> c.textTertiary
        item.danger -> c.error
        item.on -> c.heart
        else -> c.accent
    }
    PressSurface(
        onClick = item.onClick,
        enabled = item.enabled,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        color = if (item.on) c.heartSoft else c.surfaceHigh,
        contentColor = fg,
        modifier = modifier.heightIn(min = 88.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, null, tint = iconTint, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(18.dp))
            Text(item.label, style = EodiTheme.type.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- 하루 제목 고치기

/** 하루 제목 고치기: 지금 제목을 채워 두고, 비우고 저장하면 자동 제목으로 돌아간다. 글자 수는 Rust(30자)와 같게 막는다. */
@Composable
private fun DayTitleSheet(day: String, current: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    var text by rememberSaveable(day) { mutableStateOf(current) }
    OverlayCard(onDismiss = onCancel, maxWidth = 960.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.day_title_title), style = type.title, modifier = Modifier.weight(1f).semantics { heading() })
                RoundIconButton(EodiIcons.Close, stringResource(R.string.edit_cancel), onCancel)
            }
            Spacer(Modifier.height(20.dp))
            TextField(
                value = text,
                onValueChange = { if (it.length <= DayTitleMax) text = it },
                singleLine = true,
                textStyle = type.title,
                placeholder = { Text(stringResource(R.string.day_title_hint), style = type.title, maxLines = 1) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                shape = RoundedCornerShape(EodiTheme.space.radiusM),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.surfaceHigh,
                    unfocusedContainerColor = c.surfaceHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.day_title_note), style = type.caption, color = c.textTertiary)
            Spacer(Modifier.height(20.dp))
            PrimaryPill(stringResource(R.string.day_title_save), EodiIcons.Check, { onSave(text) }, Modifier.fillMaxWidth())
        }
    }
}

/** Rust `edits::DAY_TITLE_MAX`와 같은 값. */
private const val DayTitleMax = 30

// ---------------------------------------------------------------- 이름 고치기

@Composable
private fun RenameSheet(r: RenameState, onSave: (String, PlaceLabel?) -> Unit, onCancel: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    var text by rememberSaveable(r.card.id) { mutableStateOf(r.card.label?.name.orEmpty()) }
    OverlayCard(onDismiss = onCancel, maxWidth = 960.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rename_title), style = type.title, modifier = Modifier.weight(1f).semantics { heading() })
                RoundIconButton(EodiIcons.Close, stringResource(R.string.edit_cancel), onCancel)
            }
            Spacer(Modifier.height(20.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = type.title,
                placeholder = { Text(stringResource(R.string.rename_hint), style = type.title, maxLines = 1) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave(text, null) }),
                shape = RoundedCornerShape(EodiTheme.space.radiusM),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.surfaceHigh,
                    unfocusedContainerColor = c.surfaceHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.rename_note), style = type.caption, color = c.textTertiary)
            Spacer(Modifier.height(20.dp))
            PrimaryPill(stringResource(R.string.rename_save), EodiIcons.Check, { onSave(text, null) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.rename_candidates), style = type.eyebrow, color = c.textTertiary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            when (val list = r.candidates) {
                null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    app.eodiyeotji.ui.common.EodiLoader(size = 32.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.rename_candidates_loading), style = type.body, color = c.textSecondary)
                }
                else -> if (list.isEmpty()) {
                    Text(stringResource(R.string.rename_candidates_none), style = type.body, color = c.textSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.forEach { cand -> CandidateRow(cand) { onSave(cand.name, cand) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(label: PlaceLabel, onClick: () -> Unit) {
    val c = EodiTheme.colors
    PressSurface(onClick = onClick, shape = RoundedCornerShape(EodiTheme.space.radiusM), color = c.surfaceHigh, modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(EodiIcons.Pin, null, tint = c.stay, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label.name, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(label.category, label.address).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = EodiTheme.type.meta, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- 지우기 확인

@Composable
private fun ConfirmDelete(target: EditTarget, onDelete: () -> Unit, onHide: () -> Unit, onCancel: () -> Unit) {
    val c = EodiTheme.colors
    OverlayCard(onDismiss = onCancel, maxWidth = 900.dp) {
        Column(Modifier.padding(32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).background(c.error.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(PlacesIcons.Trash, null, tint = c.error, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(20.dp))
                Text(
                    stringResource(
                        when (target) {
                            is EditTarget.Trip -> R.string.delete_title_trip
                            is EditTarget.Stay -> R.string.delete_title_stay
                            is EditTarget.Day -> R.string.delete_title_day
                        },
                    ),
                    style = EodiTheme.type.title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (target is EditTarget.Trip) R.string.delete_body_trip else R.string.delete_body),
                style = EodiTheme.type.body,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryPill(stringResource(R.string.edit_cancel), null, onCancel, Modifier.weight(1f))
                SecondaryPill(stringResource(R.string.delete_instead_hide), PlacesIcons.Hide, onHide, Modifier.weight(1f))
                PressSurface(onClick = onDelete, shape = CircleShape, color = c.error, contentColor = Color.White, modifier = Modifier.weight(1f).heightIn(min = 76.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.delete_confirm), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
