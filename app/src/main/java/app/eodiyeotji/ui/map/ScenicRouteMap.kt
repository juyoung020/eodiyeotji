package app.eodiyeotji.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import app.eodiyeotji.ui.theme.mapInk
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import kotlin.coroutines.resume
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/** 비행이 끝나면 처음 모습으로 돌아가는 시간. */
private const val RETURN_MS = 1_900

/** 처음 모습에서 비행 첫 장면까지 내려가는 시간. */
private const val DESCEND_MS = 1_800

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
    /** 비행이 끝까지 가서 도착했을 때 (손으로 멈춘 때는 부르지 않는다) */
    onArrive: () -> Unit = {},
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
    ScenicMapContent(style, theme, camera, frames, flying, flightMs, onFlightEnd, onFlightProgress, onArrive, options, bg, modifier.semantics { this.contentDescription = contentDescription })
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
    onArrive: () -> Unit,
    baseOptions: MapLibreMapOptions,
    bg: Color,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle(context, baseOptions, bg.toArgb(), tiltAndRotate = true, label = "scenic")
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    LaunchedEffect(mapView) { mapView.getMapAsync { m -> map = m } }
    val m = map
    // 스타일이 다 뜬 뒤에야 차 · 지나온 길 층을 붙이고 비행을 시작할 수 있다
    var loaded by remember { mutableStateOf<Style?>(null) }
    val ink = remember(theme) { FlightInk.of(context, theme) }
    LaunchedEffect(m, style) {
        loaded = null
        if (m != null && style != null) m.setStyle(Style.Builder().fromJson(style)) { s -> s.addFlightLayers(ink); loaded = s }
    }
    LaunchedEffect(m, camera) {
        if (m != null && camera != null) m.moveCamera(CameraUpdateFactory.newCameraPosition(camera.toPosition()))
    }
    val endFlight by rememberUpdatedState(onFlightEnd)
    val flightProgress by rememberUpdatedState(onFlightProgress)
    val overview by rememberUpdatedState(camera)
    val arrive by rememberUpdatedState(onArrive)
    val st = loaded
    LaunchedEffect(m, st, flying, frames) {
        if (m == null || st == null || !flying || frames.size < 2) return@LaunchedEffect
        val car = st.getSourceAs<GeoJsonSource>(CAR_SRC)
        val trail = st.getLayerAs<LineLayer>(TRAIL)
        val carLayer = st.getLayer(CAR)
        // 비행 동안만 무거운 층(두 번째 산 음영 · 3D 건물 · 흐리게 번지는 선 · 지명 글자 배치)을 끈다 → 끝나면 되돌린다
        val heavy = HEAVY_LAYERS.mapNotNull { st.getLayer(it) }
        // 차는 화면 아래 1/3쯤: 위쪽 여백만큼 초점이 아래로 내려간다
        val chaseTop = mapView.height * CHASE_TOP_FRACTION
        val me = coroutineContext.job
        // 지도를 만지면 비행을 멈춘다 (그 자리에서 둘러볼 수 있게). 터치는 지도에도 그대로 넘긴다.
        mapView.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) me.cancel()
            false
        }
        var natural = false
        try {
            heavy.forEach { it.setProperties(PropertyFactory.visibility(Property.NONE)) }
            val pos = FlightPose()
            pos.at(frames, 0.0)
            car?.setGeoJson(pos.feature())
            trail?.setProperties(PropertyFactory.lineGradient(ink.trailGradient(0f)), PropertyFactory.lineOpacity(1f))
            carLayer?.setProperties(PropertyFactory.iconOpacity(1f)) // 층에 걸어 둔 전환으로 살며시 나타난다
            // 여는 장면: 길 전체 모습에서 출발점으로 내려앉는다
            m.easeTo(pos.camera(chaseTop), DESCEND_MS)
            // 달리기: 매 화면(vsync) 카메라와 점 하나짜리 소스만 바꾼다 (재구성 없음)
            val start = withFrameNanos { it }
            var lastTrail = -1f
            while (true) {
                val now = withFrameNanos { it }
                val u = ((now - start) / 1e6 / flightMs).coerceAtMost(1.0)
                val s = easeTrip(u)
                pos.at(frames, s)
                m.moveCamera(CameraUpdateFactory.newCameraPosition(pos.camera(chaseTop)))
                car?.setGeoJson(pos.feature())
                val t = s.toFloat()
                if (t - lastTrail >= TRAIL_STEP || u >= 1.0) {
                    lastTrail = t
                    trail?.setProperties(PropertyFactory.lineGradient(ink.trailGradient(t)))
                }
                flightProgress(t)
                if (u >= 1.0) break
            }
            natural = true
            arrive()
            // 닫는 장면: 길 전체 모습으로 천천히 물러난다
            overview?.let { m.easeTo(it.toPosition(), RETURN_MS) }
        } finally {
            mapView.setOnTouchListener(null)
            if (!natural) m.cancelTransitions()
            heavy.forEach { it.setProperties(PropertyFactory.visibility(Property.VISIBLE)) }
            carLayer?.setProperties(PropertyFactory.iconOpacity(0f))
            trail?.setProperties(PropertyFactory.lineOpacity(0f))
            endFlight()
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
    CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).bearing(bearing).tilt(pitch).padding(0.0, 0.0, 0.0, 0.0).build()

/** 카메라를 [ms] 동안 부드럽게 옮기고 끝날 때까지 기다린다 (취소되면 옮기기도 멈춘다). */
private suspend fun MapLibreMap.easeTo(p: CameraPosition, ms: Int) = suspendCancellableCoroutine { cont ->
    animateCamera(
        CameraUpdateFactory.newCameraPosition(p),
        ms,
        object : MapLibreMap.CancelableCallback {
            override fun onFinish() { if (cont.isActive) cont.resume(Unit) }
            override fun onCancel() { if (cont.isActive) cont.resume(Unit) }
        },
    )
    cont.invokeOnCancellation { cancelTransitions() }
}

/**
 * 달리기 진행: 처음 [RAMP]만큼 천천히 출발해 일정한 빠르기로 달리고 끝에서 천천히 선다 (사다리꼴 빠르기).
 * [u] 시간 비율(0~1) → 길 거리 비율(0~1).
 */
internal fun easeTrip(u: Double): Double {
    val a = RAMP
    val k = 1.0 / (1.0 - a) // 가운데 빠르기 (전체 면적이 1이 되게)
    return when {
        u <= 0.0 -> 0.0
        u >= 1.0 -> 1.0
        u < a -> k * u * u / (2 * a)
        u > 1 - a -> 1 - k * (1 - u) * (1 - u) / (2 * a)
        else -> k * (u - a / 2)
    }
}

private const val RAMP = 0.14

/** 장면 배열(Rust, 같은 거리 간격 · 매끈하게 편 길 · 고르게 돈 방향) 사이를 섞어 읽는 자리 하나. 매 화면 새로 만들지 않는다. */
private class FlightPose {
    var lat = 0.0
    var lon = 0.0
    var bearing = 0.0
    var zoom = 14.0

    fun at(frames: List<FlightFrame>, s: Double) {
        val x = s.coerceIn(0.0, 1.0) * (frames.size - 1)
        val i = floor(x).toInt().coerceAtMost(frames.size - 2)
        val f = x - i
        val a = frames[i]
        val b = frames[i + 1]
        lat = a.lat + (b.lat - a.lat) * f
        lon = a.lon + (b.lon - a.lon) * f
        bearing = a.bearing + (b.bearing - a.bearing) * f
        zoom = a.zoom + (b.zoom - a.zoom) * f
    }

    fun camera(top: Float): CameraPosition = CameraPosition.Builder()
        .target(LatLng(lat, lon)).zoom(zoom).bearing(bearing.mod(360.0)).tilt(FLIGHT_PITCH)
        .padding(0.0, top.toDouble(), 0.0, 0.0)
        .build()

    fun feature(): Feature = Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply { addNumberProperty("b", bearing.mod(360.0)) }
}

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

/** 뒤따라가는 카메라 기울기: 살짝만 (지평선까지 타일을 덜 그리고, 차와 지나온 길이 잘 보이게). */
private const val FLIGHT_PITCH = 58.0

/** 뒤따라가는 카메라: 위쪽 여백 비율 (차가 화면 아래 1/3쯤에 온다) */
private const val CHASE_TOP_FRACTION = 0.4f

private const val CAR_SRC = "flight-car"
private const val CAR = "flight-car"
private const val TRAIL = "flight-trail"

/** 지나온 길 그라데이션은 진행 0.2%마다 한 번만 바꾼다 (눈에는 매끄럽고 매 화면 다시 만들지 않는다). */
private const val TRAIL_STEP = 0.002f
private const val FADE_MS = 700L

/** 지도 위 위치 표시 크기(dp) */
private const val PUCK_DP = 52
private val HEAVY_LAYERS = listOf("hills-rim", "buildings-3d", "route-halo", "route-glow", "coast-glow", "road-lights", "place", "water-name")

/** 미리 달리기 색: 길 색([mapInk].trip, 밤 지도면 밤 색)과 차 그림(그림자 · 빛까지 한 번만 굽는다). */
private class FlightInk(val trip: Int, val glow: Int, val car: Bitmap) {
    /** 진행 [t]까지 지나온 길: 처음은 옅게, 차 가까이 갈수록 진하고 차 바로 뒤는 밝게. 그 뒤는 투명. */
    fun trailGradient(t: Float): Expression {
        val clear = ColorUtils.setAlphaComponent(trip, 0)
        if (t <= 0.001f) return Expression.interpolate(Expression.linear(), Expression.lineProgress(), Expression.stop(0f, Expression.color(clear)), Expression.stop(1f, Expression.color(clear)))
        val end = t + 0.0005f
        val stops = mutableListOf(
            Expression.stop(0f, Expression.color(ColorUtils.setAlphaComponent(trip, 90))),
            Expression.stop(t * 0.75f, Expression.color(trip)),
            Expression.stop(t, Expression.color(glow)),
        )
        if (end < 1f) {
            stops += Expression.stop(end, Expression.color(clear))
            stops += Expression.stop(1f, Expression.color(clear))
        }
        return Expression.interpolate(Expression.linear(), Expression.lineProgress(), *stops.toTypedArray())
    }

    companion object {
        fun of(context: Context, theme: RouteTheme): FlightInk {
            val dark = theme == RouteTheme.NIGHT
            val trip = mapInk(dark).trip.toArgb()
            val glow = ColorUtils.blendARGB(trip, android.graphics.Color.WHITE, 0.55f)
            return FlightInk(trip, glow, carBitmap(context, trip))
        }

        /**
         * 내비식 위치 표시(퍽): 옅은 길 색 원 위에 매끈한 화살촉 하나 (길 색[mapInk].trip 채움 + 흰 테두리 + 부드러운 그림자).
         * 창 · 바퀴 같은 그림은 넣지 않는다. 48dp 정사각형에 한 번만 굽는다.
         */
        private fun carBitmap(context: Context, accent: Int): Bitmap {
            val dens = context.resources.displayMetrics.density
            val size = (PUCK_DP * dens).toInt()
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val cx = size / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // 옅은 강조색 원 (지도 위에서 자리 잡아 주는 바닥)
            paint.color = ColorUtils.setAlphaComponent(accent, 46)
            c.drawCircle(cx, cx, size * 0.46f, paint)
            // 화살촉: 앞(위)이 뾰족하고 뒤가 살짝 파인 한 줄 모양, 모서리는 둥글게
            val u = size * 0.30f
            val arrow = android.graphics.Path().apply {
                moveTo(cx, cx - u * 1.05f)
                lineTo(cx + u * 0.78f, cx + u * 0.88f)
                lineTo(cx, cx + u * 0.48f)
                lineTo(cx - u * 0.78f, cx + u * 0.88f)
                close()
            }
            val round = android.graphics.CornerPathEffect(2.5f * dens)
            paint.pathEffect = round
            paint.strokeJoin = Paint.Join.ROUND
            // 그림자 (살짝 아래로 번짐)
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.argb(70, 0, 0, 0)
            paint.maskFilter = BlurMaskFilter(3f * dens, BlurMaskFilter.Blur.NORMAL)
            c.save(); c.translate(0f, 1.5f * dens); c.drawPath(arrow, paint); c.restore()
            paint.maskFilter = null
            // 흰 테두리 → 강조색 채움
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * dens * 2
            paint.color = android.graphics.Color.WHITE
            c.drawPath(arrow, paint)
            paint.style = Paint.Style.FILL
            paint.color = accent
            c.drawPath(arrow, paint)
            return out
        }
    }
}

/** 차(점 하나 소스 + 길 방향으로 돌아 지도에 눕는 그림)와 지나온 길(길 선 위 line-progress 그라데이션). 처음엔 숨겨 둔다. */
private fun Style.addFlightLayers(ink: FlightInk) {
    addImage(CAR, ink.car)
    addSource(GeoJsonSource(CAR_SRC))
    val fade = TransitionOptions(FADE_MS, 0)
    val trail = LineLayer(TRAIL, "route").withFilter(Expression.eq(Expression.get("kind"), Expression.literal("route"))).withProperties(
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 7f), Expression.stop(16, 12f))),
        PropertyFactory.lineGradient(ink.trailGradient(0f)),
        PropertyFactory.lineOpacity(0f),
    )
    trail.lineOpacityTransition = fade
    if (getLayer("sight-dot") != null) addLayerBelow(trail, "sight-dot") else addLayer(trail)
    val car = SymbolLayer(CAR, CAR_SRC).withProperties(
        PropertyFactory.iconImage(CAR),
        PropertyFactory.iconRotate(Expression.get("b")),
        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconOpacity(0f),
    )
    car.iconOpacityTransition = fade
    addLayer(car)
}

/** 스타일을 불러오는 동안 보일 바탕색 (때와 같은 색이라 깜빡이지 않게). */
internal fun loadingColor(theme: RouteTheme): Color = when (theme) {
    RouteTheme.DAY -> Color(0xFFECE7DE)
    RouteTheme.SUNSET -> Color(0xFFF1CDAE)
    RouteTheme.NIGHT -> Color(0xFF0F1626)
}
