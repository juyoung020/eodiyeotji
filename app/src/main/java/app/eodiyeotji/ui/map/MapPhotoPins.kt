package app.eodiyeotji.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Immutable
import app.eodiyeotji.place.PlacePhotoRepository
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 지도 위 정차한 곳 사진 상자 하나. [key]는 지도 GeoJSON의 머문 곳 키(`s<도착 ms>`)와 같아야 그 핀 위에 뜬다.
 */
@Immutable
data class MapPhotoPin(val key: String, val name: String, val address: String, val lat: Double, val lon: Double)

/**
 * 정차한 곳마다 대표 사진(구글 Places 첫 사진)을 받아 지도 아이콘(말풍선 모양 네모 상자)으로 만든다.
 * 사진이 없는 곳은 빠진다. 같은 곳은 기기에 저장된 결과·이미지 캐시를 쓴다.
 */
internal suspend fun loadPhotoPinIcons(
    context: Context,
    repo: PlacePhotoRepository,
    pins: List<MapPhotoPin>,
    density: Float,
): Map<String, Bitmap> = coroutineScope {
    val boxPx = (BOX_DP * density).roundToInt()
    pins.map { pin ->
        async {
            val photo = repo.photos(pin.name, pin.address, pin.lat, pin.lon).firstOrNull() ?: return@async null
            val req = ImageRequest.Builder(context)
                .data(repo.imageUrl(photo, PHOTO_PX))
                .size(boxPx)
                .allowHardware(false) // Canvas로 다시 그리려면 소프트웨어 비트맵이어야 한다
                .build()
            val bmp = ((context.imageLoader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                ?: return@async null
            pin.key to framed(bmp, density)
        }
    }.awaitAll().filterNotNull().toMap()
}

/** 흰 테두리 둥근 네모 + 아래로 뾰족한 꼬리(핀을 가리킨다) 안에 사진을 꽉 채워 그린다. */
private fun framed(src: Bitmap, density: Float): Bitmap {
    val box = BOX_DP * density
    val border = BORDER_DP * density
    val radius = RADIUS_DP * density
    val tail = TAIL_DP * density
    val out = Bitmap.createBitmap(box.roundToInt(), (box + tail).roundToInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    // 그림자 살짝, 테두리, 꼬리
    c.drawRoundRect(RectF(0f, density, box, box), radius, radius, shadow)
    c.drawRoundRect(RectF(0f, 0f, box, box - density), radius, radius, white)
    val mid = box / 2
    c.drawPath(Path().apply { moveTo(mid - tail, box - 2 * density); lineTo(mid + tail, box - 2 * density); lineTo(mid, box + tail); close() }, white)
    // 사진: 가운데를 잘라 꽉 채움 (centerCrop)
    val inner = RectF(border, border, box - border, box - border - density)
    val scale = max(inner.width() / src.width, inner.height() / src.height)
    val m = Matrix().apply {
        setScale(scale, scale)
        postTranslate(inner.centerX() - src.width * scale / 2, inner.centerY() - src.height * scale / 2)
    }
    val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
    }
    c.drawRoundRect(inner, radius - border, radius - border, photo)
    return out
}

/** 사진 상자 크기(dp) — 차 화면에서 알아볼 만큼, 하루 몇 곳이 겹치지 않을 만큼. */
private const val BOX_DP = 64f
private const val BORDER_DP = 3f
private const val RADIUS_DP = 12f
private const val TAIL_DP = 8f
private const val PHOTO_PX = 240

/** 꼬리 끝이 핀 위쪽에 닿도록 올리는 거리(dp): 핀 반지름 + 여유. */
internal const val PHOTO_PIN_LIFT_DP = 16f
