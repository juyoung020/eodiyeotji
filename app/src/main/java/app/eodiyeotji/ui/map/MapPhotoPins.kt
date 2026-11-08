package app.eodiyeotji.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Immutable
import app.eodiyeotji.place.PlacePhotoRepository
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 지도 위 정차한 곳 하나의 대표 사진을 찾을 정보. [key]는 지도 GeoJSON의 머문 곳 키(`s<도착 ms>`)와 같아야
 * 그 곳 말풍선 카드에 사진이 들어간다.
 */
@Immutable
data class MapPhotoPin(val key: String, val name: String, val address: String, val lat: Double, val lon: Double)

/**
 * 정차한 곳마다 대표 사진(구글 Places 첫 사진)을 받아 온다 (키 → 사진). 사진이 없는 곳은 빠진다.
 * 같은 곳은 기기에 저장된 결과·이미지 캐시를 쓴다. 카드 그림은 [buildMarkerIcons]가 이 사진으로 만든다.
 */
internal suspend fun loadStayPhotos(
    context: Context,
    repo: PlacePhotoRepository,
    pins: List<MapPhotoPin>,
    density: Float,
    /** 한 장 받을 때마다 바로 알린다 (화면이 오는 대로 채우게). */
    onPhoto: (key: String, bitmap: Bitmap) -> Unit = { _, _ -> },
): Map<String, Bitmap> = coroutineScope {
    val px = (PHOTO_DECODE_DP * density).roundToInt()
    pins.map { pin ->
        async {
            val photo = repo.photos(pin.name, pin.address, pin.lat, pin.lon).firstOrNull() ?: return@async null
            val req = repo.imageRequest(photo).newBuilder(context)
                .size(px)
                .allowHardware(false) // Canvas로 다시 그리려면 소프트웨어 비트맵이어야 한다
                .build()
            val result = context.imageLoader.execute(req)
            val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            if (bmp == null) {
                android.util.Log.w("EodiMapPhoto", "지도 사진 못 그림: ${pin.name} ${(result as? coil.request.ErrorResult)?.throwable} ${(result as? SuccessResult)?.drawable?.javaClass}")
                return@async null
            }
            onPhoto(pin.key, bmp)
            pin.key to bmp
        }
    }.awaitAll().filterNotNull().toMap()
}

/** 받아 둘 사진 크기: 카드 사진(68dp)을 가운데 잘라 꽉 채울 만큼. */
private const val PHOTO_DECODE_DP = 96f
