package app.eodiyeotji.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import app.eodiyeotji.core.MapPhotoSpot
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/*
 * 하루 지도 위 "내 사진": 사진을 찍은 곳에 작은 사진 썸네일 핀을 찍고, 한 장짜리 핀을 누르면 말풍선, 여러 장 묶인 핀을 누르면 바로 크게 보기(넘겨 보기).
 * (사진을 전부 펼치면 길과 지도가 가려진다 → 핀만 두고 고른 것 하나만 크게.) 말풍선을 누르면 크게 보기.
 * 자리는 Rust(`gallery::map_spots`)가 정하고, 여기서는 핀 · 말풍선 그림을 그리고 GeoJSON으로 옮기기만 한다.
 * 가까운 사진끼리는 지도(MapLibre 묶기)가 한 핀으로 묶고, 묶인 핀에는 장수 배지가 붙는다.
 */

/** 지도에 얹을 내 사진 그림: 카메라 핀 [PIN] · 고른 핀 [PIN_SEL] + 장수 배지 `gpn<수>`. [geojson]은 사진 자리(번호 = 목록 순서). */
internal class MyPhotoIcons(val geojson: String, val images: Map<String, Bitmap>, val maxBadge: Int)

/** 고른 핀의 사진 말풍선: 자리 · 그 핀의 사진 번호들(찍은 시각순) · 말풍선 그림. */
internal class PhotoBubble(val lat: Double, val lon: Double, val idx: List<Int>, val image: Bitmap)

/** 사진 자리 GeoJSON. 속성 `idx`는 [spots] 안 번호, `rep`는 묶였을 때 대표(가장 이른) 사진 번호를 모으는 칸. */
internal fun myPhotoGeoJson(spots: List<MapPhotoSpot>): String {
    val features = JSONArray()
    spots.forEachIndexed { i, s ->
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(s.lon).put(s.lat)))
                .put("properties", JSONObject().put("idx", i).put("rep", i)),
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

/**
 * 핀 · 배지 그림. [thumbs]가 있으면(핀 번호 → 작은 사진) 그 자리 핀은 사진 썸네일(`gpth<번호>`)로 그린다.
 * 묶인 핀은 대표(가장 이른) 사진 썸네일. 썸네일이 없으면 흰 카메라 핀 [PIN]. 배경 스레드에서 부른다.
 */
internal fun buildMyPhotoIcons(spots: List<MapPhotoSpot>, density: Float, thumbs: Map<Int, Bitmap> = emptyMap()): MyPhotoIcons? {
    if (spots.isEmpty()) return null
    val images = HashMap<String, Bitmap>()
    images[PIN] = photoPin(null, density)
    images[PIN_SEL] = selectedRing(density)
    thumbs.forEach { (i, b) -> images["$THUMB_PREFIX$i"] = photoPin(b, density) }
    val maxBadge = spots.size.coerceAtMost(MAX_BADGE)
    for (n in 2..maxBadge) images["$BADGE_PREFIX$n"] = countBadge(if (n == MAX_BADGE && spots.size > MAX_BADGE) "$MAX_BADGE+" else "$n", density)
    return MyPhotoIcons(myPhotoGeoJson(spots), images, maxBadge)
}

/** 핀 썸네일용 작은 사진들 (Coil 캐시를 거친다). 못 읽은 사진은 빠진다 → 그 핀은 카메라 핀으로 남는다. */
internal suspend fun loadPinThumbs(context: Context, spots: List<MapPhotoSpot>, density: Float): Map<Int, Bitmap> {
    val px = (PIN_DP * density).roundToInt()
    val out = HashMap<Int, Bitmap>()
    spots.forEachIndexed { i, s -> runCatching { decodeThumb(context, s.uri, px) }.getOrNull()?.let { out[i] = it } }
    return out
}

private suspend fun decodeThumb(context: Context, uri: String, px: Int): Bitmap? {
    val req = ImageRequest.Builder(context).data(uri).size(px).allowHardware(false).build()
    val r = context.imageLoader.execute(req)
    return ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
}

/**
 * 고른 핀의 사진 말풍선: [spots]의 첫 사진(가장 이른 것)을 크게, 흰 테두리 · 아래 꼬리. 여러 장이면 오른쪽 위에 "+N".
 * 사진을 못 읽으면 null. [maxHeightPx] = 지도 높이.
 */
internal suspend fun buildPhotoBubble(context: Context, spots: List<MapPhotoSpot>, density: Float, maxHeightPx: Int): Bitmap? {
    val first = spots.firstOrNull() ?: return null
    val photo = decodeThumb(context, first.uri, (BUBBLE_W_DP * density).roundToInt()) ?: return null
    // 지도가 낮으면(하루 상세의 작은 지도) 말풍선도 줄인다: 지도 높이의 [BUBBLE_MAX_MAP_FRACTION]까지
    val fit = (maxHeightPx * BUBBLE_MAX_MAP_FRACTION / (BUBBLE_H_DP * density)).coerceIn(BUBBLE_MIN_SCALE, 1f)
    val w = BUBBLE_W_DP * density * fit
    val h = BUBBLE_H_DP * density * fit
    val border = BUBBLE_BORDER_DP * density
    val shadow = SHADOW_DP * density
    val tail = BUBBLE_TAIL_DP * density
    val r = BUBBLE_RADIUS_DP * density
    val out = Bitmap.createBitmap((w + shadow * 2).roundToInt(), (h + tail + shadow * 2).roundToInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(shadow, shadow * 0.6f, shadow + w, shadow * 0.6f + h)
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(shadow * 0.9f, 0f, shadow * 0.4f, 0x66000000)
    }
    c.drawRoundRect(rect, r, r, frame)
    val tip = android.graphics.Path().apply {
        moveTo(rect.centerX() - tail * 0.8f, rect.bottom - 1f)
        lineTo(rect.centerX() + tail * 0.8f, rect.bottom - 1f)
        lineTo(rect.centerX(), rect.bottom + tail)
        close()
    }
    c.drawPath(tip, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    val inner = RectF(rect.left + border, rect.top + border, rect.right - border, rect.bottom - border)
    val innerR = max(0f, r - border)
    val scale = max(inner.width() / photo.width, inner.height() / photo.height)
    val m = Matrix().apply {
        setScale(scale, scale)
        postTranslate(inner.centerX() - photo.width * scale / 2, inner.centerY() - photo.height * scale / 2)
    }
    c.drawRoundRect(inner, innerR, innerR, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
    })
    if (spots.size > 1) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = BADGE_TEXT_SP * 1.2f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val text = "+${spots.size - 1}"
        val ch = BADGE_DP * 1.3f * density
        val cw = max(ch, tp.measureText(text) + ch * 0.7f)
        val chip = RectF(inner.right - cw - 8f * density, inner.top + 8f * density, inner.right - 8f * density, inner.top + 8f * density + ch)
        c.drawRoundRect(chip, ch / 2, ch / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() })
        val fm = tp.fontMetrics
        c.drawText(text, chip.centerX(), chip.centerY() - (fm.ascent + fm.descent) / 2, tp)
    }
    return out
}

/** 핀 몸통(둥근 네모) 자리. 그림 높이 = 그림자 + 몸통 + 꼬리 → 꼬리 끝이 그림 맨 아래 가운데 (icon-anchor = bottom). */
private fun pinBody(density: Float): RectF {
    val d = PIN_DP * density
    val shadow = SHADOW_DP * density
    return RectF(shadow, shadow, shadow + d, shadow + d)
}

private fun pinBitmap(density: Float): Bitmap {
    val d = PIN_DP * density
    val shadow = SHADOW_DP * density
    return Bitmap.createBitmap((d + shadow * 2).roundToInt(), (shadow + d + PIN_TAIL_DP * density).roundToInt(), Bitmap.Config.ARGB_8888)
}

private fun tailPath(rect: RectF, tail: Float) = android.graphics.Path().apply {
    moveTo(rect.centerX() - tail * 0.75f, rect.bottom - 1f)
    lineTo(rect.centerX() + tail * 0.75f, rect.bottom - 1f)
    lineTo(rect.centerX(), rect.bottom + tail)
    close()
}

/**
 * 사진 핀: 흰 테두리 둥근 네모 + 부드러운 그림자 + 아래 작은 꼬리(꼬리 끝 = 찍은 자리).
 * [photo]가 있으면 그 사진을 채워 넣고, 없으면 흰 바탕에 진한 카메라 그림.
 */
private fun photoPin(photo: Bitmap?, density: Float): Bitmap {
    val out = pinBitmap(density)
    val c = Canvas(out)
    val rect = pinBody(density)
    val shadow = SHADOW_DP * density
    val tail = PIN_TAIL_DP * density
    val r = PIN_RADIUS_DP * density
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(shadow * 0.8f, 0f, shadow * 0.3f, 0x59000000)
    }
    c.drawRoundRect(rect, r, r, white)
    c.drawPath(tailPath(rect, tail), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    val b = PIN_BORDER_DP * density
    val inner = RectF(rect.left + b, rect.top + b, rect.right - b, rect.bottom - b)
    val innerR = max(0f, r - b)
    if (photo != null) {
        val scale = max(inner.width() / photo.width, inner.height() / photo.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(inner.centerX() - photo.width * scale / 2, inner.centerY() - photo.height * scale / 2)
        }
        c.drawRoundRect(inner, innerR, innerR, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
        })
        return out
    }
    // 카메라: 둥근 몸통 + 위 돌기 + 렌즈 (선으로)
    val cx = rect.centerX()
    val cy = rect.centerY()
    val u = rect.width() / 24f
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF16191C.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * u
        strokeJoin = Paint.Join.ROUND
    }
    c.drawRoundRect(RectF(cx - 6.2f * u, cy - 3.6f * u, cx + 6.2f * u, cy + 5.2f * u), 1.6f * u, 1.6f * u, ink)
    c.drawLine(cx - 2.4f * u, cy - 3.6f * u, cx - 1.4f * u, cy - 5.4f * u, ink)
    c.drawLine(cx - 1.4f * u, cy - 5.4f * u, cx + 1.4f * u, cy - 5.4f * u, ink)
    c.drawLine(cx + 1.4f * u, cy - 5.4f * u, cx + 2.4f * u, cy - 3.6f * u, ink)
    c.drawCircle(cx, cy + 0.9f * u, 2.5f * u, ink)
    return out
}

/** 고른 핀 표시: 같은 모양의 파란(강조색) 테두리만 그린 그림. 핀 위에 겹쳐 그린다. */
private fun selectedRing(density: Float): Bitmap {
    val out = pinBitmap(density)
    val c = Canvas(out)
    val rect = pinBody(density)
    val w = PIN_BORDER_DP * density
    val half = w / 2
    val ring = RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
    val r = PIN_RADIUS_DP * density - half
    c.drawRoundRect(ring, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT
        style = Paint.Style.STROKE
        strokeWidth = w
    })
    c.drawPath(tailPath(rect, PIN_TAIL_DP * density), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
    return out
}

/** 묶인 핀 위 장수 배지 (파란 동그라미 + 흰 숫자, 흰 테두리). */
private fun countBadge(text: String, density: Float): Bitmap {
    val h = BADGE_DP * density
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = BADGE_TEXT_SP * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val w = max(h, tp.measureText(text) + h * 0.7f)
    val out = Bitmap.createBitmap(w.roundToInt() + 2, h.roundToInt() + 2, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(1f, 1f, w + 1f, h + 1f)
    c.drawRoundRect(rect, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    val b = 1.5f * density
    val inner = RectF(rect.left + b, rect.top + b, rect.right - b, rect.bottom - b)
    c.drawRoundRect(inner, inner.height() / 2, inner.height() / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
    val fm = tp.fontMetrics
    c.drawText(text, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, tp)
    return out
}

internal const val PIN = "gpin"
internal const val PIN_SEL = "gpinsel"
internal const val BUBBLE = "gpbubble"
internal const val THUMB_PREFIX = "gpth"
/** 사진 핀 한 변(dp, 흰 테두리 포함): 길을 가리지 않게 작게 */
internal const val PIN_DP = 40f
private const val PIN_BORDER_DP = 3f
private const val PIN_RADIUS_DP = 10f
/** 핀 아래 꼬리 (끝 = 찍은 자리) */
internal const val PIN_TAIL_DP = 7f
/** 꼬리 끝에서 핀 위끝까지 높이(dp) — 배지 · 말풍선 자리 */
internal const val PIN_HEIGHT_DP = PIN_DP + PIN_TAIL_DP
/** 사진 말풍선 (dp) */
private const val BUBBLE_W_DP = 248f
private const val BUBBLE_H_DP = 166f
private const val BUBBLE_BORDER_DP = 4f
private const val BUBBLE_RADIUS_DP = 12f
internal const val BUBBLE_TAIL_DP = 12f
private const val BUBBLE_MAX_MAP_FRACTION = 0.55f
private const val BUBBLE_MIN_SCALE = 0.6f
/** 강조색 (ui/theme/Color.kt accent와 같은 값) */
private const val ACCENT = 0xFF0064FF.toInt()
internal const val BADGE_PREFIX = "gpn"
/** 이보다 많이 묶이면 "N+"로 */
internal const val MAX_BADGE = 30

internal const val SHADOW_DP = 4f
private const val BADGE_DP = 20f
private const val BADGE_TEXT_SP = 12f
