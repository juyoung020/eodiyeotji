package app.eodiyeotji.ui.share

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.R
import app.eodiyeotji.ui.theme.EodiTheme
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 아무 Compose 카드를 이미지 한 장으로 그려 공유 시트(카카오톡 · 메시지 등)로 보낸다. 하루 · 코스 카드([ShareImageButton])와
 * 월간 · 연간 결산 카드가 같이 쓴다.
 *
 * ```
 * val sharer = rememberImageSharer()
 * Button(onClick = { sharer.share(text = "2026년 가을 결산 #어디였지", fileName = "recap-2026-fall") { RecapCard(data) } })
 * ```
 *
 * - 그리기: 화면 밖(창 왼쪽 바깥)에 잠깐 붙인 ComposeView에 카드를 한 번 그려 [Bitmap]으로 옮긴다. 화면에는 보이지 않는다.
 *   카드는 고정 폭 [widthDp](기본 360dp) 좌표계로 그리고, 실제 픽셀은 [pixelsPerDp]배(기본 3 → 1080px)로 나온다 —
 *   폰 화면 비율(PhoneScale)·시스템 글자 크기와 상관없이 늘 같은 그림이다. 테마는 라이트(종이 앨범)로 고정한다.
 * - 카드 안에서는 비동기 이미지(AsyncImage)를 쓰지 않는다: 사진은 미리 받아 [androidx.compose.ui.graphics.ImageBitmap]으로 넘긴다
 *   (한 번 그릴 때 이미 있어야 그림에 들어간다).
 * - Compose 구성·그리기만 메인 스레드(수십 ms)에서, JPEG 압축·파일 쓰기는 IO 스레드에서 한다.
 * - 파일은 캐시의 share/ 폴더에 두고(지난 카드는 지운다) FileProvider(`${applicationId}.share`, 폰 매니페스트)로 넘긴다.
 *
 * 차(Pleos) 판은 공유 시트와 카카오톡이 없어 [supported]가 false다 → 공유 버튼을 감춘다.
 */
object ComposableImageShare {
    /** 이 판에서 공유 시트로 보낼 수 있는지. 차 판은 감춘다 (AAOS에는 공유 시트·메신저 앱이 없다). */
    val supported: Boolean get() = BuildConfig.FLAVOR == "phone"

    /**
     * [content]를 폭 [widthDp]dp로 그린 그림. [heightDp]가 null이면 내용 높이만큼. 메인 스레드에서 그린다(부르는 곳은 아무 스레드).
     * [activity]의 창에 잠깐 붙였다 뗀다.
     */
    suspend fun render(
        activity: Activity,
        widthDp: Int = 360,
        heightDp: Int? = null,
        pixelsPerDp: Float = 3f,
        content: @Composable () -> Unit,
    ): Bitmap = withContext(Dispatchers.Main.immediate) {
        val root = activity.window.decorView as ViewGroup
        val widthPx = (widthDp * pixelsPerDp).roundToInt()
        val heightPx = heightDp?.let { (it * pixelsPerDp).roundToInt() }
        val compose = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(pixelsPerDp, 1f)) {
                    EodiTheme(darkTheme = false, content = content)
                }
            }
        }
        val host = OffscreenHost(activity, widthPx, heightPx).apply {
            addView(compose)
            // 창 왼쪽 바깥에 둔다 (보이지 않게). 크기는 화면보다 커도 된다 (OffscreenHost가 정한다).
            translationX = -(widthPx * 2f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        root.addView(host, FrameLayout.LayoutParams(widthPx, heightPx ?: ViewGroup.LayoutParams.WRAP_CONTENT))
        try {
            suspendCancellableCoroutine { cont -> host.doOnPreDraw { if (cont.isActive) cont.resume(Unit) } }
            val w = compose.width.coerceAtLeast(1)
            val h = compose.height.coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { compose.draw(Canvas(it)) }
        } finally {
            root.removeView(host)
        }
    }

    /** 그림을 캐시 share/[fileName].jpg로 저장한다 (지난 공유 그림은 지운다). IO 스레드. */
    suspend fun save(context: Context, bitmap: Bitmap, fileName: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safe = fileName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "eodiyeotji" }
        File(dir, "$safe.jpg").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } }
    }

    /**
     * 공유 시트를 띄운다: 그림 + 글. 카카오톡은 그림과 글을 함께 받으면 글을 버리기도 해서 글은 클립보드에도 넣는다.
     * 반환: 시트를 띄웠으면 true.
     */
    fun send(context: Context, file: File, text: String?): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!text.isNullOrBlank()) {
            send.putExtra(Intent.EXTRA_TEXT, text)
            runCatching {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("어디였지", text))
            }
        }
        send.clipData = ClipData.newRawUri(null, uri)
        val chooser = Intent.createChooser(send, context.getString(R.string.share_card_chooser))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    /** 그리기 → 저장 → 공유 시트. 반환: 시트를 띄웠으면 true. 실패하면 예외(그리기·저장)나 false(시트 없음). */
    suspend fun share(
        activity: Activity,
        text: String?,
        fileName: String,
        widthDp: Int = 360,
        heightDp: Int? = null,
        content: @Composable () -> Unit,
    ): Boolean {
        val bitmap = render(activity, widthDp, heightDp, content = content)
        val file = try {
            save(activity, bitmap, fileName)
        } finally {
            bitmap.recycle()
        }
        return withContext(Dispatchers.Main.immediate) { send(activity, file, text) }
    }

    private const val JPEG_QUALITY = 92
}

/**
 * 화면 밖 그리기용 틀: 부모(창)가 주는 크기와 상관없이 자식을 폭 [widthPx] · 높이 [heightPx](null이면 내용만큼, 제한 없음)로 잰다.
 * 그래서 화면보다 긴 카드도 잘리지 않는다.
 */
private class OffscreenHost(context: Context, private val widthPx: Int, private val heightPx: Int?) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        val ws = MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY)
        val hs = heightPx?.let { MeasureSpec.makeMeasureSpec(it, MeasureSpec.EXACTLY) } ?: MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        child?.measure(ws, hs)
        setMeasuredDimension(widthPx, child?.measuredHeight ?: 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        getChildAt(0)?.layout(0, 0, widthPx, measuredHeight)
    }
}

/** 화면에서 카드 공유를 시작하는 손잡이 ([rememberImageSharer]). [busy] 동안은 버튼을 막는다. */
@Stable
class ImageSharer internal constructor(private val activity: Activity?, private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set

    /** 공유할 수 있는지 (폰 판 + 화면이 Activity 안). */
    val available: Boolean get() = ComposableImageShare.supported && activity != null

    /**
     * [content]를 이미지로 그려 [text]와 함께 공유 시트로 보낸다. [prepare]가 있으면 먼저 (백그라운드에서) 불러 그 결과를 [content]에 넘긴다
     * — 예: 카드 데이터 · 사진 받기. [onDone]은 성공 여부와 함께 메인 스레드에서 불린다.
     */
    fun <T> share(
        fileName: String,
        widthDp: Int = 360,
        heightDp: Int? = null,
        prepare: suspend () -> T,
        text: (T) -> String?,
        onDone: (T?, Boolean) -> Unit = { _, _ -> },
        content: @Composable (T) -> Unit,
    ) {
        val a = activity ?: return
        if (busy || !available) return
        busy = true
        scope.launch {
            var data: T? = null
            val ok = runCatching {
                val d = prepare().also { data = it }
                ComposableImageShare.share(a, text(d), fileName, widthDp, heightDp) { content(d) }
            }.getOrDefault(false)
            busy = false
            onDone(data, ok)
        }
    }

    /** 이미 가진 카드를 바로 공유 ([prepare] 없이). 결산 카드처럼 화면에 있는 값을 그대로 그릴 때. */
    fun share(text: String?, fileName: String, widthDp: Int = 360, heightDp: Int? = null, onDone: (Boolean) -> Unit = {}, content: @Composable () -> Unit) =
        share(fileName, widthDp, heightDp, prepare = {}, text = { text }, onDone = { _, ok -> onDone(ok) }) { content() }
}

/** 이 화면에서 쓸 [ImageSharer]. */
@Composable
fun rememberImageSharer(): ImageSharer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { ImageSharer(context.findActivity(), scope) }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
