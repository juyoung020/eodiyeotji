package app.eodiyeotji.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.eodiyeotji.ui.theme.EodiTheme

/*
 * 폰 판 공통 부품: 크기 비율(PhoneScale) · 아래에서 올라오는 시트(PhoneSheet · PhoneSheetHost) · 폰 전용 아이콘.
 */

/**
 * 폰 화면 크기 비율. 어디였지의 글자·간격 토큰(Type.kt · Theme.kt)은 차 화면(팔 길이)에 맞춰 폰 앱의 약 두 배로 잡혀 있다.
 * 폰 판은 같은 토큰·같은 부품을 그대로 쓰되 화면 전체를 이 비율로 그린다 → 디자인은 같고 크기만 폰에 맞는다.
 *   본문 26sp → 16sp, 가장 작은 글자 20sp → 12.4sp, 하루 제목 48sp → 30sp, 터치 76dp → 47dp, 주행 중 버튼 144dp → 89dp
 * 사용자의 시스템 글자 크기 설정(fontScale)은 그대로 따른다.
 */
const val PhoneUiScale = 0.62f

/** [PhoneUiScale]을 적용한다. 실제 화면 밀도에서 계산하므로 (새 창에서) 겹쳐 감싸도 두 번 줄지 않는다. */
@Composable
fun PhoneScale(content: @Composable () -> Unit) {
    val base = LocalContext.current.resources.displayMetrics.density
    val fontScale = LocalDensity.current.fontScale
    val density = remember(base, fontScale) { Density(base * PhoneUiScale, fontScale) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/** 화면에 떠 있는 시트 하나. 내용·닫기는 부른 쪽이 다시 그릴 때마다 바뀐다. */
internal class SheetEntry(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var onDismiss by mutableStateOf(onDismiss)
    var content by mutableStateOf(content)
}

/** 시트를 그리는 곳 (PhoneApp 맨 위 한 군데). 부른 순서대로 쌓고 맨 위 것만 보인다. */
class PhoneSheetHostState {
    internal val entries = mutableStateListOf<SheetEntry>()
}

val LocalPhoneSheetHost = staticCompositionLocalOf<PhoneSheetHostState?> { null }

/**
 * 아래에서 올라오는 시트 (폰 판의 [app.eodiyeotji.ui.community.OverlayCard] 자리 · 설정 · 지도 앱 없음).
 * 어디서 부르든 화면 맨 위([PhoneSheetHost])에 그려서 아래 탭·운전 중 띠까지 덮는다. 뒤로 가기 · 바깥 누르기 = [onDismiss].
 * 대화상자 창을 따로 띄우지 않는다: 같은 창이라 키보드가 올라오면 키보드 높이(WindowInsets.ime)만큼 시트가 같이 올라간다.
 */
@Composable
fun PhoneSheet(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val host = LocalPhoneSheetHost.current ?: return
    val entry = remember { SheetEntry(onDismiss, content) }
    SideEffect {
        entry.onDismiss = onDismiss
        entry.content = content
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** 시트 그리기: 검정 45% 막 + 아래 붙은 카드(위 모서리만 둥글게, 손잡이). 높이는 화면의 92%까지, 넘치면 안에서 굴린다. */
@Composable
fun PhoneSheetHost(state: PhoneSheetHostState) {
    val top = state.entries.lastOrNull()
    AnimatedVisibility(visible = top != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
        val c = EodiTheme.colors
        val none = remember { MutableInteractionSource() }
        // 사라지는 동안에도 마지막 시트를 그린다
        val last = remember { arrayOfNulls<SheetEntry>(1) }
        top?.let { last[0] = it }
        val shown = last[0] ?: return@AnimatedVisibility
        BackHandler(enabled = top != null) { shown.onDismiss() }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = none, indication = null) { shown.onDismiss() }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = c.surface,
                contentColor = c.textPrimary,
                shape = RoundedCornerShape(topStart = EodiTheme.space.radiusL, topEnd = EodiTheme.space.radiusL),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .clickable(interactionSource = none, indication = null) {}
                    .animateEnterFromBottom(),
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                    // 손잡이 (시트라는 표시)
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 64.dp, height = 8.dp).background(c.outline, CircleShape))
                    }
                    Box(content = shown.content)
                }
            }
        }
    }
}

/** 처음 나타날 때 아래에서 살짝 밀려 올라온다 (240ms). */
@Composable
private fun Modifier.animateEnterFromBottom(): Modifier {
    val offset = remember { Animatable(1f) }
    LaunchedEffect(Unit) { offset.animateTo(0f, tween(240)) }
    return graphicsLayer { translationY = size.height * 0.25f * offset.value }
}

/** 폰 화면에만 쓰는 아이콘 (24 격자, 선 2.25, 둥근 끝 — EodiIcons와 같은 규칙). */
internal object PhoneIcons {
    private val Ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 2.25f, b: PathBuilder.() -> Unit) =
        path(stroke = Ink, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = b)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    /** 설정: 조절 막대 세 줄 + 손잡이 */
    val Tune: ImageVector by lazy {
        icon("Tune") {
            stroke {
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 17f); lineTo(20f, 17f)
            }
            path(fill = Ink) {
                circle(9f, 7f, 2.4f)
                circle(15f, 12f, 2.4f)
                circle(7f, 17f, 2.4f)
            }
        }
    }

    /** 알림 종 */
    val Bell: ImageVector by lazy {
        icon("Bell") {
            stroke {
                moveTo(6f, 16f); lineTo(6f, 10.5f)
                curveTo(6f, 7f, 8.7f, 4.5f, 12f, 4.5f)
                curveTo(15.3f, 4.5f, 18f, 7f, 18f, 10.5f)
                lineTo(18f, 16f); lineTo(19.5f, 17.5f); lineTo(4.5f, 17.5f); close()
                moveTo(10f, 20.5f); lineTo(14f, 20.5f)
            }
        }
    }

    /** 위치 과녁 (지금 위치) */
    val Locate: ImageVector by lazy {
        icon("Locate") {
            stroke {
                circle(12f, 12f, 7f)
                moveTo(12f, 2f); lineTo(12f, 5f)
                moveTo(12f, 19f); lineTo(12f, 22f)
                moveTo(2f, 12f); lineTo(5f, 12f)
                moveTo(19f, 12f); lineTo(22f, 12f)
            }
            path(fill = Ink) { circle(12f, 12f, 2.5f) }
        }
    }

    /** 지도 (접힌 지도) */
    val Map: ImageVector by lazy {
        icon("Map") {
            stroke {
                moveTo(3.5f, 6.5f); lineTo(9f, 4.5f); lineTo(15f, 6.5f); lineTo(20.5f, 4.5f)
                lineTo(20.5f, 17.5f); lineTo(15f, 19.5f); lineTo(9f, 17.5f); lineTo(3.5f, 19.5f); close()
                moveTo(9f, 4.5f); lineTo(9f, 17.5f)
                moveTo(15f, 6.5f); lineTo(15f, 19.5f)
            }
        }
    }
}
