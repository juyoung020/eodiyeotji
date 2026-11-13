package app.eodiyeotji.ui.shell

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.offset
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 차가 아닌 기기(태블릿)에서 차 판을 띄울 때 쓰는 가짜 인포 틀.
 * 차 화면은 인포테인먼트 안의 한 칸(차 상태 판 옆, 위 · 아래 막대 사이)에 뜬다. 일반 태블릿에는 그 틀이 없어서
 * 앱만 화면 가득 뜨고 글자 · 간격도 차와 다르게 보인다. 그래서 틀 그림 한 장을 바탕에 깔고, 앱은 차에서와 같은 자리 ·
 * 같은 크기(dp)로 그 안에 그린다. 틀의 버튼(기어 · 공조 · 홈)은 그림일 뿐이라 눌러도 아무 일도 없다.
 * - 틀 그림은 저장소에 두지 않는다: 기기의 `files/shell/frame.png` (에뮬레이터 화면을 찍어 넣는다, scripts/demo-shell/push_shell.py).
 *   그림이 없으면 틀 없이 앱만 그린다.
 * - 진짜 차(자동차용 안드로이드)에서는 아무것도 하지 않는다.
 */

/** 틀 그림의 기준 크기와 그 안에서 앱이 뜨는 칸 (px, Pleos 에뮬레이터 2560x1440 화면 · 밀도 1.0 기준). */
private const val FRAME_W = 2560f
private const val FRAME_H = 1440f
private const val APP_X = 854f
private const val APP_Y = 90f
private const val APP_W = 1690f
private const val APP_H = 1210f

@Composable
fun FakeCarShell(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val automotive = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) }
    if (automotive) {
        content()
        return
    }
    // 그림 읽기는 IO 스레드에서. 읽는 동안(그리고 없을 때)은 틀 없이 앱만.
    val frame by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "shell/frame.png")
            if (file.isFile) runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() else null
        }
    }
    val image = frame
    if (image == null) {
        content()
        return
    }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).consumeWindowInsets(WindowInsets.safeDrawing)
            .onSizeChanged { box = it },
        contentAlignment = Alignment.Center,
    ) {
        if (box.width == 0 || box.height == 0) return@Box
        // 틀 전체가 화면에 들어가는 비율 (틀 px → 화면 px). 앱 칸의 1dp는 틀의 1px이다 (에뮬레이터 밀도 1.0).
        val scale = min(box.width / FRAME_W, box.height / FRAME_H)
        val fontScale = LocalDensity.current.fontScale
        val outer = LocalDensity.current
        fun dp(px: Float) = with(outer) { (px * scale).toDp() }
        Box(Modifier.size(dp(FRAME_W), dp(FRAME_H))) {
            Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .offset { IntOffset((APP_X * scale).roundToInt(), (APP_Y * scale).roundToInt()) }
                    .size(dp(APP_W), dp(APP_H))
                    .clipToBounds(),
            ) {
                CompositionLocalProvider(LocalDensity provides Density(scale, fontScale)) { content() }
            }
        }
    }
}
