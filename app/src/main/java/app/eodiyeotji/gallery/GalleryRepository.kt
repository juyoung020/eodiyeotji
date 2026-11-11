package app.eodiyeotji.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.GalleryItem
import app.eodiyeotji.core.MyPhoto
import app.eodiyeotji.core.StayMyPhotos
import app.eodiyeotji.core.galleryForDay
import app.eodiyeotji.core.galleryForTrip
import app.eodiyeotji.core.galleryWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 기기 갤러리(MediaStore) 사진 연동. 두 판(차·폰) 모두 Android 표준 MediaStore라 공통 코드다.
 *
 * - 읽기: 찍은 시각(DATE_TAKEN)이 범위 안인 사진만 묻고, 위치는 EXIF GPS에서 읽는다
 *   (Android 10+는 [Manifest.permission.ACCESS_MEDIA_LOCATION] + [MediaStore.setRequireOriginal]이 있어야 위치가 보인다).
 *   모두 IO 스레드. 범위는 Rust가 정하고([galleryWindow]), 맞추기도 Rust([galleryForDay]) — 여기서는 읽어서 넘기기만 한다.
 * - 권한이 없으면 빈 결과 → 화면은 사진 없이 그대로다 (조용히).
 * - 올리기: 사용자가 고른 사진만 앱 안 폴더에 줄여 다시 저장한다([copyForPost]). 다시 저장하면 EXIF(GPS·기기 정보)가 빠진다.
 */
@Singleton
class GalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
) {
    /** 사진 읽기 권한이 있는가 (Android 14의 "일부 사진만 허용"은 고른 사진만 보인다). */
    fun canRead(): Boolean = READ_PERMISSIONS.any { granted(it) }

    /** 그날 들른 곳마다 내 사진 (도착 시각 → 사진). 권한이 없거나 못 읽으면 빈 것. */
    suspend fun stayPhotos(day: String): Map<Long, StayMyPhotos> = guard("하루 사진 $day", emptyMap()) {
        if (!canRead()) return@guard emptyMap()
        val range = core.galleryWindow(day) ?: return@guard emptyMap()
        val items = read(range.first, range.last)
        if (items.isEmpty()) emptyMap() else core.galleryForDay(day, items)
    }

    /** 주행 하나 동안 찍은 사진 (드라이브 코스에 붙일 후보). */
    suspend fun tripPhotos(startMs: Long, endMs: Long): List<MyPhoto> = guard("주행 사진", emptyList()) {
        if (!canRead()) return@guard emptyList()
        val items = read(startMs - WINDOW_SLACK_MS, endMs + WINDOW_SLACK_MS)
        if (items.isEmpty()) emptyList() else core.galleryForTrip(startMs, endMs, items)
    }

    /**
     * 올린 글에 붙일 사본: 긴 변 [POST_MAX_PX]로 줄여 앱 안 `files/post_photos/[name].jpg`로 다시 저장한다 (EXIF 없음).
     * 실패하면 null (그 사진만 빠진다).
     */
    suspend fun copyForPost(uri: String, name: String): String? = guard("사진 사본", null) {
        val dir = File(context.filesDir, POST_DIR).apply { mkdirs() }
        val out = File(dir, "${name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.jpg")
        val bmp = decodeScaled(Uri.parse(uri), POST_MAX_PX) ?: return@guard null
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, POST_QUALITY, it) }
        bmp.recycle()
        out.absolutePath
    }

    private fun read(fromMs: Long, toMs: Long): List<GalleryItem> {
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
        val sel = "${MediaStore.Images.Media.DATE_TAKEN} BETWEEN ? AND ?"
        val args = arrayOf(fromMs.toString(), toMs.toString())
        val withLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_MEDIA_LOCATION)
        val out = ArrayList<GalleryItem>()
        context.contentResolver.query(base, projection, sel, args, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val tCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (c.moveToNext() && out.size < MAX_READ) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(base, id)
                val ll = if (withLocation) latLong(uri) else null
                out += GalleryItem(id, uri.toString(), c.getLong(tCol), ll?.get(0), ll?.get(1))
            }
        }
        return out
    }

    /** EXIF GPS (없거나 못 읽으면 null). 머리 부분만 읽는다. */
    private fun latLong(uri: Uri): DoubleArray? = runCatching {
        val src = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.setRequireOriginal(uri) else uri
        context.contentResolver.openInputStream(src)?.use { input ->
            val ll = FloatArray(2)
            if (ExifInterface(input).getLatLong(ll)) doubleArrayOf(ll[0].toDouble(), ll[1].toDouble()) else null
        }
    }.getOrNull()

    private fun decodeScaled(uri: Uri, maxPx: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val s = info.size
                val scale = maxPx.toFloat() / maxOf(s.width, s.height)
                if (scale < 1f) d.setTargetSize((s.width * scale).toInt().coerceAtLeast(1), (s.height * scale).toInt().coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private suspend fun <T> guard(what: String, fallback: T, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what 실패", e)
            fallback
        }
    }

    companion object {
        private const val TAG = "EodiGallery"
        private const val POST_DIR = "post_photos"
        private const val POST_MAX_PX = 1280
        private const val POST_QUALITY = 85
        /** 한 번에 읽는 사진 수 상한 (하루 범위 안, 최신부터). */
        private const val MAX_READ = 400
        /** 주행 사진: Rust 여유(10분)보다 조금 넓게 읽는다. */
        private const val WINDOW_SLACK_MS = 15 * 60_000L
        const val THUMB_PX = 192

        /** 이 기기에서 물을 사진 권한 (위치 EXIF 권한은 Android 10+에서 같이). */
        val REQUEST_PERMISSIONS: Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.READ_MEDIA_IMAGES)
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }.toTypedArray()

        private val READ_PERMISSIONS: List<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.READ_MEDIA_IMAGES)
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        }
    }
}
