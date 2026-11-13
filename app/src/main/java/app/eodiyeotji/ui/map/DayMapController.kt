package app.eodiyeotji.ui.map

import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.Immutable
import android.animation.ValueAnimator
import android.view.animation.OvershootInterpolator
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
import org.maplibre.android.style.expressions.Expression.any
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.concat
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.expressions.Expression.accumulated
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
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconPadding
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.PropertyFactory.symbolSpacing
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
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
    /** 카드 이름 글자, 출발·도착 깃발 바탕 */
    val label: Int,
)

/**
 * MapView 하나를 맡아 스타일·하루 데이터·선택 강조·카메라를 적용한다. 메인 스레드에서만 부른다.
 *
 * - 하루 데이터는 Rust가 만든 GeoJSON 문자열을 GeoJsonSource에 그대로 넣는다 (Kotlin에서 점을 가공하지 않는다).
 *   MapLibre가 파싱·타일링을 자기 작업 스레드에서 한다.
 * - 같은 날이 다시 읽히면 소스 데이터만 바꾸고, 날이 바뀌었을 때만 카메라를 범위에 맞춘다(애니메이션 없이 한 번에).
 * - 선택 강조는 데이터를 다시 넣지 않고 강조 레이어의 filter와 나머지의 투명도만 바꾼다.
 * - 경로: 그림자 · 흰 테두리 · 경로색 선 위에 진행 방향 흰 화살표(선 따라 일정 간격), 주행 가운데에 "12분 · 4.9km" 알약,
 *   머문 곳이 아닌 출발·도착 지점에는 깃발.
 * - 머문 곳: 순서 숫자 핀 + 그 위 말풍선 카드(대표 사진·이름·"18분 정차", [setIcons]). 카드끼리 겹치면 순서가 빠른
 *   카드가 이기고, 빠진 곳은 남는 자리가 있으면 작은 사진 말풍선으로, 그마저 겹치면 순서 핀만 남는다.
 *   고른 곳 카드는 늘 보인다.
 * - 아이콘은 전부 미리 그린 그림이다. 글자 레이어(글꼴 모양 잡기)는 에뮬레이터에서 지도 끌기를 느리게 해서
 *   핀 안 숫자 하나만 쓴다.
 */
internal class DayMapController(
    private val mapView: MapView,
    private val density: Float,
    private val onSelectKey: (String?) -> Unit,
    private val onStyleFailed: () -> Unit,
    /** 사진 말풍선을 눌렀을 때 (크게 보기): 그 핀(묶였으면 묶인 사진 전부)의 사진 번호, 찍은 시각순 */
    private val onOpenPhotos: (List<Int>) -> Unit = {},
    /** 카메라 핀을 눌렀을 때: 그 핀의 사진 번호들과 자리 → 부르는 쪽이 말풍선을 그려 [setPhotoBubble]로 돌려준다. 빈 목록 = 선택 해제 */
    private val onSelectPhotos: (idx: List<Int>, lat: Double, lon: Double) -> Unit = { _, _, _ -> },
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
    private var icons: MarkerIcons? = null
    private var addedImages: Set<String> = emptySet()
    private var myPhotos: MyPhotoIcons? = null
    private var photoSource: GeoJsonSource? = null
    private var addedPhotoImages: Set<String> = emptySet()
    private var bubble: PhotoBubble? = null
    private var bubbleSource: GeoJsonSource? = null
    /** 한 문장 코스 장면 진행 (0 = 그날 달린 길 그대로, 1 = 뺀 곳 ✕ 카드 · 점선 새 코스 · 흐린 옛길). 보통은 1. */
    private var planT = 1f
    private var planAnim: ValueAnimator? = null

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
        // 스타일을 바꾸면 소스·레이어·이미지가 사라진다 → 새 스타일이 뜨면 처음부터 다시 얹는다
        style = null
        source = null
        appliedData = null
        addedImages = emptySet()
        photoSource = null
        addedPhotoImages = emptySet()
        bubbleSource = null
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

    /** 카드·작은 사진·주행 글·깃발 그림 ([buildMarkerIcons]). 지금 지도 데이터와 같은 날 것일 때만 보인다. */
    fun setIcons(i: MarkerIcons?) {
        if (i === icons) return
        icons = i
        style?.let { applyIcons(it) }
    }

    /** 고른 핀의 사진 말풍선 ([buildPhotoBubble]). null이면 뺀다. */
    fun setPhotoBubble(b: PhotoBubble?) {
        if (b === bubble) return
        bubble = b
        style?.let { applyBubble(it) }
        b?.let { revealBubble(it) }
    }

    /** 말풍선이 지도 밖으로 잘리지 않게, 모자란 만큼만 지도를 민다 (핀 위로 말풍선 높이, 좌우로 반 너비). */
    private fun revealBubble(b: PhotoBubble) {
        val m = map ?: return
        val p = m.projection.toScreenLocation(LatLng(b.lat, b.lon))
        val margin = 12f * density
        val needTop = b.image.height + (PIN_SEL_DP / 2 + 2f) * density + margin
        val half = b.image.width / 2f + margin
        val toX = p.x.coerceIn(half, (mapView.width - half).coerceAtLeast(half))
        val toY = p.y.coerceIn(needTop.coerceAtMost(mapView.height - margin), mapView.height - margin)
        if (toX == p.x && toY == p.y) return
        val center = m.projection.fromScreenLocation(android.graphics.PointF(mapView.width / 2f + (p.x - toX), mapView.height / 2f + (p.y - toY)))
        m.easeCamera(CameraUpdateFactory.newLatLng(center), REVEAL_MS)
    }

    private fun applyBubble(s: Style) {
        val b = bubble
        if (b == null) {
            bubbleSource?.setGeoJson(EMPTY_GEOJSON)
            return
        }
        s.addImage(BUBBLE, b.image)
        bubbleSource?.setGeoJson(
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${b.lon},${b.lat}]},"properties":{}}]}""",
        )
    }

    /** 내 사진 핀 ([buildMyPhotoIcons]). null이면 뺀다. */
    fun setMyPhotos(p: MyPhotoIcons?) {
        if (p === myPhotos) return
        val first = myPhotos == null && p != null
        myPhotos = p
        // 사진 목록이 바뀌면 떠 있던 말풍선은 닫는다 (번호가 달라진다)
        setPhotoBubble(null)
        style?.let { applyMyPhotos(it) }
        // 사진이 지도보다 늦게 오면 카드 자리만큼 아래를 비워 다시 맞춘다
        if (first) data?.takeIf { fittedDay == it.day }?.let { fitCamera(it) }
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
        s.addImage(MapIconId.ARROW, arrowIcon(density))

        val roundLine = arrayOf(lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_ROUND))

        // 경로는 바탕 지도의 글자(심볼) 아래에 깐다 → 길 이름이 경로에 가리지 않는다. 핀·카드는 맨 위.
        val firstSymbol = s.layers.firstOrNull { it is SymbolLayer }?.id
        fun addLine(layer: Layer) = if (firstSymbol != null) s.addLayerBelow(layer, firstSymbol) else s.addLayer(layer)

        addLine(
            LineLayer(TRIP_SHADOW, SOURCE_ID).withFilter(isTrip)
                .withProperties(*roundLine, lineWidth(widthByZoom(SHADOW_W)), lineBlur(4f), lineColor(SHADOW_COLOR)),
        )
        addLine(LineLayer(TRIP_CASING, SOURCE_ID).withFilter(isTrip).withProperties(*roundLine, lineWidth(widthByZoom(CASING_W))))
        addLine(LineLayer(TRIP_LINE, SOURCE_ID).withFilter(isTrip).withProperties(*roundLine, lineWidth(widthByZoom(LINE_W))))
        addLine(LineLayer(TRIP_SEL_CASING, SOURCE_ID).withFilter(NOTHING).withProperties(*roundLine, lineWidth(widthByZoom(SEL_CASING_W))))
        addLine(LineLayer(TRIP_SEL, SOURCE_ID).withFilter(NOTHING).withProperties(*roundLine, lineWidth(widthByZoom(SEL_LINE_W))))
        // 한 문장 코스로 다시 짠 길: 흰 테두리 + 점선 (그날 달린 길은 흐리게 뒤에 남는다)
        addLine(LineLayer(PLAN_CASING, SOURCE_ID).withFilter(isPlan).withProperties(*roundLine, lineWidth(widthByZoom(CASING_W))))
        addLine(
            LineLayer(PLAN_LINE, SOURCE_ID).withFilter(isPlan)
                .withProperties(lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_BUTT), lineWidth(widthByZoom(LINE_W)), lineDasharray(arrayOf(2f, 1.2f))),
        )
        // 진행 방향 화살표: 선을 따라 일정 간격, 다른 글자와 자리 다툼 없이 (아주 멀리서는 숨김, 선 굵기 따라 크기)
        addLine(
            SymbolLayer(TRIP_ARROW, SOURCE_ID).withFilter(isTrip).withProperties(
                symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                symbolSpacing(ARROW_SPACING_DP),
                iconImage(MapIconId.ARROW),
                iconSize(interpolate(linear(), zoom(), stop(ARROW_MIN_ZOOM, 0.75f), stop(13, 1f), stop(17, 1.3f))),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            ).apply { minZoom = ARROW_MIN_ZOOM },
        )

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
        // 출발·도착 지점 점 (머문 곳이 아닐 때만 Rust가 넣는다)
        s.addLayer(
            CircleLayer(END_DOT, SOURCE_ID).withFilter(isEnd)
                .withProperties(circleRadius(7f), circleStrokeWidth(4f)),
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
        // 주행 가운데 글: 선 위 가운데. 카드와 겹치면 빠진다(카드가 먼저 자리를 잡는다).
        s.addLayer(
            SymbolLayer(TRIP_LABEL, SOURCE_ID).withFilter(NOTHING).withProperties(
                iconImage(concat(literal(MapIconId.LABEL), get("key"))),
                iconAllowOverlap(false),
                iconPadding(4f),
            ).apply { minZoom = LABEL_MIN_ZOOM },
        )
        // 출발·도착 깃발: 점 바로 위
        s.addLayer(
            SymbolLayer(END_FLAG, SOURCE_ID).withFilter(NOTHING).withProperties(
                iconImage(concat(literal(MapIconId.END), get("role"))),
                iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                iconOffset(arrayOf(0f, -END_LIFT_DP)),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            ),
        )
        // 카드가 빠진 곳의 작은 사진 말풍선: 카드 다음 순서로 자리를 잡는다. 제 카드나 다른 카드·말풍선과 겹치면
        // 빠지므로 카드가 보이는 곳에는 안 그려진다.
        val calloutLayout = arrayOf(
            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            iconOffset(arrayOf(0f, -CALLOUT_PIN_LIFT_DP)),
            // 한 문장 코스로 뺀 곳 카드가 가장 먼저 자리를 잡는다 (빠진 걸 보여 주는 게 그 지도의 요점)
            symbolSortKey(switchCase(isSkipped, literal(SKIPPED_SORT), coalesce(get("order"), literal(NO_ORDER_SORT)))),
        )
        // 내 사진: 찍은 곳에 작은 카메라 핀. 가까운 사진은 지도가 한 핀으로 묶고 장수 배지를 붙인다.
        // 늘 보이게 하되 다른 표시의 자리를 뺏지 않는다. 고른 핀은 금색으로 커지고 그 위에 사진 말풍선이 뜬다.
        val photos = GeoJsonSource(
            PHOTO_SOURCE_ID,
            EMPTY_GEOJSON,
            GeoJsonOptions().withCluster(true).withClusterRadius(PHOTO_CLUSTER_RADIUS).withClusterMaxZoom(PHOTO_CLUSTER_MAX_ZOOM)
                .withClusterProperty("rep", Expression.min(accumulated(), get("rep")), get("idx")),
        )
        s.addSource(photos)
        photoSource = photos
        val photoLayout = arrayOf(iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER))
        s.addLayer(SymbolLayer(PHOTO, PHOTO_SOURCE_ID).withProperties(*photoLayout, iconImage(PIN)))
        s.addLayer(
            SymbolLayer(PHOTO_BADGE, PHOTO_SOURCE_ID).withFilter(has("point_count")).withProperties(
                *photoLayout,
                iconOffset(arrayOf(PHOTO_BADGE_X_DP, -PHOTO_BADGE_LIFT_DP)),
                iconImage(concat(literal(BADGE_PREFIX), Expression.toString(Expression.min(get("point_count"), literal(MAX_BADGE))))),
            ),
        )
        s.addLayer(
            SymbolLayer(STAY_MINI, SOURCE_ID).withFilter(NOTHING).withProperties(
                *calloutLayout,
                iconImage(concat(literal(MapIconId.MINI), get("key"))),
                iconAllowOverlap(false),
                iconPadding(2f),
            ),
        )
        // 카드: 겹치면 순서가 빠른 것만 (symbol-sort-key = 순서)
        s.addLayer(
            SymbolLayer(STAY_CARD, SOURCE_ID).withFilter(NOTHING).withProperties(
                *calloutLayout,
                iconImage(concat(literal(MapIconId.CARD), get("key"))),
                iconAllowOverlap(false),
                iconPadding(2f),
            ),
        )
        // 고른 곳 카드: 겹침과 상관없이 맨 위에 늘 (다른 카드가 이 자리를 피한다)
        s.addLayer(
            SymbolLayer(STAY_CARD_SEL, SOURCE_ID).withFilter(NOTHING).withProperties(
                *calloutLayout,
                iconImage(concat(literal(MapIconId.CARD), get("key"))),
                iconAllowOverlap(true),
            ),
        )

        // 고른 사진 핀과 말풍선은 맨 위에 (머문 곳 말풍선에 가리지 않게)
        val picked = GeoJsonSource(PHOTO_PICK_SOURCE_ID, EMPTY_GEOJSON)
        s.addSource(picked)
        bubbleSource = picked
        s.addLayer(SymbolLayer(PHOTO_SEL, PHOTO_PICK_SOURCE_ID).withProperties(*photoLayout, iconImage(PIN_SEL)))
        s.addLayer(
            SymbolLayer(PHOTO_BUBBLE, PHOTO_PICK_SOURCE_ID).withProperties(
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                iconOffset(arrayOf(0f, -(PIN_SEL_DP / 2 + 2f))),
                iconImage(BUBBLE),
            ),
        )

        palette?.let { applyPalette(s, it) }
        applyIcons(s)
        applyMyPhotos(s)
        applyBubble(s)
        applySelection(s)
        applyData()
    }

    private fun applyMyPhotos(s: Style) {
        val p = myPhotos
        val now = p?.images?.keys.orEmpty()
        (addedPhotoImages - now).forEach { s.removeImage(it) }
        p?.images?.forEach { (id, bmp) -> s.addImage(id, bmp) }
        addedPhotoImages = now
        photoSource?.setGeoJson(p?.geojson ?: EMPTY_GEOJSON)
    }

    /** 새 그림을 스타일에 넣고(같은 이름은 덮어씀) 이제 안 쓰는 그림은 뺀다. */
    private fun applyIcons(s: Style) {
        val i = icons
        val now = i?.images?.keys.orEmpty()
        (addedImages - now).forEach { s.removeImage(it) }
        i?.images?.forEach { (id, bmp) -> s.addImage(id, bmp) }
        addedImages = now
        applyFilters(s)
    }

    private fun applyPalette(s: Style, p: MapPalette) {
        val stayColor = switchCase(isSkipped, color(SKIPPED_COLOR), isOpen, color(p.openStay), color(p.stay))
        s.getLayer(TRIP_CASING)?.setProperties(lineColor(p.casing))
        s.getLayer(TRIP_LINE)?.setProperties(lineColor(p.trip))
        s.getLayer(TRIP_SEL_CASING)?.setProperties(lineColor(p.casing))
        s.getLayer(TRIP_SEL)?.setProperties(lineColor(p.trip))
        s.getLayer(PLAN_CASING)?.setProperties(lineColor(p.casing))
        s.getLayer(PLAN_LINE)?.setProperties(lineColor(p.trip))
        s.getLayer(STAY_HALO)?.setProperties(circleColor(p.openStay))
        s.getLayer(STAY)?.setProperties(circleColor(stayColor), circleStrokeColor(p.casing))
        s.getLayer(STAY_SEL)?.setProperties(circleColor(stayColor), circleStrokeColor(p.selectedStroke))
        s.getLayer(STAY_SEL_HALO)?.setProperties(circleColor(stayColor))
        s.getLayer(END_DOT)?.setProperties(circleColor(p.casing), circleStrokeColor(p.label))
        s.getLayer(STAY_NUM)?.setProperties(textColor(switchCase(isOpen, color(p.onOpenStay), color(p.onStay))))
    }

    /**
     * 그림 레이어의 filter: 그림이 지금 지도 데이터(같은 GeoJSON)로 그린 것일 때만 켠다
     * (날이 바뀐 직후 옛 그림 이름을 찾지 않게). 고른 곳 카드는 강조 레이어로 옮긴다.
     */
    private fun applyFilters(s: Style) {
        val i = icons
        val ready = i != null && appliedData != null && i.geojson == appliedData?.geojson
        val pick = pick()
        fun SymbolLayer.filter(e: Expression) = setFilter(if (ready) e else NOTHING)
        (s.getLayer(STAY_CARD) as? SymbolLayer)?.filter(all(isStay, not(pick)))
        (s.getLayer(STAY_CARD_SEL) as? SymbolLayer)?.filter(all(isStay, pick))
        val minis = i?.miniKeys.orEmpty().map { eq(get("key"), literal(it)) }
        (s.getLayer(STAY_MINI) as? SymbolLayer)?.filter(if (minis.isEmpty()) NOTHING else all(isStay, any(*minis.toTypedArray())))
        (s.getLayer(TRIP_LABEL) as? SymbolLayer)?.filter(isLabel)
        (s.getLayer(END_FLAG) as? SymbolLayer)?.filter(isEnd)
    }

    private fun pick(): Expression = selected?.let { eq(get("key"), literal(it)) } ?: NOTHING

    /** 고른 항목은 강조 레이어로 위에 다시 그리고, 나머지는 살짝 흐리게. 카드는 글을 읽을 수 있게 흐리게 하지 않는다. */
    private fun applySelection(s: Style) {
        val key = selected
        val pick = pick()
        val dim = if (key == null) 1f else DIMMED_OPACITY
        val keepPicked = if (key == null) literal(1f) else switchCase(pick, literal(1f), literal(dim))
        // 종류도 같이 걸러야 한다: 원 레이어는 선의 꼭짓점마다 원을 찍는다
        s.getLayer(TRIP_SEL_CASING)?.let { (it as LineLayer).setFilter(all(isTrip, pick)) }
        s.getLayer(TRIP_SEL)?.let { (it as LineLayer).setFilter(all(isTrip, pick)) }
        s.getLayer(STAY_SEL)?.let { (it as CircleLayer).setFilter(all(isStay, pick)) }
        s.getLayer(STAY_SEL_HALO)?.let { (it as CircleLayer).setFilter(all(isStay, pick)) }
        // 한 문장 코스로 다시 짰으면 그날 달린 길(faded)은 더 흐리게
        applyPlan(s)
        s.getLayer(TRIP_LABEL)?.setProperties(iconOpacity(keepPicked))
        s.getLayer(END_DOT)?.setProperties(circleOpacity(dim), circleStrokeOpacity(dim))
        s.getLayer(END_FLAG)?.setProperties(iconOpacity(dim))
        s.getLayer(STAY_HALO)?.setProperties(circleOpacity(HALO_OPACITY * dim))
        // 숫자는 고른 핀(강조 원 위)만 진하게, 나머지는 원과 같이 흐리게
        s.getLayer(STAY_NUM)?.setProperties(textOpacity(keepPicked))
        applyFilters(s)
    }

    /**
     * 한 문장 코스 장면의 [planT]에 따라: 그날 달린 길(faded)이 흐려지고, 점선 새 코스가 나타나고, 뺀 곳 ✕ 카드가 튀어나온다.
     * 애니메이션 한 프레임마다 부르므로 이 속성들만 바꾼다.
     */
    private fun applyPlan(s: Style) {
        val t = planT
        val dim = if (selected == null) 1f else DIMMED_OPACITY
        val keepPicked = if (selected == null) literal(1f) else switchCase(pick(), literal(1f), literal(dim))
        val faded = dim * (1f - (1f - FADED_OPACITY) * t)
        val tripDim = switchCase(isFaded, literal(faded), literal(dim))
        s.getLayer(TRIP_SHADOW)?.setProperties(lineOpacity(tripDim))
        s.getLayer(TRIP_CASING)?.setProperties(lineOpacity(tripDim))
        s.getLayer(TRIP_LINE)?.setProperties(lineOpacity(tripDim))
        s.getLayer(TRIP_ARROW)?.setProperties(iconOpacity(switchCase(isFaded, literal(1f - t), keepPicked)))
        s.getLayer(PLAN_CASING)?.setProperties(lineOpacity(t))
        s.getLayer(PLAN_LINE)?.setProperties(lineOpacity(t))
        val skippedPin = dim * (1f - (1f - SKIPPED_OPACITY) * t)
        s.getLayer(STAY)?.setProperties(circleOpacity(switchCase(isSkipped, literal(skippedPin), literal(dim))), circleStrokeOpacity(dim))
        // ✕ 카드: 작게 시작해 살짝 넘치며 제 크기로 (OvershootInterpolator가 t를 1 너머까지 보낸다)
        val pop = switchCase(isSkipped, literal(t.coerceIn(0f, 1f)), literal(1f))
        val grow = switchCase(isSkipped, literal(SKIPPED_POP_FROM + (1f - SKIPPED_POP_FROM) * t), literal(1f))
        for (id in arrayOf(STAY_CARD, STAY_CARD_SEL, STAY_MINI)) s.getLayer(id)?.setProperties(iconOpacity(pop), iconSize(grow))
    }

    /** 뺀 곳이 있는 지도가 새로 들어오면 그날 길을 잠깐 보인 뒤 한 문장 코스로 바뀌는 장면을 한 번 튼다. */
    private fun startPlanScene(d: DayMapData?) {
        planAnim?.cancel()
        if (d == null || !d.geojson.contains(PLAN_MARK)) {
            planT = 1f
            return
        }
        planT = 0f
        planAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = PLAN_DELAY_MS
            duration = PLAN_MS
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener { a ->
                if (!mapView.isAttachedToWindow) {
                    a.cancel()
                    return@addUpdateListener
                }
                planT = a.animatedValue as Float
                style?.let { applyPlan(it) }
            }
            start()
        }
    }

    private fun applyData() {
        val src = source ?: return
        val d = data
        if (d !== appliedData) {
            src.setGeoJson(d?.geojson ?: EMPTY_GEOJSON)
            appliedData = d
            startPlanScene(d)
            style?.let {
                applyFilters(it)
                applyPlan(it)
            }
        }
        if (d != null && d.day != fittedDay) {
            fittedDay = d.day
            fitCamera(d)
        }
    }

    private var fitInsets = IntArray(4)

    /** 지도 위 판이 가리는 폭이 바뀌면 그날 길을 다시 맞춘다. */
    fun setFitInsets(insets: IntArray?) {
        val next = insets ?: IntArray(4)
        if (next.contentEquals(fitInsets)) return
        fitInsets = next
        data?.let(::fitCamera)
    }

    /**
     * 화면 맞춤 여백: 위는 핀 위 카드 높이만큼, 옆은 카드 반 너비만큼 더 띄운다 (지도 폭의 일부까지만).
     * 순서: 왼쪽, 위, 오른쪽, 아래 (px).
     */
    private fun fitPadding(): IntArray {
        val base = FIT_PADDING_DP * density
        val side = min(CALLOUT_HALF_WIDTH_DP * density, mapView.width * MAX_SIDE_PAD_FRACTION).coerceAtLeast(base)
        val top = min((FIT_PADDING_DP + CALLOUT_TOP_EXTRA_DP) * density, mapView.height * MAX_TOP_PAD_FRACTION).coerceAtLeast(base)
        // 아래: 핀 아래 매다는 내 사진 카드(있을 때)가 잘리지 않게
        val bottom = base
        val e = fitInsets
        return intArrayOf(side.toInt() + e[0], top.toInt() + e[1], side.toInt() + e[2], bottom.toInt() + e[3])
    }

    /** 그날 경로·핀 전체가 보이게 맞춘다. 범위가 한 점이면 너무 확대되지 않게 [MAX_FIT_ZOOM]에서 멈춘다. */
    private fun fitCamera(d: DayMapData) {
        val b = d.bounds ?: return
        val m = map ?: return
        mapView.doOnLayout {
            val bounds = LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon)
            val cam = m.getCameraForLatLngBounds(bounds, fitPadding()) ?: return@doOnLayout
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
        val target = if (b.minLat == b.maxLat && b.minLon == b.maxLon) {
            CameraUpdateFactory.newLatLngZoom(LatLng(b.minLat, b.minLon), MAX_FIT_ZOOM.coerceAtMost(m.cameraPosition.zoom.coerceAtLeast(REVEAL_MIN_ZOOM)))
        } else {
            val p = fitPadding()
            CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon), p[0], p[1], p[2], p[3])
        }
        m.easeCamera(target, REVEAL_MS)
    }

    /**
     * 지도를 누르면 그 자리의 카드·사진 말풍선 → 손가락 크기(76dp) 안의 핀 → 주행 글·깃발 → 경로 순서로 찾아
     * 그 항목을 고른다. 빈 곳이면 선택 해제.
     */
    private fun onMapClick(m: MapLibreMap, latLng: LatLng): Boolean {
        if (style == null) return false
        val p = m.projection.toScreenLocation(latLng)
        val r = TOUCH_RADIUS_DP * density
        val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
        // 사진 말풍선을 누르면 크게 보기, 카메라 핀을 누르면 그 자리에 말풍선
        if (bubble != null && m.queryRenderedFeatures(p, PHOTO_BUBBLE).isNotEmpty()) {
            bubble?.let { onOpenPhotos(it.idx) }
            return true
        }
        m.queryRenderedFeatures(box, PHOTO_BADGE, PHOTO).firstOrNull()?.let { f ->
            selectPhotos(f)
            return true
        }
        // 다른 곳을 누르면 말풍선을 닫는다
        if (bubble != null) {
            setPhotoBubble(null)
            onSelectPhotos(emptyList(), 0.0, 0.0)
        }
        val key = m.queryRenderedFeatures(p, STAY_CARD_SEL, STAY_CARD, STAY_MINI).firstNotNullOfOrNull { it.keyOrNull() }
            ?: m.queryRenderedFeatures(box, STAY_SEL, STAY).firstNotNullOfOrNull { it.keyOrNull() }
            ?: m.queryRenderedFeatures(box, TRIP_LABEL, END_FLAG, END_DOT, TRIP_SEL, TRIP_LINE).firstNotNullOfOrNull { it.keyOrNull() }
        onSelectKey(key)
        return true
    }

    /** 누른 카메라 핀의 사진 번호들 (묶음이면 묶인 사진 전부)과 핀 자리를 알린다. */
    private fun selectPhotos(f: org.maplibre.geojson.Feature) {
        val src = photoSource ?: return
        val at = f.geometry() as? org.maplibre.geojson.Point ?: return
        val idx = if (f.hasProperty("point_count")) {
            src.getClusterLeaves(f, PHOTO_LEAVES_MAX, 0).features().orEmpty().mapNotNull { it.idxOrNull() }
        } else {
            listOfNotNull(f.idxOrNull())
        }
        if (idx.isNotEmpty()) onSelectPhotos(idx.sorted(), at.latitude(), at.longitude())
    }

    private fun org.maplibre.geojson.Feature.idxOrNull(): Int? =
        if (hasNonNullValueForProperty("idx")) getNumberProperty("idx").toInt() else null

    private fun org.maplibre.geojson.Feature.keyOrNull(): String? =
        if (hasNonNullValueForProperty("key")) getStringProperty("key") else null

    private companion object {
        const val TAG = "EodiMap"
        const val SOURCE_ID = "eodi-day"
        const val TRIP_SHADOW = "eodi-trip-shadow"
        const val TRIP_CASING = "eodi-trip-casing"
        const val TRIP_LINE = "eodi-trip"
        const val TRIP_SEL_CASING = "eodi-trip-sel-casing"
        const val TRIP_SEL = "eodi-trip-sel"
        const val TRIP_ARROW = "eodi-trip-arrow"
        const val TRIP_LABEL = "eodi-trip-label"
        const val END_DOT = "eodi-end-dot"
        const val END_FLAG = "eodi-end-flag"
        const val STAY_HALO = "eodi-stay-open-halo"
        const val STAY = "eodi-stay"
        const val STAY_SEL = "eodi-stay-sel"
        const val STAY_SEL_HALO = "eodi-stay-sel-halo"
        const val STAY_NUM = "eodi-stay-order"
        const val STAY_MINI = "eodi-stay-mini"
        const val STAY_CARD = "eodi-stay-card"
        const val STAY_CARD_SEL = "eodi-stay-card-sel"
        const val PLAN_CASING = "eodi-plan-casing"
        const val PLAN_LINE = "eodi-plan"
        const val PHOTO_SOURCE_ID = "eodi-my-photo"
        const val PHOTO = "eodi-my-photo-card"
        const val PHOTO_BADGE = "eodi-my-photo-count"
        const val PHOTO_PICK_SOURCE_ID = "eodi-my-photo-picked"
        const val PHOTO_SEL = "eodi-my-photo-picked-pin"
        const val PHOTO_BUBBLE = "eodi-my-photo-bubble"

        /** 사진 카드를 찍은 곳(핀) 아래로 내리는 거리(dp) · 배지 자리 · 묶는 반경(화면 px 기준) */
        const val PHOTO_BADGE_X_DP = 14f
        const val PHOTO_BADGE_LIFT_DP = 14f
        const val PHOTO_CLUSTER_RADIUS = 36
        const val PHOTO_CLUSTER_MAX_ZOOM = 17
        const val PHOTO_LEAVES_MAX = 200L

        /** 앱 assets 글꼴 이름 (scripts/map/README.md 스타일 약속). */
        const val FONT_MEDIUM = "NotoSans-Medium"

        const val EMPTY_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
        val NOTHING: Expression = literal(false)

        /** 경로 굵기(dp, 줌 13 기준). 멀리서는 가늘게, 가까이서는 굵게 ([widthByZoom]). */
        const val LINE_W = 7f
        const val CASING_W = 11.5f
        const val SHADOW_W = 15f
        const val SEL_LINE_W = 10f
        const val SEL_CASING_W = 15f
        const val SHADOW_COLOR = 0x47000000

        fun widthByZoom(w: Float): Expression =
            interpolate(linear(), zoom(), stop(8, w * 0.75f), stop(13, w), stop(17, w * 1.4f))

        const val ARROW_SPACING_DP = 90f
        const val ARROW_MIN_ZOOM = 7f
        const val LABEL_MIN_ZOOM = 6f
        const val NO_ORDER_SORT = 999

        /** 카드 꼬리 끝이 핀 위 가장자리에 닿게 올리는 거리(dp) / 깃발을 점 위로 올리는 거리 */
        const val CALLOUT_PIN_LIFT_DP = 15f
        const val END_LIFT_DP = 8f

        /** 카드(높이 80dp + 꼬리 12dp + 핀 위 15dp)가 화면 위에서 잘리지 않게 / 카드 반 너비(최대 약 130dp) */
        const val CALLOUT_TOP_EXTRA_DP = 110f
        const val CALLOUT_HALF_WIDTH_DP = 130f
        const val MAX_SIDE_PAD_FRACTION = 0.22f
        const val MAX_TOP_PAD_FRACTION = 0.35f
        const val MAX_BOTTOM_PAD_FRACTION = 0.3f

        const val DIMMED_OPACITY = 0.35f
        /** 한 문장 코스로 다시 짰을 때 그날 달린 길 · 뺀 곳 핀의 투명도 */
        const val FADED_OPACITY = 0.3f
        const val SKIPPED_OPACITY = 0.6f
        /** 뺀 곳 핀 색 (회색) */
        const val SKIPPED_COLOR = 0xFF9AA3AE.toInt()
        const val SKIPPED_SORT = -1f
        /** ✕ 카드가 튀어나오기 시작하는 크기 */
        const val SKIPPED_POP_FROM = 0.4f
        /** 한 문장 코스 장면: 그날 길을 이만큼 보인 뒤([PLAN_DELAY_MS]) 이 시간([PLAN_MS]) 동안 바뀐다 */
        const val PLAN_DELAY_MS = 700L
        const val PLAN_MS = 900L
        const val PLAN_MARK = "\"kind\":\"plan\""
        const val HALO_OPACITY = 0.22f
        const val SEL_HALO_OPACITY = 0.28f
        const val FIT_PADDING_DP = 56f
        const val TOUCH_RADIUS_DP = 38f
        const val MAX_FIT_ZOOM = 16.0
        const val MAX_ZOOM = 18.0
        const val REVEAL_MIN_ZOOM = 14.0
        const val REVEAL_MS = 400

        val isTrip: Expression = eq(get("kind"), literal("trip"))
        val isStay: Expression = eq(get("kind"), literal("stay"))
        val isLabel: Expression = eq(get("kind"), literal("trip_label"))
        val isEnd: Expression = eq(get("kind"), literal("endpoint"))
        val isOpen: Expression = eq(get("open"), literal(true))
        val hasOrder: Expression = has("order")
        val isPlan: Expression = eq(get("kind"), literal("plan"))
        val isFaded: Expression = has("faded")
        val isSkipped: Expression = has("skipped")
    }
}
