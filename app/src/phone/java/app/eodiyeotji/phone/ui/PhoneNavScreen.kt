package app.eodiyeotji.phone.ui

import androidx.compose.ui.draw.clip
import app.eodiyeotji.ui.common.eodiShadow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.eodiyeotji.core.rust.NavRoute
import app.eodiyeotji.core.rust.NavStep
import app.eodiyeotji.core.rust.NavTurn
import app.eodiyeotji.phone.NavUi
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.map.MapStyleSource
import app.eodiyeotji.ui.map.MapStyleSpec
import app.eodiyeotji.ui.map.rememberMapViewWithLifecycle
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// 차 판과 같은 색(ui/theme/Color.kt · 지도 MapInk): 안내 판은 짙은 회색, 갈 길은 파랑, 내 위치는 초록
private val NavInk = Color(0xFF111827)
private val NavDark = Color(0xFF111214)
private const val RouteBlue = "#0064FF"
private const val HereGreen = "#00A35A"

/**
 * 앱 안 길 안내 화면 (폰): 위 = 다음 안내(화살표 · 남은 거리 · 길 이름), 가운데 = 내 위치를 따라가는 기울어진 지도와 경로,
 * 아래 = 도착 예정 시각 · 남은 시간 · 거리 · 종료. 경로 계산 · 진행은 [app.eodiyeotji.phone.PhoneNavigator].
 */
@Composable
fun PhoneNavScreen(nav: NavUi, onEnd: () -> Unit, onNext: () -> Unit) {
    BackHandler(onBack = onEnd)
    Box(Modifier.fillMaxSize().background(NavDark)) {
        NavMap(nav, Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
            when {
                nav.error != null -> Banner(EodiIcons.Close, nav.error, nav.title, null)
                nav.loading || nav.route == null -> Banner(null, "경로를 찾는 중", nav.title, null, busy = true)
                nav.rerouting -> Banner(null, "경로를 다시 찾는 중", nav.title, null, busy = true)
                nav.arrivedAt != null -> Banner(EodiIcons.Check, "${nav.arrivedAt} 도착", nav.next?.let { "다음 · $it" } ?: "코스 끝", null)
                else -> {
                    val p = nav.progress
                    val step = p?.nextStep?.toInt()?.let { nav.route.steps.getOrNull(it) }
                    Banner(step?.icon(), step?.text ?: "경로를 따라 이동", step?.road?.takeIf { it.isNotBlank() } ?: nav.title, p?.toNextM, turn = step?.turn)
                }
            }
        }
        BottomBar(nav, onEnd, onNext, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Banner(icon: ImageVector?, text: String, sub: String, meters: Double?, busy: Boolean = false, turn: NavTurn? = null) {
    Row(
        Modifier.fillMaxWidth().eodiShadow(EodiTheme.elevation.level2, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(NavInk, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            when {
                busy -> app.eodiyeotji.ui.common.EodiLoader(size = 36.dp, dark = true)
                icon != null -> Icon(icon, null, Modifier.size(48.dp).rotate(turn.angle()), tint = Color.White)
            }
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            if (meters != null) Text(distance(meters), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Text(text, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, maxLines = 1)
        }
    }
}

@Composable
private fun BottomBar(nav: NavUi, onEnd: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    val p = nav.progress
    val c = EodiTheme.colors
    Row(
        modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (nav.arrivedAt != null && nav.next != null) {
                Text(nav.next, style = EodiTheme.type.title, color = c.textPrimary, maxLines = 1)
                Text("머무는 동안 기다릴게요", style = EodiTheme.type.meta, color = c.textSecondary)
            } else if (p != null && !p.arrived) {
                val eta = LocalTime.now().plusSeconds(p.remainingS.toLong()).format(DateTimeFormatter.ofPattern("a h:mm"))
                Text("$eta 도착", style = EodiTheme.type.title, color = c.textPrimary)
                Text("${minutes(p.remainingS)} · ${distance(p.remainingM)}", style = EodiTheme.type.meta, color = c.textSecondary)
            } else {
                Text(nav.title, style = EodiTheme.type.title, color = c.textPrimary, maxLines = 1)
            }
        }
        when {
            nav.arrivedAt != null && nav.next != null -> {
                SecondaryPill("끝내기", null, onEnd)
                PrimaryPill("다음 곳 안내", EodiIcons.Navi, onNext)
            }
            nav.arrivedAt != null || nav.error != null -> PrimaryPill("닫기", null, onEnd)
            else -> SecondaryPill("안내 종료", EodiIcons.Close, onEnd)
        }
    }
}

/** 기울어진 지도: 경로선 + 내 위치, 카메라는 진행 방향으로 따라간다. */
@Composable
private fun NavMap(nav: NavUi, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle(
        context,
        remember { MapLibreMapOptions.createFromAttributes(context) },
        android.graphics.Color.rgb(11, 18, 32),
        tiltAndRotate = true,
        label = "nav",
    )
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    LaunchedEffect(mapView) {
        // 이 지도 위에 그리는 선 · 표시 색이 밝은 지도 기준이라 낮 지도만 쓴다
        val spec = MapStyleSource.resolve(context.applicationContext, night = false)
        val json = when (spec) {
            is MapStyleSpec.Offline -> spec.json
            is MapStyleSpec.Online -> spec.json
            MapStyleSpec.Fallback -> MapStyleSource.fallbackJson(android.graphics.Color.rgb(230, 233, 238))
        }
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isAttributionEnabled = false
            m.setStyle(Style.Builder().fromJson(json)) { s ->
                s.addSource(GeoJsonSource(SRC_ROUTE))
                s.addSource(GeoJsonSource(SRC_NEXT))
                s.addSource(GeoJsonSource(SRC_STOPS))
                // 다음 코스 길: 아직 안내하지 않으므로 회색 점선 (경로선 아래)
                s.addLayer(LineLayer("nav-next", SRC_NEXT).withProperties(
                    PropertyFactory.lineColor("#9AA3B2"), PropertyFactory.lineWidth(7f), PropertyFactory.lineOpacity(0.85f),
                    PropertyFactory.lineDasharray(arrayOf(1.2f, 1.4f)), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                ))
                s.addSource(GeoJsonSource(SRC_ME))
                s.addLayer(LineLayer("nav-route-casing", SRC_ROUTE).withProperties(
                    PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(14f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ))
                s.addLayer(LineLayer("nav-route", SRC_ROUTE).withProperties(
                    PropertyFactory.lineColor(RouteBlue), PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ))
                // 들를 곳: 지금 목적지는 파랑, 다음 곳들은 회색
                s.addLayer(CircleLayer("nav-stops", SRC_STOPS).withProperties(
                    PropertyFactory.circleRadius(11f),
                    PropertyFactory.circleColor(org.maplibre.android.style.expressions.Expression.switchCase(org.maplibre.android.style.expressions.Expression.get("now"), org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.rgb(37, 99, 235)), org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.rgb(154, 163, 178)))),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(4f),
                ))
                s.addLayer(CircleLayer("nav-me-halo", SRC_ME).withProperties(
                    PropertyFactory.circleRadius(22f), PropertyFactory.circleColor(HereGreen), PropertyFactory.circleOpacity(0.18f),
                ))
                s.addLayer(CircleLayer("nav-me", SRC_ME).withProperties(
                    PropertyFactory.circleRadius(10f), PropertyFactory.circleColor(HereGreen),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(4f),
                ))
                style = s
            }
        }
    }
    // 경로가 바뀌면 다시 그리고 처음엔 전체를 한 번 보여 준다
    LaunchedEffect(style, nav.route) {
        val s = style ?: return@LaunchedEffect
        val r = nav.route ?: return@LaunchedEffect
        (s.getSource(SRC_ROUTE) as? GeoJsonSource)?.setGeoJson(Feature.fromGeometry(r.line()))
    }
    LaunchedEffect(style, nav.upcoming, nav.stops) {
        val s = style ?: return@LaunchedEffect
        (s.getSource(SRC_NEXT) as? GeoJsonSource)?.setGeoJson(
            org.maplibre.geojson.FeatureCollection.fromFeatures(if (nav.upcoming.size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(nav.upcoming.map { Point.fromLngLat(it.lon, it.lat) }))) else emptyList()),
        )
        (s.getSource(SRC_STOPS) as? GeoJsonSource)?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(
            nav.stops.mapIndexed { i, p -> Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also { it.addBooleanProperty("now", i == 0) } },
        ))
    }
    // 내 위치를 따라가는 카메라 (진행 방향 위로, 기울여서).
    // 위치는 1초에 한 번 오므로, 이전 위치에서 새 위치까지 매 프레임 같은 속도로 이어 움직여 끊김 없이 보이게 한다.
    var shown by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
    LaunchedEffect(style, nav.progress) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val p = nav.progress ?: return@LaunchedEffect
        val to = Triple(p.snapped.lat, p.snapped.lon, p.bearingDeg)
        val from = shown ?: to
        val src = s.getSource(SRC_ME) as? GeoJsonSource
        val turn = ((to.third - from.third + 540.0) % 360.0) - 180.0
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - start) / FOLLOW_NS.toDouble()).coerceAtMost(1.0)
            val lat = from.first + (to.first - from.first) * t
            val lon = from.second + (to.second - from.second) * t
            val brg = from.third + turn * t
            shown = Triple(lat, lon, brg)
            src?.setGeoJson(Point.fromLngLat(lon, lat))
            m.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(lat, lon)).zoom(16.6).tilt(55.0).bearing(brg).padding(0.0, 420.0, 0.0, 0.0).build(),
            ))
            if (t >= 1.0) break
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 위치 사이를 이어 움직이는 시간: 위치가 오는 간격(1초)과 같게 해서 멈춤 없이 이어지게 한다. */
private const val FOLLOW_NS = 1_000_000_000L

private fun NavRoute.line(): LineString = LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })

/** 화살표 하나(위쪽)를 돌려 쓴다. */
private fun NavStep.icon(): ImageVector = when (turn) {
    NavTurn.DESTINATION, NavTurn.WAYPOINT -> EodiIcons.Pin
    else -> TurnArrow
}

private fun NavTurn?.angle(): Float = when (this) {
    NavTurn.LEFT -> -90f
    NavTurn.RIGHT -> 90f
    NavTurn.SLIGHT_LEFT -> -45f
    NavTurn.SLIGHT_RIGHT -> 45f
    NavTurn.U_TURN -> 180f
    else -> 0f
}

private fun distance(m: Double): String = if (m >= 1000) String.format("%.1fkm", m / 1000) else "${((m / 10).toInt() * 10).coerceAtLeast(0)}m"

private fun minutes(s: Double): String {
    val min = (s / 60).toInt().coerceAtLeast(1)
    return if (min >= 60) "${min / 60}시간 ${min % 60}분" else "${min}분"
}

private const val SRC_ROUTE = "nav-route-src"
private const val SRC_ME = "nav-me-src"
private const val SRC_NEXT = "nav-next-src"
private const val SRC_STOPS = "nav-stops-src"

/** 위쪽 화살표 (돌려서 좌·우회전을 나타낸다). 24 격자, 굵은 선. */
private val TurnArrow: ImageVector by lazy {
    ImageVector.Builder("TurnArrow", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 3f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 21f); lineTo(12f, 4f)
            moveTo(5f, 11f); lineTo(12f, 4f); lineTo(19f, 11f)
        }
    }.build()
}
