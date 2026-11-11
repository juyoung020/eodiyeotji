package app.eodiyeotji.ui.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.eodiyeotji.R
import app.eodiyeotji.core.FlightFrame
import app.eodiyeotji.core.RouteCameraSpec
import app.eodiyeotji.core.RouteTheme
import app.eodiyeotji.core.SkyPhase
import app.eodiyeotji.ui.memorymap.CinematicSky
import kotlin.math.floor
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style

/** 비행이 끝나면 처음 모습으로 돌아가는 시간. */
private const val RETURN_MS = 1_600

/** 처음 모습에서 비행 첫 장면까지 내려가는 시간. */
private const val DESCEND_MS = 1_400

/**
 * 드라이브 코스 3D 미리보기 (상세 화면용 라이브 지도 하나). 기울이기·돌리기 손짓을 쓴다.
 *
 * @param style [ScenicMapStyle]로 만든 스타일 (null이면 불러오는 중 — 바탕색만)
 * @param camera Rust가 길 방향·이 칸 크기에 맞춘 처음 카메라 (`drive_route_camera`)
 * @param frames 미리보기 비행 장면 (Rust `drive_route_flight`, 길을 따라 같은 간격)
 * @param flying true면 [flightMs] 동안 장면을 따라 카메라를 부드럽게(처음·끝은 천천히) 옮긴다. 지도를 만지면 멈춘다.
 * @param onFlightEnd 비행이 끝나거나 손으로 멈췄을 때
 */
@Composable
fun ScenicRouteMap(
    style: String?,
    theme: RouteTheme,
    camera: RouteCameraSpec?,
    frames: List<FlightFrame>,
    flying: Boolean,
    flightMs: Long,
    onFlightEnd: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    /** 비행 진행(0~1, 장면 순서 = 길 거리 비율). 비행 중 매 화면마다 불린다 → 받는 쪽은 그리기 단계에서만 읽는다. */
    onFlightProgress: (Float) -> Unit = {},
) {
    val inPreview = LocalInspectionMode.current
    val appContext = LocalContext.current.applicationContext
    val baseOptions by produceState<MapLibreMapOptions?>(initialValue = null) {
        if (!inPreview) value = MapLibreNative.prepare(appContext)
    }
    val options = baseOptions
    val bg = remember(theme) { loadingColor(theme) }
    if (options == null) {
        Box(modifier.background(bg).semantics { this.contentDescription = contentDescription })
        return
    }
    ScenicMapContent(style, theme, camera, frames, flying, flightMs, onFlightEnd, onFlightProgress, options, bg, modifier.semantics { this.contentDescription = contentDescription })
}

@SuppressLint("ClickableViewAccessibility") // 터치는 지도에 그대로 넘기고 비행만 멈춘다
@Composable
private fun ScenicMapContent(
    style: String?,
    theme: RouteTheme,
    camera: RouteCameraSpec?,
    frames: List<FlightFrame>,
    flying: Boolean,
    flightMs: Long,
    onFlightEnd: () -> Unit,
    onFlightProgress: (Float) -> Unit,
    baseOptions: MapLibreMapOptions,
    bg: Color,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle(context, baseOptions, bg.toArgb(), tiltAndRotate = true, label = "scenic")
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    LaunchedEffect(mapView) { mapView.getMapAsync { m -> map = m } }
    val m = map
    LaunchedEffect(m, style) {
        if (m != null && style != null) m.setStyle(Style.Builder().fromJson(style))
    }
    LaunchedEffect(m, camera) {
        if (m != null && camera != null) m.moveCamera(CameraUpdateFactory.newCameraPosition(camera.toPosition()))
    }
    val endFlight by rememberUpdatedState(onFlightEnd)
    val flightProgress by rememberUpdatedState(onFlightProgress)
    val overview by rememberUpdatedState(camera)
    DisposableEffect(m, flying, frames) {
        if (m == null || !flying || frames.size < 2) {
            return@DisposableEffect onDispose { }
        }
        var ended = false
        val finish = { natural: Boolean ->
            if (!ended) {
                ended = true
                if (natural) overview?.let { m.animateCamera(CameraUpdateFactory.newCameraPosition(it.toPosition()), RETURN_MS) }
                endFlight()
            }
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = flightMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                m.moveCamera(CameraUpdateFactory.newCameraPosition(frameAt(frames, t)))
                flightProgress(t)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) = finish(!cancelled)
            })
        }
        // 지도를 만지면 비행을 멈춘다 (그 자리에서 둘러볼 수 있게). 비행 중에는 카메라가 계속 움직여 MapLibre의
        // "손짓으로 움직이기 시작" 알림이 오지 않으므로 손가락이 닿는 순간을 직접 본다. 터치는 지도에도 그대로 넘긴다.
        var started = false
        mapView.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                if (started) anim.cancel() else m.cancelTransitions()
            }
            false
        }
        // 처음 모습에서 비행 첫 장면까지는 부드럽게 내려간 뒤 출발한다
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(frameAt(frames, 0f)),
            DESCEND_MS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    if (!ended) {
                        started = true
                        anim.start()
                    }
                }

                override fun onCancel() {
                    if (!started) finish(false)
                }
            },
        )
        onDispose {
            mapView.setOnTouchListener(null)
            if (!started) m.cancelTransitions() // 첫 장면으로 내려가는 중에 멈춤
            anim.cancel()
        }
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        // 기울어진 지도 지평선 위 하늘 · 지평선 빛 · 비네트 (때 색, 밤이면 별)
        val sky = when (theme) {
            RouteTheme.DAY -> SkyPhase.DAY
            RouteTheme.SUNSET -> SkyPhase.GOLDEN
            RouteTheme.NIGHT -> SkyPhase.NIGHT
        }
        // 지평선이 안 보이는 기울기라 낮·노을엔 하늘을 덮지 않는다 (덮으면 지도 위쪽이 뿌옇게 번진다). 밤엔 별만.
        if (theme == RouteTheme.NIGHT) CinematicSky(sky, skyFraction = 0.26f, haze = false)
        Text(
            stringResource(R.string.drive_map_attribution),
            style = AttributionStyle,
            color = Color(0xC8151A24),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .background(Color(0xD9FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val AttributionStyle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

internal fun RouteCameraSpec.toPosition(): CameraPosition =
    CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).bearing(bearing).tilt(pitch).build()

/** 비행 진행 [t](0~1)의 카메라: 이웃한 두 장면 사이를 섞는다 (방향은 Rust가 이어지게 풀어 둔 값이라 그대로 섞는다). */
internal fun frameAt(frames: List<FlightFrame>, t: Float, pitch: Double = FLIGHT_PITCH): CameraPosition {
    val x = t.coerceIn(0f, 1f) * (frames.size - 1)
    val i = floor(x).toInt().coerceAtMost(frames.size - 2)
    val f = (x - i).toDouble()
    val a = frames[i]
    val b = frames[i + 1]
    fun mix(p: Double, q: Double) = p + (q - p) * f
    return CameraPosition.Builder()
        .target(LatLng(mix(a.lat, b.lat), mix(a.lon, b.lon)))
        .zoom(mix(a.zoom, b.zoom))
        .bearing(mix(a.bearing, b.bearing).mod(360.0))
        .tilt(pitch)
        .build()
}

private const val FLIGHT_PITCH = 64.0

/** 스타일을 불러오는 동안 보일 바탕색 (때와 같은 색이라 깜빡이지 않게). */
internal fun loadingColor(theme: RouteTheme): Color = when (theme) {
    RouteTheme.DAY -> Color(0xFFECE7DE)
    RouteTheme.SUNSET -> Color(0xFFF1CDAE)
    RouteTheme.NIGHT -> Color(0xFF0F1626)
}
