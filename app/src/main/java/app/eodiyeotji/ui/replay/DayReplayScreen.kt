package app.eodiyeotji.ui.replay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.DayReplay
import app.eodiyeotji.core.ReplayScene
import app.eodiyeotji.ui.agent.AgentIcons
import app.eodiyeotji.ui.map.ReplayMap
import app.eodiyeotji.ui.place.PhotoLoadingPlaceholder
import app.eodiyeotji.ui.place.PlaceThumbnail
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.MemoryStamp

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

/** 한 장면 시간 · 구간 그리기 시간 · 사진이 천천히 다가가는 시간 (ms). */
private const val INTRO_MS = 3_200L
private const val SCENE_MS = 3_600L
private const val LEG_MS = 1_800L
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
            r != null -> Player(r, onClose, onNavigate)
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
 * 재생기: 0 = 시작 화면, 1..n = 들른 곳 장면, n+1 = 끝 화면. 누르면 멈춤/다시 재생, 옆으로 밀거나 화살표로 이전 · 다음.
 */
@Composable
private fun Player(r: DayReplay, onClose: () -> Unit, onNavigate: () -> Unit) {
    val n = r.scenes.size
    val last = n + 1
    var step by remember(r) { mutableIntStateOf(0) }
    var paused by remember(r) { mutableStateOf(false) }
    val go = { to: Int -> step = to.coerceIn(0, last) }
    val currentStep by rememberUpdatedState(step)

    LaunchedEffect(r, step, paused) {
        if (!paused && step < last) {
            kotlinx.coroutines.delay(if (step == 0) INTRO_MS else SCENE_MS)
            step += 1
        }
    }
    // 구간 그리기 단계 (멈추면 그 자리에서 선다)
    val scene = r.scenes.getOrNull(step - 1)
    var frame by remember(r, step) { mutableIntStateOf(0) }
    LaunchedEffect(r, step, paused) {
        val frames = scene?.legFrames ?: return@LaunchedEffect
        if (frames.isEmpty()) return@LaunchedEffect
        val each = LEG_MS / frames.size
        while (!paused && frame < frames.size - 1) {
            kotlinx.coroutines.delay(each)
            frame += 1
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
            Crossfade(targetState = step, animationSpec = tween(FADE_MS), label = "replay-scene") { s ->
                if (s == 0) IntroCard(r) else r.scenes.getOrNull(s - 1)?.let { SceneCard(r, it, paused) }
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
            // 작은 지도 (오른쪽 위): 지나온 길 + 지금 구간
            val mapW = if (wide) minOf(460.dp, maxWidth * 0.36f) else maxWidth * 0.5f
            ReplayMap(
                bounds = r.bounds,
                done = scene?.doneGeojson ?: EMPTY,
                leg = scene?.legFrames?.getOrNull(frame),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 104.dp, end = 32.dp)
                    .width(mapW)
                    .height(mapW * 0.68f)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Progress(n, step, Modifier.align(Alignment.TopCenter).padding(top = 28.dp, start = 140.dp, end = 140.dp))
            RoundButton(AgentIcons.Close, stringResource(R.string.replay_close), onClose, Modifier.align(Alignment.TopStart).padding(28.dp))
            RoundButton(
                if (paused) EodiIcons.Play else EodiIcons.Pause,
                stringResource(if (paused) R.string.replay_resume else R.string.replay_pause),
                { paused = !paused },
                Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 32.dp),
            )
            if (step > 0) {
                RoundButton(EodiIcons.Back, stringResource(R.string.replay_prev), { go(step - 1) }, Modifier.align(Alignment.CenterStart).padding(start = 24.dp))
            }
            RoundButton(EodiIcons.Chevron, stringResource(R.string.replay_next), { go(step + 1) }, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
            if (paused) {
                Box(Modifier.align(Alignment.Center).size(140.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EodiIcons.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }
        }
    }
}

/** 시작 화면: 도장 · 날짜 · 하루 제목이 차례로 떠오른다. */
@Composable
private fun IntroCard(r: DayReplay) {
    val a1 = remember { Animatable(0f) }
    val a2 = remember { Animatable(0f) }
    val a3 = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a1.animateTo(1f, tween(700))
        a2.animateTo(1f, tween(700))
        a3.animateTo(1f, tween(900))
    }
    val first = r.scenes.firstOrNull()
    Box(Modifier.fillMaxSize()) {
        // 첫 들른 곳 사진을 어둡게 깔아 둔다
        if (first != null) {
            PlaceThumbnail(first.stop.name, first.stop.address, first.stop.lat, first.stop.lon, Modifier.fillMaxSize().alpha(0.35f))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night.copy(alpha = 0.4f), Night))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 120.dp, end = 120.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (r.stamp.isNotBlank()) MemoryStamp(r.stamp, large = true, modifier = Modifier.alpha(a1.value))
            Text(r.dateText, style = EodiTheme.type.title, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.alpha(a2.value))
            Text(r.title, style = EodiTheme.type.hero, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(a3.value))
            Text(r.subtitle, style = EodiTheme.type.body, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.alpha(a3.value))
            if (r.scenes.isEmpty()) {
                Text(stringResource(R.string.replay_no_places), style = EodiTheme.type.body, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/** 들른 곳 장면: 큰 사진이 천천히 다가가고 움직인다(켄 번스), 아래 그늘 위에 시각 · 이름 · 머문 시간 · 하루 줄. */
@Composable
private fun SceneCard(r: DayReplay, s: ReplayScene, paused: Boolean) {
    val kb = remember(s) { Animatable(0f) }
    LaunchedEffect(s, paused) {
        if (!paused) kb.animateTo(1f, tween(((1f - kb.value) * KEN_BURNS_MS).toInt().coerceAtLeast(1), easing = LinearEasing))
    }
    // 장면마다 움직이는 방향을 번갈아 (홀수: 오른쪽 위로, 짝수: 왼쪽 아래로)
    val dir = if (s.order % 2 == 1) 1f else -1f
    Box(Modifier.fillMaxSize()) {
        PlaceThumbnail(
            s.stop.name, s.stop.address, s.stop.lat, s.stop.lon,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val k = kb.value
                scaleX = 1.04f + 0.14f * k
                scaleY = scaleX
                translationX = dir * size.width * 0.03f * k
                translationY = -dir * size.height * 0.02f * k
            },
            fallback = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A3140), Night)))) },
            loading = { PhotoLoadingPlaceholder(Color(0xFF1C222C)) },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 120.dp, end = 120.dp, bottom = 56.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.stamp.isNotBlank()) {
                    MemoryStamp(r.stamp)
                    Spacer(Modifier.width(14.dp))
                }
                Text(listOf(r.title, r.subtitle).filter { it.isNotBlank() }.joinToString(" · "), style = EodiTheme.type.label, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(s.timeText, style = EodiTheme.type.hero, color = EodiTheme.colors.memory)
            Text(s.stop.name, style = EodiTheme.type.hero, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(stringResource(R.string.replay_stop_order, s.order), s.stayText).filter { it.isNotBlank() }.joinToString(" · "),
                style = EodiTheme.type.bodyStrong,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** 끝 화면: "그날, 우리는 384km를 달렸어요" + 그날 경로 전체 지도 + [그 코스로 안내] [처음부터] [닫기]. */
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
                if (r.episodeId != null) {
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
    val map: @Composable (Modifier) -> Unit = { m -> ReplayMap(r.bounds, r.finalGeojson, null, m.clip(RoundedCornerShape(28.dp))) }
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
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.size(76.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}
