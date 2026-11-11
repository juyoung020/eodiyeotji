package app.eodiyeotji.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.eodiyeotji.R
import app.eodiyeotji.core.rust.HandoffPairing
import app.eodiyeotji.core.rust.qrMatrix
import app.eodiyeotji.ui.community.LocalOverlayPresenter
import app.eodiyeotji.ui.community.OverlayCard
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 차 판의 공유 버튼: 차에는 카카오톡이 없으므로 연결된 폰으로 카드를 보낸다 (폰 알림 → 카톡).
 * 아직 폰이 연결되지 않았으면 먼저 연결 창(6자리 코드 + QR)을 띄우고, 연결되면 바로 보낸다.
 */
@Composable
internal fun CarHandoffButton(target: ShareTarget, modifier: Modifier, style: ShareButtonStyle) {
    val context = LocalContext.current
    val deps = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, ShareEntryPoint::class.java) }
    val handoff = deps.handoff()
    val link by handoff.link.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf(false) }
    val notice = LocalShareNotice.current

    // 결과는 앱 안 알림으로 (Pleos 화면에서는 Toast가 보이지 않는다). 보내는 동안에는 다시 보내지 않는다.
    fun send() {
        if (busy) return
        busy = true
        scope.launch {
            val ok = runCatching { handoff.sendCard(deps.core().shareCardRaw(target)) }.getOrDefault(false)
            busy = false
            if (ok) {
                notice.notify(context, context.getString(R.string.handoff_sent))
            } else {
                notice.notify(context, context.getString(R.string.handoff_send_failed), context.getString(R.string.handoff_retry)) { send() }
            }
        }
    }
    val onClick = { if (!busy) { if (link == null) pairing = true else send() } }

    when (style) {
        ShareButtonStyle.PILL -> SecondaryPill(
            stringResource(if (busy) R.string.handoff_sending else R.string.handoff_button),
            ShareIcon,
            onClick,
            modifier,
            enabled = !busy,
        )
        // 아이콘만으로는 무슨 버튼인지 몰라 짧은 글자("폰으로")를 같이 붙인다
        ShareButtonStyle.ICON -> {
            val c = EodiTheme.colors
            val label = stringResource(R.string.handoff_button)
            Surface(
                onClick = onClick,
                enabled = !busy,
                shape = CircleShape,
                color = c.surfaceHigh,
                contentColor = c.textPrimary,
                modifier = modifier.height(EodiTheme.space.touch).semantics { contentDescription = label },
            ) {
                Row(
                    Modifier.padding(start = 20.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(30.dp), color = c.accent, strokeWidth = 3.dp)
                    else Icon(ShareIcon, null, Modifier.size(30.dp), tint = c.accent)
                    Text(
                        stringResource(if (busy) R.string.handoff_sending else R.string.handoff_icon_label),
                        style = EodiTheme.type.label,
                        color = c.textPrimary,
                    )
                }
            }
        }
    }

    if (pairing) {
        PairPhoneWindow(
            onDone = { ok ->
                pairing = false
                if (ok) send()
            },
        )
    }
}

/** 폰 연결 창: 코드를 만들어 띄우고 폰의 인사를 기다린다. */
@Composable
private fun PairPhoneWindow(onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val handoff = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, ShareEntryPoint::class.java).handoff() }
    val notice = LocalShareNotice.current
    var p by remember { mutableStateOf<HandoffPairing?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val made = runCatching { handoff.startPairing() }.getOrNull()
        if (made == null) {
            failed = true
            return@LaunchedEffect
        }
        p = made
        if (handoff.awaitPhone(made)) {
            notice.notify(context, context.getString(R.string.handoff_paired))
            onDone(true)
        } else {
            failed = true
        }
    }
    val content: @Composable BoxScope.() -> Unit = { PairContent(p, failed) { onDone(false) } }
    if (LocalOverlayPresenter.current != null) {
        OverlayCard(onDismiss = { onDone(false) }, content = content)
    } else {
        Dialog(onDismissRequest = { onDone(false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(shape = RoundedCornerShape(EodiTheme.space.radiusL), color = EodiTheme.colors.surface, modifier = Modifier.widthIn(max = 1000.dp).padding(32.dp)) {
                Box(content = content)
            }
        }
    }
}

@Composable
private fun PairContent(p: HandoffPairing?, failed: Boolean, onClose: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.widthIn(max = 520.dp)) {
            Text(stringResource(R.string.handoff_pair_title), style = type.title)
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.handoff_pair_body), style = type.body, color = c.textSecondary)
            Spacer(Modifier.size(32.dp))
            when {
                failed -> Text(stringResource(R.string.handoff_pair_failed), style = type.bodyStrong, color = c.textSecondary)
                p == null -> CircularProgressIndicator(color = c.accent)
                else -> {
                    Text(stringResource(R.string.handoff_pair_code), style = type.label, color = c.textTertiary)
                    Text(p.code.chunked(3).joinToString(" "), fontSize = 88.sp, fontWeight = FontWeight.ExtraBold, color = c.accent, letterSpacing = 4.sp)
                    Spacer(Modifier.size(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 3.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.handoff_pair_waiting), style = type.meta, color = c.textSecondary)
                    }
                }
            }
            Spacer(Modifier.size(32.dp))
            SecondaryPill(stringResource(R.string.handoff_pair_cancel), null, onClose)
        }
        if (p != null && !failed) QrBox("eodiyeotji://pair?c=${p.code}")
    }
}

/** QR (흰 바탕 · 여백 4칸). 칸 계산은 Rust. */
@Composable
private fun QrBox(text: String) {
    var m by remember(text) { mutableStateOf<app.eodiyeotji.core.rust.QrMatrix?>(null) }
    LaunchedEffect(text) { m = withContext(Dispatchers.Default) { runCatching { qrMatrix(text) }.getOrNull() } }
    val q = m ?: return
    Box(Modifier.size(320.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(20.dp)) {
        Canvas(Modifier.size(280.dp)) {
            val n = q.size.toInt()
            val cell = size.minDimension / n
            for (y in 0 until n) for (x in 0 until n) {
                if (q.modules[y * n + x]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/**
 * 차 판: 휴대폰이 연결돼 있으면 기록을 폰으로 계속 맞춘다 (처음 연결 직후 · 앱이 떠 있는 동안 [SYNC_EVERY_MS]마다).
 * 보낼 새 기록이 없으면 아무것도 보내지 않는다 (handoff.HandoffRepository.syncNow).
 */
@Composable
fun HandoffSyncLoop() {
    val context = LocalContext.current
    val handoff = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, ShareEntryPoint::class.java).handoff() }
    val link by handoff.link.collectAsState()
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        while (true) {
            handoff.syncNow()
            kotlinx.coroutines.delay(SYNC_EVERY_MS)
        }
    }
}

private const val SYNC_EVERY_MS = 5 * 60_000L
