package app.eodiyeotji.ui.replay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.DayReplay
import app.eodiyeotji.core.MapPhotoSpot
import app.eodiyeotji.core.ReplayScene
import app.eodiyeotji.ui.agent.AgentIcons
import app.eodiyeotji.ui.gallery.MyPhotoImage
import app.eodiyeotji.ui.map.ReplayFlightMap
import app.eodiyeotji.ui.map.ReplayMap
import app.eodiyeotji.ui.memorymap.CinematicSky
import app.eodiyeotji.ui.place.PhotoLoadingPlaceholder
import app.eodiyeotji.ui.place.PlaceThumbnail
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.MemoryStamp
import kotlin.math.cos
import kotlin.math.hypot

/** 그날로 돌아가기 열기 (날 "YYYY-MM-DD"). 앱 전체에서 하나 ([ReplayScope]가 넣는다). 운전 중에는 열리지 않는다. */
val LocalOpenReplay = staticCompositionLocalOf<(String) -> Unit> { {} }

/** 앱 전체를 감싼다 (MainActivity). 열려 있으면 앱 화면 위를 전부 덮어 그린다. */
@Composable
fun ReplayScope(viewModel: ReplayViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val open = remember(viewModel) { { day: String -> viewModel.open(day) } }
    CompositionLocalProvider(LocalOpenReplay provides open) {
        Box(Modifier.fillMaxSize()) {
            content()
            val state by viewModel.state.collectAsStateWithLifecycle()
            if (state.day != null && !state.driving) {
                BackHandler(onBack = viewModel::close)
                DayReplayScreen(state, onClose = viewModel::close, onNavigate = viewModel::navigate)
            }
        }
    }
}

private val Night = Color(0xFF0D1015)

/** 시작 화면 · 사진 장면 시간 · 장면 사이 3D 비행 시간 · 사진이 천천히 다가가는 시간 (ms). */
private const val INTRO_MS = 3_200L
private const val SCENE_MS = 3_600L
private const val FLIGHT_MS = 2_600L
/** 길 위 사진 한 장마다 비행을 늘리는 시간, 사진이 떠 있는 비행 구간 (찍은 자리 앞 · 뒤) */
private const val PHOTO_MS = 2_200L
private const val MAX_LEG_PHOTOS = 3
private const val PHOTO_BEFORE = 0.14f
private const val PHOTO_AFTER = 0.2f
private const val ROAD_PHOTO_NEAR_M = 1_500.0
private const val ROAD_PHOTO_PX = 1280
private const val KEN_BURNS_MS = 4_800
private const val FADE_MS = 900
private const val SWIPE_PX = 120f

private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

@Composable
private fun DayReplayScreen(state: ReplayUiState, onClose: () -> Unit, onNavigate: () -> Unit) {
    val r = state.replay
    Box(
        Modifier
            .fillMaxSize()
            .background(Night)
            // 뒤 화면으로 손짓이 새지 않게
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        when {
            r != null -> Player(r, state.roadPhotos, onClose, onNavigate)
            state.failed -> Center(stringResource(R.string.replay_failed))
            else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = EodiTheme.colors.memory)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.replay_loading), style = EodiTheme.type.body, color = Color.White.copy(alpha = 0.8f))
            }
        }
        if (r == null) RoundButton(AgentIcons.Close, stringResource(R.string.replay_close), onClose, Modifier.align(Alignment.TopStart).padding(28.dp))
        state.note?.let {
            Text(
                stringResource(it),
                style = EodiTheme.type.label,
                color = Night,
                modifier = Modifier.align(Alignment.BottomCenter).padding(40.dp).background(Color.White, CircleShape).padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = EodiTheme.type.title, color = Color.White)
    }
}

/**
 * 재생기: 0 = 시작 화면, 1..n = 들른 곳 장면, n+1 = 끝 화면. 들른 곳 장면은 앞 곳에서 그곳까지 노을빛 3D 지도 위를 길 따라
 * 날아간 뒤(전체 화면, [FLIGHT_MS]) 사진으로 넘어간다. 누르면 멈춤/다시 재생, 옆으로 밀거나 화살표로 이전 · 다음.
 */
@Composable
private fun Player(r: DayReplay, roadPhotos: List<MapPhotoSpot>, onClose: () -> Unit, onNavigate: () -> Unit) {
    val n = r.scenes.size
    val last = n + 1
    var step by remember(r) { mutableIntStateOf(0) }
    var paused by remember(r) { mutableStateOf(false) }
    val go = { to: Int -> step = to.coerceIn(0, last) }
    val currentStep by rememberUpdatedState(step)

    val scene = r.scenes.getOrNull(step - 1)
    // 이 장면으로 오는 비행이 끝났나 (끝나면 사진으로 넘어간다)
    var arrived by remember(r, step) { mutableStateOf(false) }
    // 이 구간 길 위에서 찍은 내 사진: 비행이 그 자리를 지날 때 잠깐 띄운다 (사진이 있으면 그만큼 천천히 난다)
    val legPhotos = remember(r, roadPhotos) { roadPhotosByLeg(r, roadPhotos) }[step - 1].orEmpty()
    val flightT = remember(r, step) { mutableFloatStateOf(0f) }
    val passing by remember(legPhotos, flightT) {
        derivedStateOf { legPhotos.lastOrNull { flightT.floatValue >= it.at - PHOTO_BEFORE && flightT.floatValue <= it.at + PHOTO_AFTER } }
    }
    LaunchedEffect(r, step, paused, arrived) {
        if (!paused && step < last && (step == 0 || arrived)) {
            kotlinx.coroutines.delay(if (step == 0) INTRO_MS else SCENE_MS)
            step += 1
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { go(currentStep - 1); true }
                    Key.DirectionRight -> { go(currentStep + 1); true }
                    Key.Spacebar, Key.DirectionCenter, Key.Enter -> { paused = !paused; true }
                    else -> false
                }
            },
    ) {
        val wide = maxWidth > maxHeight
        if (step == last) {
            EndScreen(r, wide, onNavigate = onNavigate, onAgain = { paused = false; go(0) }, onClose = onClose)
        } else {
            // 바탕: 전체 화면 3D 비행 지도 (MapView 하나를 장면 내내 쓴다, 그날 하늘 때의 색) + 장면 시각의 하늘 · 지평선 빛 · 비네트
            ReplayFlightMap(
                bounds = r.bounds,
                scene = scene,
                flightMs = FLIGHT_MS + legPhotos.size.coerceAtMost(MAX_LEG_PHOTOS) * PHOTO_MS,
                paused = paused,
                onArrive = { arrived = true },
                modifier = Modifier.fillMaxSize(),
                sky = r.sky,
                onProgress = { flightT.floatValue = it },
            )
            Crossfade(targetState = scene?.sky ?: r.sky, animationSpec = tween(FLIGHT_MS.toInt() / 2), label = "replay-sky") { CinematicSky(it) }
            // 위: 시작 화면 · 사진 장면 (비행 중에는 비워 지도가 보인다, -1)
            Crossfade(targetState = if (step > 0 && !arrived) -1 else step, animationSpec = tween(FADE_MS), label = "replay-scene") { s ->
                when {
                    s == 0 -> IntroCard(r, wide)
                    s > 0 -> r.scenes.getOrNull(s - 1)?.let { SceneCard(r, it, paused, wide) }
                    else -> Box(Modifier.fillMaxSize())
                }
            }
            Crossfade(targetState = passing.takeIf { step > 0 && !arrived }, animationSpec = tween(FADE_MS), label = "replay-road-photo") { p ->
                if (p != null) RoadPhotoCard(p.spot, wide) else Box(Modifier.fillMaxSize())
            }
            // 손짓 판: 누르면 멈춤/다시 재생, 옆으로 밀면 이전 · 다음 (지도 위도 덮는다)
            var drag by remember { mutableStateOf(0f) }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(r) { detectTapGestures { paused = !paused } }
                    .pointerInput(r) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onHorizontalDrag = { _, dx -> drag += dx },
                            onDragEnd = {
                                if (drag > SWIPE_PX) go(currentStep - 1) else if (drag < -SWIPE_PX) go(currentStep + 1)
                            },
                        )
                    },
            )
            // 작은 지도 (오른쪽 위, 사진 장면에서만): 지나온 길 + 방금 온 구간을 비스듬히 내려다본다
            val mapW = if (wide) minOf(460.dp, maxWidth * 0.36f) else maxWidth * 0.5f
            val insetAlpha = animateFloatAsState(if (step > 0 && arrived) 1f else 0f, tween(FADE_MS), label = "inset")
            ReplayMap(
                bounds = r.bounds,
                done = scene?.doneGeojson ?: EMPTY,
                leg = scene?.legFrames?.lastOrNull(),
                sky = r.sky,
                visible = step > 0 && arrived,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (wide) 104.dp else 116.dp, end = if (wide) 32.dp else 16.dp)
                    .width(mapW)
                    .height(mapW * 0.68f)
                    .graphicsLayer { alpha = insetAlpha.value }
                    .clip(RoundedCornerShape(24.dp)),
            )
            // 좁은 창(폰 세로): 버튼을 작게 하고 진행 줄은 버튼 아래로 내린다 (넓은 창 값 그대로면 버튼끼리 겹친다)
            val button = if (wide) 76.dp else 56.dp
            val edge = if (wide) 28.dp else 16.dp
            Progress(
                n, step,
                if (wide) Modifier.align(Alignment.TopCenter).padding(top = 28.dp, start = 140.dp, end = 320.dp)
                else Modifier.align(Alignment.TopCenter).padding(top = 92.dp, start = 16.dp, end = 16.dp),
            )
            RoundButton(AgentIcons.Close, stringResource(R.string.replay_close), onClose, Modifier.align(Alignment.TopStart).padding(edge), button)
            // 이전 · 멈춤 · 다음: 오른쪽 위 한 줄 (사진 카드 · 자막과 겹치지 않는 자리, 반투명 바탕)
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = if (wide) 20.dp else 16.dp, end = if (wide) 32.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 8.dp),
            ) {
                RoundButton(EodiIcons.Back, stringResource(R.string.replay_prev), { go(step - 1) }, Modifier.alpha(if (step > 0) 1f else 0.4f), button)
                RoundButton(
                    if (paused) EodiIcons.Play else EodiIcons.Pause,
                    stringResource(if (paused) R.string.replay_resume else R.string.replay_pause),
                    { paused = !paused },
                    Modifier,
                    button,
                )
                RoundButton(EodiIcons.Chevron, stringResource(R.string.replay_next), { go(step + 1) }, Modifier, button)
            }
            if (paused) {
                Box(Modifier.align(Alignment.Center).size(140.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EodiIcons.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }
        }
    }
}

/** 비행 중 길 위 사진: 금색 테두리 카드 + "09:35에 찍은 사진" (지도를 다 가리지 않게 한쪽에). */
@Composable
private fun RoadPhotoCard(spot: MapPhotoSpot, wide: Boolean) {
    Box(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 96.dp), contentAlignment = if (wide) Alignment.CenterStart else Alignment.BottomCenter) {
        Column {
            MyPhotoImage(
                spot.uri,
                Modifier
                    .fillMaxWidth(if (wide) 0.42f else 0.8f)
                    .aspectRatio(4f / 3f)
                    .shadow(18.dp, RoundedCornerShape(20.dp))
                    .border(5.dp, EodiTheme.colors.memory, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)),
                sizePx = ROAD_PHOTO_PX,
            )
            Text(
                stringResource(R.string.replay_road_photo, clock(spot.takenMs)),
                color = Color.White,
                style = EodiTheme.type.title.copy(shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 12f)),
                modifier = Modifier.padding(top = 14.dp, start = 6.dp),
            )
        }
    }
}

private class LegPhoto(val spot: MapPhotoSpot, val at: Float)

/**
 * 길 위 사진을 가장 가까운 비행 구간에 붙인다: 장면 번호(0부터) → 그 구간의 사진 (지나는 순).
 * 어느 구간과도 [ROAD_PHOTO_NEAR_M]보다 멀면 뺀다.
 */
private fun roadPhotosByLeg(r: DayReplay, photos: List<MapPhotoSpot>): Map<Int, List<LegPhoto>> {
    val out = HashMap<Int, MutableList<LegPhoto>>()
    for (p in photos) {
        var leg = -1
        var at = 0f
        var near = ROAD_PHOTO_NEAR_M
        val kx = 111_320.0 * cos(Math.toRadians(p.lat))
        r.scenes.forEachIndexed { i, s ->
            val f = s.legFlight
            for (k in 0 until f.size - 1) {
                // 선분 위 가장 가까운 점 (좁은 범위라 평면으로 본다)
                val ax = (f[k].lon - p.lon) * kx
                val ay = (f[k].lat - p.lat) * 110_540.0
                val dx = (f[k + 1].lon - f[k].lon) * kx
                val dy = (f[k + 1].lat - f[k].lat) * 110_540.0
                val len2 = dx * dx + dy * dy
                val u = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val d = hypot(ax + dx * u, ay + dy * u)
                if (d < near) {
                    near = d
                    leg = i
                    at = ((k + u) / (f.size - 1)).toFloat()
                }
            }
        }
        if (leg >= 0) out.getOrPut(leg) { ArrayList() } += LegPhoto(p, at)
    }
    out.values.forEach { l -> l.sortBy { it.at } }
    return out
}

private fun clock(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).let { "%02d:%02d".format(it.hour, it.minute) }

/** 시작 화면: 도장 · 날짜 · 하루 제목이 차례로 떠오른다. 좁은 창(폰 세로)에서는 글이 화면 폭을 다 쓴다. */
@Composable
private fun IntroCard(r: DayReplay, wide: Boolean) {
    val a1 = remember { Animatable(0f) }
    val a2 = remember { Animatable(0f) }
    val a3 = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a1.animateTo(1f, tween(700))
        a2.animateTo(1f, tween(700))
        a3.animateTo(1f, tween(900))
    }
    Box(Modifier.fillMaxSize()) {
        // 사진 없이 지도가 비치게 두고, 글 쪽(왼쪽)만 짙은 밤색으로 가린다 (글 자리는 86% 이상 → 흰 글 대비 4.5:1 넘게)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Night.copy(alpha = 0.94f), 0.6f to Night.copy(alpha = 0.86f), 1f to Night.copy(alpha = 0.35f))))
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(if (wide) 0.6f else 1f).padding(start = if (wide) 120.dp else 28.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (r.stamp.isNotBlank()) MemoryStamp(r.stamp, large = true, modifier = Modifier.alpha(a1.value))
            Text(r.dateText, style = EodiTheme.type.title, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.alpha(a2.value))
            Text(r.title, style = EodiTheme.type.hero, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(a3.value))
            Text(r.subtitle, style = EodiTheme.type.body, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.alpha(a3.value))
            if (r.scenes.isEmpty()) {
                Text(stringResource(R.string.replay_no_places), style = EodiTheme.type.body, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

/**
 * 들른 곳 장면: 지도는 덮지 않는다 (좁은 창에서는 사진 위 · 글 아래로 쌓는다). 왼쪽 아래에 사진 카드(화면 폭 28%, 둥근 모서리 · 그림자, 천천히 다가가는 켄 번스),
 * 그 옆에 시각 · 이름 · 머문 시간 · 하루 줄. 글 뒤에는 아래로 짙어지는 스크림(글 자리 78% 이상 → 흰 글 대비 4.5:1 넘게).
 * 집 · 회사 · 회사 건물처럼 사진이 맥락과 어긋나는 곳(Rust `show_photo` = false)은 사진 없이 글만.
 */
@Composable
private fun SceneCard(r: DayReplay, s: ReplayScene, paused: Boolean, wide: Boolean) {
    val kb = remember(s) { Animatable(0f) }
    LaunchedEffect(s, paused) {
        if (!paused && s.showPhoto) kb.animateTo(1f, tween(((1f - kb.value) * KEN_BURNS_MS).toInt().coerceAtLeast(1), easing = LinearEasing))
    }
    // 장면마다 움직이는 방향을 번갈아 (홀수: 오른쪽 위로, 짝수: 왼쪽 아래로)
    val dir = if (s.order % 2 == 1) 1f else -1f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 카드 폭: 넓은 창은 화면 폭 28% (낮은 창에서는 카드 높이가 화면 높이 40%를 넘지 않게), 좁은 창(폰 세로)은 화면 폭 62%
        val cardW = if (wide) minOf(maxWidth * 0.28f, maxHeight * 0.4f * (4f / 3f)) else maxWidth * 0.62f
        val scrim = Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black.copy(alpha = 0.78f), 1f to Color.Black.copy(alpha = 0.9f))
        val photo: @Composable () -> Unit = {
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier
                    .width(cardW)
                    .height(cardW * 0.75f)
                    .shadow(20.dp, shape)
                    .clip(shape)
                    .background(Color(0xFF1C222C)),
            ) {
                PlaceThumbnail(
                    s.stop.name, s.stop.address, s.stop.lat, s.stop.lon,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val k = kb.value
                        scaleX = 1.04f + 0.10f * k
                        scaleY = scaleX
                        translationX = dir * size.width * 0.02f * k
                        translationY = -dir * size.height * 0.015f * k
                    },
                    fallback = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A3140), Night)))) },
                    loading = { PhotoLoadingPlaceholder(Color(0xFF1C222C)) },
                )
            }
        }
        val text: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.stamp.isNotBlank()) {
                        MemoryStamp(r.stamp)
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(listOf(r.title, r.subtitle).filter { it.isNotBlank() }.joinToString(" · "), style = EodiTheme.type.label, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(s.timeText, style = EodiTheme.type.hero, color = EodiTheme.colors.memory)
                Text(s.stop.name, style = EodiTheme.type.hero, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(stopOrderText(s.order), s.stayText).filter { it.isNotBlank() }.joinToString(" · "),
                    style = EodiTheme.type.bodyStrong,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
        if (wide) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(scrim).padding(start = 40.dp, end = 120.dp, top = 96.dp, bottom = 48.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (s.showPhoto) {
                    photo()
                    Spacer(Modifier.width(36.dp))
                } else {
                    Spacer(Modifier.width(80.dp))
                }
                text(Modifier.weight(1f))
            }
        } else {
            // 좁은 창: 옆으로 나란히 두면 글 칸이 한 글자 폭으로 줄어든다 → 사진 위, 글 아래
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(scrim).padding(start = 24.dp, end = 24.dp, top = 96.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (s.showPhoto) photo()
                text(Modifier.fillMaxWidth())
            }
        }
    }
}

/** 끝 화면: "그날, 우리는 384km를 달렸어요" + 그날 경로 전체 3D 지도(비스듬히, 천천히 돈다) + [그 코스로 안내] [처음부터] [닫기]. */
@Composable
private fun EndScreen(r: DayReplay, wide: Boolean, onNavigate: () -> Unit, onAgain: () -> Unit, onClose: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(1_000)) }
    val text: @Composable (Modifier) -> Unit = { m ->
        Column(m.alpha(a.value), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (r.stamp.isNotBlank()) MemoryStamp(r.stamp, large = true)
            Text(r.dateText, style = EodiTheme.type.label, color = Color.White.copy(alpha = 0.7f))
            Text(r.summaryLine, style = EodiTheme.type.hero, color = Color.White)
            if (r.statsLine.isNotBlank()) Text(r.statsLine, style = EodiTheme.type.body, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // 출퇴근 날은 숨긴다 (늘 다니는 길, Rust `can_guide`)
                if (r.canGuide) {
                    Button(
                        onClick = onNavigate,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = EodiTheme.colors.accent, contentColor = EodiTheme.colors.onAccent),
                        modifier = Modifier.heightIn(min = 76.dp),
                    ) {
                        Icon(AgentIcons.Navigate, contentDescription = null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.recap_navigate), style = EodiTheme.type.label, fontWeight = FontWeight.Bold)
                    }
                }
                FilledTonalButton(onClick = onAgain, shape = CircleShape, modifier = Modifier.heightIn(min = 76.dp)) {
                    Text(stringResource(R.string.replay_again), style = EodiTheme.type.label, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(onClick = onClose, shape = CircleShape, modifier = Modifier.heightIn(min = 76.dp)) {
                    Text(stringResource(R.string.replay_close), style = EodiTheme.type.label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    val map: @Composable (Modifier) -> Unit = { m -> ReplayMap(r.bounds, r.finalGeojson, null, m.clip(RoundedCornerShape(28.dp)), orbit = true, sky = r.sky) }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(56.dp), verticalAlignment = Alignment.CenterVertically) {
            text(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
            map(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            map(Modifier.fillMaxWidth().weight(1f))
            text(Modifier.fillMaxWidth())
        }
    }
}

/** 위쪽 진행 막대 (장면마다 한 칸). */
@Composable
private fun Progress(n: Int, step: Int, modifier: Modifier) {
    if (n == 0) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(n) { i ->
            Box(
                Modifier.weight(1f).height(6.dp).clip(CircleShape)
                    .background(if (i < step) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.3f)),
            )
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, modifier: Modifier, size: Dp = 76.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.47f))
    }
}

/** "첫 번째 들른 곳" … "열 번째 들른 곳", 그 뒤는 "11번째 들른 곳". */
@Composable
private fun stopOrderText(order: Int): String {
    val words = androidx.compose.ui.res.stringArrayResource(R.array.replay_stop_order_words)
    return words.getOrNull(order - 1) ?: stringResource(R.string.replay_stop_order, order)
}
