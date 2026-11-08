package app.eodiyeotji.ui.map

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import app.eodiyeotji.core.MapBoundsLatLon
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.MapInk
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * 그날로 돌아가기의 작은 지도. 지나온 길([done])과 지금 그리는 구간 한 단계([leg], 선 + 차)를 Rust가 만든 GeoJSON 그대로 넣는다.
 * 카메라는 그날 범위([bounds])에 한 번 맞추고 움직이지 않는다 (작은 창이 흔들리지 않게). 손짓은 받지 않는다.
 */
@Composable
fun ReplayMap(bounds: MapBoundsLatLon?, done: String, leg: String?, modifier: Modifier = Modifier) {
    val inPreview = LocalInspectionMode.current
    val appContext = LocalContext.current.applicationContext
    val baseOptions by produceState<MapLibreMapOptions?>(initialValue = null) {
        if (!inPreview) value = MapLibreNative.prepare(appContext)
    }
    val options = baseOptions
    if (options == null) {
        Box(modifier.background(EodiTheme.colors.surfaceHigh))
        return
    }
    ReplayMapContent(bounds, done, leg, options, modifier)
}

@Composable
private fun ReplayMapContent(bounds: MapBoundsLatLon?, done: String, leg: String?, options: MapLibreMapOptions, modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val bg = EodiTheme.colors.surfaceHigh.toArgb()
    val mapView = rememberMapViewWithLifecycle(context, options, bg)
    var style by remember { mutableStateOf<MapStyleSpec?>(null) }
    val controller = remember(mapView) {
        ReplayMapController(mapView, density) {
            style = when (val s = style) {
                is MapStyleSpec.Offline -> s.offlineOnly?.let { MapStyleSpec.Offline(it) } ?: MapStyleSpec.Fallback
                else -> MapStyleSpec.Fallback
            }
        }
    }
    LaunchedEffect(Unit) { style = MapStyleSource.resolve(context.applicationContext) }
    LaunchedEffect(controller, style) {
        when (val s = style) {
            null -> Unit
            is MapStyleSpec.Offline -> controller.setStyle(s.json)
            is MapStyleSpec.Online -> controller.setStyle(s.json)
            MapStyleSpec.Fallback -> controller.setStyle(MapStyleSource.fallbackJson(bg))
        }
    }
    LaunchedEffect(controller, bounds) { controller.setBounds(bounds) }
    LaunchedEffect(controller, done, leg) { controller.setData(done, leg) }
    AndroidView(factory = { mapView }, modifier = modifier)
}

private class ReplayMapController(private val mapView: MapView, private val density: Float, private val onStyleFailed: () -> Unit) {
    private var map: MapLibreMap? = null
    private var styleJson: String? = null
    private var pendingStyle: String? = null
    private var doneSrc: GeoJsonSource? = null
    private var legSrc: GeoJsonSource? = null
    private var done: String = EMPTY
    private var leg: String = EMPTY
    private var bounds: MapBoundsLatLon? = null

    init {
        mapView.addOnDidFailLoadingMapListener { msg ->
            Log.w(TAG, "스타일 불러오기 실패: $msg")
            onStyleFailed()
        }
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.setAllGesturesEnabled(false)
            pendingStyle?.let { setStyle(it) }
            fit()
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
        m.setStyle(Style.Builder().fromJson(json)) { s -> onStyle(s) }
    }

    fun setBounds(b: MapBoundsLatLon?) {
        bounds = b
        fit()
    }

    fun setData(d: String, l: String?) {
        done = d
        leg = l ?: EMPTY
        doneSrc?.setGeoJson(done)
        legSrc?.setGeoJson(leg)
    }

    private fun onStyle(s: Style) {
        val d = GeoJsonSource(DONE, done).also { s.addSource(it) }
        val l = GeoJsonSource(LEG, leg).also { s.addSource(it) }
        doneSrc = d
        legSrc = l
        val round = arrayOf(lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_ROUND))
        val casing = MapInk.casing.toArgb()
        val trip = MapInk.trip.toArgb()
        for ((src, kind) in listOf(DONE to "route", LEG to "leg")) {
            val f = eq(get("kind"), literal(kind))
            s.addLayer(LineLayer("$src-casing", src).withFilter(f).withProperties(*round, lineWidth(9f), lineColor(casing)))
            s.addLayer(LineLayer("$src-line", src).withFilter(f).withProperties(*round, lineWidth(5f), lineColor(trip)))
        }
        val isNow = eq(get("now"), literal(true))
        s.addLayer(
            CircleLayer("$DONE-stop", DONE).withFilter(eq(get("kind"), literal("stop"))).withProperties(
                circleRadius(switchCase(isNow, literal(10f), literal(6f))),
                circleColor(MapInk.stay.toArgb()),
                circleStrokeColor(casing),
                circleStrokeWidth(switchCase(isNow, literal(3f), literal(2f))),
            ),
        )
        s.addLayer(
            CircleLayer("$LEG-car", LEG).withFilter(eq(get("kind"), literal("car"))).withProperties(
                circleRadius(9f),
                circleColor(MapInk.here.toArgb()),
                circleStrokeColor(casing),
                circleStrokeWidth(3f),
            ),
        )
    }

    private fun fit() {
        val b = bounds ?: return
        val m = map ?: return
        mapView.doOnLayout {
            val pad = (PAD_DP * density).toInt()
            val bb = LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon)
            val cam = m.getCameraForLatLngBounds(bb, intArrayOf(pad, pad, pad, pad)) ?: return@doOnLayout
            m.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
            if (m.cameraPosition.zoom > MAX_ZOOM) m.moveCamera(CameraUpdateFactory.zoomTo(MAX_ZOOM))
        }
    }

    companion object {
        const val TAG = "EodiReplayMap"
        const val DONE = "replay-done"
        const val LEG = "replay-leg"
        const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
        const val PAD_DP = 28f
        const val MAX_ZOOM = 15.0
    }
}
