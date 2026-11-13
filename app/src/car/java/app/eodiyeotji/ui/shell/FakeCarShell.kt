package app.eodiyeotji.ui.shell

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.platform.TabletVehicle
import app.eodiyeotji.ui.theme.BrandMark
import app.eodiyeotji.ui.theme.EodiIcons
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/*
 * 차가 아닌 기기(태블릿)에 차 판을 깔았을 때 쓰는 시연용 인포 화면. 진짜 차(자동차용 안드로이드)에서는 아무것도 하지 않는다.
 *
 * 차 화면은 인포테인먼트 안의 한 칸에 뜬다: 왼쪽 주행 정보 화면 · 오른쪽 앱 화면 · 맨 아래 하단 바
 * (Pleos 디자인 가이드 Display Layout). 일반 태블릿에는 그 틀도, 기어 · 속도 같은 차 신호도 없다. 그래서 그 구성을 우리가 직접
 * 그리고(현대차 화면을 가져다 쓰지 않는다), 앱은 차에서와 같은 자리 · 같은 크기(dp)로 그 안에 띄운다.
 * - 기준 크기는 Pleos 에뮬레이터 화면(2560x1440, 밀도 1.0)이다. 틀 전체를 그 크기로 그린 뒤 태블릿 화면에 맞게 줄인다.
 * - 동작하는 것: 시계, 기어(P R N D — D · R이면 앱이 주행 중으로 바뀌고 시연 주행을 기록한다, platform/TabletVehicle.kt),
 *   속도(시연 주행의 값), 실내 온도 올리고 내리기.
 * - 이 화면이 떠 있는 동안에는 화면이 꺼지지 않는다 (앱을 벗어나면 기기 설정대로). 대신 서 있는 채로 [SAVER_AFTER_MS] 동안 손대지 않으면
 *   화면 보호기(검은 화면 + 자리를 옮겨 다니는 시계)가 덮는다 — 같은 그림이 오래 켜져 화면에 자국이 남지 않게. 누르면 돌아온다.
 * - 그림뿐인 것: 주행 가능 거리 · 음악 · 홈 · 내비 · 전화 · 앱 버튼 (눌러도 아무 일도 없다).
 * - 꾸밈 그림(선택): 기기의 `files/shell/panel.png`(주행 정보 화면의 차 그림 · 음악 줄) · `bar.png`(하단 바) · `status.png`(위 상태 줄)가 있으면
 *   직접 그린 것 대신 그 그림을 쓴다. 에뮬레이터 화면에서 오려 넣는 것이라 저장소에는 두지 않는다 (scripts/demo-shell/push_shell.py).
 */

private const val FRAME_W = 2560
private const val FRAME_H = 1440
private val PanelWidth = 854.dp
private val StatusHeight = 90.dp
private val BarHeight = 140.dp

private val PanelBg = Color(0xFFF3F4F6)
private val TopInk = Color(0xFF333333)
private val TopMuted = Color(0xFFAAAAAA)
private val PanelInk = Color(0xFF16191C)
private val PanelMuted = Color(0xFF8A9099)
private val BarBg = Color(0xFF000000)
private val BarInk = Color(0xFFF1F1EE)
private val BarTile = Color(0xFF2A2E33)
private val Active = Color(0xFFF2A100)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ShellEntryPoint {
    fun vehicle(): TabletVehicle
}

@Composable
fun FakeCarShell(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val automotive = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) }
    if (automotive) {
        content()
        return
    }
    // 시연용 화면이 떠 있는 동안에는 화면이 꺼지지 않게 한다 (꺼지면 잠겨서 시연이 끊긴다). 앱을 벗어나면 풀린다
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        // 기기의 위 상태 줄 · 아래 줄(태블릿의 작업 표시줄 포함)을 숨겨 인포 화면만 보이게 한다. 가장자리에서 쓸면 잠깐 나온다
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).consumeWindowInsets(WindowInsets.safeDrawing)
            .onSizeChanged { box = it },
        contentAlignment = Alignment.Center,
    ) {
        if (box.width == 0 || box.height == 0) return@Box
        // 틀 전체(2560x1440dp)가 화면에 들어가는 밀도: 이 안에서는 1dp가 에뮬레이터의 1px이다
        val scale = min(box.width / FRAME_W.toFloat(), box.height / FRAME_H.toFloat())
        CompositionLocalProvider(LocalDensity provides Density(scale, LocalDensity.current.fontScale)) {
            // 마지막으로 화면을 누른 때. 누름은 여기(부모)에서 먼저 보기만 하고 그대로 아래 화면에 흘려보낸다
            // (화면 위에 따로 덮은 칸에서 보면 그 칸이 누름을 가로채 아래 버튼이 하나도 안 눌린다)
            var touched by remember { mutableStateOf(0L) }
            Box(
                Modifier.requiredSize(FRAME_W.dp, FRAME_H.dp).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            touched = System.nanoTime()
                        }
                    }
                },
            ) {
                Shell(content)
                ScreenSaver(touched, onWake = { touched = System.nanoTime() })
            }
        }
    }
}

@Composable
private fun Shell(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // 기어 · 속도는 가짜 차(platform/TabletVehicle.kt)가 들고 있다: D · R이면 앱이 주행 중으로 보고 시연 주행을 기록한다
    val vehicle = remember { EntryPointAccessors.fromApplication(context.applicationContext, ShellEntryPoint::class.java).vehicle() }
    val gear by vehicle.gear.collectAsStateWithLifecycle()
    val speed by vehicle.speedKmh.collectAsStateWithLifecycle()
    // 꾸밈 그림 (있을 때만, 읽기는 IO 스레드에서)
    val art by produceState(ShellArt(null, null)) {
        value = withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "shell")
            fun load(name: String): ImageBitmap? = File(dir, name).takeIf { it.isFile }?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
            ShellArt(load("panel.png"), load("bar.png"), load("status.png"))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            DrivePanel(gear, speed, onGear = vehicle::setGear, art.panel, Modifier.width(PanelWidth).fillMaxHeight())
            Column(Modifier.weight(1f)) {
                StatusBar(art.status, Modifier.fillMaxWidth().height(StatusHeight))
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { content() }
            }
        }
        BottomBar(art.bar, Modifier.fillMaxWidth().height(BarHeight))
    }
}

/** 꾸밈 그림: 주행 정보 화면의 윗줄 아래 전부([panel]) · 하단 바([bar]) · 위 상태 줄([status]). 없으면 null. */
@Immutable
private class ShellArt(val panel: ImageBitmap?, val bar: ImageBitmap?, val status: ImageBitmap? = null)

/** 주행 정보 화면: 주행 가능 거리 · 속도 · 기어(윗줄, 늘 직접 그린다) + 차 그림 · 음악 ([art]가 있으면 그 그림). */
@Composable
private fun DrivePanel(gear: String, speedKmh: Float, onGear: (String) -> Unit, art: ImageBitmap?, modifier: Modifier) {
    val moving = TabletVehicle.isMoving(gear)
    Column(modifier.background(PanelBg)) {
        // 윗줄: 자리 · 크기는 Pleos 에뮬레이터의 주행 정보 화면에 맞췄다 (px = dp). 기어 글자는 작지만 누르는 칸은 줄 높이 전체다
        Box(Modifier.fillMaxWidth().height(StatusHeight).background(if (art != null) Color.White else PanelBg)) {
            Row(Modifier.align(Alignment.CenterStart).padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 72.dp, height = 32.dp).background(TopInk))
                Box(Modifier.padding(start = 4.dp).size(width = 4.dp, height = 14.dp).background(Color(0xFFD0D0D0)))
                Spacer(Modifier.width(15.dp))
                Text("500", color = Color.Black, fontSize = 32.sp)
                Text(" km", color = Color.Black, fontSize = 25.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Row(Modifier.align(Alignment.CenterStart).padding(start = 384.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(speedKmh.roundToInt().toString(), color = if (moving) TopInk else TopMuted, fontSize = 32.sp)
                Text(" km/h", color = TopMuted, fontSize = 25.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 21.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                listOf("P", "R", "N", "D").forEach { g ->
                    val on = g == gear
                    // 고른 기어는 48dp 짙은 네모(양옆 8dp), 나머지는 32dp 칸의 회색 글자
                    Box(Modifier.width(if (on) 64.dp else 32.dp).fillMaxHeight().clickable { onGear(g) }, contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(48.dp).background(TopInk))
                        Text(g, color = if (on) Color.White else TopMuted, fontSize = 30.sp)
                    }
                }
            }
        }
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CarSketch(Modifier.size(width = 560.dp, height = 300.dp))
                    Spacer(Modifier.height(36.dp))
                    Text(
                        when (gear) {
                            "D" -> "주행 중"
                            "R" -> "후진 중"
                            "N" -> "중립"
                            else -> "주차 중"
                        },
                        color = PanelInk, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("기어를 눌러 주행 · 정차를 바꿔 보세요", color = PanelMuted, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().height(180.dp).background(Color.White).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(100.dp).background(PanelBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("♪", color = PanelInk, fontSize = 48.sp)
                }
                Spacer(Modifier.width(24.dp))
                Text("재생 중인 항목 없음", color = PanelInk, fontSize = 28.sp, modifier = Modifier.weight(1f))
                PanelIcon(EodiIcons.Back)
                Spacer(Modifier.width(36.dp))
                PanelIcon(EodiIcons.Play)
                Spacer(Modifier.width(36.dp))
                PanelIcon(EodiIcons.Chevron)
            }
        }
    }
}

@Composable
private fun PanelIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = PanelMuted, modifier = Modifier.size(52.dp))
}

/** 옆에서 본 차를 선 몇 개로 (그림 파일을 쓰지 않는다). */
@Composable
private fun CarSketch(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = Color(0xFFD5D9DE)
        val glass = Color(0xFF9AA3AE)
        val tire = Color(0xFF2A2E33)
        // 그림자 · 아래 몸통 · 지붕 · 창 · 바퀴
        drawOval(Color(0x22000000), topLeft = Offset(w * 0.06f, h * 0.84f), size = Size(w * 0.88f, h * 0.12f))
        drawRoundRect(body, topLeft = Offset(w * 0.04f, h * 0.44f), size = Size(w * 0.92f, h * 0.36f), cornerRadius = CornerRadius(h * 0.14f))
        drawRoundRect(body, topLeft = Offset(w * 0.24f, h * 0.12f), size = Size(w * 0.50f, h * 0.44f), cornerRadius = CornerRadius(h * 0.16f))
        drawRoundRect(glass, topLeft = Offset(w * 0.28f, h * 0.18f), size = Size(w * 0.20f, h * 0.24f), cornerRadius = CornerRadius(h * 0.06f))
        drawRoundRect(glass, topLeft = Offset(w * 0.50f, h * 0.18f), size = Size(w * 0.20f, h * 0.24f), cornerRadius = CornerRadius(h * 0.06f))
        listOf(0.24f, 0.76f).forEach { x ->
            drawCircle(tire, radius = h * 0.17f, center = Offset(w * x, h * 0.80f))
            drawCircle(body, radius = h * 0.08f, center = Offset(w * x, h * 0.80f))
        }
    }
}

/**
 * 화면 보호기: 기어가 P · N인 채로 [SAVER_AFTER_MS] 동안 화면을 누르지 않으면 검은 화면으로 덮고, 시계만 [SAVER_MOVE_MS]마다 자리를 옮겨 보인다.
 * 아무 데나 누르면 걷힌다 (그 누름은 아래 화면에 전달하지 않는다). 주행 중(D · R)에는 뜨지 않는다.
 * [touched] = 마지막으로 화면을 누른 때 (부모가 지켜본다).
 */
@Composable
private fun ScreenSaver(touched: Long, onWake: () -> Unit) {
    val context = LocalContext.current
    val vehicle = remember { EntryPointAccessors.fromApplication(context.applicationContext, ShellEntryPoint::class.java).vehicle() }
    val gear by vehicle.gear.collectAsStateWithLifecycle()
    var on by remember { mutableStateOf(false) }
    // 누를 때마다([touched]) · 기어가 바뀔 때마다 기다림을 다시 시작한다
    LaunchedEffect(touched, gear) {
        on = false
        if (TabletVehicle.isMoving(gear)) return@LaunchedEffect
        delay(SAVER_AFTER_MS)
        on = true
    }
    if (!on) return
    val spot by produceState(0) {
        while (true) {
            delay(SAVER_MOVE_MS)
            value++
        }
    }
    val clock by produceState(initialValue = clockText(), spot) { value = clockText() }
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { onWake() } },
    ) {
        Text(
            clock,
            color = Color(0xFF8A9099),
            fontSize = 96.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.align(SAVER_SPOTS[spot % SAVER_SPOTS.size]).padding(160.dp),
        )
    }
}

/** 손대지 않고 이만큼 지나면 화면 보호기 */
private const val SAVER_AFTER_MS = 180_000L

/** 화면 보호기의 시계가 자리를 옮기는 간격 */
private const val SAVER_MOVE_MS = 30_000L
private val SAVER_SPOTS = listOf(Alignment.Center, Alignment.TopStart, Alignment.BottomEnd, Alignment.TopEnd, Alignment.BottomStart, Alignment.CenterStart, Alignment.CenterEnd)

/** 위 상태 줄: 시연용 화면이라는 표시 · 사용자 · 시계 (시계는 실제 시각). */
@Composable
private fun StatusBar(art: ImageBitmap?, modifier: Modifier) {
    // 지금 시각 (5초마다 다시 읽는다)
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            delay(5_000)
            value = LocalTime.now()
        }
    }
    if (art != null) {
        // 꾸밈 그림 위에 시계 자리만 덮고 실제 시각을 쓴다 (그림에 찍힌 시각은 멈춰 있으므로)
        Box(modifier) {
            Image(art, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).width(164.dp).height(70.dp).background(Color(0xFFF7F7F7)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    now.format(ART_CLOCK),
                    color = Color.Black, fontSize = 30.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 18.dp),
                )
            }
        }
        return
    }
    Row(modifier.background(Color.White).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("시연용 인포 화면", color = PanelMuted, fontSize = 24.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(52.dp).background(Color(0xFFF1E8FA), CircleShape), contentAlignment = Alignment.Center) {
            Text("운", color = Color(0xFF8A3FC4), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(36.dp))
        Text(now.format(CLOCK), color = PanelInk, fontSize = 30.sp, fontWeight = FontWeight.Medium)
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/** 꾸밈 그림(에뮬레이터 상태 줄)의 시계와 같은 모양: "05:32 오후" */
private val ART_CLOCK = DateTimeFormatter.ofPattern("hh:mm a", Locale.KOREAN)

private fun clockText(): String = LocalTime.now().format(CLOCK)

/** 하단 바: 온도(올리고 내릴 수 있다) · 홈 · 내비 · 전화 · 앱 · 지금 떠 있는 우리 앱. */
@Composable
private fun BottomBar(art: ImageBitmap?, modifier: Modifier) {
    if (art != null) {
        Image(art, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = modifier)
        return
    }
    Row(modifier.background(BarBg).padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Temperature()
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("홈", "내비", "전화", "앱").forEach { BarTile(it) }
            Box(Modifier.padding(horizontal = 6.dp).width(2.dp).height(48.dp).background(BarTile))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(size = 80.dp)
                Box(Modifier.padding(top = 8.dp).width(72.dp).height(6.dp).background(Active, CircleShape))
            }
        }
        Spacer(Modifier.weight(1f))
        Temperature()
    }
}

@Composable
private fun BarTile(label: String) {
    Box(Modifier.size(80.dp).background(BarTile, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(label, color = BarInk, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Temperature() {
    var temp by rememberSaveable { mutableFloatStateOf(22f) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            EodiIcons.Chevron, contentDescription = null, tint = Color(0xFF3A87FF),
            modifier = Modifier.size(76.dp).clip(CircleShape).clickable { temp = (temp - 0.5f).coerceAtLeast(17f) }.padding(14.dp).rotate(90f),
        )
        Text("%.1f°".format(temp), color = BarInk, fontSize = 44.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp))
        Icon(
            EodiIcons.Chevron, contentDescription = null, tint = Color(0xFFFF5A5A),
            modifier = Modifier.size(76.dp).clip(CircleShape).clickable { temp = (temp + 0.5f).coerceAtMost(30f) }.padding(14.dp).rotate(-90f),
        )
    }
}
