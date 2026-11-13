package app.eodiyeotji.ui.share

import app.eodiyeotji.ui.common.eodiShadow
import app.eodiyeotji.ui.common.PressSurface
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eodiyeotji.ui.theme.EodiTheme
import kotlinx.coroutines.delay

/** 화면 아래에 잠깐 뜨는 알림 한 장. [action]이 있으면 오른쪽에 버튼([actionLabel])이 붙는다. */
@Stable
class ShareNotice internal constructor(val text: String, val actionLabel: String?, val action: (() -> Unit)?)

/**
 * 앱 안 알림(스낵바)의 상태. Pleos(AAOS) 화면에서는 `Toast`가 보이지 않아("Attempt to hide non-current toast")
 * 버튼을 눌러도 반응이 없는 것처럼 보이므로, 결과는 앱 창 안에 직접 그린다. 새 알림은 앞의 것을 바꿔 낀다.
 */
@Stable
class ShareNoticeState {
    var current by mutableStateOf<ShareNotice?>(null)
        private set

    fun show(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        current = ShareNotice(text, actionLabel, action)
    }

    fun dismiss(notice: ShareNotice) {
        if (current === notice) current = null
    }
}

/** 앱 안 알림을 띄우는 곳. MainActivity의 [ShareNoticeScope]가 공급하고, 그리는 자리는 판마다 [ShareNoticeHost]. */
val LocalShareNotice = staticCompositionLocalOf<ShareNoticeState?> { null }

/** 앱 전체에 [LocalShareNotice]를 공급한다 (MainActivity). */
@Composable
fun ShareNoticeScope(content: @Composable () -> Unit) {
    val state = remember { ShareNoticeState() }
    CompositionLocalProvider(LocalShareNotice provides state, content = content)
}

/**
 * 알림을 띄운다. 공급하는 곳이 없는 화면(디버그 창 등)에서는 예전처럼 `Toast`로 낸다.
 */
internal fun ShareNoticeState?.notify(context: Context, text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    if (this != null) show(text, actionLabel, action) else Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

/**
 * 알림을 그리는 자리: 화면 아래 가운데 어두운 카드가 몇 초(버튼이 있으면 조금 더) 떠 있다가 사라진다.
 * 판마다 화면 뼈대 맨 위에 한 번 둔다 (차 AppRoot · 폰 PhoneApp — 폰은 아래 탭을 피하게 [bottom]을 키운다).
 * 카드 밖은 터치를 가로채지 않는다.
 * 규격은 Pleos 가이드 components/toast.md: 앱 창 안 좌우 · 아래 24dp, 전체 최대 792dp, 글 최대 712dp.
 * (가이드 토스트는 메시지만 — 되돌리기 같은 버튼이 꼭 필요할 때만 오른쪽에 하나 붙인다.)
 */
@Composable
fun ShareNoticeHost(bottom: Dp = ToastMargin) {
    val state = LocalShareNotice.current ?: return
    val notice = state.current
    // 사라지는 동안에도 마지막 내용을 그린다 (상태로 두지 않는 보관함)
    val last = remember { arrayOfNulls<ShareNotice>(1) }
    if (notice != null) last[0] = notice
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        delay(if (notice.action != null) ACTION_MS else PLAIN_MS)
        state.dismiss(notice)
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = bottom), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            last[0]?.let { NoticeCard(it) { state.dismiss(it) } }
        }
    }
}

@Composable
private fun NoticeCard(n: ShareNotice, onDone: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    Surface(
        color = c.ink,
        contentColor = c.onInk,
        shape = RoundedCornerShape(EodiTheme.space.radiusM),
        modifier = Modifier.padding(horizontal = ToastMargin).widthIn(max = ToastMaxWidth)
            .eodiShadow(EodiTheme.elevation.level2, RoundedCornerShape(EodiTheme.space.radiusM))
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.heightIn(min = EodiTheme.space.touch).padding(start = 32.dp, end = if (n.action != null) 12.dp else 32.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(n.text, style = type.bodyStrong, modifier = Modifier.weight(1f, fill = false).widthIn(max = ToastTextMaxWidth))
            if (n.action != null && n.actionLabel != null) {
                PressSurface(
                    onClick = { onDone(); n.action.invoke() },
                    shape = CircleShape,
                    color = c.accent,
                    contentColor = c.onAccent,
                ) {
                    Box(Modifier.heightIn(min = EodiTheme.space.touch).padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                        Text(n.actionLabel, style = type.label)
                    }
                }
            }
        }
    }
}

/** 토스트 규격 (Pleos 가이드 components/toast.md) */
private val ToastMargin = 24.dp
private val ToastMaxWidth = 792.dp
private val ToastTextMaxWidth = 712.dp

private const val PLAIN_MS = 5_000L
private const val ACTION_MS = 8_000L
