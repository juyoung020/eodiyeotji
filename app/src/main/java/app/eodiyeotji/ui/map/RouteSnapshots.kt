package app.eodiyeotji.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.eodiyeotji.core.DriveRoute
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.RouteTheme
import app.eodiyeotji.core.driveRouteCamera
import app.eodiyeotji.core.driveRouteMap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * 드라이브 코스 목록 카드의 3D 대표 그림: 상세 지도와 같은 스타일·기울기·때 색으로 MapLibre `MapSnapshotter`가 그린 한 장.
 * 카드마다 MapView를 띄우지 않는다.
 *
 * - 그림 크기는 하나로 정해 두고(카드 폭이 달라도 잘라 맞춘다) 길 id + 때 + 길 모양으로 디스크에 저장한다 → 두 번째부터는 파일만 읽는다.
 * - 그리기는 한 번에 하나씩 (지도 엔진을 여러 개 동시에 띄우지 않게). 스냅숏 렌더링은 MapLibre 안쪽 스레드에서 하고,
 *   파일 읽기·쓰기는 IO 스레드에서 한다. 타일을 못 받으면(오프라인) 저장하지 않고 다음에 다시 그린다.
 */
@Singleton
class RouteSnapshots @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
) {
    private val lock = Mutex()
    // 한 장이 약 1280×610×4 ≈ 3MB → 장 수(24장 = 75MB)가 아니라 바이트로 묶는다 (목록 한 화면 + 앞뒤 몇 장)
    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val dir by lazy { File(context.cacheDir, "route-snapshots").apply { mkdirs() } }

    /** [route]의 그림 (저장된 것 → 새로 그림). 못 그리면 null. */
    suspend fun get(route: DriveRoute): Bitmap? {
        val map = runCatching { core.driveRouteMap(route.id) }.getOrNull() ?: return null
        val key = "${route.id.filter { it.isLetterOrDigit() || it == '-' }}-${route.theme.name.lowercase()}-${map.geojson.hashCode().toUInt()}-v$VERSION"
        memory.get(key)?.let { return it }
        val file = File(dir, "$key.webp")
        withContext(Dispatchers.IO) { if (file.isFile) BitmapFactory.decodeFile(file.path) else null }?.let {
            memory.put(key, it)
            return it
        }
        return lock.withLock {
            memory.get(key) ?: render(route, map.geojson)?.also { bmp ->
                memory.put(key, bmp)
                // 인터넷이 없으면 타일이 빈 그림일 수 있다 → 디스크에 남기지 않고 다음에 다시 그린다
                if (online()) {
                    withContext(Dispatchers.IO) {
                        runCatching { file.outputStream().use { bmp.compress(webp(), 88, it) } }
                            .onFailure { Log.w(TAG, "그림 저장 실패 ${route.id}", it) }
                    }
                }
            }
        }
    }

    private fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private suspend fun render(route: DriveRoute, geojson: String): Bitmap? {
        val style = ScenicMapStyle.build(context, route.theme, geojson)
        val cam = runCatching { core.driveRouteCamera(route.id, WIDTH_DP.toFloat(), HEIGHT_DP.toFloat(), PADDING_DP.toFloat()) }.getOrNull() ?: return null
        MapLibreNative.prepare(context)
        return withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                MapLibre.getInstance(context)
                val options = MapSnapshotter.Options(WIDTH_DP, HEIGHT_DP)
                    .withStyleBuilder(Style.Builder().fromJson(style))
                    .withCameraPosition(cam.toPosition())
                    .withPixelRatio(context.resources.displayMetrics.density.coerceAtMost(MAX_PIXEL_RATIO))
                    .withLocalIdeographFontFamily(LOCAL_IDEOGRAPH_FONT)
                    .withLogo(false)
                    .withAttribution(false)
                val snapshotter = MapSnapshotter(context, options)
                suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { snapshotter.cancel() }
                    snapshotter.start(
                        { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
                        { error ->
                            Log.w(TAG, "그림 실패 ${route.id}: $error")
                            if (cont.isActive) cont.resume(null)
                        },
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "EodiRouteSnapshot"
        /** 스타일·카메라를 바꾸면 올린다 (저장된 그림을 다시 그리게). */
        const val VERSION = 4
        /** 그림 크기 (논리 픽셀). 카드 대표 그림 비율 2.1 : 1. */
        const val WIDTH_DP = 640
        const val HEIGHT_DP = 305
        const val PADDING_DP = 56
        const val MAX_PIXEL_RATIO = 2f
        const val TIMEOUT_MS = 25_000L
        const val MEMORY_BYTES = 16 * 1024 * 1024
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RouteSnapshotEntryPoint {
    fun routeSnapshots(): RouteSnapshots
}

/**
 * 드라이브 코스 3D 대표 그림 (스냅숏). 그리는 동안·못 그렸을 때는 때 색 그라데이션.
 * 크기는 [modifier]가 정하고, 그림은 가운데를 잘라 채운다.
 */
@Composable
fun RouteSnapshotImage(route: DriveRoute, contentDescription: String, modifier: Modifier = Modifier) {
    val inPreview = LocalInspectionMode.current
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(initialValue = null, route.id, route.theme, inPreview) {
        if (inPreview) return@produceState
        val repo = EntryPointAccessors.fromApplication(context, RouteSnapshotEntryPoint::class.java).routeSnapshots()
        value = repo.get(route)
    }
    val placeholder = remember(route.theme) { placeholderBrush(route.theme) }
    Box(modifier.background(placeholder).semantics { this.contentDescription = contentDescription }) {
        bitmap?.let { b ->
            val img = remember(b) { b.asImageBitmap() }
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

private fun placeholderBrush(theme: RouteTheme): Brush = when (theme) {
    RouteTheme.DAY -> Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFDDEBF2), loadingColor(theme)))
    RouteTheme.SUNSET -> Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFF7B48A), loadingColor(theme)))
    RouteTheme.NIGHT -> Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF1B2440), loadingColor(theme)))
}
