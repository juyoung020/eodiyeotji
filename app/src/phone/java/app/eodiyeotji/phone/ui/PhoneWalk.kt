package app.eodiyeotji.phone.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.core.rust.ParkedCar
import app.eodiyeotji.core.rust.WalkGuide
import app.eodiyeotji.core.rust.walkGuide
import app.eodiyeotji.platform.LocationSource
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.map.MapStyleSource
import app.eodiyeotji.ui.map.MapStyleSpec
import app.eodiyeotji.ui.map.rememberMapViewWithLifecycle
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** 걸어서 찾아갈 곳: 차에서 내려 가게까지, 또는 다시 내 차로. */
@Immutable
data class WalkTarget(val name: String, val lat: Double, val lon: Double, val toCar: Boolean)

fun ParkedCar.carTarget() = WalkTarget(name.ifBlank { "내 차" }, lat, lon, toCar = true)
fun ParkedCar.destTarget(): WalkTarget? = if (destName.isBlank()) null else WalkTarget(destName, destLat, destLon, toCar = false)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WalkEntryPoint {
    fun location(): LocationSource
}

private val WalkGreen = Color(0xFF0F9D6B)
private val CarBlue = Color(0xFF0B5CFF)

/**
 * 기록 화면 맨 위 "내 차" 카드: 어디에 언제 세웠는지 + [가게까지 걷기] [내 차 찾기] + "찾았어요".
 * 차가 P에 들어가며 보낸 것(또는 폰이 직접 기록한 주행의 끝)이 있을 때만 보인다.
 */
@Composable
fun ParkedCarCard(car: ParkedCar, onWalk: (WalkTarget) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val time = remember(car.tMs) { Instant.ofEpochMilli(car.tMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("a h:mm")) }
    Surface(color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), shadowElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(CarBlue, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text("P", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("내 차 · $time 주차", style = type.label, color = c.textSecondary, maxLines = 1)
                    Text(car.name.ifBlank { "주차한 곳" }, style = type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SecondaryPill("찾았어요", null, onDismiss)
            }
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                car.destTarget()?.let { d ->
                    PrimaryPill("${d.name}까지 걷기", EodiIcons.Navi, { onWalk(d) }, Modifier.weight(1f))
                }
                (if (car.destTarget() == null) PrimaryPill("내 차 찾기", EodiIcons.Pin, { onWalk(car.carTarget()) }, Modifier.weight(1f))
                else SecondaryPill("내 차 찾기", EodiIcons.Pin, { onWalk(car.carTarget()) }, Modifier.weight(1f)))
            }
        }
    }
}

/**
 * 걸어서 찾아가기 (전체 화면): 위 = 큰 화살표(내가 보는 방향 기준) + "오른쪽 앞 · 120m" + 걸어서 N분,
 * 가운데 = 지도(나 · 목표 · 잇는 점선), 아래 = 목표 이름 + 닫기. 방향 · 거리 계산은 Rust `walk_guide`.
 * 보는 방향은 폰 방향 센서(없으면 걸어가는 방향)로 안다. 다 오면 "다 왔어요" + 내 차로 가는 길이면 "찾았어요".
 */
@Composable
fun PhoneWalkScreen(target: WalkTarget, onClose: () -> Unit, onFoundCar: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val location = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, WalkEntryPoint::class.java).location() }
    var me by remember { mutableStateOf<LocationFix?>(null) }
    var moveHeading by remember { mutableDoubleStateOf(-1.0) }
    val sensorHeading = rememberCompassHeading()
    LaunchedEffect(location) {
        var prev: LocationFix? = null
        location.updates().flowOn(Dispatchers.Default).catch { }.collect { f ->
            // 센서가 없을 때: 5m 넘게 걸었으면 걸어온 방향을 보는 방향으로
            val p = prev
            if (p == null) {
                prev = f
            } else {
                val g = walkGuide(p.lat, p.lon, -1.0, f.lat, f.lon)
                if (g.distanceM > 5.0) {
                    moveHeading = g.bearingDeg
                    prev = f
                }
            }
            me = f
        }
    }
    val heading = sensorHeading ?: moveHeading
    var guide by remember { mutableStateOf<WalkGuide?>(null) }
    LaunchedEffect(me, heading, target) {
        val f = me ?: return@LaunchedEffect
        guide = withContext(Dispatchers.Default) { walkGuide(f.lat, f.lon, heading, target.lat, target.lon) }
    }
    val accent = if (target.toCar) CarBlue else WalkGreen
    Box(Modifier.fillMaxSize().background(Color(0xFFE9ECF1))) {
        WalkMap(me, target, accent, Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
            WalkBanner(guide, target, accent)
        }
        val c = EodiTheme.colors
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(c.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (target.toCar) "내 차로" else "걸어서", style = EodiTheme.type.meta, color = c.textSecondary)
                Text(target.name, style = EodiTheme.type.title, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (guide?.arrived == true && target.toCar) {
                PrimaryPill("찾았어요", EodiIcons.Check, { onFoundCar(); onClose() })
            } else {
                SecondaryPill(if (guide?.arrived == true) "닫기" else "안내 끝", EodiIcons.Close, onClose)
            }
        }
    }
}

@Composable
private fun WalkBanner(guide: WalkGuide?, target: WalkTarget, accent: Color) {
    // 화살표는 가장 가까운 쪽으로 돌도록 이어 붙인 각도로 움직인다
    var unwrapped by remember { mutableStateOf(0f) }
    guide?.let { g ->
        val d = (((g.relativeDeg.toFloat() - unwrapped) % 360f) + 540f) % 360f - 180f
        if (kotlin.math.abs(d) > 0.5f) unwrapped += d
    }
    val angle by animateFloatAsState(unwrapped, label = "walk-arrow")
    Row(
        Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp)).background(accent, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(96.dp).background(Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            if (guide?.arrived == true) {
                Icon(EodiIcons.Check, null, Modifier.size(56.dp), tint = Color.White)
            } else {
                Icon(WalkArrow, null, Modifier.size(64.dp).rotate(angle), tint = Color.White)
            }
        }
        Spacer(Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            when {
                guide == null -> Text("내 위치를 찾는 중", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                guide.arrived -> Text(if (target.toCar) "내 차 바로 앞이에요" else "다 왔어요", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                else -> {
                    Text(guide.text, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text("걸어서 ${guide.minutes}분", color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp)
                }
            }
        }
    }
}

/** 북쪽이 위인 지도: 나(파란 점) · 목표(핀) · 둘을 잇는 점선. 둘 다 보이게 맞춘다. */
@Composable
private fun WalkMap(me: LocationFix?, target: WalkTarget, accent: Color, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle(
        context,
        remember { MapLibreMapOptions.createFromAttributes(context) },
        android.graphics.Color.rgb(233, 236, 241),
        tiltAndRotate = false,
        label = "walk",
    )
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val accentHex = remember(accent) { String.format("#%06X", 0xFFFFFF and android.graphics.Color.argb(255, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt())) }
    LaunchedEffect(mapView) {
        val spec = MapStyleSource.resolve(context.applicationContext)
        val json = when (spec) {
            is MapStyleSpec.Offline -> spec.json
            is MapStyleSpec.Online -> spec.json
            MapStyleSpec.Fallback -> MapStyleSource.fallbackJson(android.graphics.Color.rgb(230, 233, 238))
        }
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isAttributionEnabled = false
            // 목표 바로 앞에서 둘을 맞추면 지도 타일보다 더 확대돼 빈 화면이 된다 → 걷는 거리에 맞는 한도
            m.setMaxZoomPreference(18.0)
            m.setStyle(Style.Builder().fromJson(json)) { s ->
                s.addSource(GeoJsonSource(SRC_LINE))
                s.addSource(GeoJsonSource(SRC_TARGET))
                s.addSource(GeoJsonSource(SRC_ME))
                s.addLayer(LineLayer("walk-line", SRC_LINE).withProperties(
                    PropertyFactory.lineColor(accentHex), PropertyFactory.lineWidth(7f),
                    PropertyFactory.lineDasharray(arrayOf(0.1f, 1.8f)), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                ))
                s.addLayer(CircleLayer("walk-target-halo", SRC_TARGET).withProperties(
                    PropertyFactory.circleRadius(26f), PropertyFactory.circleColor(accentHex), PropertyFactory.circleOpacity(0.2f),
                ))
                // 목표: 흰 바탕 + 색 테두리 + 가운데 글자(내 차 = P, 가게 = ★) — 파란 내 위치 점과 한눈에 구별
                s.addLayer(CircleLayer("walk-target", SRC_TARGET).withProperties(
                    PropertyFactory.circleRadius(19f), PropertyFactory.circleColor("#FFFFFF"),
                    PropertyFactory.circleStrokeColor(accentHex), PropertyFactory.circleStrokeWidth(6f),
                ))
                s.addLayer(SymbolLayer("walk-target-mark", SRC_TARGET).withProperties(
                    PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("mark")),
                    PropertyFactory.textFont(arrayOf("NotoSans-Medium")),
                    PropertyFactory.textSize(20f), PropertyFactory.textColor(accentHex), PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true),
                ))
                s.addLayer(SymbolLayer("walk-target-label", SRC_TARGET).withProperties(
                    PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("label")),
                    PropertyFactory.textFont(arrayOf("NotoSans-Medium")),
                    PropertyFactory.textSize(15f), PropertyFactory.textColor("#0E1726"),
                    PropertyFactory.textHaloColor("#FFFFFF"), PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textOffset(arrayOf(0f, 2.2f)), PropertyFactory.textAllowOverlap(true),
                ))
                s.addLayer(CircleLayer("walk-me-halo", SRC_ME).withProperties(
                    PropertyFactory.circleRadius(22f), PropertyFactory.circleColor("#0B5CFF"), PropertyFactory.circleOpacity(0.18f),
                ))
                s.addLayer(CircleLayer("walk-me", SRC_ME).withProperties(
                    PropertyFactory.circleRadius(10f), PropertyFactory.circleColor("#0B5CFF"),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(4f),
                ))
                style = s
            }
        }
    }
    LaunchedEffect(style, target) {
        val s = style ?: return@LaunchedEffect
        (s.getSource(SRC_TARGET) as? GeoJsonSource)?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(target.lon, target.lat)).also {
                it.addStringProperty("label", target.name)
                it.addStringProperty("mark", if (target.toCar) "P" else "★")
            },
        )
        if (me == null) map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), 17.0))
    }
    var framed by remember { mutableStateOf(false) }
    LaunchedEffect(style, me, target) {
        val s = style ?: return@LaunchedEffect
        val f = me ?: return@LaunchedEffect
        (s.getSource(SRC_ME) as? GeoJsonSource)?.setGeoJson(Point.fromLngLat(f.lon, f.lat))
        (s.getSource(SRC_LINE) as? GeoJsonSource)?.setGeoJson(
            Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(f.lon, f.lat), Point.fromLngLat(target.lon, target.lat)))),
        )
        val bounds = LatLngBounds.Builder().include(LatLng(f.lat, f.lon)).include(LatLng(target.lat, target.lon)).build()
        val update = CameraUpdateFactory.newLatLngBounds(bounds, 200, 440, 200, 360)
        if (framed) map?.animateCamera(update, 600) else map?.moveCamera(update)
        framed = true
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 폰이 향한 방향 (북 0°, 시계 방향). 방향 센서가 없으면 null. */
@Composable
private fun rememberCompassHeading(): Double? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Double?>(null) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val deg = (Math.toDegrees(ori[0].toDouble()) + 360.0) % 360.0
                // 2° 넘게 바뀔 때만 (화면 다시 그리기를 줄인다)
                val prev = heading
                if (prev == null || kotlin.math.abs((((deg - prev) % 360) + 540) % 360 - 180) > 2.0) heading = deg
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    return heading
}

private const val SRC_LINE = "walk-line-src"
private const val SRC_TARGET = "walk-target-src"
private const val SRC_ME = "walk-me-src"

/** 위쪽을 가리키는 굵은 화살표 (돌려서 방향을 나타낸다). */
private val WalkArrow by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("WalkArrow", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12 2 L20 20 L12 16 L4 20 Z").toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()
}
