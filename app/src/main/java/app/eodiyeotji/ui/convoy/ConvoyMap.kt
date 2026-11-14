package app.eodiyeotji.ui.convoy

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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import app.eodiyeotji.core.MapBoundsLatLon
import app.eodiyeotji.core.rust.ConvoyMemberView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import app.eodiyeotji.ui.map.MapLibreNative
import app.eodiyeotji.ui.map.MapStyleSource
import app.eodiyeotji.ui.map.MapStyleSpec
import app.eodiyeotji.ui.map.rememberMapViewWithLifecycle
import app.eodiyeotji.ui.theme.EodiTheme
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * 같이 가기 지도: 기기 안 지도 위에 Rust `convoy`가 만든 GeoJSON(차 · 나 · 목적지 · 지나온 길)을 그대로 얹는다.
 * 차 색은 [ConvoyColors] 번호, 나는 앱 강조색(파랑). 범위가 바뀌면 카메라를 맞춘다 (처음 한 번 + 차가 범위를 벗어날 때).
 */
@Composable
fun ConvoyMap(geojson: String, bounds: MapBoundsLatLon?, members: List<ConvoyMemberView>, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val night = EodiTheme.colors.isDark
    val options by produceState<MapLibreMapOptions?>(initialValue = null) {
        value = MapLibreNative.prepare(context)
    }
    val bg = EodiTheme.colors.surfaceHigh
    val o = options
    if (o == null || LocalInspectionMode.current) {
        Box(modifier.background(bg))
        return
    }
    val mapView = rememberMapViewWithLifecycle(LocalContext.current, o, bg.toArgb(), label = "convoy", overlay = true)
    val me = EodiTheme.colors.accent.toArgb()
    val ink = EodiTheme.colors.textPrimary.toArgb()
    val halo = EodiTheme.colors.surface.toArgb()
    val warn = EodiTheme.colors.error.toArgb()
    val controller = remember(mapView) { ConvoyMapController(mapView, me, ink, halo, warn) }
    var style by remember { mutableStateOf<MapStyleSpec?>(null) }
    LaunchedEffect(night) { style = MapStyleSource.resolve(context, night) }
    LaunchedEffect(style) {
        when (val s = style) {
            is MapStyleSpec.Offline -> controller.setStyle(s.json)
            is MapStyleSpec.Online -> controller.setStyle(s.json)
            MapStyleSpec.Fallback -> controller.setStyle(MapStyleSource.fallbackJson(bg.toArgb()))
            null -> Unit
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val surface = EodiTheme.colors.surface.toArgb()
    // 차 표시(핀 + 이름표)는 비트맵으로 미리 그려 둔다 (기본 스레드 밖에서)
    LaunchedEffect(geojson, bounds, members, night) {
        val icons = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            members.associate { m -> "car-${m.id}" to carMarker(m, density, ink, surface, warn) }
        }
        controller.setIcons(icons)
        controller.setData(geojson, bounds)
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 다른 차 색: 앱 파랑 · 경고 빨강과 겹치지 않는 것 (테마 [app.eodiyeotji.ui.theme.ConvoyCarColors]), 번호 순서대로 돌려 쓴다. */
object ConvoyColors {
    val argb = app.eodiyeotji.ui.theme.ConvoyCarColors.map { it.toArgb() }.toIntArray()
    fun of(index: Int): Int = argb[((index % argb.size) + argb.size) % argb.size]
}

private class ConvoyMapController(private val view: MapView, private val meColor: Int, private val ink: Int, private val halo: Int, private val warn: Int) {
    private var map: MapLibreMap? = null
    private var source: GeoJsonSource? = null
    private var pendingStyle: String? = null
    private var styleJson: String? = null
    private var data: String = EMPTY
    private var bounds: MapBoundsLatLon? = null
    private var fitted: MapBoundsLatLon? = null
    private var icons: Map<String, CarMarker> = emptyMap()
    private var style: Style? = null

    fun setIcons(next: Map<String, CarMarker>) {
        icons = next
        style?.let { s -> next.forEach { (id, b) -> s.addImage(id, b.bitmap) } }
    }

    init {
        view.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            pendingStyle?.let { setStyle(it) }
        }
    }

    fun setStyle(json: String) {
        val m = map ?: run { pendingStyle = json; return }
        if (json == styleJson) return
        styleJson = json
        source = null
        style = null
        m.setStyle(Style.Builder().fromJson(json)) { s -> onStyle(s) }
    }

    fun setData(geojson: String, b: MapBoundsLatLon?) {
        data = geojson.ifEmpty { EMPTY }
        bounds = b
        source?.setGeoJson(data)
        fit()
    }

    private fun colorExpr(): Expression {
        val stops = ConvoyColors.argb.indices.flatMap { i -> listOf(literal(i), Expression.color(ConvoyColors.of(i))) }.toTypedArray()
        // 번호가 색 수를 넘으면 나머지로 (Rust 번호는 0부터)
        // match(입력, 값1, 색1, …, 기본색): 기본색은 맨 뒤 (앞에 두면 식이 깨져 점이 검게 그려졌다)
        return Expression.match(Expression.mod(get("color"), literal(ConvoyColors.argb.size)), *stops, Expression.color(meColor))
    }

    private fun onStyle(s: Style) {
        style = s
        icons.forEach { (id, b) -> s.addImage(id, b.bitmap) }
        val src = GeoJsonSource(SRC, data)
        s.addSource(src)
        source = src
        val kind = get("kind")
        s.addLayer(
            LineLayer("convoy-trail", SRC).withFilter(eq(kind, literal("trail"))).withProperties(
                lineColor(Expression.switchCase(eq(get("color"), literal(-1)), Expression.color(meColor), colorExpr())),
                lineWidth(5f), lineOpacity(0.75f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        s.addLayer(
            CircleLayer("convoy-dest", SRC).withFilter(eq(kind, literal("dest"))).withProperties(
                circleRadius(13f), circleColor(ink), circleStrokeColor(halo), circleStrokeWidth(4f),
            ),
        )
        s.addLayer(
            CircleLayer("convoy-me", SRC).withFilter(eq(kind, literal("me"))).withProperties(
                circleRadius(16f), circleColor(meColor), circleStrokeColor(0xFFFFFFFF.toInt()), circleStrokeWidth(5f),
            ),
        )
        // 차: 미리 그린 핀(색 원 + 흰 테두리 + 첫 글자, 부족이면 빨간 테두리 + 느낌표) + 아래 이름표 칩. 핀 가운데가 차 위치
        s.addLayer(
            SymbolLayer("convoy-car", SRC).withFilter(eq(kind, literal("car"))).withProperties(
                iconImage(Expression.concat(literal("car-"), get("id"))),
                iconAnchor(Property.ICON_ANCHOR_TOP),
                iconOffset(arrayOf(0f, -CarMarker.PIN_CENTER_DP)),
                iconAllowOverlap(true), iconIgnorePlacement(true),
                iconOpacity(Expression.switchCase(eq(get("stale"), literal(true)), literal(0.55f), literal(1f))),
            ),
        )
        s.addLayer(
            SymbolLayer("convoy-label", SRC).withFilter(eq(kind, literal("dest"))).withProperties(
                textField(get("label")), textSize(20f), textColor(ink), textHaloColor(halo), textHaloWidth(2.5f),
                textFont(arrayOf(FONT)), textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 1.4f)),
                textAllowOverlap(true),
            ),
        )
        fitted = null
        fit()
    }

    /** 처음, 또는 새 범위가 맞춰 둔 범위를 벗어날 때만 카메라를 옮긴다 (사용자가 옮긴 지도를 4초마다 되돌리지 않게). */
    private fun fit() {
        val m = map ?: return
        if (source == null) return
        val b = bounds ?: return
        val f = fitted
        if (f != null && b.minLat >= f.minLat && b.maxLat <= f.maxLat && b.minLon >= f.minLon && b.maxLon <= f.maxLon) return
        val pad = 0.004
        val box = MapBoundsLatLon(b.minLat - pad, b.minLon - pad, b.maxLat + pad, b.maxLon + pad)
        fitted = box
        val ll = LatLngBounds.Builder().include(LatLng(box.minLat, box.minLon)).include(LatLng(box.maxLat, box.maxLon)).build()
        runCatching { m.easeCamera(CameraUpdateFactory.newLatLngBounds(ll, 80), 600) }
    }

    companion object {
        private const val SRC = "convoy"
        private const val FONT = "NotoSans-Medium"
        private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
    }
}

/** 지도 차 표시 한 장 (핀 + 이름표). */
internal class CarMarker(val bitmap: Bitmap) {
    companion object {
        /** 그림 위 끝에서 핀 가운데까지 (dp) */
        const val PIN_CENTER_DP = 36f
    }
}

/** 차 핀: 그림자 · 흰 테두리 · 색 원 · 첫 글자, 부족이면 빨간 테두리 + 느낌표 배지. 아래에 바탕 있는 이름표 칩. */
internal fun carMarker(m: ConvoyMemberView, d: Float, ink: Int, surface: Int, warn: Int): CarMarker {
    val low = m.low && !m.stale
    val color = ConvoyColors.of(m.color.toInt())
    val r = 28f * d
    val ring = (if (low) 5f else 4f) * d
    val cy = CarMarker.PIN_CENTER_DP * d
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 19f * d; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); this.color = if (low) warn else ink }
    val label = m.label
    val tw = text.measureText(label)
    val chipH = 34f * d
    val chipW = tw + 28f * d
    val w = maxOf(chipW, 2 * (r + ring) + 24f * d) + 12f * d
    val chipTop = cy + r + ring + 8f * d
    val h = chipTop + chipH + 8f * d
    val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val cx = w / 2f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // 테두리 + 그림자
    p.color = if (low) warn else 0xFFFFFFFF.toInt()
    p.setShadowLayer(4f * d, 0f, 1.5f * d, 0x40000000)
    cv.drawCircle(cx, cy, r + ring, p)
    p.clearShadowLayer()
    p.color = color
    cv.drawCircle(cx, cy, r, p)
    // 첫 글자
    val ini = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f * d; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); this.color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER }
    cv.drawText(m.initial, cx, cy - (ini.descent() + ini.ascent()) / 2f, ini)
    if (low) {
        val bx = cx + r * 0.78f
        val by = cy - r * 0.78f
        p.color = 0xFFFFFFFF.toInt(); cv.drawCircle(bx, by, 12f * d, p)
        p.color = warn; cv.drawCircle(bx, by, 10f * d, p)
        val ex = Paint(ini).apply { textSize = 15f * d }
        cv.drawText("!", bx, by - (ex.descent() + ex.ascent()) / 2f, ex)
    }
    // 이름표 칩
    p.color = surface
    p.setShadowLayer(3f * d, 0f, 1f * d, 0x33000000)
    val chip = RectF(cx - chipW / 2f, chipTop, cx + chipW / 2f, chipTop + chipH)
    cv.drawRoundRect(chip, chipH / 2f, chipH / 2f, p)
    text.textAlign = Paint.Align.CENTER
    cv.drawText(label, cx, chip.centerY() - (text.descent() + text.ascent()) / 2f, text)
    return CarMarker(bmp)
}
