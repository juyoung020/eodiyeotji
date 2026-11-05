package app.eodiyeotji.ui.map

import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.view.doOnLayout
import app.eodiyeotji.core.DayMapData
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.min

/** 경로·핀 색 (테마에서 뽑아 ARGB로 넘긴다). */
@Immutable
internal data class MapPalette(
    val trip: Int,
    val casing: Int,
    val stay: Int,
    val openStay: Int,
    val selectedStroke: Int,
    val background: Int,
)

/**
 * MapView 하나를 맡아 스타일·하루 데이터·선택 강조·카메라를 적용한다. 메인 스레드에서만 부른다.
 *
 * - 하루 데이터는 Rust가 만든 GeoJSON 문자열을 GeoJsonSource에 그대로 넣는다 (Kotlin에서 점을 가공하지 않는다).
 *   MapLibre가 파싱·타일링을 자기 작업 스레드에서 한다.
 * - 같은 날이 다시 읽히면 소스 데이터만 바꾸고, 날이 바뀌었을 때만 카메라를 범위에 맞춘다(애니메이션 없이 한 번에).
 * - 선택 강조는 데이터를 다시 넣지 않고 강조 레이어의 filter와 나머지의 투명도만 바꾼다.
 */
internal class DayMapController(
    private val mapView: MapView,
    private val density: Float,
    private val onSelectKey: (String?) -> Unit,
    private val onStyleFailed: () -> Unit,
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var source: GeoJsonSource? = null

    private var pendingStyleJson: String? = null
    private var styleJson: String? = null

    private var data: DayMapData? = null
    private var appliedData: DayMapData? = null
    private var fittedDay: String? = null
    private var selected: String? = null
    private var palette: MapPalette? = null

    init {
        mapView.addOnDidFailLoadingMapListener { msg ->
            Log.w(TAG, "스타일 불러오기 실패: $msg")
            onStyleFailed()
        }
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.setMaxZoomPreference(MAX_ZOOM)
            m.addOnMapClickListener { latLng -> onMapClick(m, latLng) }
            pendingStyleJson?.let { setStyle(it) }
        }
    }

    fun setStyle(json: String) {
        val m = map
        if (m == null) {
            pendingStyleJson = json
            return
        }
        pendingStyleJson = null
        if (json == styleJson) return
        styleJson = json
        // 스타일을 바꾸면 소스·레이어가 사라진다 → 새 스타일이 뜨면 처음부터 다시 얹는다
        style = null
        source = null
        appliedData = null
        m.setStyle(Style.Builder().fromJson(json)) { s -> onStyleLoaded(s) }
    }

    fun setPalette(p: MapPalette) {
        if (p == palette) return
        palette = p
        style?.let { applyPalette(it, p) }
    }

    fun setData(d: DayMapData?) {
        data = d
        applyData()
    }

    fun setSelection(key: String?) {
        if (key == selected) return
        selected = key
        style?.let { applySelection(it) }
        key?.let { revealEntry(it) }
    }

    // ------------------------------------------------------------ 내부

    private fun onStyleLoaded(s: Style) {
        style = s
        val src = GeoJsonSource(SOURCE_ID, EMPTY_GEOJSON)
        s.addSource(src)
        source = src

        val roundLine = arrayOf(lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_ROUND))

        // 경로는 바탕 지도의 글자(심볼) 아래에 깐다 → 길 이름이 경로에 가리지 않는다. 핀은 맨 위.
        val firstSymbol = s.layers.firstOrNull { it is SymbolLayer }?.id
        fun addLine(layer: Layer) = if (firstSymbol != null) s.addLayerBelow(layer, firstSymbol) else s.addLayer(layer)

        addLine(LineLayer(TRIP_CASING, SOURCE_ID).withFilter(isTrip).withProperties(*roundLine, lineWidth(10f)))
        addLine(LineLayer(TRIP_LINE, SOURCE_ID).withFilter(isTrip).withProperties(*roundLine, lineWidth(6f)))
        addLine(LineLayer(TRIP_SEL_CASING, SOURCE_ID).withFilter(NOTHING).withProperties(*roundLine, lineWidth(14f)))
        addLine(LineLayer(TRIP_SEL, SOURCE_ID).withFilter(NOTHING).withProperties(*roundLine, lineWidth(9f)))

        // 아직 머무는 곳(= 지금 차가 있는 곳)은 큰 핀 + 옅은 후광으로 구분한다
        s.addLayer(
            CircleLayer(STAY_HALO, SOURCE_ID)
                .withFilter(all(isStay, isOpen))
                .withProperties(circleRadius(24f), circleOpacity(HALO_OPACITY)),
        )
        val openRadius = switchCase(isOpen, literal(12f), literal(9f))
        s.addLayer(
            CircleLayer(STAY, SOURCE_ID).withFilter(isStay)
                .withProperties(circleRadius(openRadius), circleStrokeWidth(3f)),
        )
        s.addLayer(
            CircleLayer(STAY_SEL, SOURCE_ID).withFilter(NOTHING)
                .withProperties(circleRadius(switchCase(isOpen, literal(16f), literal(13f))), circleStrokeWidth(4f)),
        )

        palette?.let { applyPalette(s, it) }
        applySelection(s)
        applyData()
    }

    private fun applyPalette(s: Style, p: MapPalette) {
        val stayColor = switchCase(isOpen, color(p.openStay), color(p.stay))
        s.getLayer(TRIP_CASING)?.setProperties(lineColor(p.casing))
        s.getLayer(TRIP_LINE)?.setProperties(lineColor(p.trip))
        s.getLayer(TRIP_SEL_CASING)?.setProperties(lineColor(p.casing))
        s.getLayer(TRIP_SEL)?.setProperties(lineColor(p.trip))
        s.getLayer(STAY_HALO)?.setProperties(circleColor(p.openStay))
        s.getLayer(STAY)?.setProperties(circleColor(stayColor), circleStrokeColor(p.casing))
        s.getLayer(STAY_SEL)?.setProperties(circleColor(stayColor), circleStrokeColor(p.selectedStroke))
    }

    /** 고른 항목은 강조 레이어로 위에 다시 그리고, 나머지는 살짝 흐리게. */
    private fun applySelection(s: Style) {
        val key = selected
        val pick = if (key == null) NOTHING else eq(get("key"), literal(key))
        val dim = if (key == null) 1f else DIMMED_OPACITY
        // 종류도 같이 걸러야 한다: 원 레이어는 선의 꼭짓점마다 원을 찍는다
        s.getLayer(TRIP_SEL_CASING)?.let { (it as LineLayer).setFilter(all(isTrip, pick)) }
        s.getLayer(TRIP_SEL)?.let { (it as LineLayer).setFilter(all(isTrip, pick)) }
        s.getLayer(STAY_SEL)?.let { (it as CircleLayer).setFilter(all(isStay, pick)) }
        s.getLayer(TRIP_CASING)?.setProperties(lineOpacity(dim))
        s.getLayer(TRIP_LINE)?.setProperties(lineOpacity(dim))
        s.getLayer(STAY)?.setProperties(circleOpacity(dim), circleStrokeOpacity(dim))
        s.getLayer(STAY_HALO)?.setProperties(circleOpacity(HALO_OPACITY * dim))
    }

    private fun applyData() {
        val src = source ?: return
        val d = data
        if (d !== appliedData) {
            src.setGeoJson(d?.geojson ?: EMPTY_GEOJSON)
            appliedData = d
        }
        if (d != null && d.day != fittedDay) {
            fittedDay = d.day
            fitCamera(d)
        }
    }

    /** 그날 경로·핀 전체가 보이게 맞춘다. 범위가 한 점이면 너무 확대되지 않게 [MAX_FIT_ZOOM]에서 멈춘다. */
    private fun fitCamera(d: DayMapData) {
        val b = d.bounds ?: return
        val m = map ?: return
        mapView.doOnLayout {
            val bounds = LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon)
            val pad = (FIT_PADDING_DP * density).toInt()
            val cam = m.getCameraForLatLngBounds(bounds, intArrayOf(pad, pad, pad, pad)) ?: return@doOnLayout
            val pos = CameraPosition.Builder(cam).zoom(min(cam.zoom, MAX_FIT_ZOOM)).build()
            m.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
        }
    }

    /**
     * 고른 항목이 화면에 다 안 보이면 그 항목 범위로 부드럽게 옮긴다 (범위는 Rust가 계산해 준 값).
     * 이미 다 보이면 지도를 움직이지 않는다 → 지도에서 직접 누른 경우 화면이 튀지 않는다.
     */
    private fun revealEntry(key: String) {
        val m = map ?: return
        val b = data?.entryBounds?.get(key) ?: return
        val visible = m.projection.visibleRegion.latLngBounds
        if (visible.contains(LatLng(b.minLat, b.minLon)) && visible.contains(LatLng(b.maxLat, b.maxLon))) return
        val pad = (FIT_PADDING_DP * density).toInt()
        val target = if (b.minLat == b.maxLat && b.minLon == b.maxLon) {
            CameraUpdateFactory.newLatLngZoom(LatLng(b.minLat, b.minLon), MAX_FIT_ZOOM.coerceAtMost(m.cameraPosition.zoom.coerceAtLeast(REVEAL_MIN_ZOOM)))
        } else {
            CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon), pad)
        }
        m.easeCamera(target, REVEAL_MS)
    }

    /** 지도를 누르면 손가락 크기(76dp) 안의 핀 → 경로 순서로 찾아 그 항목을 고른다. 빈 곳이면 선택 해제. */
    private fun onMapClick(m: MapLibreMap, latLng: LatLng): Boolean {
        if (style == null) return false
        val p = m.projection.toScreenLocation(latLng)
        val r = TOUCH_RADIUS_DP * density
        val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
        val key = m.queryRenderedFeatures(box, STAY_SEL, STAY).firstNotNullOfOrNull { it.keyOrNull() }
            ?: m.queryRenderedFeatures(box, TRIP_SEL, TRIP_LINE).firstNotNullOfOrNull { it.keyOrNull() }
        onSelectKey(key)
        return true
    }

    private fun org.maplibre.geojson.Feature.keyOrNull(): String? =
        if (hasNonNullValueForProperty("key")) getStringProperty("key") else null

    private companion object {
        const val TAG = "EodiMap"
        const val SOURCE_ID = "eodi-day"
        const val TRIP_CASING = "eodi-trip-casing"
        const val TRIP_LINE = "eodi-trip"
        const val TRIP_SEL_CASING = "eodi-trip-sel-casing"
        const val TRIP_SEL = "eodi-trip-sel"
        const val STAY_HALO = "eodi-stay-open-halo"
        const val STAY = "eodi-stay"
        const val STAY_SEL = "eodi-stay-sel"

        const val EMPTY_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
        val NOTHING: Expression = literal(false)

        const val DIMMED_OPACITY = 0.35f
        const val HALO_OPACITY = 0.25f
        const val FIT_PADDING_DP = 56f
        const val TOUCH_RADIUS_DP = 38f
        const val MAX_FIT_ZOOM = 16.0
        const val MAX_ZOOM = 18.0
        const val REVEAL_MIN_ZOOM = 14.0
        const val REVEAL_MS = 400

        val isTrip: Expression = eq(get("kind"), literal("trip"))
        val isStay: Expression = eq(get("kind"), literal("stay"))
        val isOpen: Expression = eq(get("open"), literal(true))
    }
}
