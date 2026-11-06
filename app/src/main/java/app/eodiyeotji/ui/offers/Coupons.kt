package app.eodiyeotji.ui.offers

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.ClaimedCoupon
import app.eodiyeotji.core.CourseOffers
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.PartnerOffer
import app.eodiyeotji.core.claimOffer
import app.eodiyeotji.core.claimedCoupons
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.community.CenterMessage
import app.eodiyeotji.ui.community.LocalOverlayPresenter
import app.eodiyeotji.ui.community.OverlayCard
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.community.dateText
import app.eodiyeotji.ui.community.localDate
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * 제휴 혜택 · 할인 쿠폰 화면 조각 (docs/80_사업모델_수익성.md "앱 안에서 보이는 모습").
 * - [CouponBadge]: 장소 · 코스 카드에 붙는 작은 귤색 배지 (티켓 아이콘 + "10% 할인" / "쿠폰 2곳"). 누르면 [CouponSheet].
 * - [CouponSheet]: 제휴처 · 혜택 · 조건 · 기한 + "쿠폰 받기" → "받았어요"와 쿠폰 번호.
 * - [CouponWallet]: 내 장소 › 쿠폰함 (받은 쿠폰).
 * 어느 장소에 어떤 쿠폰이 붙는지는 Rust(offers.rs)가 정하고, 여기서는 그리기만 한다.
 * 운전 중에는 창을 띄우지 않는다: 배지는 보이되 눌리지 않는다.
 */

/** 받은 쿠폰 · 주행 상태 (앱 전체에서 하나, [CouponScope]가 넣는다). */
@HiltViewModel
class CouponViewModel @Inject constructor(
    private val core: EodiCore,
    driving: DrivingStatePort,
) : ViewModel() {
    val isDriving: StateFlow<Boolean> = driving.isDriving
    private val _claimed = MutableStateFlow<List<ClaimedCoupon>>(emptyList())

    /** 받은 쿠폰 (최근에 받은 것부터, Rust가 정렬). */
    val claimed: StateFlow<List<ClaimedCoupon>> = _claimed.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { core.claimedCoupons() }
                .onSuccess { _claimed.value = it }
                .onFailure { Log.w(TAG, "쿠폰함 읽기 실패", it) }
        }
    }

    fun claim(offer: PartnerOffer) {
        viewModelScope.launch {
            runCatching { core.claimOffer(offer.id) }
                .onSuccess { refresh() }
                .onFailure { Log.w(TAG, "쿠폰 받기 실패: ${offer.id}", it) }
        }
    }

    private companion object {
        const val TAG = "EodiCoupons"
    }
}

/** 쿠폰 창을 띄울 수 있는 곳 (없으면 배지만 보이고 눌리지 않는다 — 미리보기 · 디버그 화면). */
val LocalCoupons = staticCompositionLocalOf<CouponViewModel?> { null }

/** 앱 전체를 감싼다 (MainActivity): 어느 화면의 배지든 같은 쿠폰함 · 주행 상태를 본다. */
@Composable
fun CouponScope(viewModel: CouponViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCoupons provides viewModel, content = content)
}

// ---------------------------------------------------------------- 배지

/** 장소 하나의 쿠폰 배지 ("10% 할인", 둘 이상이면 "10% 할인 외 1"). 쿠폰이 없으면 아무것도 그리지 않는다. */
@Composable
fun PlaceCouponBadge(offers: List<PartnerOffer>, modifier: Modifier = Modifier) {
    val first = offers.firstOrNull() ?: return
    val text = if (offers.size > 1) stringResource(R.string.coupon_more, first.badge, offers.size - 1) else first.badge
    CouponBadge(text, offers, modifier)
}

/** 코스의 쿠폰 배지 ("쿠폰 2곳"). 쿠폰이 없으면 아무것도 그리지 않는다. */
@Composable
fun CourseCouponBadge(offers: CourseOffers, modifier: Modifier = Modifier) {
    if (offers.placeCount <= 0 || offers.offers.isEmpty()) return
    CouponBadge(stringResource(R.string.coupon_places, offers.placeCount), offers.offers, modifier, courseCount = offers.placeCount)
}

/**
 * 귤색 쿠폰 배지. 보이는 알약은 작지만 누르는 자리는 세로 60dp까지 넓힌다 (카드 안 작은 동작).
 * 운전 중이거나 [LocalCoupons]가 없으면 눌리지 않는다.
 */
@Composable
fun CouponBadge(text: String, offers: List<PartnerOffer>, modifier: Modifier = Modifier, courseCount: Int? = null) {
    val c = EodiTheme.colors
    val host = LocalCoupons.current
    val driving = host?.isDriving?.collectAsStateWithLifecycle()?.value ?: false
    val interactive = host != null && !driving && offers.isNotEmpty()
    var open by rememberSaveable { mutableStateOf(false) }
    val pill: @Composable () -> Unit = {
        Row(
            Modifier.background(c.couponSoft, CircleShape).padding(start = 10.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CouponIcons.Ticket, null, tint = c.coupon, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = EodiTheme.type.caption, fontWeight = FontWeight.Bold, color = c.couponInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (!interactive) {
        Box(modifier) { pill() }
    } else {
        val label = stringResource(R.string.coupon_open)
        Box(
            modifier
                .heightIn(min = 60.dp)
                .clip(RoundedCornerShape(EodiTheme.space.radiusS))
                .clickable(role = Role.Button, onClickLabel = label) { open = true },
            contentAlignment = Alignment.CenterStart,
        ) { pill() }
    }
    if (open && interactive) {
        CouponSheet(offers, host, courseCount, onDismiss = { open = false })
    }
}

// ---------------------------------------------------------------- 쿠폰 창

/** 쿠폰 창. 폰은 아래 시트, 차는 화면 가운데 카드 (대화상자 창 — 카드 안 배지에서 열어도 화면 전체를 덮게). */
@Composable
fun CouponSheet(offers: List<PartnerOffer>, host: CouponViewModel, courseCount: Int?, onDismiss: () -> Unit) {
    val content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = { CouponSheetContent(offers, host, courseCount, onDismiss) }
    if (LocalOverlayPresenter.current != null) {
        OverlayCard(onDismiss = onDismiss, content = content)
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = RoundedCornerShape(EodiTheme.space.radiusL),
                color = EodiTheme.colors.surface,
                contentColor = EodiTheme.colors.textPrimary,
                modifier = Modifier.widthIn(max = 900.dp).padding(32.dp),
            ) {
                Box(content = content)
            }
        }
    }
}

@Composable
private fun CouponSheetContent(offers: List<PartnerOffer>, host: CouponViewModel, courseCount: Int?, onDismiss: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val claimed by host.claimed.collectAsStateWithLifecycle()
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 36.dp, end = 36.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(c.couponSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(CouponIcons.Ticket, null, tint = c.coupon, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                if (courseCount != null) stringResource(R.string.coupon_sheet_course, courseCount) else stringResource(R.string.coupon_sheet_title),
                style = type.title,
                modifier = Modifier.semantics { heading() },
            )
        }
        offers.forEach { o ->
            val mine = claimed.firstOrNull { it.offer.id == o.id }
            OfferTicket(o, mine, onClaim = { host.claim(o) })
        }
        Text(stringResource(R.string.coupon_partner_note), style = type.caption, color = c.textTertiary)
        SecondaryPill(stringResource(R.string.coupon_close), null, onDismiss, Modifier.fillMaxWidth())
    }
}

/** 쿠폰 한 장: 왼쪽 귤색 칸(배지) | 점선 | 제휴처 · 혜택 · 조건 · 기한 + 받기 버튼 (받았으면 쿠폰 번호). */
@Composable
private fun OfferTicket(o: PartnerOffer, mine: ClaimedCoupon?, onClaim: (() -> Unit)?) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val shape = RoundedCornerShape(EodiTheme.space.radiusM)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).border(2.dp, c.couponSoft, shape).background(c.surface),
    ) {
        Column(
            Modifier.width(168.dp).fillMaxHeight().background(c.couponSoft).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(CouponIcons.Ticket, null, tint = c.coupon, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(o.badge, style = type.label, fontWeight = FontWeight.ExtraBold, color = c.couponInk, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Column(Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(listOf(o.categoryLabel, o.validText).joinToString(" · "), style = type.caption, color = c.coupon, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(o.partnerName, style = type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(o.title, style = type.body, color = c.textPrimary)
            Text(o.conditions, style = type.meta, color = c.textSecondary)
            Spacer(Modifier.height(10.dp))
            when {
                mine != null -> ClaimedCode(mine)
                onClaim != null -> PrimaryPill(stringResource(R.string.coupon_claim), CouponIcons.Ticket, onClaim, Modifier.fillMaxWidth())
            }
        }
    }
}

/** "받았어요 · 쿠폰 번호 EODI-7K3Q" (매장에서 보여 준다). */
@Composable
private fun ClaimedCode(mine: ClaimedCoupon) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    Column(
        Modifier.fillMaxWidth().background(c.hereSoft, RoundedCornerShape(EodiTheme.space.radiusM)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(app.eodiyeotji.ui.community.CommunityIcons.Check, null, tint = c.here, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.coupon_claimed), style = type.label, fontWeight = FontWeight.Bold, color = c.here)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.coupon_code_label), style = type.caption, color = c.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text(mine.code, style = type.bodyStrong, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1)
        }
        Text(stringResource(R.string.coupon_show_at_store), style = type.caption, color = c.textSecondary)
    }
}

// ---------------------------------------------------------------- 쿠폰함 (내 장소)

/** 받은 쿠폰 목록. 넓으면 두 칸. */
@Composable
fun CouponWallet(compact: Boolean, modifier: Modifier = Modifier) {
    val host = LocalCoupons.current
    val claimed = host?.claimed?.collectAsStateWithLifecycle()?.value.orEmpty()
    if (claimed.isEmpty()) {
        CenterMessage(stringResource(R.string.coupon_wallet_empty), modifier)
        return
    }
    LazyVerticalGrid(
        columns = if (compact) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 620.dp),
        contentPadding = PaddingValues(bottom = EodiTheme.space.page),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        items(claimed, key = { it.offer.id }) { mine ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OfferTicket(mine.offer, mine, onClaim = null)
                Text(
                    stringResource(R.string.coupon_claimed_on, dateText(localDate(mine.claimedMs))),
                    style = EodiTheme.type.caption,
                    color = EodiTheme.colors.textTertiary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 아이콘

object CouponIcons {
    private val Ink = SolidColor(Color.Black)

    /** 쿠폰 티켓: 양옆이 파인 둥근 표 + 가운데 점선. */
    val Ticket: ImageVector by lazy {
        ImageVector.Builder("Ticket", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4.5f, 6f)
                lineTo(19.5f, 6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(21.5f, 10f)
                arcToRelative(2f, 2f, 0f, false, false, 0f, 4f)
                lineTo(21.5f, 16f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(4.5f, 18f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(2.5f, 14f)
                arcToRelative(2f, 2f, 0f, false, false, 0f, -4f)
                lineTo(2.5f, 8f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
            }
            path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(15f, 8.6f); lineTo(15f, 9.4f)
                moveTo(15f, 11.6f); lineTo(15f, 12.4f)
                moveTo(15f, 14.6f); lineTo(15f, 15.4f)
            }
        }.build()
    }
}
