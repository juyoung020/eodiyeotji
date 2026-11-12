package app.eodiyeotji.gallery

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.provider.MediaStore
import android.util.Log
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.StaySummary
import app.eodiyeotji.place.PlacePhotoRepository
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.SuccessResult
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [디버그 빌드 전용] 시연용 "내 사진"을 기기 갤러리(DCIM/Camera)에 넣는다. 에뮬레이터 갤러리는 비어 있어서
 * 하루 상세의 사진 카드 · 올리기 창의 사진 고르기를 보여 주려면 사진이 있어야 한다.
 *
 * 그날(기본 2025-11-07, 1년 전 강릉 여행) 실제 기록에서 머문 곳을 읽어, 곳마다 머문 시각 안에 찍고(EXIF DateTimeOriginal)
 * 그 곳 근처 위치(EXIF GPS)가 박힌 사진을 만든다. 그림은 앱이 이미 받아 둔 그곳 장소 사진(구글, 기기 캐시)을 쓰고,
 * 못 받으면 단색 그라데이션 + 장소 이름·시각 글자로 대신한다 (저장소에 바이너리 없음).
 * 곳마다 한 장은 위치 없이 넣어 "시각만으로 맞추기"도 보인다. 첫 주행 중에 찍은 사진도 한 장(드라이브 코스 후보).
 * 다시 보내면 예전 시연 사진(`eodi_demo_*`)을 지우고 새로 넣는다.
 *
 * ```
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.gallery.GalleryDemoReceiver [--es day 2025-11-07]
 * ```
 * 결과는 logcat 태그 EodiDemo.
 */
@AndroidEntryPoint
class GalleryDemoReceiver : BroadcastReceiver() {
    @Inject lateinit var core: EodiCore
    @Inject lateinit var places: PlacePhotoRepository

    override fun onReceive(context: Context, intent: Intent) {
        val day = intent.getStringExtra("day") ?: DEFAULT_DAY
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val removed = clearOld(context)
                val detail = core.dayDetail(day)
                // 마지막 머문 곳은 집(귀가)이라 시연 사진을 넣지 않는다
                val stays = detail.stays.dropLast(1).filter { it.place?.name?.isNotBlank() == true }
                var n = 0
                // 곳마다 남은 장소 사진 (지도 말풍선이 첫 사진을 쓰므로 내 사진은 둘째부터, 같은 사진을 두 번 쓰지 않는다)
                val spare = HashMap<Long, List<coil.request.ImageRequest>>()
                stays.forEachIndexed { si, stay ->
                    val label = stay.place!!
                    val all = runCatching { places.photos(label.name, label.address, stay.center.lat, stay.center.lon) }.getOrDefault(emptyList())
                    val shots = (if (all.size > 1) all.drop(1) else all).map { places.imageRequest(it) }
                    val want = if (si == 1) 5 else 2
                    // 장소 사진이 모자라면 그만큼만 (똑같은 사진이 여러 장 꽂혀 가짜처럼 보이지 않게). 하나도 없으면 단색 그림 한 장
                    // 둘째 곳은 길 위 사진에 쓸 한 장을 남겨 둔다
                    val keep = if (si == 1 && shots.size > 2) 1 else 0
                    val count = if (shots.isEmpty()) 1 else want.coerceAtMost(shots.size - keep)
                    repeat(count) { k ->
                        val real = shots.getOrNull(k)?.let { load(context, it) }
                        val t = shotTime(stay, k, count)
                        // 그곳 둘레에 흩어 찍은 자리 (마지막 한 장은 위치 없이 → 시각만으로 맞추는 경우)
                        val gps = if (count > 1 && k == count - 1) null else spread(stay.center.lat, stay.center.lon, k)
                        insert(context, "eodi_demo_${stay.arriveMs}_$k", label.name, t, gps, si * 3 + k, real)
                        n++
                    }
                    spare[stay.arriveMs] = shots.drop(count)
                }
                // 달리는 길 위에서 찍은 사진: 둘째 곳으로 가는 주행의 끝 무렵(바다가 보이기 시작할 때), 그곳의 남은 사진으로
                stays.getOrNull(1)?.let { dest ->
                    val trip = detail.trips.lastOrNull { it.endMs <= dest.arriveMs && it.path.size > 4 } ?: return@let
                    val at = trip.path[(trip.path.size * 0.8).toInt().coerceAtMost(trip.path.lastIndex)]
                    val t = trip.startMs + (trip.endMs - trip.startMs) * 8 / 10
                    // 남은 사진이 없으면 길 위 사진은 넣지 않는다 (단색 그림이 지도 대표 카드가 되지 않게)
                    val real = spare[dest.arriveMs]?.firstOrNull()?.let { load(context, it) } ?: return@let
                    insert(context, "eodi_demo_drive_${trip.startMs}", "가는 길", t, at.lat to at.lon, 11, real)
                    n++
                }
                Log.i(TAG, "시연 사진: $day 머문 곳 ${stays.size}곳에 ${n}장 넣음 (예전 것 ${removed}장 지움)")
            } catch (e: Exception) {
                Log.w(TAG, "시연 사진 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** 머문 곳 둘레에 흩어 찍은 자리 (약 40~120 m, 장마다 다른 방향). */
    private fun spread(lat: Double, lon: Double, k: Int): Pair<Double, Double> {
        val angle = k * 2.4
        val r = 0.0004 + 0.0003 * (k % 3)
        return (lat + r * kotlin.math.sin(angle)) to (lon + r * 1.25 * kotlin.math.cos(angle))
    }

    /** 머문 시간을 고르게 나눈 시각 (떠나기 전까지). */
    private fun shotTime(s: StaySummary, k: Int, count: Int): Long {
        val end = if (s.leaveMs > s.arriveMs) s.leaveMs else s.arriveMs + 30 * 60_000L
        return s.arriveMs + (end - s.arriveMs) * (k + 1) / (count + 1)
    }

    private fun clearOld(context: Context): Int = context.contentResolver.delete(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
        arrayOf("eodi_demo_%"),
    )

    /** 화면과 같은 기기 캐시에서 장소 사진을 비트맵으로 (없으면 내려받음). 실패하면 null. */
    private suspend fun load(context: Context, req: coil.request.ImageRequest): Bitmap? {
        val r = context.imageLoader.execute(req.newBuilder(context).allowHardware(false).build())
        return ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    }

    private fun insert(context: Context, name: String, title: String, takenMs: Long, gps: Pair<Double, Double>?, tone: Int, real: Bitmap? = null) {
        val file = File(context.cacheDir, "$name.jpg")
        try {
            val bmp = real?.copy(Bitmap.Config.ARGB_8888, false) ?: picture(title, takenMs, tone)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            ExifInterface(file.absolutePath).apply {
                val local = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(takenMs))
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, local)
                setAttribute(ExifInterface.TAG_DATETIME, local)
                setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetText(takenMs))
                setAttribute(ExifInterface.TAG_MAKE, "Eodiyeotji Demo")
                gps?.let { (lat, lon) ->
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE, dms(lat))
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dms(lon))
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0) "E" else "W")
                }
                saveAttributes()
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                put(MediaStore.Images.Media.DATE_TAKEN, takenMs)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
                put(MediaStore.Images.Media.DATE_TAKEN, takenMs)
            }, null, null)
        } finally {
            file.delete()
        }
    }

    /** 단색 그라데이션 + 장소 이름 · 찍은 시각. */
    private fun picture(title: String, takenMs: Long, tone: Int): Bitmap {
        val w = 1280
        val h = 960
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val (a, b) = TONES[tone % TONES.size]
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a, b, Shader.TileMode.CLAMP)
        })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = 104f
            setShadowLayer(10f, 0f, 4f, 0x66000000)
        }
        canvas.drawText(title, 72f, h - 220f, text)
        text.textSize = 56f
        text.typeface = Typeface.DEFAULT
        canvas.drawText(SimpleDateFormat("yyyy.M.d HH:mm", Locale.KOREA).format(Date(takenMs)), 76f, h - 130f, text)
        return bmp
    }

    /** EXIF GPS 값 "도/1,분/1,초×1000/1000". */
    private fun dms(v: Double): String {
        val a = kotlin.math.abs(v)
        val d = a.toInt()
        val m = ((a - d) * 60).toInt()
        val s = ((a - d - m / 60.0) * 3600 * 1000).toLong()
        return "$d/1,$m/1,$s/1000"
    }

    private fun offsetText(t: Long): String {
        val m = TimeZone.getDefault().getOffset(t) / 60_000
        return "%s%02d:%02d".format(if (m >= 0) "+" else "-", kotlin.math.abs(m) / 60, kotlin.math.abs(m) % 60)
    }

    private companion object {
        const val TAG = "EodiDemo"
        const val DEFAULT_DAY = "2025-11-07"
        val TONES = listOf(
            0xFF1E88E5.toInt() to 0xFF80DEEA.toInt(), // 바다
            0xFFFF8F00.toInt() to 0xFFFFE082.toInt(), // 노을
            0xFF2E7D32.toInt() to 0xFFA5D6A7.toInt(), // 숲
            0xFF6A1B9A.toInt() to 0xFFF48FB1.toInt(), // 저녁
            0xFF5D4037.toInt() to 0xFFD7CCC8.toInt(), // 카페
        )
    }
}
