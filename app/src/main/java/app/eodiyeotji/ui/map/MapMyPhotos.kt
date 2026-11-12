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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/*
 * 하루 지도 위 "내 사진" 카드: 갤러리 사진을 찍은 곳에 작은 사진 카드로 꽂는다.
 * 자리는 Rust(`gallery::map_spots`)가 정하고, 여기서는 사진을 줄여 카드 그림으로 그리고 GeoJSON으로 옮기기만 한다.
 * 가까운 사진끼리는 지도(MapLibre 묶기)가 한 장으로 묶고, 묶인 카드에는 장수 배지가 붙는다.
 */

/** 지도에 얹을 내 사진 그림: 카드 `gp<번호>` + 장수 배지 `gpn<수>`. [geojson]은 사진 자리(번호 = 목록 순서). */
internal class MyPhotoIcons(val geojson: String, val images: Map<String, Bitmap>, val maxBadge: Int)

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

/** 사진을 줄여 받아 카드 그림으로 (못 읽은 사진은 빈 회색 카드). 배경 스레드에서 부른다. */
internal suspend fun buildMyPhotoIcons(context: Context, spots: List<MapPhotoSpot>, density: Float): MyPhotoIcons? {
    if (spots.isEmpty()) return null
    val px = (CARD_DP * density * 1.5f).roundToInt()
    val gate = Semaphore(DECODE_PARALLEL)
    val bitmaps = coroutineScope {
        spots.map { s -> async { gate.withPermit { decodeThumb(context, s.uri, px) } } }.awaitAll()
    }
    val images = HashMap<String, Bitmap>()
    bitmaps.forEachIndexed { i, b -> images["$CARD_PREFIX$i"] = photoCard(b, density) }
    val maxBadge = spots.size.coerceAtMost(MAX_BADGE)
    for (n in 2..maxBadge) images["$BADGE_PREFIX$n"] = countBadge(if (n == MAX_BADGE && spots.size > MAX_BADGE) "$MAX_BADGE+" else "$n", density)
    return MyPhotoIcons(myPhotoGeoJson(spots), images, maxBadge)
}

private suspend fun decodeThumb(context: Context, uri: String, px: Int): Bitmap? {
    val req = ImageRequest.Builder(context).data(uri).size(px).allowHardware(false).build()
    val r = context.imageLoader.execute(req)
    return ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
}

/** 흰 테두리 · 둥근 모서리 · 그림자 사진 카드 (가운데를 잘라 꽉 채움). */
private fun photoCard(photo: Bitmap?, density: Float): Bitmap {
    val card = CARD_DP * density
    val border = BORDER_DP * density
    val shadow = SHADOW_DP * density
    val r = RADIUS_DP * density
    val size = (card + shadow * 2).roundToInt()
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(shadow, shadow * 0.6f, shadow + card, shadow * 0.6f + card)
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(shadow * 0.8f, 0f, shadow * 0.35f, 0x55000000)
    }
    c.drawRoundRect(rect, r, r, frame)
    val inner = RectF(rect.left + border, rect.top + border, rect.right - border, rect.bottom - border)
    val innerR = max(0f, r - border)
    if (photo != null) {
        val scale = max(inner.width() / photo.width, inner.height() / photo.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(inner.centerX() - photo.width * scale / 2, inner.centerY() - photo.height * scale / 2)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
        }
        c.drawRoundRect(inner, innerR, innerR, p)
    } else {
        c.drawRoundRect(inner, innerR, innerR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDE3EC.toInt() })
    }
    return out
}

/** 묶인 카드 위 장수 배지 (파란 동그라미 + 흰 숫자, 흰 테두리). */
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
    c.drawRoundRect(inner, inner.height() / 2, inner.height() / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0064FF.toInt() })
    val fm = tp.fontMetrics
    c.drawText(text, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, tp)
    return out
}

internal const val CARD_PREFIX = "gp"
internal const val BADGE_PREFIX = "gpn"
/** 이보다 많이 묶이면 "N+"로 */
internal const val MAX_BADGE = 30

/** 카드 크기(dp): 한곳에 여러 장이 꽂혀도 지도를 덮지 않게 작게 */
internal const val CARD_DP = 40f
private const val BORDER_DP = 2f
private const val RADIUS_DP = 8f
internal const val SHADOW_DP = 4f
private const val BADGE_DP = 20f
private const val BADGE_TEXT_SP = 12f
private const val DECODE_PARALLEL = 4
