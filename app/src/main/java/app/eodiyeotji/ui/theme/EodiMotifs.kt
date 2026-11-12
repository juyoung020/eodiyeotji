package app.eodiyeotji.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 어디였지 무늬 (docs/90_디자인_컨셉.md "무늬"). 앱 아이콘의 세 요소 — 점선 길 · 핀 · 반짝임 — 를 화면 곳곳에 조금씩 쓴다.
 * 전부 Compose로 직접 그린 정적인 그림이라 이미지 파일이 없고, 테마 색을 그대로 따라 라이트 · 다크에 같이 맞는다.
 */

/** 앱 아이콘의 짙은 파랑 그라데이션 두 색 (res/drawable/ic_launcher_background.xml과 같은 값). */
private val MarkTop = Color(0xFF2F86FF)
private val MarkBottom = Color(0xFF0050E0)

/**
 * 앱 표시: 파란 둥근 네모 위에 흰 점선 길 → 흰 핀, 핀 옆에 금색 반짝임. 앱 아이콘을 그대로 줄인 모양이다.
 * 레일 맨 위에 두어 어느 탭에서든 "어디였지" 앱이라는 것이 보인다.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val spark = EodiTheme.colors.memory
    Canvas(modifier.size(size)) {
        val s = this.size.width
        drawRoundRect(
            Brush.linearGradient(listOf(MarkTop, MarkBottom), Offset(s * 0.2f, 0f), Offset(s * 0.8f, s)),
            cornerRadius = CornerRadius(s * 0.28f),
        )
        // 아이콘 108 격자의 가운데 [18, 90] 칸을 이 네모에 맞춘다
        val u = s / 72f
        fun p(x: Float, y: Float) = Offset((x - 18f) * u, (y - 18f) * u)
        val road = Path().apply {
            val a = p(29f, 81f); moveTo(a.x, a.y)
            val c1 = p(42f, 80f); val c2 = p(45f, 69f); val e1 = p(51f, 60f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            val c3 = p(56f, 52f); val c4 = p(61f, 50f); val e2 = p(68f, 50f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }
        drawPath(
            road,
            Color.White,
            style = Stroke(width = 4.6f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.2f * u, 5.4f * u))),
        )
        drawPin(p(69f, 47.5f), 8.4f * u, Color.White)
        drawSparkle(p(80.5f, 27.5f), 5.2f * u, spark)
    }
}

/**
 * 점선 길 구분선: 짧은 점이 일정하게 이어지다 끝에 작은 핀. 머리말 옆 · 칸 사이에 깔아 "길 위의 기록" 느낌을 준다.
 * 높이는 [height], 폭은 받은 만큼 채운다.
 */
@Composable
fun DottedRoute(modifier: Modifier = Modifier, color: Color = EodiTheme.colors.outline, pin: Color? = null, height: Dp = 16.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val y = size.height / 2
        val r = 2.5.dp.toPx()
        val step = 11.dp.toPx()
        val end = if (pin != null) size.width - 18.dp.toPx() else size.width
        var x = r
        while (x < end) {
            drawCircle(color, r, Offset(x, y))
            x += step
        }
        if (pin != null) drawPin(Offset(size.width - 7.dp.toPx(), y + 7.dp.toPx()), 7.dp.toPx(), pin)
    }
}

/**
 * 기억 도장: 금색 알약 안에 반짝임 + 짧은 말 ("1년 전", "지난 가을"). AI가 찾아 준 기억 · 회상에만 붙인다.
 * 읽기용 표시(누르는 곳이 아님). [large]는 주행 중 카드용.
 */
@Composable
fun MemoryStamp(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val c = EodiTheme.colors
    Row(
        modifier.background(c.memory, CircleShape)
            .padding(start = if (large) 20.dp else 14.dp, end = if (large) 20.dp else 14.dp, top = if (large) 8.dp else 5.dp, bottom = if (large) 8.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = if (large) EodiTheme.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else EodiTheme.type.eyebrow,
            color = c.onMemory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** AI 표시 동그라미: 파랑 바탕 + 금색 반짝임 (에이전트 답 앞, 입구 버튼). */
@Composable
fun SparkleBadge(modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val c = EodiTheme.colors
    Canvas(modifier.size(size)) {
        val s = this.size.width
        drawCircle(Brush.linearGradient(listOf(MarkTop, MarkBottom), Offset.Zero, Offset(s, s)))
        drawSparkle(Offset(s * 0.46f, s * 0.46f), s * 0.24f, c.memory)
        drawSparkle(Offset(s * 0.70f, s * 0.68f), s * 0.10f, Color.White)
    }
}

/**
 * 기억 앨범 그림 (에이전트 첫 화면): 점선 길이 왼쪽 아래에서 오른쪽 위 핀까지 굽이치고, 길 위에 사진 세 장(바다 · 노을 · 숲)이
 * 앨범에 붙인 것처럼 살짝 기울어 놓인다. 핀 옆에 금색 반짝임 — "지나온 길에서 그때 거기를 찾아 준다".
 */
@Composable
fun MemoryAlbumArt(modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    Canvas(modifier.size(width = 520.dp, height = 200.dp)) {
        val u = size.width / 520f
        val road = Path().apply {
            moveTo(20 * u, 176 * u)
            cubicTo(110 * u, 176 * u, 120 * u, 110 * u, 200 * u, 112 * u)
            cubicTo(290 * u, 114 * u, 300 * u, 170 * u, 380 * u, 150 * u)
            cubicTo(440 * u, 136 * u, 450 * u, 84 * u, 468 * u, 64 * u)
        }
        drawPath(road, c.accent.copy(alpha = 0.18f), style = Stroke(width = 22 * u, cap = StrokeCap.Round))
        drawPath(
            road,
            c.accent,
            style = Stroke(width = 6 * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14 * u, 12 * u))),
        )
        drawCircle(c.surface, 11 * u, Offset(20 * u, 176 * u))
        drawCircle(c.accent, 7 * u, Offset(20 * u, 176 * u))
        // 사진 세 장: 바다(파랑) · 노을(코랄 → 금색) · 숲(초록)
        polaroid(Offset(96 * u, 70 * u), u, -8f, Brush.verticalGradient(listOf(Color(0xFF8CC8FF), Color(0xFF2F7BEA))), c)
        polaroid(Offset(250 * u, 52 * u), u, 5f, Brush.verticalGradient(listOf(c.memory, c.stay)), c)
        polaroid(Offset(366 * u, 88 * u), u, -4f, Brush.verticalGradient(listOf(Color(0xFF9BE3B9), Color(0xFF1E9E62))), c)
        drawPin(Offset(468 * u, 60 * u), 22 * u, c.stay)
        drawSparkle(Offset(500 * u, 22 * u), 13 * u, c.memoryInk)
        drawSparkle(Offset(440 * u, 18 * u), 7 * u, c.memoryInk.copy(alpha = 0.7f))
    }
}

/** 앨범에 붙인 사진 한 장: 흰 테두리(아래가 조금 넓다) + 안쪽 그림 + 그 안의 해/산 실루엣. */
private fun DrawScope.polaroid(center: Offset, u: Float, degrees: Float, picture: Brush, c: EodiColors) {
    val w = 92 * u
    val h = 104 * u
    rotate(degrees, center) {
        val topLeft = Offset(center.x - w / 2, center.y - h / 2)
        drawRoundRect(Color.Black.copy(alpha = if (c.isDark) 0.35f else 0.10f), topLeft + Offset(0f, 4 * u), Size(w, h), CornerRadius(10 * u))
        drawRoundRect(if (c.isDark) c.surfaceHigh else Color.White, topLeft, Size(w, h), CornerRadius(10 * u))
        val inner = Offset(topLeft.x + 8 * u, topLeft.y + 8 * u)
        val innerSize = Size(w - 16 * u, w - 16 * u)
        drawRoundRect(picture, inner, innerSize, CornerRadius(6 * u))
        // 해 하나 + 낮은 언덕 하나 (사진처럼 보이게)
        drawCircle(Color.White.copy(alpha = 0.85f), 8 * u, Offset(inner.x + innerSize.width * 0.72f, inner.y + innerSize.height * 0.30f))
        val hill = Path().apply {
            moveTo(inner.x, inner.y + innerSize.height)
            cubicTo(
                inner.x + innerSize.width * 0.3f, inner.y + innerSize.height * 0.55f,
                inner.x + innerSize.width * 0.6f, inner.y + innerSize.height * 0.75f,
                inner.x + innerSize.width, inner.y + innerSize.height * 0.62f,
            )
            lineTo(inner.x + innerSize.width, inner.y + innerSize.height)
            close()
        }
        drawPath(hill, Color.White.copy(alpha = 0.35f))
    }
}

/**
 * 앨범에 붙인 사진 모양: 흰 테두리(다크는 한 단계 밝은 남색) + 옅은 그림자 + [tilt]도 기울임.
 * 사진 칸([content])은 안쪽에 꽉 채운다. 기울임은 그리기 단계(graphicsLayer)에서만 바뀌어 배치 · 재구성 비용이 없다.
 */
@Composable
fun AlbumPhoto(modifier: Modifier = Modifier, tilt: Float = 0f, radius: Dp = 14.dp, content: @Composable () -> Unit) {
    val c = EodiTheme.colors
    val outer = RoundedCornerShape(radius + 4.dp)
    Box(
        modifier
            .graphicsLayer { rotationZ = tilt }
            .shadow(3.dp, outer)
            .background(if (c.isDark) c.surfaceHigh else c.surface, outer)
            .padding(4.dp)
            .clip(RoundedCornerShape(radius))
            .background(c.surfaceHigh),
    ) {
        // 사진이 오기 전·못 받았을 때 보이는 자리: 옅은 칸 가운데 핀 (사진이 오면 그 위를 덮는다)
        Icon(
            EodiIcons.Pin, contentDescription = null, tint = c.textTertiary.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.Center).fillMaxSize(0.36f),
        )
        content()
    }
}

/** 핀 하나 ([tip] = 뾰족한 끝, [r] = 머리 반지름). 가운데 구멍은 뚫는다. */
internal fun DrawScope.drawPin(tip: Offset, r: Float, color: Color) {
    val cy = tip.y - r * 1.75f
    val pin = Path().apply {
        fillType = PathFillType.EvenOdd
        moveTo(tip.x, tip.y)
        cubicTo(tip.x - r * 0.35f, tip.y - r * 0.55f, tip.x - r, cy + r * 0.75f, tip.x - r, cy)
        cubicTo(tip.x - r, cy - r * 0.56f, tip.x - r * 0.56f, cy - r, tip.x, cy - r)
        cubicTo(tip.x + r * 0.56f, cy - r, tip.x + r, cy - r * 0.56f, tip.x + r, cy)
        cubicTo(tip.x + r, cy + r * 0.75f, tip.x + r * 0.35f, tip.y - r * 0.55f, tip.x, tip.y)
        close()
        addOval(androidx.compose.ui.geometry.Rect(Offset(tip.x, cy), r * 0.38f))
    }
    drawPath(pin, color)
}

/** 네 갈래 반짝임 ([r] = 중심에서 끝까지). */
internal fun DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
    val k = r * 0.16f
    val star = Path().apply {
        moveTo(center.x, center.y - r)
        cubicTo(center.x + k, center.y - k, center.x + k, center.y - k, center.x + r, center.y)
        cubicTo(center.x + k, center.y + k, center.x + k, center.y + k, center.x, center.y + r)
        cubicTo(center.x - k, center.y + k, center.x - k, center.y + k, center.x - r, center.y)
        cubicTo(center.x - k, center.y - k, center.x - k, center.y - k, center.x, center.y - r)
        close()
    }
    drawPath(star, color)
}
