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
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
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
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textLetterSpacing
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textRadialOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.textVariableAnchor
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
    /** 핀 안 순서 숫자 (머문 곳 · 지금 있는 곳 핀 위) */
    val onStay: Int,
    val onOpenStay: Int,
    /** 핀 옆 이름 글자와 그 테두리(후광) */
    val label: Int,
    val labelHalo: Int,
)

/**
 * MapView 하나를 맡아 스타일·하루 데이터·선택 강조·카메라를 적용한다. 메인 스레드에서만 부른다.
 *
 * - 하루 데이터는 Rust가 만든 GeoJSON 문자열을 GeoJsonSource에 그대로 넣는다 (Kotlin에서 점을 가공하지 않는다).
 *   MapLibre가 파싱·타일링을 자기 작업 스레드에서 한다.
 * - 같은 날이 다시 읽히면 소스 데이터만 바꾸고, 날이 바뀌었을 때만 카메라를 범위에 맞춘다(애니메이션 없이 한 번에).
 * - 선택 강조는 데이터를 다시 넣지 않고 강조 레이어의 filter와 나머지의 투명도만 바꾼다.
 * - 머문 곳 핀에는 그날 순서(GeoJSON `order`)를 핀 안에, 이름(`name`)을 핀 옆에 띄운다. 글자 레이어는 에뮬레이터에서
 *   지도 끌기를 느리게 하므로 머문 곳(하루 몇 개)에만 쓰고, 이름은 서로 겹치면 빼고(text-allow-overlap=false)
 *   멀리서 볼 때(줌 [NAME_MIN_ZOOM] 미만)는 숨긴다. 고른 머문 곳 이름만 크게, 겹쳐도 보이게 따로 그린다.
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

        addLine(LineLayer(TRIP_CASING, SOURCE_ID).withFilter(isTrip).withProperties(*roundLine, lineWidth(12f)))
        addLine(LineLayer(TRIP_LINE, SOURCE_ID).withFilter(isTrip).withProperties(*roundLine, lineWidth(7f)))
        addLine(LineLayer(TRIP_SEL_CASING, SOURCE_ID).withFilter(NOTHING).withProperties(*roundLine, lineWidth(18f)))
        addLine(LineLayer(TRIP_SEL, SOURCE_ID).withFilter(NOTHING).withProperties(*roundLine, lineWidth(11f)))

        // 아직 머무는 곳(= 지금 차가 있는 곳)은 큰 핀 + 옅은 후광으로 구분한다
        s.addLayer(
            CircleLayer(STAY_HALO, SOURCE_ID)
                .withFilter(all(isStay, isOpen))
                .withProperties(circleRadius(28f), circleOpacity(HALO_OPACITY)),
        )
        // 순서 숫자가 들어가는 핀(order 있음)은 숫자가 들어갈 만큼 키운다
        val radius = switchCase(
            isOpen, switchCase(hasOrder, literal(16f), literal(13f)),
            switchCase(hasOrder, literal(14f), literal(11f)),
        )
        val selRadius = switchCase(
            isOpen, switchCase(hasOrder, literal(20f), literal(18f)),
            switchCase(hasOrder, literal(19f), literal(16f)),
        )
        val selHaloRadius = switchCase(isOpen, literal(34f), switchCase(hasOrder, literal(32f), literal(29f)))
        s.addLayer(
            CircleLayer(STAY, SOURCE_ID).withFilter(isStay)
                .withProperties(circleRadius(radius), circleStrokeWidth(3.5f)),
        )
        // 고른 핀 뒤 옅은 후광 (핀 색, 원 레이어라 글자 레이어처럼 끌기를 느리게 하지 않는다)
        s.addLayer(
            CircleLayer(STAY_SEL_HALO, SOURCE_ID).withFilter(NOTHING)
                .withProperties(circleRadius(selHaloRadius), circleOpacity(SEL_HALO_OPACITY)),
        )
        s.addLayer(
            CircleLayer(STAY_SEL, SOURCE_ID).withFilter(NOTHING)
                .withProperties(circleRadius(selRadius), circleStrokeWidth(4.5f)),
        )

        // 핀 안 순서 숫자: 핀과 한 몸이라 겹침 검사 없이 항상 그린다 (숫자만 → 글꼴 0-255 범위 하나)
        s.addLayer(
            SymbolLayer(STAY_NUM, SOURCE_ID).withFilter(all(isStay, hasOrder)).withProperties(
                textField(Expression.toString(get("order"))),
                textFont(arrayOf(FONT_MEDIUM)),
                textSize(switchCase(isOpen, literal(15f), literal(14f))),
                textAllowOverlap(true),
                textIgnorePlacement(true),
            ),
        )
        // 핀 옆 이름: 오른쪽 → 왼쪽 → 위 → 아래 중 빈 자리에, 자리가 없으면 뺀다
        val nameLayout = arrayOf(
            textField(get("name")),
            textFont(arrayOf(FONT_REGULAR)),
            textVariableAnchor(NAME_ANCHORS),
            textRadialOffset(NAME_OFFSET_EM),
            textMaxWidth(NAME_MAX_WIDTH_EM),
            textLetterSpacing(HANGUL_LETTER_SPACING),
            textHaloWidth(2f),
        )
        s.addLayer(
            SymbolLayer(STAY_NAME, SOURCE_ID).withFilter(all(isStay, hasName)).withProperties(
                *nameLayout,
                textSize(NAME_SIZE),
                textAllowOverlap(false),
            ).apply { minZoom = NAME_MIN_ZOOM },
        )
        // 고른 머문 곳 이름: 더 크게, 줌·겹침과 상관없이 항상
        s.addLayer(
            SymbolLayer(STAY_NAME_SEL, SOURCE_ID).withFilter(NOTHING).withProperties(
                *nameLayout,
                textSize(NAME_SEL_SIZE),
                textAllowOverlap(true),
            ),
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
        s.getLayer(STAY_SEL_HALO)?.setProperties(circleColor(stayColor))
        s.getLayer(STAY_NUM)?.setProperties(textColor(switchCase(isOpen, color(p.onOpenStay), color(p.onStay))))
        s.getLayer(STAY_NAME)?.setProperties(textColor(p.label), textHaloColor(p.labelHalo))
        s.getLayer(STAY_NAME_SEL)?.setProperties(textColor(p.label), textHaloColor(p.labelHalo))
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
        s.getLayer(STAY_SEL_HALO)?.let { (it as CircleLayer).setFilter(all(isStay, pick)) }
        // 고른 곳 이름은 강조 레이어로 옮기고 보통 이름 레이어에서는 뺀다 (두 번 그리지 않게)
        s.getLayer(STAY_NAME_SEL)?.let { (it as SymbolLayer).setFilter(all(isStay, hasName, pick)) }
        s.getLayer(STAY_NAME)?.let { (it as SymbolLayer).setFilter(all(isStay, hasName, not(pick))) }
        s.getLayer(TRIP_CASING)?.setProperties(lineOpacity(dim))
        s.getLayer(TRIP_LINE)?.setProperties(lineOpacity(dim))
        s.getLayer(STAY)?.setProperties(circleOpacity(dim), circleStrokeOpacity(dim))
        s.getLayer(STAY_HALO)?.setProperties(circleOpacity(HALO_OPACITY * dim))
        // 숫자는 고른 핀(강조 원 위)만 진하게, 나머지는 원과 같이 흐리게
        s.getLayer(STAY_NUM)?.setProperties(textOpacity(if (key == null) literal(1f) else switchCase(pick, literal(1f), literal(dim))))
        s.getLayer(STAY_NAME)?.setProperties(textOpacity(dim))
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

    /** 지도를 누르면 손가락 크기(76dp) 안의 핀(이름 글자 포함) → 경로 순서로 찾아 그 항목을 고른다. 빈 곳이면 선택 해제. */
    private fun onMapClick(m: MapLibreMap, latLng: LatLng): Boolean {
        if (style == null) return false
        val p = m.projection.toScreenLocation(latLng)
        val r = TOUCH_RADIUS_DP * density
        val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
        val key = m.queryRenderedFeatures(box, STAY_SEL, STAY, STAY_NAME_SEL, STAY_NAME).firstNotNullOfOrNull { it.keyOrNull() }
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
        const val STAY_SEL_HALO = "eodi-stay-sel-halo"
        const val STAY_NUM = "eodi-stay-order"
        const val STAY_NAME = "eodi-stay-name"
        const val STAY_NAME_SEL = "eodi-stay-name-sel"

        /** 앱 assets 글꼴 이름 (scripts/map/README.md 스타일 약속). 한글은 기기 글꼴(localIdeographFontFamily)로 그린다. */
        const val FONT_REGULAR = "NotoSans-Regular"
        const val FONT_MEDIUM = "NotoSans-Medium"

        const val EMPTY_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
        val NOTHING: Expression = literal(false)

        const val DIMMED_OPACITY = 0.35f
        const val HALO_OPACITY = 0.22f
        const val SEL_HALO_OPACITY = 0.28f
        const val FIT_PADDING_DP = 56f
        const val TOUCH_RADIUS_DP = 38f
        const val MAX_FIT_ZOOM = 16.0
        const val MAX_ZOOM = 18.0
        const val REVEAL_MIN_ZOOM = 14.0
        const val REVEAL_MS = 400

        const val NAME_SIZE = 15f
        const val NAME_SEL_SIZE = 19f
        const val NAME_MIN_ZOOM = 11f
        const val NAME_OFFSET_EM = 1.1f
        const val NAME_MAX_WIDTH_EM = 9f

        /** 바탕 지도 한글 라벨과 같은 글자 간격 (scripts/map/README.md "글자") */
        const val HANGUL_LETTER_SPACING = -0.08f
        val NAME_ANCHORS = arrayOf(
            Property.TEXT_ANCHOR_LEFT, Property.TEXT_ANCHOR_RIGHT, Property.TEXT_ANCHOR_TOP, Property.TEXT_ANCHOR_BOTTOM,
        )

        val isTrip: Expression = eq(get("kind"), literal("trip"))
        val isStay: Expression = eq(get("kind"), literal("stay"))
        val isOpen: Expression = eq(get("open"), literal(true))
        val hasOrder: Expression = has("order")
        val hasName: Expression = has("name")
    }
}
