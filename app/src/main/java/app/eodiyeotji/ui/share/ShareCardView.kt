package app.eodiyeotji.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eodiyeotji.R
import app.eodiyeotji.ui.theme.BrandMark
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.common.distanceText
import app.eodiyeotji.ui.common.durationText
import kotlin.math.roundToInt

/** 카드 폭(dp). [ComposableImageShare]가 3배(1080px)로 그린다. */
const val SHARE_CARD_WIDTH_DP = 360

private val Pad = 20.dp
/** 길 그림 칸 높이 — 폭(360 - 2×20 = 320dp)과 함께 [SKETCH_ASPECT]가 된다. */
private val SketchHeight = 150.dp

/**
 * 공유 이미지 카드 한 장 ("길 위의 앨범"): 종이 바탕 위에
 * 날짜 · 계절 · 날씨 → 지역 · AI 제목 → 들른 곳 사진(순서 번호) → 작은 길 그림(번호 핀) → 들른 곳 순서 · 거리 · 시간 → "어디였지" 표시.
 * 화면에 띄우지 않고 [ComposableImageShare.render]로 그림만 만든다. 사진은 미리 받은 것([photos], 순서 번호 → 사진)만 쓴다.
 * [map]은 길 그림 밑에 까는 지도([loadCardMap], 없으면 점 무늬 종이).
 * 동승자·집·회사 정보는 없다 (Rust가 거른 값만 받는다).
 */
@Composable
fun ShareCardView(card: ShareCardData, photos: Map<Int, ImageBitmap>, map: ImageBitmap? = null) {
    val c = EodiTheme.colors
    Column(
        Modifier
            .width(SHARE_CARD_WIDTH_DP.dp)
            .background(c.background)
            .padding(start = Pad, end = Pad, top = 22.dp, bottom = 18.dp),
    ) {
        // 날짜 · 계절 · 날씨
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.dateText, style = text(13.sp, FontWeight.SemiBold), color = c.textSecondary, maxLines = 1)
            if (card.season.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Pill(card.season, c.accentSoft, c.accent)
            }
            card.weather?.let {
                Spacer(Modifier.width(6.dp))
                Pill(it, c.surfaceHigh, c.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (card.region.isNotBlank()) {
            Text(card.region, style = text(13.sp, FontWeight.Bold), color = c.accent, maxLines = 1)
            Spacer(Modifier.height(2.dp))
        }
        Text(
            card.title,
            style = text(25.sp, FontWeight.ExtraBold, line = 32.sp),
            color = c.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val shown = card.stops.filter { photos.containsKey(it.order) }.take(CARD_PHOTOS)
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            PhotoCollage(shown, photos)
        }
        if (card.lines.isNotEmpty() || card.stops.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RouteSketch(card, map)
        }
        if (card.stops.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            StopChain(card.stops)
        }
        Spacer(Modifier.height(8.dp))
        val dist = distanceText(card.distanceM)
        val dur = durationText(card.durationMs)
        val stats = listOfNotNull(
            if (card.kind == ShareCardKind.COURSE) stringResource(R.string.share_card_distance_course, dist, dur)
            else stringResource(R.string.share_card_distance_drive, dist, dur),
            card.stops.size.takeIf { it > 0 }?.let { stringResource(R.string.share_card_stops, it) },
        ).joinToString(" · ")
        Text(stats, style = text(13.sp, FontWeight.Medium), color = c.textSecondary, maxLines = 1)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
        Spacer(Modifier.height(12.dp))
        // 앱 표시
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_name), style = text(16.sp, FontWeight.ExtraBold), color = c.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.tagline), style = text(11.sp, FontWeight.Medium), color = c.textTertiary, maxLines = 1)
        }
    }
}

/** 사진 1장 = 가득, 2장 = 반반, 3장 = 큰 사진 + 오른쪽 작은 두 장. */
@Composable
private fun PhotoCollage(stops: List<ShareCardStop>, photos: Map<Int, ImageBitmap>) {
    val gap = 6.dp
    when (stops.size) {
        1 -> PhotoTile(stops[0], photos.getValue(stops[0].order), Modifier.fillMaxWidth().height(196.dp))
        2 -> Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            stops.forEach { PhotoTile(it, photos.getValue(it.order), Modifier.weight(1f).fillMaxSize()) }
        }
        else -> Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            PhotoTile(stops[0], photos.getValue(stops[0].order), Modifier.weight(1.45f).fillMaxSize())
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                PhotoTile(stops[1], photos.getValue(stops[1].order), Modifier.weight(1f).fillMaxWidth())
                PhotoTile(stops[2], photos.getValue(stops[2].order), Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PhotoTile(stop: ShareCardStop, photo: ImageBitmap, modifier: Modifier) {
    val c = EodiTheme.colors
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(c.ink)) {
        Image(photo, contentDescription = stop.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        OrderBadge(stop.order, Modifier.align(Alignment.TopStart).padding(8.dp))
        Text(
            stop.name,
            style = text(12.sp, FontWeight.Bold),
            color = c.onInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(c.scrim, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 코랄 동그라미 + 흰 번호 (머문 곳 핀과 같은 색). */
@Composable
private fun OrderBadge(order: Int, modifier: Modifier = Modifier, size: Int = 24) {
    val c = EodiTheme.colors
    Box(
        modifier.size(size.dp).border(2.dp, Color.White, CircleShape).background(c.stay, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$order", style = text((size * 0.5f).sp, FontWeight.ExtraBold), color = c.onMarker)
    }
}

/**
 * 작은 길 그림: 지도([map], 없으면 흰 카드에 점 무늬) 위 파란 길(코스는 점선) + 번호 핀.
 * 좌표는 Rust가 이 칸 비율에 맞춰 0~1로 주고, 지도도 같은 범위([ShareCardData.mapBox])로 받아 둔 것이라 그대로 겹친다.
 */
@Composable
private fun RouteSketch(card: ShareCardData, map: ImageBitmap?) {
    val c = EodiTheme.colors
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(SketchHeight).clip(RoundedCornerShape(20.dp)).background(c.surface),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (map != null) Image(map, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            // 지도가 없으면 종이 위 옅은 점 무늬 (지도 대신 "그림"이라는 표시)
            val step = 14.dp.toPx()
            var y = if (map == null) step / 2 else size.height
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(c.outline, 1.dp.toPx(), Offset(x, y))
                    x += step
                }
                y += step
            }
            val casing = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val line = Stroke(
                if (card.straight) 3.dp.toPx() else 4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = if (card.straight) PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 7.dp.toPx())) else null,
            )
            card.lines.forEach { pts ->
                val path = Path()
                pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x * w, p.y * h) else path.lineTo(p.x * w, p.y * h) }
                if (!card.straight) drawPath(path, c.surface, style = casing)
                drawPath(path, c.trip, style = line)
            }
        }
        val badge = 22
        card.stops.forEach { s ->
            val half = badge.dp.toPxInt() / 2
            OrderBadge(
                s.order,
                Modifier.offset { IntOffset((s.at.x * w).roundToInt() - half, (s.at.y * h).roundToInt() - half) },
                size = badge,
            )
        }
        Text(
            stringResource(if (card.straight) R.string.share_card_course_route else R.string.share_card_route),
            style = text(11.sp, FontWeight.Bold),
            color = if (map != null) c.textSecondary else c.textTertiary,
            modifier = Modifier
                .padding(start = 10.dp, top = 8.dp)
                .then(if (map != null) Modifier.background(c.surface.copy(alpha = 0.85f), CircleShape) else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        // 지도 출처 (지도를 깔았을 때만)
        if (map != null) {
            Text(
                MAP_CREDIT,
                style = text(8.sp, FontWeight.Medium),
                color = c.textSecondary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 6.dp)
                    .background(c.surface.copy(alpha = 0.75f), CircleShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** 카드 지도의 출처 표기 (앱 지도와 같은 자료: OpenStreetMap). */
private const val MAP_CREDIT = "© OpenStreetMap"

/** "1 경포해변 › 2 초당할머니순두부 › 3 …" (번호는 코랄). */
@Composable
private fun StopChain(stops: List<ShareCardStop>) {
    val c = EodiTheme.colors
    val chain = buildAnnotatedString {
        stops.forEachIndexed { i, s ->
            if (i > 0) withStyle(SpanStyle(color = c.textTertiary)) { append("  ›  ") }
            withStyle(SpanStyle(color = c.stay, fontWeight = FontWeight.ExtraBold)) { append("${s.order} ") }
            append(s.name)
        }
    }
    Text(chain, style = text(14.sp, FontWeight.SemiBold, line = 21.sp), color = c.textPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Pill(label: String, bg: Color, fg: Color) {
    Text(
        label,
        style = text(11.sp, FontWeight.Bold),
        color = fg,
        maxLines = 1,
        modifier = Modifier.background(bg, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun androidx.compose.ui.unit.Dp.toPxInt(): Int = with(androidx.compose.ui.platform.LocalDensity.current) { roundToPx() }

/** 카드 글자. 한국어가 낱말 중간("마/실")에서 끊기지 않게 어절 단위로 줄을 바꾼다 (Android 13 이상). */
private fun text(size: TextUnit, weight: FontWeight, line: TextUnit = TextUnit.Unspecified) =
    TextStyle(
        fontSize = size, fontWeight = weight, lineHeight = line,
        lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
    )
