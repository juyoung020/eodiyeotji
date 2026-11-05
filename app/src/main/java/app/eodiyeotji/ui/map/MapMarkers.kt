package app.eodiyeotji.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.runtime.Immutable
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

// ------------------------------------------------------------------ 지도 위 표시 (Rust GeoJSON에서 읽기만)

/** 머문 곳 말풍선 카드에 쓸 값. 글은 전부 Rust(`mapdata.rs`)가 만든 그대로다. */
@Immutable
internal data class StayMark(
    val key: String,
    val order: Int?,
    val name: String?,
    /** "18분 정차" / "지금 여기" */
    val dwell: String,
    /** 그날 경로의 출발·도착 지점이면 "출발"/"도착" */
    val tag: String?,
    val open: Boolean,
)

/** 주행 가운데 글 ("12분 · 4.9km"), [key]는 그 주행 키. */
@Immutable
internal data class LabelMark(val key: String, val label: String)

/** 머문 곳이 아닌 출발·도착 지점 깃발. [role]은 "start"/"end". */
@Immutable
internal data class EndMark(val role: String, val label: String)

/** 하루 지도에 그림(아이콘)으로 얹을 것들. [geojson]은 이 값을 읽어 낸 지도 데이터(어느 날 것인지 맞춰 보는 데 쓴다). */
@Immutable
internal data class MapMarks(
    val geojson: String,
    val stays: List<StayMark>,
    val labels: List<LabelMark>,
    val ends: List<EndMark>,
)

/**
 * Rust가 만든 GeoJSON에서 그림으로 얹을 점(머문 곳·주행 가운데 글·출발/도착)의 속성만 꺼낸다. 계산은 하지 않는다.
 * 하루치 문자열 전체를 읽으므로 백그라운드 스레드에서 부른다.
 */
internal fun parseMapMarks(geojson: String): MapMarks {
    val features = FeatureCollection.fromJson(geojson).features().orEmpty()
    val stays = ArrayList<StayMark>()
    val labels = ArrayList<LabelMark>()
    val ends = ArrayList<EndMark>()
    for (f in features) {
        when (f.str("kind")) {
            "stay" -> stays += StayMark(
                key = f.str("key") ?: continue,
                order = if (f.hasNonNullValueForProperty("order")) f.getNumberProperty("order").toInt() else null,
                name = f.str("name"),
                dwell = f.str("dwell").orEmpty(),
                tag = f.str("tag"),
                open = f.hasNonNullValueForProperty("open") && f.getBooleanProperty("open"),
            )
            "trip_label" -> labels += LabelMark(f.str("key") ?: continue, f.str("label") ?: continue)
            "endpoint" -> ends += EndMark(f.str("role") ?: continue, f.str("label") ?: continue)
        }
    }
    return MapMarks(geojson, stays, labels, ends)
}

private fun Feature.str(name: String): String? = if (hasNonNullValueForProperty(name)) getStringProperty(name) else null

// ------------------------------------------------------------------ 아이콘 그림

/** 지도 스타일 이미지 이름. 레이어는 `접두어 + key`(카드·작은 사진·주행 글) / `접두어 + role`(깃발)로 찾는다. */
internal object MapIconId {
    const val CARD = "eodi-card-"
    const val MINI = "eodi-mini-"
    const val LABEL = "eodi-label-"
    const val END = "eodi-end-"
    const val ARROW = "eodi-arrow"
}

/** 하루치 아이콘 그림 묶음. [miniKeys]는 사진이 있어 작은 사진 말풍선도 만든 머문 곳 키. */
internal class MarkerIcons(val geojson: String, val images: Map<String, Bitmap>, val miniKeys: List<String>)

/**
 * 하루치 아이콘을 그린다 (백그라운드 스레드에서). 머문 곳마다 카드(사진·이름·정차 시간)를 만들고, 사진이 있으면
 * 카드가 겹쳐 빠질 때 대신 보일 작은 사진 말풍선도 만든다. 주행 가운데 글 알약과 출발·도착 깃발도 여기서 그린다.
 */
internal fun buildMarkerIcons(marks: MapMarks, photos: Map<String, Bitmap>, p: MapPalette, density: Float): MarkerIcons {
    val d = Drawer(p, density)
    val images = HashMap<String, Bitmap>()
    val minis = ArrayList<String>()
    for (s in marks.stays) {
        val photo = photos[s.key]
        images[MapIconId.CARD + s.key] = d.card(s, photo)
        if (photo != null) {
            images[MapIconId.MINI + s.key] = d.mini(s, photo)
            minis += s.key
        }
    }
    for (l in marks.labels) images[MapIconId.LABEL + l.key] = d.tripLabel(l.label)
    for (e in marks.ends) images[MapIconId.END + e.role] = d.flag(e.label)
    return MarkerIcons(marks.geojson, images, minis)
}

/** 경로 방향 화살표 (오른쪽을 가리키는 흰 꺾쇠). 선 위에 놓으면 MapLibre가 선 방향으로 돌린다. */
internal fun arrowIcon(density: Float): Bitmap {
    val size = (ARROW_DP * density).roundToInt()
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val u = size / ARROW_DP
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * u
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    Canvas(out).drawPath(Path().apply { moveTo(5.5f * u, 3.5f * u); lineTo(10.5f * u, 8f * u); lineTo(5.5f * u, 12.5f * u) }, paint)
    return out
}

/** 말풍선 꼬리 높이(dp). 카드·작은 사진 말풍선·깃발이 같아 핀 위 같은 높이를 가리킨다. */
private const val TAIL_DP = 12f

private const val ARROW_DP = 16f

private class Drawer(private val p: MapPalette, private val d: Float) {
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    private fun textPaint(sizeDp: Float, color: Int, face: Typeface = bold) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizeDp * d
        this.color = color
        typeface = face
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** 흰 바탕 + 은은한 그림자 (소프트웨어 Canvas라 setShadowLayer가 그대로 그려진다) */
    private fun shadowed(color: Int) = fill(color).apply { setShadowLayer(SHADOW_DP * d, 0f, 1.5f * d, SHADOW) }

    /** 말풍선 몸통(둥근 네모) + 아래 가운데 꼬리를 한 경로로. 꼬리 끝이 그림 맨 아래다. */
    private fun bubble(left: Float, top: Float, right: Float, bottom: Float, radius: Float): Path = Path().apply {
        addRoundRect(RectF(left, top, right, bottom), radius, radius, Path.Direction.CW)
        val mid = (left + right) / 2
        val half = TAIL_HALF_DP * d
        moveTo(mid - half, bottom - d)
        lineTo(mid, bottom + TAIL_DP * d)
        lineTo(mid + half, bottom - d)
        close()
    }

    private fun accent(open: Boolean) = if (open) p.openStay else p.stay

    /** 사진을 가운데 잘라 둥근 네모에 꽉 채운다 (centerCrop). */
    private fun Canvas.photo(src: Bitmap, r: RectF, radius: Float) {
        val scale = max(r.width() / src.width, r.height() / src.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(r.centerX() - src.width * scale / 2, r.centerY() - src.height * scale / 2)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
        }
        drawRoundRect(r, radius, radius, paint)
    }

    /** 사진 왼쪽 위 순서 동그라미 (핀 숫자와 같은 색·숫자 → 카드와 핀을 잇는다) */
    private fun Canvas.badge(order: Int, cx: Float, cy: Float, open: Boolean, rDp: Float) {
        drawCircle(cx, cy, rDp * d, fill(p.casing))
        drawCircle(cx, cy, (rDp - 2f) * d, fill(accent(open)))
        val t = textPaint(rDp * 1.05f, p.onStay).apply { textAlign = Paint.Align.CENTER }
        drawText(order.toString(), cx, cy - (t.ascent() + t.descent()) / 2, t)
    }

    /**
     * 정차한 곳 카드: [사진 또는 순서 타일] [이름 / (출발·도착) 정차 시간], 아래 꼬리가 핀을 가리킨다.
     * 그림 둘레에 그림자 자리([MARGIN_DP])를 두고, 꼬리 끝이 그림 맨 아래 가운데다.
     */
    fun card(s: StayMark, photo: Bitmap?): Bitmap {
        val m = MARGIN_DP * d
        val pad = CARD_PAD_DP * d
        val ps = CARD_PHOTO_DP * d
        val h = ps + pad * 2
        val namePaint = textPaint(NAME_DP, p.label)
        val dwellPaint = textPaint(DWELL_DP, accent(s.open))
        val tagPaint = textPaint(TAG_DP, p.casing)
        val tagW = s.tag?.let { tagPaint.measureText(it) + TAG_PAD_DP * 2 * d + TAG_GAP_DP * d } ?: 0f
        val maxText = TEXT_MAX_DP * d
        val name = s.name?.let { TextUtils.ellipsize(it, namePaint, maxText, TextUtils.TruncateAt.END).toString() }
        val rowW = tagW + dwellPaint.measureText(s.dwell)
        val textW = max(TEXT_MIN_DP * d, max(name?.let { namePaint.measureText(it) } ?: 0f, rowW))
        val textX = m + pad + ps + TEXT_GAP_DP * d
        val w = ceil(textX + textW + CARD_END_DP * d + m).toInt()
        val out = Bitmap.createBitmap(w, ceil(m + h + TAIL_DP * d).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawPath(bubble(m, m, w - m, m + h, CARD_RADIUS_DP * d), shadowed(p.casing))

        val pr = RectF(m + pad, m + pad, m + pad + ps, m + pad + ps)
        val pRadius = (CARD_RADIUS_DP - CARD_PAD_DP + 2f) * d
        if (photo != null) {
            c.photo(photo, pr, pRadius)
            s.order?.let { c.badge(it, pr.left + BADGE_DP * d + 3 * d, pr.top + BADGE_DP * d + 3 * d, s.open, BADGE_DP) }
        } else {
            // 사진이 없으면 핀 색 타일에 순서 숫자 크게
            c.drawRoundRect(pr, pRadius, pRadius, fill(accent(s.open)))
            val t = textPaint(TILE_NUM_DP, p.onStay).apply { textAlign = Paint.Align.CENTER }
            val label = s.order?.toString() ?: "·"
            c.drawText(label, pr.centerX(), pr.centerY() - (t.ascent() + t.descent()) / 2, t)
        }

        // 글: 이름이 있으면 두 줄(이름 / 정차), 없으면 정차 한 줄을 가운데
        val rowBase = if (name != null) m + pad + ps * 0.80f else m + h / 2 - (dwellPaint.ascent() + dwellPaint.descent()) / 2
        if (name != null) c.drawText(name, textX, m + pad + ps * 0.38f, namePaint)
        var x = textX
        s.tag?.let { tag ->
            val th = TAG_H_DP * d
            val mid = rowBase + (dwellPaint.ascent() + dwellPaint.descent()) / 2
            val r = RectF(x, mid - th / 2, x + tagPaint.measureText(tag) + TAG_PAD_DP * 2 * d, mid + th / 2)
            c.drawRoundRect(r, th / 2, th / 2, fill(p.label))
            c.drawText(tag, r.left + TAG_PAD_DP * d, mid - (tagPaint.ascent() + tagPaint.descent()) / 2, tagPaint)
            x = r.right + TAG_GAP_DP * d
        }
        c.drawText(s.dwell, x, rowBase, dwellPaint)
        return out
    }

    /** 카드가 다른 카드와 겹쳐 빠졌을 때 대신 보이는 작은 사진 말풍선 (같은 자리, 더 작게). */
    fun mini(s: StayMark, photo: Bitmap): Bitmap {
        val m = MARGIN_DP * d
        val size = MINI_DP * d
        val out = Bitmap.createBitmap(ceil(size + m * 2).toInt(), ceil(m + size + TAIL_DP * d).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawPath(bubble(m, m, m + size, m + size, MINI_RADIUS_DP * d), shadowed(p.casing))
        val border = MINI_BORDER_DP * d
        val r = RectF(m + border, m + border, m + size - border, m + size - border)
        c.photo(photo, r, (MINI_RADIUS_DP - MINI_BORDER_DP) * d)
        s.order?.let { c.badge(it, r.left + MINI_BADGE_DP * d + 2 * d, r.top + MINI_BADGE_DP * d + 2 * d, s.open, MINI_BADGE_DP) }
        return out
    }

    /** 주행 가운데 글: 흰 알약 + 경로색 테두리·글자. 가운데가 선 위에 놓인다. */
    fun tripLabel(text: String): Bitmap {
        val t = textPaint(LABEL_DP, p.trip)
        val m = MARGIN_DP * d
        val h = LABEL_H_DP * d
        val w = t.measureText(text) + LABEL_PAD_DP * 2 * d
        val out = Bitmap.createBitmap(ceil(w + m * 2).toInt(), ceil(h + m * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = RectF(m, m, m + w, m + h)
        c.drawRoundRect(r, h / 2, h / 2, shadowed(p.casing))
        val stroke = fill(p.trip).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d }
        val inset = RectF(r).apply { inset(d, d) }
        c.drawRoundRect(inset, h / 2 - d, h / 2 - d, stroke)
        c.drawText(text, m + LABEL_PAD_DP * d, r.centerY() - (t.ascent() + t.descent()) / 2, t)
        return out
    }

    /** 출발·도착 깃발: 진한 알약 + 아래 꼬리 (점 위에 선다). */
    fun flag(text: String): Bitmap {
        val t = textPaint(FLAG_DP, p.casing)
        val m = MARGIN_DP * d
        val h = FLAG_H_DP * d
        val w = t.measureText(text) + FLAG_PAD_DP * 2 * d
        val out = Bitmap.createBitmap(ceil(w + m * 2).toInt(), ceil(m + h + TAIL_DP * d).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawPath(bubble(m, m, m + w, m + h, h / 2), shadowed(p.label))
        c.drawText(text, m + FLAG_PAD_DP * d, m + h / 2 - (t.ascent() + t.descent()) / 2, t)
        return out
    }

    private companion object {
        const val SHADOW = 0x40000000
        const val SHADOW_DP = 4f
        const val MARGIN_DP = 6f
        const val TAIL_HALF_DP = 10f

        const val CARD_PAD_DP = 6f
        const val CARD_PHOTO_DP = 68f
        const val CARD_RADIUS_DP = 16f
        const val CARD_END_DP = 16f
        const val TEXT_GAP_DP = 12f
        const val TEXT_MIN_DP = 72f
        const val TEXT_MAX_DP = 190f
        const val NAME_DP = 22f
        const val DWELL_DP = 18f
        const val TAG_DP = 15f
        const val TAG_H_DP = 24f
        const val TAG_PAD_DP = 7f
        const val TAG_GAP_DP = 6f
        const val TILE_NUM_DP = 28f
        const val BADGE_DP = 12f

        const val MINI_DP = 52f
        const val MINI_BORDER_DP = 3f
        const val MINI_RADIUS_DP = 12f
        const val MINI_BADGE_DP = 10f

        const val LABEL_DP = 15f
        const val LABEL_H_DP = 30f
        const val LABEL_PAD_DP = 11f

        const val FLAG_DP = 15f
        const val FLAG_H_DP = 28f
        const val FLAG_PAD_DP = 11f
    }
}
