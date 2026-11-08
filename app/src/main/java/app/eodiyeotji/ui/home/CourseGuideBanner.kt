package app.eodiyeotji.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.pleos.CourseGuide
import app.eodiyeotji.pleos.CourseProgress
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class CourseGuideViewModel @Inject constructor(private val guide: CourseGuide) : ViewModel() {
    val progress: StateFlow<CourseProgress?> = guide.progress
    fun next() = guide.skipToNextLeg()
    fun retry() = guide.retry()
    fun stop() = guide.stop()
}

/**
 * 코스 안내 중 알림 카드 (화면 아래 가운데, 어느 탭에서든 보인다).
 * 내비 화면에서 앱으로 돌아왔을 때 "지금 몇 번째 구간이고 다음은 어디인지"를 바로 알 수 있게 한다.
 * 장소 하나로 가는 "길안내"도 같은 카드다. 다음 구간은 도착하면 알아서 이어지므로 버튼은 건너뛰기·안내 종료만 둔다
 * (안내 종료 = 내비 안내까지 끈다). 내비에서 안내를 끄면 카드가 닫히고 이 앱이 다시 앞으로 나온다 ([CourseGuide]).
 */
@Composable
fun CourseGuideBanner(
    modifier: Modifier = Modifier,
    viewModel: CourseGuideViewModel = hiltViewModel(),
    interactive: Boolean = true,
    compact: Boolean = false,
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    // 사라지는 동안에도 마지막 내용을 그린다
    val last = remember { arrayOfNulls<CourseProgress>(1) }
    progress?.let { last[0] = it }
    AnimatedVisibility(
        visible = progress != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            last[0]?.let {
                if (compact) {
                    CompactBannerCard(it, onNext = viewModel::next, onRetry = viewModel::retry, onStop = viewModel::stop)
                } else {
                    BannerCard(it, interactive, onNext = viewModel::next, onRetry = viewModel::retry, onStop = viewModel::stop)
                }
            }
        }
    }
}

@Composable
private fun BannerCard(p: CourseProgress, interactive: Boolean, onNext: () -> Unit, onRetry: () -> Unit, onStop: () -> Unit) {
    val c = EodiTheme.colors
    val title = bannerTitle(p)
    val detail = bannerDetail(p)
    // 테마와 상관없이 진한 남색 판 (라이트에서도 다크에서도 "지금 안내 중"이 화면 위에 떠 보인다)
    Surface(
        color = c.ink,
        contentColor = c.onInk,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).background(if (p.failed) c.error else c.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (p.finished) EodiIcons.Check else EodiIcons.Navi, null, tint = c.onAccent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!detail.isNullOrBlank()) {
                    Text(detail, style = EodiTheme.type.body, color = c.onInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!interactive) return@Row
            Spacer(Modifier.width(16.dp))
            when {
                p.finished -> BannerButton(stringResource(R.string.guide_close), null, primary = false, onStop)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (p.failed) {
                        BannerButton(stringResource(R.string.guide_retry), EodiIcons.Refresh, primary = true, onRetry)
                    } else if (p.hasNextLeg) {
                        BannerButton(stringResource(R.string.guide_skip), null, primary = true, onNext)
                    }
                    BannerButton(stringResource(R.string.guide_stop), null, primary = false, onStop)
                }
            }
        }
    }
}

/**
 * 폰 세로 화면: 글은 위, 버튼은 아래 줄. 지도 앱은 도착을 알려 주지 않으므로 "다음 구간"을 손으로 누른다는 안내를 붙인다.
 * 마지막 구간에서 "다음 구간"을 누르면 그 구간을 지도 앱에 다시 보낸다 ([CourseGuide.skipToNextLeg]).
 */
@Composable
private fun CompactBannerCard(p: CourseProgress, onNext: () -> Unit, onRetry: () -> Unit, onStop: () -> Unit) {
    val c = EodiTheme.colors
    val title = bannerTitle(p)
    val detail = bannerDetail(p)
    Surface(
        color = c.ink,
        contentColor = c.onInk,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(if (p.failed) c.error else c.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (p.finished) EodiIcons.Check else EodiIcons.Navi, null, tint = c.onAccent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!detail.isNullOrBlank()) {
                        Text(detail, style = EodiTheme.type.body, color = c.onInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (!p.finished && !p.failed && p.hasNextLeg) {
                        Text(stringResource(R.string.guide_manual_hint), style = EodiTheme.type.caption, color = c.onInkMuted, maxLines = 2)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                when {
                    p.finished -> BannerButton(stringResource(R.string.guide_close), null, primary = false, onStop)
                    else -> {
                        BannerButton(stringResource(R.string.guide_stop), null, primary = false, onStop)
                        when {
                            p.failed -> BannerButton(stringResource(R.string.guide_retry), EodiIcons.Refresh, primary = true, onRetry)
                            p.hasNextLeg -> BannerButton(stringResource(R.string.guide_skip), null, primary = true, onNext)
                            !p.single -> BannerButton(stringResource(R.string.guide_reopen), EodiIcons.Navi, primary = true, onNext)
                        }
                    }
                }
            }
        }
    }
}

/** 코스: "코스 안내 중 · 1/2 구간", 장소 하나: "길 안내 중 · 이름". */
@Composable
private fun bannerTitle(p: CourseProgress): String = when {
    p.finished && p.single -> stringResource(R.string.guide_place_finished)
    p.finished -> stringResource(R.string.guide_finished)
    p.single -> stringResource(R.string.guide_place_title, p.title.orEmpty())
    else -> stringResource(R.string.guide_title, p.leg, p.total)
}

@Composable
private fun bannerDetail(p: CourseProgress): String? = when {
    p.finished -> p.title
    p.failed -> stringResource(R.string.guide_failed)
    p.single -> stringResource(R.string.guide_place_hint)
    else -> listOfNotNull(
        p.upcoming.firstOrNull()?.let { stringResource(R.string.guide_next_stop, it) },
        p.nextLeg?.let { stringResource(R.string.guide_next_leg, it) } ?: stringResource(R.string.guide_last_leg),
    ).joinToString("  ·  ")
}

@Composable
private fun BannerButton(text: String, icon: ImageVector?, primary: Boolean, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (primary) c.accent else c.onInk.copy(alpha = 0.14f),
        contentColor = if (primary) c.onAccent else c.onInk,
        modifier = Modifier.heightIn(min = 76.dp),
    ) {
        Row(Modifier.padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
