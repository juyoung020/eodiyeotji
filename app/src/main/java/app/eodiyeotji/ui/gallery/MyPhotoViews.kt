package app.eodiyeotji.ui.gallery

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.eodiyeotji.R
import app.eodiyeotji.gallery.GalleryRepository
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 내 사진(갤러리) 작은 카드 · 크게 보기 · 처음 쓸 때 권한 묻기.
 * 썸네일은 Coil이 칸 크기([GalleryRepository.THUMB_PX])만큼만 줄여 디코딩한다 (원본을 통째로 올리지 않는다).
 */

/** 사진 한 칸: `content://` 사진이나 앱 안 사본 경로(`/data/…`) 모두. [sizePx]만큼 줄여 읽는다. */
@Composable
fun MyPhotoImage(source: String, modifier: Modifier = Modifier, sizePx: Int = GalleryRepository.THUMB_PX, crop: Boolean = true) {
    val context = LocalContext.current
    val request = remember(source, sizePx) {
        ImageRequest.Builder(context)
            .data(if (source.startsWith("/")) File(source) else source)
            .size(sizePx)
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * 들른 곳의 내 사진 줄: [size] 정사각 [maxShown]장 + 나머지는 "+N" 칸 (누르면 그다음 사진부터 크게). 누르면 [onOpen](몇 번째).
 * [total]은 맞은 전체 수 (넘겨받은 [uris]보다 많을 수 있다).
 */
@Composable
fun MyPhotoStrip(uris: List<String>, total: Int, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, size: Dp = 72.dp, maxShown: Int = 3) {
    val c = EodiTheme.colors
    val shown = uris.take(maxShown)
    val more = total - shown.size
    val shape = RoundedCornerShape(12.dp)
    val label = stringResource(R.string.my_photo_open)
    // 칸이 몇 개뿐이고 타임라인 줄이 내용 높이(intrinsic)를 재므로 Lazy 목록 대신 Row (Lazy는 intrinsic 측정을 못 한다)
    Row(modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEachIndexed { i, u ->
            key(u) {
                Box(
                    Modifier.size(size).clip(shape).background(c.surfaceHigh, shape)
                        .clickable(onClickLabel = label, role = Role.Image) { onOpen(i) },
                ) { MyPhotoImage(u, Modifier.fillMaxSize()) }
            }
        }
        if (more > 0) {
            Box(
                Modifier.size(size).clip(shape).background(c.staySoft, shape).clickable(onClickLabel = label) { onOpen(shown.size.coerceAtMost(uris.lastIndex)) },
                contentAlignment = Alignment.Center,
            ) { Text("+$more", style = EodiTheme.type.bodyStrong, color = c.stay) }
        }
    }
}

/** 크게 보기: 화면 가운데 큰 사진, 옆으로 넘기기, 닫기. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyPhotoViewer(sources: List<String>, start: Int, title: String?, onDismiss: () -> Unit) {
    val c = EodiTheme.colors
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (sources.size - 1).coerceAtLeast(0))) { sources.size }
    val px = with(LocalDensity.current) { 900.dp.roundToPx() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable(onClick = onDismiss)) {
            HorizontalPager(pager, Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 72.dp), key = { sources[it] }) { i ->
                MyPhotoImage(sources[i], Modifier.fillMaxSize(), sizePx = px, crop = false)
            }
            Text(
                listOfNotNull(title, "${pager.currentPage + 1} / ${sources.size}").joinToString(" · "),
                style = EodiTheme.type.bodyStrong,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(28.dp),
            )
            val close = stringResource(R.string.community_close)
            Box(
                Modifier.align(Alignment.TopEnd).padding(16.dp).size(64.dp).background(c.surface, CircleShape)
                    .clickable(onClick = onDismiss).semantics { contentDescription = close },
                contentAlignment = Alignment.Center,
            ) { Icon(EodiIcons.Close, null, tint = c.textPrimary, modifier = Modifier.size(32.dp)) }
        }
    }
}

/** 고르기 칸 (올리기 창): 사진 + 고르면 파란 테두리·체크. 기본은 안 고름. */
@Composable
fun MyPhotoPick(source: String, picked: Boolean, onToggle: () -> Unit, size: Dp = 88.dp) {
    val c = EodiTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(size).clip(shape).background(c.surfaceHigh, shape)
            .then(if (picked) Modifier.border(4.dp, c.accent, shape) else Modifier)
            .clickable(role = Role.Checkbox, onClick = onToggle),
    ) {
        MyPhotoImage(source, Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).background(if (picked) c.accent else Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (picked) Icon(EodiIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(20.dp)) }
    }
}

/**
 * 사진 기능을 처음 쓸 때 한 번만 권한을 묻는다 (설정 화면 없음). 이미 있거나 예전에 물었으면 아무것도 안 한다.
 * 거부하면 사진 없이 그대로 (다시 묻지 않는다). 허용하면 [onGranted] (화면이 사진을 다시 읽게).
 */
@Composable
fun AskGalleryOnce(enabled: Boolean, onGranted: () -> Unit) {
    val context = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) granted()
    }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val ask = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val repoGranted = GalleryRepository.REQUEST_PERMISSIONS.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (repoGranted || prefs.getBoolean(KEY_ASKED, false)) {
                false
            } else {
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
                true
            }
        }
        if (ask) launcher.launch(GalleryRepository.REQUEST_PERMISSIONS)
    }
}

private const val PREFS = "gallery"
private const val KEY_ASKED = "asked"

