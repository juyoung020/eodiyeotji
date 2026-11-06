package app.eodiyeotji.ui.places

import app.eodiyeotji.core.PartnerOffer
import app.eodiyeotji.ui.offers.CouponWallet
import app.eodiyeotji.ui.offers.LocalCoupons
import app.eodiyeotji.ui.offers.PlaceCouponBadge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.AnchorInfo
import app.eodiyeotji.core.AnchorRole
import app.eodiyeotji.core.AnchorSource
import app.eodiyeotji.core.HiddenKind
import app.eodiyeotji.core.HiddenRecord
import app.eodiyeotji.core.PlaceCard
import app.eodiyeotji.ui.community.CenterMessage
import app.eodiyeotji.ui.community.ChoiceRow
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.LoadingBox
import app.eodiyeotji.ui.community.Notice
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.place.PhotoLoadingPlaceholder
import app.eodiyeotji.ui.place.PlaceThumbnail
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.clockText
import java.time.LocalDate

/**
 * 내 장소 화면: [저장한 곳 | 자주 가는 곳 | 집 · 회사 · 숨긴 기록].
 * - 차: 왼쪽 레일 "내 장소"에서 연다 (머리 높이 104dp, 카드는 넓으면 두 칸).
 * - 폰: 내 기록 머리의 ♥ 버튼에서 연다 ([onBack]이 있으면 뒤로 버튼).
 * [onOpenRecords]: "기록 보기" → 그 장소 이름으로 내 기록 검색을 연다.
 */
@Composable
fun PlacesRoute(
    onOpenRecords: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    compact: Boolean = false,
    viewModel: PlacesViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val c = EodiTheme.colors
    val space = EodiTheme.space
    if (onBack != null) BackHandler(onBack = onBack)
    // 화면에 들어올 때마다 다시 읽는다 (다른 화면에서 저장 · 숨기기 · 쿠폰 받기를 했을 수 있다)
    val coupons = LocalCoupons.current
    LaunchedEffect(Unit) {
        viewModel.reload()
        coupons?.refresh()
    }
    Surface(Modifier.fillMaxSize(), color = c.background, contentColor = c.textPrimary) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Header(onBack, compact)
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = space.page)) {
                    ChoiceRow(
                        options = listOf(
                            PlacesTab.SAVED to stringResource(R.string.places_tab_saved),
                            PlacesTab.FREQUENT to stringResource(R.string.places_tab_frequent),
                            PlacesTab.COUPONS to stringResource(R.string.places_tab_coupons),
                            PlacesTab.SETTINGS to stringResource(R.string.places_tab_settings),
                        ),
                        selected = s.tab,
                        onSelect = viewModel::setTab,
                        big = !compact,
                    )
                }
                Spacer(Modifier.height(20.dp))
                val body = Modifier.fillMaxSize().padding(horizontal = space.page)
                when {
                    !s.loaded -> LoadingBox(body)
                    s.tab == PlacesTab.SAVED -> CardGrid(s.saved, s.offers, compact, R.string.places_saved_empty, viewModel, onOpenRecords, body)
                    s.tab == PlacesTab.FREQUENT -> CardGrid(s.frequent, s.offers, compact, R.string.places_frequent_empty, viewModel, onOpenRecords, body)
                    s.tab == PlacesTab.COUPONS -> CouponWallet(compact, body)
                    else -> SettingsPane(s, viewModel, compact, body)
                }
            }
            s.notice?.let { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { Notice(stringResource(it)) } }
        }
    }
}

@Composable
private fun Header(onBack: (() -> Unit)?, compact: Boolean) {
    val c = EodiTheme.colors
    Row(
        Modifier.fillMaxWidth().height(if (compact) 96.dp else 104.dp).padding(horizontal = EodiTheme.space.page),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val description = stringResource(R.string.timeline_back_description)
            Surface(onClick = onBack, shape = CircleShape, color = c.surfaceHigh, modifier = Modifier.size(EodiTheme.space.touch).semantics { contentDescription = description }) {
                Box(contentAlignment = Alignment.Center) { Icon(EodiIcons.Back, null, modifier = Modifier.size(36.dp)) }
            }
            Spacer(Modifier.width(20.dp))
        }
        Icon(CommunityIcons.HeartFilled, null, tint = c.heart, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.places_title),
            style = EodiTheme.type.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() },
        )
    }
}

// ---------------------------------------------------------------- 장소 카드

@Composable
private fun CardGrid(
    cards: List<PlaceCard>,
    offers: Map<Long, List<PartnerOffer>>,
    compact: Boolean,
    empty: Int,
    vm: PlacesViewModel,
    onOpenRecords: (String) -> Unit,
    modifier: Modifier,
) {
    if (cards.isEmpty()) {
        CenterMessage(stringResource(empty), modifier)
        return
    }
    LazyVerticalGrid(
        columns = if (compact) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 620.dp),
        contentPadding = PaddingValues(bottom = EodiTheme.space.page),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        items(cards, key = { it.id }) { card ->
            PlaceCardView(
                card,
                offers = offers[card.id].orEmpty(),
                onHeart = { vm.toggleSaved(card) },
                onNavigate = { vm.navigate(card) },
                onRecords = card.label?.name?.let { n -> { onOpenRecords(n) } },
            )
        }
    }
}

/** 장소 카드: [사진] 이름 / 분류 · 주소 / 다녀온 날 · [♥] + [여기로 안내] [기록 보기]. */
@Composable
private fun PlaceCardView(card: PlaceCard, offers: List<PartnerOffer>, onHeart: () -> Unit, onNavigate: () -> Unit, onRecords: (() -> Unit)?) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val label = card.label
    Surface(color = c.surface, contentColor = c.textPrimary, shape = RoundedCornerShape(EodiTheme.space.radiusL)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(EodiTheme.space.radiusM)).background(c.staySoft), contentAlignment = Alignment.Center) {
                    val pin: @Composable () -> Unit = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(EodiIcons.Pin, null, tint = c.stay.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
                        }
                    }
                    if (label != null) {
                        PlaceThumbnail(label.name, label.address, card.lat, card.lon, Modifier.fillMaxSize(), fallback = pin, loading = { PhotoLoadingPlaceholder(c.staySoft) })
                    } else {
                        pin()
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label?.name ?: stringResource(R.string.edit_unknown_place), style = type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(label?.category, label?.address).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = type.meta, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    visitLine(card)?.let { Text(it, style = type.meta, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    PlaceCouponBadge(offers)
                }
                Spacer(Modifier.width(12.dp))
                val desc = stringResource(if (card.saved) R.string.places_unsave else R.string.places_save)
                Surface(
                    onClick = onHeart,
                    shape = CircleShape,
                    color = if (card.saved) c.heartSoft else c.surfaceHigh,
                    contentColor = if (card.saved) c.heart else c.textSecondary,
                    modifier = Modifier.size(EodiTheme.space.touch).semantics { contentDescription = desc },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (card.saved) CommunityIcons.HeartFilled else CommunityIcons.Heart, null, Modifier.size(34.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryPill(stringResource(R.string.edit_navigate), EodiIcons.Navi, onNavigate, Modifier.weight(1f))
                if (onRecords != null) SecondaryPill(stringResource(R.string.places_records), app.eodiyeotji.ui.agent.AgentIcons.Calendar, onRecords, Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- 집 · 회사 · 숨긴 기록

@Composable
private fun SettingsPane(s: PlacesUiState, vm: PlacesViewModel, compact: Boolean, modifier: Modifier) {
    val c = EodiTheme.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = PaddingValues(bottom = EodiTheme.space.page),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        full("anchor-h") { SectionTitle(stringResource(R.string.anchor_section)) }
        full("home") { AnchorRow(AnchorRole.HOME, s.homeOffice.home, onReset = { vm.resetAnchor(AnchorRole.HOME) }) }
        full("office") { AnchorRow(AnchorRole.OFFICE, s.homeOffice.office, onReset = { vm.resetAnchor(AnchorRole.OFFICE) }) }
        full("anchor-hint") { Text(stringResource(R.string.anchor_hint), style = EodiTheme.type.caption, color = c.textTertiary, modifier = Modifier.padding(start = 4.dp)) }
        full("hidden-h") { Box(Modifier.padding(top = 20.dp)) { SectionTitle(stringResource(R.string.hidden_section)) } }
        if (s.hidden.isEmpty()) {
            full("hidden-empty") { Text(stringResource(R.string.hidden_empty), style = EodiTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp)) }
        } else {
            items(s.hidden, key = { "h${it.id}" }) { r -> HiddenRow(r, compact, onRestore = { vm.restore(r) }) }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = EodiTheme.type.eyebrow, color = EodiTheme.colors.textTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun AnchorRow(role: AnchorRole, info: AnchorInfo?, onReset: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    Surface(color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusM)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (role == AnchorRole.HOME) PlacesIcons.Home else PlacesIcons.Office, null, tint = c.accent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (role == AnchorRole.HOME) R.string.anchor_home else R.string.anchor_office), style = type.label, color = c.textTertiary)
                Text(
                    when {
                        info == null -> stringResource(R.string.anchor_unknown)
                        info.name.isBlank() -> stringResource(R.string.anchor_unnamed)
                        else -> info.name
                    },
                    style = type.bodyStrong,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info != null) {
                    Text(
                        stringResource(
                            when (info.source) {
                                AnchorSource.USER -> R.string.anchor_source_user
                                AnchorSource.NAVI -> R.string.anchor_source_navi
                                AnchorSource.GUESSED -> R.string.anchor_source_guessed
                            },
                        ),
                        style = type.caption,
                        color = if (info.source == AnchorSource.USER) c.accent else c.textTertiary,
                    )
                }
            }
            if (info?.source == AnchorSource.USER) {
                Spacer(Modifier.width(12.dp))
                SecondaryPill(stringResource(R.string.anchor_reset), PlacesIcons.Restore, onReset)
            }
        }
    }
}

@Composable
private fun HiddenRow(r: HiddenRecord, compact: Boolean, onRestore: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val kind = stringResource(
        when (r.kind) {
            HiddenKind.TRIP -> R.string.hidden_kind_trip
            HiddenKind.STAY -> R.string.hidden_kind_stay
            HiddenKind.DAY -> R.string.hidden_kind_day
        },
    )
    val day = runCatching { LocalDate.parse(r.day) }.getOrNull()?.let { shortDay(it) } ?: r.day
    val whenText = if (r.kind == HiddenKind.DAY) day else "$day ${clockText(r.fromMs)}"
    Surface(color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusM)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PlacesIcons.Hide, null, tint = c.textTertiary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("$kind · $whenText", style = type.label, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.label.isNotBlank()) {
                    Text(r.label, style = type.bodyStrong, maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            SecondaryPill(stringResource(R.string.hidden_restore), PlacesIcons.Restore, onRestore)
        }
    }
}
