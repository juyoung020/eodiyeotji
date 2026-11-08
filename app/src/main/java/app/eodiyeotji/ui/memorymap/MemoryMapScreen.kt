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
import app.eodiyeotji.ui.map.MapLibreNative
import app.eodiyeotji.ui.map.rememberMapViewWithLifecycle
import app.eodiyeotji.ui.recap.Load
import app.eodiyeotji.ui.recap.RecapViewModel
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer

/** 밤하늘 바탕 (스타일 background와 같은 색 → 불러오는 동안 깜빡이지 않게). */
private val NightSky = Color(0xFF070D1C)
private val Gold = Color(0xFFFFD58A)
private val Moon = Color(0xFFF3EEDF)

/** 전체 그리기 시간을 달 수로 나눈다 (한 달 0.3~0.7초). */
private const val DRAW_TOTAL_MS = 6_500L
private const val CAPTION_FADE_MS = 1_800

/** 달마다 넓히는 레이어 (filter m <= k). */
private val ShownLayers = listOf("route-haze", "route-glow", "route-core", "visit-halo", "visit-star")
private const val FRESH_LAYER = "route-fresh"

/**
 * 기억 지도 (요약 화면 · 날짜 목록 위를 덮는 전체 화면). 1년 동안 달린 모든 길을 밤하늘 지도 위에 금빛으로,
 * 오래된 달부터 한 달씩 차례로 그리고, 끝나면 조용히 한 줄을 띄운다. 숫자 · 경로는 Rust `memory_map`.
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

    val opts = options
    Box(Modifier.fillMaxSize()) {
        if (opts != null) {
            val mapView = rememberMapViewWithLifecycle(context, opts, NightSky.toArgb())
            LaunchedEffect(mapView) { mapView.getMapAsync { m -> map = m } }
            val m = map
            val json = styleJson
            LaunchedEffect(m, json) {
                if (m != null && json != null) m.setStyle(Style.Builder().fromJson(json)) { s -> style = s }
            }
            val description = stringResource(R.string.memory_map_description)
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().semantics { contentDescription = description })
        }

        // 그리기: 카메라는 조금 가까이서 시작해 전체 범위로 천천히 물러나고, 달은 하나씩 켠다
        val s = style
        val m = map
        LaunchedEffect(s, playKey) {
            if (s == null || m == null) return@LaunchedEffect
            val stepMs = (DRAW_TOTAL_MS / total.coerceAtLeast(1)).coerceIn(300L, 700L)
            data.bounds?.let { b ->
                val target = m.getCameraForLatLngBounds(
                    LatLngBounds.from(b.maxLat, b.maxLon, b.minLat, b.minLon),
                    intArrayOf(120, 200, 120, 260),
                )
                if (target != null) {
                    m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(target).zoom(target.zoom + 0.8).build()))
                    m.animateCamera(CameraUpdateFactory.newCameraPosition(target), (stepMs * total + 1_000).toInt())
                }
            }
            step = -1
            delay(600)
            for (k in 0 until total) {
                step = k
                delay(stepMs)
            }
            step = total
        }
        LaunchedEffect(s, step) {
            if (s == null) return@LaunchedEffect
            val shown = Expression.lte(Expression.get("m"), Expression.literal(step.coerceAtMost(total)))
            ShownLayers.forEach { id ->
                when (val l = s.getLayer(id)) {
                    is LineLayer -> l.setFilter(shown)
                    is CircleLayer -> l.setFilter(shown)
                    else -> Unit
                }
            }
            (s.getLayer(FRESH_LAYER) as? LineLayer)?.setFilter(Expression.eq(Expression.get("m"), Expression.literal(if (done) -1 else step)))
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
            visible = done,
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
}
