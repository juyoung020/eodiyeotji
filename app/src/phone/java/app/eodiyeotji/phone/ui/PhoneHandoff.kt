package app.eodiyeotji.phone.ui

import app.eodiyeotji.ui.common.eodiShadow
import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.shadow
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.theme.EodiIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.eodiyeotji.MainActivity
import app.eodiyeotji.R
import app.eodiyeotji.core.rust.ShareCard as RShareCard
import app.eodiyeotji.handoff.HandoffRepository
import app.eodiyeotji.place.PlacePhotoRepository
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.community.SectionTitle
import app.eodiyeotji.ui.share.SHARE_CARD_WIDTH_DP
import app.eodiyeotji.ui.share.ShareCardView
import app.eodiyeotji.ui.share.ShareIcon
import app.eodiyeotji.ui.share.loadCardPhotos
import app.eodiyeotji.ui.share.rememberImageSharer
import app.eodiyeotji.ui.share.toModel
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PhoneHandoffEntryPoint {
    fun handoff(): HandoffRepository
    fun photos(): PlacePhotoRepository
    fun parking(): app.eodiyeotji.phone.PhoneParking
}

@Composable
private fun handoffDeps(): PhoneHandoffEntryPoint {
    val context = LocalContext.current
    return remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, PhoneHandoffEntryPoint::class.java) }
}

/**
 * 폰 앱이 떠 있는 동안: 차가 보낸 카드를 받아 알림을 띄우고, "차에서 온 공유" 시트로 보여 준다 → 카카오톡으로 보내기.
 * QR(eodiyeotji://pair?c=코드)로 앱이 열리면 그 코드로 바로 연결한다.
 */
@Composable
fun PhoneHandoffHost() {
    val context = LocalContext.current
    val deps = handoffDeps()
    val repo = deps.handoff()
    val link by repo.link.collectAsState()
    var inbox by remember { mutableStateOf<List<RShareCard>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // QR로 열렸으면 연결 (처음 열 때의 intent + 이미 떠 있을 때 새로 온 intent)
    var pairUri by remember { mutableStateOf((context as? Activity)?.intent?.data) }
    DisposableEffect(context) {
        val a = context as? ComponentActivity
        val l = Consumer<Intent> { pairUri = it.data }
        a?.addOnNewIntentListener(l)
        onDispose { a?.removeOnNewIntentListener(l) }
    }
    LaunchedEffect(pairUri) {
        val data = pairUri ?: return@LaunchedEffect
        if (data.scheme == "eodiyeotji" && data.host == "pair") {
            val code = data.getQueryParameter("c") ?: return@LaunchedEffect
            val ok = repo.pairWithCode(code)
            Toast.makeText(context, if (ok) R.string.handoff_paired else R.string.handoff_phone_wrong, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        while (true) {
            repo.listen(
                onCard = { card ->
                    notify(context, card)
                    scope.launch { inbox = inbox + card }
                },
                onParked = { car ->
                    deps.parking().onReceived(car)
                    notifyParked(context, car)
                },
                onMoving = { t -> deps.parking().onMoving(t) },
            )
            delay(RETRY_MS)
        }
    }
    inbox.firstOrNull()?.let { card ->
        InboxSheet(card, deps.photos()) { inbox = inbox.drop(1) }
    }
}

@Composable
private fun InboxSheet(raw: RShareCard, photosRepo: PlacePhotoRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    val card = remember(raw) { raw.toModel() }
    var photos by remember(raw) { mutableStateOf<Map<Int, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(raw) { photos = loadCardPhotos(context.applicationContext, photosRepo, card) }
    val sharer = rememberImageSharer()
    val c = EodiTheme.colors
    val type = EodiTheme.type
    PhoneSheet(onDismiss = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 28.dp)) {
            // 머리: 어디서 왔는지 + 제목 + 닫기
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.handoff_inbox_eyebrow),
                        style = type.eyebrow,
                        color = c.accent,
                        modifier = Modifier.background(c.accent.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(card.title, style = type.title, maxLines = 2)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(R.string.handoff_inbox_meta, card.dateText, card.stops.size),
                        style = type.meta,
                        color = c.textSecondary,
                    )
                }
                RoundIconButton(EodiIcons.Close, stringResource(R.string.settings_close), onClose)
            }
            Spacer(Modifier.size(24.dp))
            // 보낼 카드 미리보기 (받는 사람이 보는 그대로)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceHigh).padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(300.dp).eodiShadow(EodiTheme.elevation.level3, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))) {
                    ShareCardView(card, photos)
                }
            }
            if (raw.routeUrl.isNotBlank()) {
                Spacer(Modifier.size(14.dp))
                Text(stringResource(R.string.handoff_inbox_route_hint), style = type.caption, color = c.textTertiary)
            }
            Spacer(Modifier.size(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (raw.routeUrl.isNotBlank()) {
                    SecondaryPill(
                        stringResource(R.string.handoff_inbox_route),
                        null,
                        { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw.routeUrl))) } },
                        Modifier.weight(1f),
                    )
                }
                PrimaryPill(
                    stringResource(R.string.handoff_inbox_send),
                    ShareIcon,
                    {
                        sharer.share(
                            fileName = "eodiyeotji-car-${card.day}",
                            widthDp = SHARE_CARD_WIDTH_DP,
                            prepare = { card to photos },
                            text = { it.first.shareText },
                            onDone = { _, ok -> if (ok) onClose() },
                        ) { (cd, p) -> ShareCardView(cd, p) }
                    },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** 설정 시트 안 "차와 연결" 칸. */
@Composable
fun CarLinkSection() {
    val context = LocalContext.current
    val repo = handoffDeps().handoff()
    val link by repo.link.collectAsState()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val c = EodiTheme.colors
    val type = EodiTheme.type
    SectionTitle(stringResource(R.string.handoff_phone_title), Modifier.padding(start = 4.dp, bottom = 12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(EodiTheme.space.radiusM)).background(c.surfaceHigh).padding(20.dp)) {
        if (link != null) {
            // 연결됨: 초록 점 + 글 한 줄, 끊기는 오른쪽 (전에는 회색 칸 안 회색 알약이라 글자만 떠 보였다)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(c.here, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.size(14.dp))
                Text(stringResource(R.string.handoff_phone_linked), style = type.bodyStrong, modifier = Modifier.weight(1f))
                androidx.compose.material3.Surface(
                    onClick = { repo.unlink() },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = c.surface,
                    contentColor = c.textPrimary,
                    modifier = Modifier.heightIn(min = EodiTheme.space.touch),
                ) {
                    Box(Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.handoff_phone_unlink), style = type.label, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        } else {
            Text(stringResource(R.string.handoff_phone_hint), style = type.meta, color = c.textSecondary)
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    textStyle = type.titleSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(12.dp))
                PrimaryPill(
                    stringResource(R.string.handoff_phone_connect),
                    null,
                    {
                        busy = true
                        scope.launch {
                            val ok = repo.pairWithCode(code)
                            busy = false
                            Toast.makeText(context, if (ok) R.string.handoff_paired else R.string.handoff_phone_wrong, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = code.length == 6 && !busy,
                )
            }
        }
    }
}

private fun notify(context: Context, card: RShareCard) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.handoff_channel), NotificationManager.IMPORTANCE_HIGH))
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val n = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.handoff_notify_title, card.title))
        .setContentText(context.getString(R.string.handoff_notify_body))
        .setContentIntent(open)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    nm.notify(card.day.hashCode() xor card.title.hashCode(), n)
}

/** 차가 P에 들어가며 보낸 주차 위치: "주차했어요 · 경포해변 공영주차장" + 가려던 곳이 있으면 "초당할머니순두부까지 걸어서 5분". */
private fun notifyParked(context: Context, car: app.eodiyeotji.core.rust.ParkedCar) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.handoff_channel), NotificationManager.IMPORTANCE_HIGH))
    val open = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val body = if (car.destName.isNotBlank()) {
        val g = app.eodiyeotji.core.rust.walkGuide(car.lat, car.lon, -1.0, car.destLat, car.destLon)
        "${car.destName}까지 걸어서 ${g.minutes}분 · 눌러서 길 보기"
    } else {
        "돌아올 때 눌러서 내 차 찾기"
    }
    val n = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("주차했어요 · ${car.name.ifBlank { "주차 위치 저장" }}")
        .setContentText(body)
        .setContentIntent(open)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    nm.notify(PARK_NOTIFICATION_ID, n)
}

private const val PARK_NOTIFICATION_ID = 7301
private const val CHANNEL = "handoff"
private const val RETRY_MS = 5_000L
