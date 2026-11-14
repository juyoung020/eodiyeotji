package app.eodiyeotji.ui.share

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.eodiyeotji.ui.map.LOCAL_IDEOGRAPH_FONT
import app.eodiyeotji.ui.map.MapLibreNative
import app.eodiyeotji.ui.map.MapStyleSource
import app.eodiyeotji.ui.map.MapStyleSpec
import kotlin.coroutines.resume
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * 공유 카드의 길 그림 밑에 까는 지도 한 장. 앱 지도와 같은 스타일(기기 안 지도 파일 → 온라인 밑그림)을 MapLibre `MapSnapshotter`로
 * 길 그림 칸 크기([SKETCH_MAP_W] × [SKETCH_MAP_H])에 그린다 — 화면에 지도를 띄우지 않는다.
 *
 * 범위는 Rust가 준 [ShareCardData.mapBox] (길 그림 칸 전체가 덮는 땅)이라, 지도 위에 카드의 길 · 번호 핀이 그대로 겹친다:
 * 가운데를 맞추고, 칸 가로(논리 픽셀)에 경도 폭이 꼭 들어가는 줌을 쓴다 (세로는 Rust가 그 위도의 가로세로 비율로 맞춰 둔 값).
 *
 * 범위가 없거나(그릴 것 없음 · 예전 판 카드) 스타일을 못 쓰거나 [MAP_TIMEOUT_MS] 안에 못 그리면 null → 카드는 점 무늬 종이로 그린다.
 * 집 · 회사 근처는 Rust가 이미 길에서 잘라 냈다 (지도에는 그 동네가 보이지만 길 · 핀은 없다).
 */
internal suspend fun loadCardMap(context: Context, card: ShareCardData): ImageBitmap? {
    val box = card.mapBox ?: return null
    val lonSpan = box.east - box.west
    if (!(lonSpan > 0.0)) return null
    val style = when (val spec = MapStyleSource.resolve(context, night = false)) {
        is MapStyleSpec.Offline -> spec.json
        is MapStyleSpec.Online -> spec.json
        MapStyleSpec.Fallback -> return null
    }
    // 줌 0에서 세계 한 바퀴 = 512 논리 픽셀
    val zoom = ln(SKETCH_MAP_W * 360.0 / (512.0 * lonSpan)) / ln(2.0)
    val camera = CameraPosition.Builder()
        .target(LatLng((box.south + box.north) / 2, (box.west + box.east) / 2))
        .zoom(zoom)
        .build()
    MapLibreNative.prepare(context)
    return withTimeoutOrNull(MAP_TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            MapLibre.getInstance(context)
            val options = MapSnapshotter.Options(SKETCH_MAP_W, SKETCH_MAP_H)
                .withStyleBuilder(Style.Builder().fromJson(style))
                .withCameraPosition(camera)
                .withPixelRatio(MAP_PIXEL_RATIO)
                .withLocalIdeographFontFamily(LOCAL_IDEOGRAPH_FONT)
                .withLogo(false)
                .withAttribution(false)
            val snapshotter = MapSnapshotter(context, options)
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { snapshotter.cancel() }
                snapshotter.start(
                    { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
                    { error ->
                        Log.w(TAG, "카드 지도 실패: $error")
                        if (cont.isActive) cont.resume(null)
                    },
                )
            }
        }
    }?.asImageBitmap()
}

private const val TAG = "EodiShareMap"

/** 길 그림 칸 크기 (ShareCardView의 칸: 폭 360 - 2×20, 높이 150 — [SKETCH_ASPECT]와 같은 비율). */
private const val SKETCH_MAP_W = 320
private const val SKETCH_MAP_H = 150

/** 카드를 3배(1080px)로 그리므로 지도도 3배로 받는다. */
private const val MAP_PIXEL_RATIO = 3f
private const val MAP_TIMEOUT_MS = 8_000L
