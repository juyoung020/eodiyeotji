package app.eodiyeotji.ui.shell

import android.content.pm.PackageManager
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eodiyeotji.ui.agent.ManualDrivingStatePort
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
import kotlinx.coroutines.delay

/*
 * 차가 아닌 기기(태블릿)에 차 판을 깔았을 때 쓰는 시연용 인포 화면. 진짜 차(자동차용 안드로이드)에서는 아무것도 하지 않는다.
 *
 * 차 화면은 인포테인먼트 안의 한 칸에 뜬다: 왼쪽 주행 정보 화면 · 오른쪽 앱 화면 · 맨 아래 하단 바
 * (Pleos 디자인 가이드 Display Layout). 일반 태블릿에는 그 틀도, 기어 · 속도 같은 차 신호도 없다. 그래서 그 구성을 우리가 직접
 * 그리고(현대차 화면을 가져다 쓰지 않는다), 앱은 차에서와 같은 자리 · 같은 크기(dp)로 그 안에 띄운다.
 * - 기준 크기는 Pleos 에뮬레이터 화면(2560x1440, 밀도 1.0)이다. 틀 전체를 그 크기로 그린 뒤 태블릿 화면에 맞게 줄인다.
 * - 동작하는 것: 시계, 기어(P R N D — D · R이면 앱이 주행 중으로 바뀐다), 실내 온도 올리고 내리기.
 * - 그림뿐인 것: 주행 가능 거리 · 음악 · 홈 · 내비 · 전화 · 앱 버튼 (눌러도 아무 일도 없다).
 */

private const val FRAME_W = 2560
private const val FRAME_H = 1440
private val PanelWidth = 854.dp
private val StatusHeight = 90.dp
private val BarHeight = 140.dp

private val PanelBg = Color(0xFFF3F4F6)
private val PanelInk = Color(0xFF16191C)
private val PanelMuted = Color(0xFF8A9099)
private val BarBg = Color(0xFF000000)
private val BarInk = Color(0xFFF1F1EE)
private val BarTile = Color(0xFF2A2E33)
private val Active = Color(0xFFF2A100)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ShellEntryPoint {
    fun manualDriving(): ManualDrivingStatePort
}

@Composable
fun FakeCarShell(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val automotive = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) }
    if (automotive) {
        content()
        return
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
            Box(Modifier.requiredSize(FRAME_W.dp, FRAME_H.dp)) { Shell(content) }
        }
    }
}

@Composable
private fun Shell(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val manual = remember { EntryPointAccessors.fromApplication(context.applicationContext, ShellEntryPoint::class.java).manualDriving() }
    var gear by rememberSaveable { mutableStateOf("P") }
    // 기어가 D · R이면 앱이 주행 중으로 본다 (차에서 운전자 주의 분산 제약이 걸리는 것과 같은 길, ManualDrivingStatePort)
    LaunchedEffect(gear) { manual.setDriving(gear == "D" || gear == "R") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            DrivePanel(gear, onGear = { gear = it }, Modifier.width(PanelWidth).fillMaxHeight())
            Column(Modifier.weight(1f)) {
                StatusBar(Modifier.fillMaxWidth().height(StatusHeight))
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { content() }
            }
        }
        BottomBar(Modifier.fillMaxWidth().height(BarHeight))
    }
}

/** 주행 정보 화면: 주행 가능 거리 · 속도 · 기어 · 차 그림 · 음악. */
@Composable
private fun DrivePanel(gear: String, onGear: (String) -> Unit, modifier: Modifier) {
    val moving = gear == "D" || gear == "R"
    Column(modifier.background(PanelBg)) {
        Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 64.dp, height = 30.dp).background(PanelInk, RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(14.dp))
            Text("500", color = PanelInk, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text(" km", color = PanelInk, fontSize = 24.sp)
            Spacer(Modifier.weight(1f))
            Text(if (moving) "30" else "0", color = if (moving) PanelInk else PanelMuted, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text(" km/h", color = PanelMuted, fontSize = 24.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("P", "R", "N", "D").forEach { g ->
                    val on = g == gear
                    Box(
                        Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(if (on) PanelInk else Color.Transparent).clickable { onGear(g) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(g, color = if (on) Color.White else PanelMuted, fontSize = 36.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
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

/** 위 상태 줄: 시연용 화면이라는 표시 · 사용자 · 시계 (시계는 실제 시각). */
@Composable
private fun StatusBar(modifier: Modifier) {
    val clock by produceState(initialValue = clockText()) {
        while (true) {
            delay(5_000)
            value = clockText()
        }
    }
    Row(modifier.background(Color.White).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("시연용 인포 화면", color = PanelMuted, fontSize = 24.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(52.dp).background(Color(0xFFF1E8FA), CircleShape), contentAlignment = Alignment.Center) {
            Text("운", color = Color(0xFF8A3FC4), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(36.dp))
        Text(clock, color = PanelInk, fontSize = 30.sp, fontWeight = FontWeight.Medium)
    }
}

private fun clockText(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN))

/** 하단 바: 온도(올리고 내릴 수 있다) · 홈 · 내비 · 전화 · 앱 · 지금 떠 있는 우리 앱. */
@Composable
private fun BottomBar(modifier: Modifier) {
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
