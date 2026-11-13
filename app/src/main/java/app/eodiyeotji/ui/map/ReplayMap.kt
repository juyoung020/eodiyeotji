package app.eodiyeotji.ui.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import app.eodiyeotji.core.MapBoundsLatLon
import app.eodiyeotji.core.ReplayScene
import app.eodiyeotji.core.SkyPhase
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleBlur
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circlePitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

/*
 * 그날로 돌아가기 지도 (노을빛 3D). 바탕은 드라이브 코스 미리보기와 같은 스타일(ScenicMapStyle, 노을 색: 살구빛 땅 · 산 음영 ·
 * 건물 3D)에 금빛으로 번지는 길을 얹는다. 길 · 들른 곳 GeoJSON과 비행 장면은 모두 Rust(`day_replay`)가 만든 것을 그대로 쓴다.
 * 온라인 타일을 못 받으면 살구빛 바탕 위에 금빛 길만 보인다. 손짓은 받지 않는다.
 */

/** 노을 바탕색 (스타일 background와 같다 → 불러오는 동안 깜빡이지 않게). */
val ReplaySunsetLand = Color(0xFFF1CDAE)

/** 작은 지도 · 끝 화면 지도 기울기, 비행 기울기. */
private const val OVERVIEW_PITCH = 50.0
private const val OVERVIEW_BEARING = -18.0
/** 비행은 더 눕혀 지평선이 보이게 (하늘은 화면이 그 위에 얹는다). */
private const val FLIGHT_PITCH = 66.0

/** 비행: 앞 곳에서 구간 첫 장면으로 내려가는 시간, 도착하면 그곳을 살짝 돌아보는 시간. */
private const val DESCEND_MS = 600
private const val ARRIVE_MS = 1_800

/** 끝 화면에서 하루 전체를 천천히 도는 동안 한 번에 움직이는 각도 · 시간. */
private const val ORBIT_DEG = 20.0
private const val ORBIT_MS = 10_000

private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

/**
 * 작은 지도 (사진 장면 오른쪽 위) · 끝 화면 지도: 그날 범위를 비스듬히(50°) 내려다본다.
 * [orbit]이면 천천히 돈다 (끝 화면). 지나온 길([done])과 지금 구간([leg])을 그린다.
 */
@Composable
fun ReplayMap(
    bounds: MapBoundsLatLon?,
    done: String,
    leg: String?,
    modifier: Modifier = Modifier,
    orbit: Boolean = false,
    sky: SkyPhase = SkyPhase.GOLDEN,
    /** false면 화면에 안 보이는 중 (작은 지도가 비행 동안 숨어 있을 때): 숨쉬기를 멈춰 비행 지도와 GPU를 나눠 쓰지 않는다. */
    visible: Boolean = true,
) {
    val ready = rememberReplayMap(sky, if (orbit) "replay-end" else "replay-mini", bounds, visible) ?: run {
        Box(modifier.background(ReplaySunsetLand))
        return
    }
    val (mapView, controller) = ready
    val density = LocalDensity.current.density
    val m = controller.map
    LaunchedEffect(m, bounds, orbit) {
        val b = bounds ?: return@LaunchedEffect
        if (m == null) return@LaunchedEffect
        val start = awaitLayout(mapView) { fitTilted(m, mapView, b, if (orbit) FIT_PAD_FRACTION else 0f, PAD_DP * density) } ?: return@LaunchedEffect
        m.moveCamera(CameraUpdateFactory.newCameraPosition(start))
        if (!orbit) return@LaunchedEffect
        var bearing = start.bearing
        try {
            while (true) {
                bearing += ORBIT_DEG
                m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(start).bearing(bearing).build()), ORBIT_MS, false)
                delay(ORBIT_MS.toLong())
            }
        } finally {
            m.cancelTransitions()
        }
    }
    LaunchedEffect(controller, done, leg) { controller.setData(done, leg) }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * 장면 사이 전체 화면 비행: [scene]으로 오는 구간을 따라 기울어진 카메라(60°)가 길 방향을 보며 날아가고, 그동안 금빛 길이
 * 차와 함께 그려진다. [flightMs] 뒤 [onArrive]를 부르고 도착한 곳을 천천히 돌아본다. [scene]이 null(시작 화면)이면 그날
 * 전체를 비스듬히 본다. [paused]면 비행을 그 자리에서 멈춘다.
 */
@Composable
fun ReplayFlightMap(
    bounds: MapBoundsLatLon?,
    scene: ReplayScene?,
    flightMs: Long,
    paused: Boolean,
    onArrive: () -> Unit,
    modifier: Modifier = Modifier,
    sky: SkyPhase = SkyPhase.GOLDEN,
    /** 비행 진행(0~1)을 알린다 (길 위 사진을 제때 띄우는 데 쓴다) */
    onProgress: (Float) -> Unit = {},
) {
    val ready = rememberReplayMap(sky, "replay-flight", bounds) ?: run {
        Box(modifier.background(ReplaySunsetLand))
        return
    }
    val (mapView, controller) = ready
    val m = controller.map
    val arrive by rememberUpdatedState(onArrive)
    val progress by rememberUpdatedState(onProgress)
    val pausedNow by rememberUpdatedState(paused)
    var anim by remember { mutableStateOf<ValueAnimator?>(null) }
    DisposableEffect(m, scene) {
        if (m == null) return@DisposableEffect onDispose { }
        val frames = scene?.legFlight.orEmpty()
        val legs = scene?.legFrames.orEmpty()
        if (scene == null) {
            controller.setData(EMPTY, null)
            bounds?.let { b ->
                mapView.doOnLayout {
                    m.getCameraForLatLngBounds(LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon), intArrayOf(80, 80, 80, 80), OVERVIEW_BEARING, OVERVIEW_PITCH)
                        ?.let { m.moveCamera(CameraUpdateFactory.newCameraPosition(it)) }
                }
            }
            return@DisposableEffect onDispose { }
        }
        controller.setData(scene.doneGeojson, legs.firstOrNull())
        if (frames.size < 2) {
            // 구간이 없다 (그날 첫 곳에서 시작): 그곳으로 바로 날아간다
            m.animateCamera(CameraUpdateFactory.newCameraPosition(arrival(scene.stop.lat, scene.stop.lon, 0.0, 15.0)), ARRIVE_MS)
            arrive()
            return@DisposableEffect onDispose { m.cancelTransitions() }
        }
        var lastLeg = -1
        var ended = false
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = flightMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { v ->
                val t = v.animatedValue as Float
                progress(t)
                m.moveCamera(CameraUpdateFactory.newCameraPosition(frameAt(frames, t, FLIGHT_PITCH)))
                if (legs.isNotEmpty()) {
                    val k = (t * (legs.size - 1)).toInt().coerceIn(0, legs.size - 1)
                    if (k != lastLeg) {
                        lastLeg = k
                        controller.setLeg(legs[k])
                    }
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled || ended) return
                    ended = true
                    val end = frames.last()
                    // 도착: 조금 내려와 들른 곳을 옆으로 돌아본다 (사진이 그 위로 천천히 덮인다)
                    m.easeCamera(
                        CameraUpdateFactory.newCameraPosition(arrival(scene.stop.lat, scene.stop.lon, end.bearing + 30.0, (end.zoom + 1.4).coerceAtMost(16.5))),
                        ARRIVE_MS,
                        true,
                    )
                    arrive()
                }
            })
        }
        anim = a
        // 앞 곳(직전 비행이 끝난 자리)에서 구간 첫 장면으로 부드럽게 옮긴 뒤 출발한다
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(frameAt(frames, 0f, FLIGHT_PITCH)),
            DESCEND_MS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    if (!ended) {
                        a.start()
                        if (pausedNow) a.pause()
                    }
                }

                override fun onCancel() = Unit
            },
        )
        onDispose {
            ended = true
            anim = null
            a.cancel()
            m.cancelTransitions()
        }
    }
    LaunchedEffect(anim, paused) {
        val a = anim ?: return@LaunchedEffect
        if (paused) a.pause() else if (a.isPaused) a.resume()
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * 범위를 화면에 맞추되 기울기는 유지한다. MapLibre에 기울기(50°)째로 맞추라고 하면 먼 쪽까지 계산해 길이 아주 작아진다
 * (4.5km 하루가 화면의 3%) → 위에서 내려다본 채로 맞춘 줌을 쓰고 기울기만 더한다. 여백은 화면의 [padFraction] (최소 [minPadPx]).
 */
private fun fitTilted(m: MapLibreMap, view: MapView, b: MapBoundsLatLon, padFraction: Float, minPadPx: Float): CameraPosition? {
    val px = maxOf(minPadPx, view.width * padFraction).toInt()
    val py = maxOf(minPadPx, view.height * padFraction).toInt()
    val flat = m.getCameraForLatLngBounds(LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon), intArrayOf(px, py, px, py), OVERVIEW_BEARING, 0.0)
        ?: return null
    return CameraPosition.Builder(flat).zoom(flat.zoom.coerceAtMost(MAX_ZOOM)).tilt(OVERVIEW_PITCH).build()
}

private fun arrival(lat: Double, lon: Double, bearing: Double, zoom: Double): CameraPosition =
    CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).bearing(bearing.mod(360.0)).tilt(FLIGHT_PITCH - 5.0).build()

/** 지도 하나 + 노을 3D 스타일 + 금빛 길 레이어. 지도 엔진 준비 전(미리보기 포함)에는 null. */
@Composable
private fun rememberReplayMap(sky: SkyPhase, label: String, bounds: MapBoundsLatLon?, pulsing: Boolean = true): Pair<MapView, ReplayMapController>? {
    val inPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val options by produceState<MapLibreMapOptions?>(initialValue = null) {
        if (!inPreview) value = MapLibreNative.prepare(context.applicationContext)
    }
    val opts = options ?: return null
    val mapView = rememberMapViewWithLifecycle(context, opts, ReplaySunsetLand.toArgb(), initialCamera = remember { bounds?.let(::roughCamera) }, label = label)
    val controller = remember(mapView) { ReplayMapController(mapView) }
    LaunchedEffect(controller, sky) { controller.setStyle(replayStyle(context.applicationContext, sky)) }
    // 지금 곳 · 차 빛이 숨 쉬듯 커졌다 작아진다 (12fps, 레이어 2개 값만 바꾼다)
    LaunchedEffect(controller, pulsing) {
        if (!pulsing) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) {
            val sec = (System.nanoTime() - start) / 1e9
            controller.pulse((0.5 + 0.5 * kotlin.math.sin(sec * 2.0 * Math.PI / 1.8)).toFloat())
            delay(83)
        }
    }
    return mapView to controller
}

private suspend fun replayStyle(context: Context, sky: SkyPhase): String =
    runCatching { ScenicMapStyle.build(context, sky, EMPTY) }.getOrElse { MapStyleSource.fallbackJson(ReplaySunsetLand.toArgb()) }

/**
 * 그날 범위의 대략적인 처음 카메라. 없으면 지도가 서울(기본 위치)에서 시작해 그 타일을 헛받은 뒤 옮겨 간다 →
 * 작은 지도 · 끝 화면 지도가 그날 타일을 바로 받아 첫 장면이 빨리 뜬다. 정확한 맞춤은 레이아웃 뒤에 한다.
 */
private fun roughCamera(b: MapBoundsLatLon): CameraPosition =
    CameraPosition.Builder(roughFlatCamera(b)).bearing(OVERVIEW_BEARING).tilt(OVERVIEW_PITCH).build()

/** 레이아웃이 끝난 뒤 [block]의 값 (카메라 맞추기는 지도 크기가 있어야 한다). */
private suspend fun <T> awaitLayout(view: MapView, block: () -> T): T? {
    if (view.width > 0 && view.height > 0) return block()
    var out: T? = null
    var done = false
    view.doOnLayout {
        out = block()
        done = true
    }
    while (!done) delay(16)
    return out
}

private class ReplayMapController(mapView: MapView) {
    var map by mutableStateOf<MapLibreMap?>(null)
        private set
    private var pendingStyle: String? = null
    private var styleJson: String? = null
    private var doneSrc: GeoJsonSource? = null
    private var legSrc: GeoJsonSource? = null
    private var done: String = EMPTY
    private var leg: String = EMPTY

    init {
        mapView.addOnDidFailLoadingMapListener { msg -> Log.w(TAG, "스타일 불러오기 실패: $msg") }
        mapView.getMapAsync { m ->
            m.uiSettings.setAllGesturesEnabled(false)
            runCatching { m.setMaxPitchPreference(70.0) } // 비행 66°: 지평선이 보이게
            map = m
            pendingStyle?.let { setStyle(it) }
        }
    }

    fun setStyle(json: String) {
        val m = map
        if (m == null) {
            pendingStyle = json
            return
        }
        pendingStyle = null
        if (json == styleJson) return
        styleJson = json
        doneSrc = null
        legSrc = null
        style = null
        m.setStyle(Style.Builder().fromJson(json)) { s -> onStyle(s) }
    }

    fun setData(d: String, l: String?) {
        done = d
        leg = l ?: EMPTY
        doneSrc?.setGeoJson(done)
        legSrc?.setGeoJson(leg)
    }

    private var style: Style? = null

    /** 숨쉬기 [k] 0~1: 지금 곳의 빛 고리와 차 둘레 빛을 키우고 줄인다. */
    fun pulse(k: Float) {
        val s = style ?: return
        // 상수 값만 바꾼다 (곳마다 다른 식을 바꾸면 MapLibre가 그 소스 타일을 다시 만든다)
        (s.getLayer("$DONE-stop-glow-now") as? CircleLayer)?.setProperties(circleRadius(26f + 18f * k))
        (s.getLayer("$LEG-car-halo") as? CircleLayer)?.setProperties(circleRadius(34f + 14f * k), circleOpacity(0.35f + 0.3f * k))
    }

    fun setLeg(l: String) {
        leg = l
        legSrc?.setGeoJson(l)
    }

    private fun onStyle(s: Style) {
        style = s
        doneSrc = GeoJsonSource(DONE, done).also { s.addSource(it) }
        legSrc = GeoJsonSource(LEG, leg).also { s.addSource(it) }
        val round = arrayOf(lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_ROUND))
        for ((src, kind) in listOf(DONE to "route", LEG to "leg")) {
            val f = eq(get("kind"), literal(kind))
            // 은은한 바깥 빛 → 흰 테두리 1px → 금빛 길 6px (보랏빛 바다 · 살구빛 땅 어디서든 길이 또렷하게)
            s.addLayer(LineLayer("$src-halo", src).withFilter(f).withProperties(*round, lineWidth(24f), lineBlur(12f), lineOpacity(0.5f), lineColor(HALO)))
            s.addLayer(LineLayer("$src-casing", src).withFilter(f).withProperties(*round, lineWidth(8f), lineColor(android.graphics.Color.WHITE)))
            s.addLayer(LineLayer("$src-core", src).withFilter(f).withProperties(*round, lineWidth(6f), lineColor(GOLD)))
        }
        val isNow = eq(get("now"), literal(true))
        val isStop = eq(get("kind"), literal("stop"))
        // 빛 고리: 지난 곳(고정)과 지금 곳(숨쉬기)을 레이어로 나눠, 숨쉬기는 상수 반지름만 바꾼다
        for ((id, filter, radius) in listOf(
            Triple("$DONE-stop-glow", all(isStop, not(isNow)), 16f),
            Triple("$DONE-stop-glow-now", all(isStop, isNow), 30f),
        )) {
            s.addLayer(
                CircleLayer(id, DONE).withFilter(filter).withProperties(
                    circleRadius(radius),
                    circleColor(HALO),
                    circleBlur(1f),
                    circleOpacity(0.7f),
                    circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
                ),
            )
        }
        s.addLayer(
            CircleLayer("$DONE-stop", DONE).withFilter(eq(get("kind"), literal("stop"))).withProperties(
                circleRadius(switchCase(isNow, literal(10f), literal(6f))),
                circleColor(CORE),
                circleStrokeColor(GLOW),
                circleStrokeWidth(switchCase(isNow, literal(3f), literal(2f))),
            ),
        )
        s.addLayer(
            CircleLayer("$LEG-car-halo", LEG).withFilter(eq(get("kind"), literal("car"))).withProperties(
                circleRadius(40f),
                circleColor(HALO),
                circleBlur(1f),
                circleOpacity(0.55f),
            ),
        )
        s.addLayer(
            CircleLayer("$LEG-car-glow", LEG).withFilter(eq(get("kind"), literal("car"))).withProperties(
                circleRadius(22f),
                circleColor(CORE),
                circleBlur(0.8f),
                circleOpacity(0.9f),
            ),
        )
        s.addLayer(
            CircleLayer("$LEG-car", LEG).withFilter(eq(get("kind"), literal("car"))).withProperties(
                circleRadius(9f),
                circleColor(android.graphics.Color.WHITE),
                circleStrokeColor(GLOW),
                circleStrokeWidth(3f),
            ),
        )
    }

    companion object {
        const val TAG = "EodiReplayMap"
        const val DONE = "replay-done"
        const val LEG = "replay-leg"
        // 길 색은 앱의 길 색(파랑) 토큰을 따른다 (주황 · 금빛을 쓰지 않는다)
        val HALO = app.eodiyeotji.ui.theme.mapInk(true).trip.toArgb()
        val GLOW = app.eodiyeotji.ui.theme.mapInk(true).trip.toArgb()
        val CORE = Color.White.toArgb()
        val GOLD = app.eodiyeotji.ui.theme.mapInk(false).trip.toArgb()
    }
}

private const val PAD_DP = 28f
/** 끝 화면 지도: 그날 길 둘레 여백 (화면의 15%). */
private const val FIT_PAD_FRACTION = 0.15f
private const val MAX_ZOOM = 15.0
