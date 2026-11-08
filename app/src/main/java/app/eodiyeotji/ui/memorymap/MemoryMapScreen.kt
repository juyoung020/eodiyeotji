package app.eodiyeotji.ui.memorymap

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.MemoryMap
import app.eodiyeotji.core.SkyPhase
import app.eodiyeotji.ui.map.MapLibreNative
import app.eodiyeotji.ui.map.awaitFullyRendered
import app.eodiyeotji.ui.map.rememberMapViewWithLifecycle
import app.eodiyeotji.ui.recap.Load
import app.eodiyeotji.ui.recap.RecapViewModel
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer

/** 밤하늘 바탕 (스타일 background와 같은 색 → 불러오는 동안 깜빡이지 않게). */
private val NightSky = Color(0xFF070D1C)
private val Gold = Color(0xFFFFD58A)
private val Moon = Color(0xFFF3EEDF)

/** 전체 그리기 시간을 달 수로 나눈다 (한 달 0.5~1.1초, 그동안 혜성 꼬리가 그 달의 길을 따라 그려진다). */
private const val DRAW_TOTAL_MS = 9_000L
private const val CAPTION_FADE_MS = 1_800

/** 달마다 넓히는 레이어 (filter m <= k). */
private val ShownLayers = listOf("route-haze", "route-glow", "route-core", "visit-halo", "visit-star", "visit-pillar", "city-lights")
/** 지금 그리는 달의 혜성 (line-gradient, 앱이 머리 위치를 프레임마다 옮긴다). */
private const val COMET_CORE = "route-fresh"
private const val COMET_GLOW = "route-comet-glow"
/** 혜성 꼬리 길이 (경로 길이 비율) · 그라데이션 칸 수 · 갱신 간격 (30fps). */
private const val COMET_TAIL = 0.35f
private const val COMET_STOPS = 24
private const val COMET_FRAME_MS = 33L
/** 별 숨쉬기 · 바닷가 반짝임 갱신 간격 (12fps면 충분히 부드럽고 GPU를 덜 쓴다). */
private const val PULSE_FRAME_MS = 83L
/** 시작: 한국 전체를 위에서 내려다보는 높은 밤하늘 → 비스듬히 내려앉는 시간. */
private const val KOREA_LAT = 36.2
private const val KOREA_LON = 127.8
private const val KOREA_ZOOM = 5.6
private const val DESCEND_MS = 2_600
private val KoreaCamera = CameraPosition.Builder().target(LatLng(KOREA_LAT, KOREA_LON)).zoom(KOREA_ZOOM).tilt(0.0).bearing(0.0).build()

/** 3D 카메라: 그리는 동안 기울기 · 도는 각도, 마지막에 달린 길 전체로 물러나는 시간. */
private const val REVEAL_PITCH = 66.0
private const val MAX_PITCH = 70.0
private const val REVEAL_BEARING_FROM = -35.0
private const val REVEAL_BEARING_TO = 35.0
private const val FLY_OUT_MS = 3_800
private const val END_PITCH = 55.0
/** 끝 장면: 달린 길 범위 둘레 여백 (화면의 15%). 빈 바다 대신 길이 화면을 채운다. */
private const val END_PAD_FRACTION = 0.15f
/** 다 그린 뒤 천천히 한 방향으로 돈다 (8초에 15°, 한 바퀴 3분 남짓 — 멀미 없이 살아 있는 느낌만). */
private const val ORBIT_DEG = 15.0
private const val ORBIT_MS = 8_000

/**
 * 기억 지도 (요약 화면 · 날짜 목록 위를 덮는 전체 화면). 1년 동안 달린 모든 길을 밤하늘 3D 지도(산 음영 · 건물 · 빛기둥) 위에
 * 금빛으로, 오래된 달부터 한 달씩 차례로 그린다. 그동안 카메라는 비스듬히 내려다보며 천천히 돌고, 다 그리면 달린 길 전체로
 * 물러난 뒤 조용히 한 줄을 띄운다. 숫자 · 경로는 Rust `memory_map`.
 * 정차 중에만 열린다 (주행을 시작하면 [RecapViewModel]이 닫는다).
 */
@Composable
fun MemoryMapOverlay(viewModel: RecapViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.memoryMapOpen) return
    BackHandler(onBack = viewModel::closeMemoryMap)
    Box(Modifier.fillMaxSize().background(NightSky).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        when (val m = state.memoryMap) {
            Load.Loading -> Hint(stringResource(R.string.memory_map_loading))
            Load.Failed -> Hint(stringResource(R.string.memory_map_failed))
            is Load.Ready -> if (m.value.bounds == null) Hint(stringResource(R.string.memory_map_empty)) else MemoryMapStage(m.value)
        }
        Row(Modifier.safeDrawingPadding().padding(EodiTheme.space.page), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.recap_back), onClick = viewModel::closeMemoryMap),
                contentAlignment = Alignment.Center,
            ) { Icon(EodiIcons.Back, contentDescription = null, tint = Moon, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.width(20.dp))
            Text(stringResource(R.string.memory_map_title), style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, color = Moon)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = EodiTheme.type.body, color = Moon.copy(alpha = 0.7f))
    }
}

@Composable
private fun MemoryMapStage(data: MemoryMap) {
    val inPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val options by produceState<MapLibreMapOptions?>(initialValue = null) {
        if (!inPreview) value = MapLibreNative.prepare(context.applicationContext)
    }
    val styleJson by produceState<String?>(initialValue = null, data) { value = buildStyle(context, data) }
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    // 그린 달 (-1 = 아직, months.size = 다 그림). playKey가 바뀌면 처음부터 다시.
    var step by remember(data) { mutableIntStateOf(-1) }
    var playKey by remember { mutableIntStateOf(0) }
    val total = data.months.size
    val done = step >= total
    // 달린 길 전체로 물러나기까지 끝나면 한 줄을 띄운다
    var captionShown by remember(data) { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val opts = options
    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        if (opts != null) {
            // 처음부터 한국 전체를 본다 → 스타일이 뜨는 동안 첫 장면 타일을 같이 받는다 (서울 타일을 헛받지 않는다)
            val mapView = rememberMapViewWithLifecycle(context, opts, NightSky.toArgb(), initialCamera = KoreaCamera, label = "memory")
            LaunchedEffect(mapView) {
                mapViewRef = mapView
                mapView.getMapAsync { m ->
                    // 60°보다 더 눕혀 지평선이 보이게 (그 위는 CinematicSky가 하늘로 덮는다)
                    runCatching { m.setMaxPitchPreference(MAX_PITCH) }
                    map = m
                }
            }
            val m = map
            val json = styleJson
            LaunchedEffect(m, json) {
                if (m != null && json != null) m.setStyle(Style.Builder().fromJson(json)) { s -> style = s }
            }
            val description = stringResource(R.string.memory_map_description)
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().semantics { contentDescription = description })
        }
        // 지평선 위 밤하늘 · 반짝이는 별 · 비네트 (지도 위에 얹는 Canvas 한 장)
        CinematicSky(SkyPhase.NIGHT, stars = true, skyFraction = 0.3f, haze = false)

        // 그리기: 카메라는 기울어진 채 조금 가까이서 시작해 천천히 돌며 전체 범위로 물러나고, 달은 하나씩 켠다.
        // 다 그리면 달린 길 전체로 물러난 뒤 한 줄을 띄우고, 숨 쉬듯 좌우로 천천히 돈다.
        val s = style
        val m = map
        LaunchedEffect(s, playKey) {
            if (s == null || m == null) return@LaunchedEffect
            captionShown = false
            val stepMs = monthMs(total)
            val revealMs = (stepMs * total + 1_000).toInt()
            data.bounds?.let { b ->
                val bb = LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon)
                val pad = intArrayOf(120, 200, 120, 260)
                // 기울기째로 맞추면 먼 쪽까지 계산해 길이 아주 작아진다 → 위에서 맞춘 줌에 기울기만 더한다
                val from = m.getCameraForLatLngBounds(bb, pad, REVEAL_BEARING_FROM, 0.0)?.let { CameraPosition.Builder(it).tilt(REVEAL_PITCH).build() }
                val to = m.getCameraForLatLngBounds(bb, pad, REVEAL_BEARING_TO, 0.0)?.let { CameraPosition.Builder(it).tilt(REVEAL_PITCH).build() }
                if (from != null && to != null) {
                    m.cancelTransitions()
                    // 별이 뜬 높은 하늘에서 한국 전체를 내려다보다가, 기울어지며 달린 곳으로 내려앉는다
                    step = -1
                    m.moveCamera(CameraUpdateFactory.newCameraPosition(KoreaCamera))
                    // 첫 장면 타일이 다 그려진 뒤 내려앉는다 (빈 산·바다 위에서 출발하지 않게). 네트워크가 느려도 2초면 출발.
                    val t0 = System.nanoTime()
                    mapViewRef?.awaitFullyRendered(2_000)
                    delay((700 - (System.nanoTime() - t0) / 1_000_000).coerceAtLeast(0))
                    val near = CameraPosition.Builder(from).zoom(from.zoom + 0.8).build()
                    m.animateCamera(CameraUpdateFactory.newCameraPosition(near), DESCEND_MS)
                    delay(DESCEND_MS.toLong())
                    m.easeCamera(CameraUpdateFactory.newCameraPosition(to), revealMs, true)
                }
            }
            step = -1
            delay(300)
            for (k in 0 until total) {
                step = k
                delay(stepMs)
            }
            step = total
            delay(400)
            // 끝: 달린 길 범위에 맞춰(위에서 맞춘 줌 + 기울기) 물러나고, 한 줄을 띄운 뒤 숨 쉬듯 좌우로 천천히 돈다
            val end = data.bounds?.let { b ->
                val px = (viewSize.width * END_PAD_FRACTION).toInt()
                val py = (viewSize.height * END_PAD_FRACTION).toInt()
                m.getCameraForLatLngBounds(LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon), intArrayOf(px, py, px, py + py / 2), 0.0, 0.0) // 아래는 한 줄 자리만큼 더
            }?.let { CameraPosition.Builder(it).tilt(END_PITCH).bearing(0.0).build() } ?: return@LaunchedEffect
            m.animateCamera(CameraUpdateFactory.newCameraPosition(end), FLY_OUT_MS)
            delay(FLY_OUT_MS - 600L)
            captionShown = true
            delay(600L)
            try {
                // 별자리처럼 켜진 곳들을 두고 천천히 한 방향으로 돈다 (등속이라 이음매가 없다)
                var bearing = end.bearing
                while (true) {
                    bearing += ORBIT_DEG
                    m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(end).bearing(bearing).build()), ORBIT_MS, false)
                    delay(ORBIT_MS.toLong())
                }
            } finally {
                m.cancelTransitions()
            }
        }
        LaunchedEffect(s, step) {
            if (s == null) return@LaunchedEffect
            // 지나간 달은 켜 두고, 지금 달은 혜성이 그린다 (다 그리면 전부 켠다)
            val shown = Expression.lt(Expression.get("m"), Expression.literal(if (step < 0) -1 else step.coerceAtMost(total)))
            ShownLayers.forEach { id ->
                when (val l = s.getLayer(id)) {
                    is LineLayer -> l.setFilter(shown)
                    is CircleLayer -> l.setFilter(shown)
                    is FillExtrusionLayer -> l.setFilter(shown)
                    else -> Unit
                }
            }
            val core = s.getLayer(COMET_CORE) as? LineLayer
            val glow = s.getLayer(COMET_GLOW) as? LineLayer
            val fresh = Expression.eq(Expression.get("m"), Expression.literal(if (done || step < 0) -1 else step))
            core?.setFilter(fresh)
            glow?.setFilter(fresh)
            if (step !in 0 until total) return@LaunchedEffect
            // 혜성: 머리가 길의 처음(0)에서 끝(1)까지 달리고, 꼬리 뒤로는 금빛 길이 남는다
            val stepMs = monthMs(total)
            val start = System.nanoTime()
            while (true) {
                val t = ((System.nanoTime() - start) / 1_000_000f / stepMs).coerceAtMost(1f)
                val head = t * 1.05f
                core?.setProperties(PropertyFactory.lineGradient(cometGradient(head, core = true)))
                glow?.setProperties(PropertyFactory.lineGradient(cometGradient(head, core = false)))
                if (t >= 1f) break
                delay(COMET_FRAME_MS)
            }
        }
        // 들른 곳 별빛 숨쉬기 · 바닷가 반짝임: 화면이 열려 있는 동안 아주 천천히 (12fps)
        LaunchedEffect(s) {
            if (s == null) return@LaunchedEffect
            val halo = s.getLayer("visit-halo") as? CircleLayer
            val coast = s.getLayer("coast-glow") as? LineLayer
            val start = System.nanoTime()
            while (true) {
                val sec = (System.nanoTime() - start) / 1e9
                val breath = (0.5 + 0.5 * sin(sec * 2.0 * PI / 2.8)).toFloat()
                // 숨쉬기는 색의 투명도(상수)로 준다. 곳마다 다른 밝기(w)는 스타일의 circle-opacity에 그대로 두고 둘이 곱해진다.
                // 곳마다 다른 식(data-driven)을 프레임마다 바꾸면 MapLibre가 그 소스 타일을 통째로 다시 만든다 (초당 12번).
                halo?.setProperties(PropertyFactory.circleColor(Expression.rgba(255f, 181f, 71f, 0.45f + 0.55f * breath)))
                coast?.setProperties(PropertyFactory.lineOpacity(0.35f + 0.3f * (0.5f + 0.5f * sin(sec * 2.0 * PI / 4.5).toFloat())))
                delay(PULSE_FRAME_MS)
            }
        }

        // 지금 그리는 달
        AnimatedContent(
            targetState = if (step in 0 until total) data.months[step] else null,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "month",
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(EodiTheme.space.page).padding(top = 18.dp),
        ) { label ->
            if (label != null) Text(label, style = EodiTheme.type.bodyStrong, color = Gold)
        }

        // 마지막 한 줄
        AnimatedVisibility(
            visible = done && captionShown,
            enter = fadeIn(tween(CAPTION_FADE_MS)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 48.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val caption = if (data.spanYears > 0) {
                    stringResource(R.string.memory_map_caption_years, data.spanYears, data.distanceKm)
                } else {
                    stringResource(R.string.memory_map_caption_months, data.spanMonths, data.distanceKm)
                }
                Text(caption, style = EodiTheme.type.hero, color = Moon, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.memory_map_sub, data.driveDays, data.driveCount),
                    style = EodiTheme.type.body,
                    color = Gold.copy(alpha = 0.8f),
                )
                Text(
                    stringResource(R.string.memory_map_replay),
                    style = EodiTheme.type.label,
                    fontWeight = FontWeight.Bold,
                    color = Moon,
                    modifier = Modifier
                        .heightIn(min = 76.dp)
                        .clip(CircleShape)
                        .border(2.dp, Gold.copy(alpha = 0.6f), CircleShape)
                        .clickable(role = Role.Button) { playKey++ }
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                )
            }
        }
    }
}

/** 스타일 asset에 Rust GeoJSON을 그대로 끼운다 (파일 읽기 · 문자열 바꾸기는 IO 스레드). */
private suspend fun buildStyle(context: Context, data: MemoryMap): String = withContext(Dispatchers.IO) {
    context.assets.open("map/memory-night.json").bufferedReader().use { it.readText() }
        .replace("\"__ROUTES__\"", data.routesGeojson)
        .replace("\"__VISITS__\"", data.visitsGeojson)
        .replace("\"__PILLARS__\"", data.pillarsGeojson)
}

private fun monthMs(total: Int): Long = (DRAW_TOTAL_MS / total.coerceAtLeast(1)).coerceIn(500L, 1_100L)

/**
 * 혜성 그라데이션 (line-progress 0~1): 머리[head] 앞은 투명, 머리는 흰빛, 꼬리로 갈수록 금빛, 그 뒤로는 남는 금빛 길.
 * [core]가 아니면 넓게 번지는 빛 (꼬리 뒤는 옅게).
 */
private fun cometGradient(head: Float, core: Boolean): Expression {
    val trail = if (core) 0.85f else 0.18f
    val stops = Array(COMET_STOPS + 1) { i ->
        val x = i.toFloat() / COMET_STOPS
        val behind = head - x
        val c = when {
            behind < 0f -> Expression.rgba(255f, 214f, 140f, 0f)
            behind < COMET_TAIL -> {
                val k = 1f - behind / COMET_TAIL // 머리 1 → 꼬리 끝 0
                Expression.rgba(255f, 196f + 59f * k, 110f + 140f * k * k, trail + (1f - trail) * k * k)
            }
            core -> Expression.rgba(255f, 222f, 160f, trail)
            else -> Expression.rgba(255f, 180f, 70f, trail)
        }
        Expression.stop(x, c)
    }
    return Expression.interpolate(Expression.linear(), Expression.lineProgress(), *stops)
}
