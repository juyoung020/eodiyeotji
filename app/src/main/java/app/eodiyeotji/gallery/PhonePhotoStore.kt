package app.eodiyeotji.gallery

import android.content.Context
import android.util.Log
import app.eodiyeotji.core.GalleryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 폰이 보내 준 사진을 차에 두는 곳 (앱 안 `files/phone_photos`). 차 화면에는 내 사진이 없으니, 연결된 폰이
 * 차로 다닌 날 찍은 사진을 긴 변 1920px 사본으로 보내 주면(`handoff.HandoffRepository`) 여기 쌓고,
 * [GalleryRepository]가 기기 갤러리 사진과 같이 읽는다 → 하루 지도 · 들른 곳 사진이 그대로 쓴다.
 *
 * 사진 정보(찍은 시각 · 위치)는 파일 이름에 둔다: `p_<찍은 시각 ms>_<위도×1e6|n>_<경도×1e6|n>.jpg`. 표를 따로 두지 않는다
 * (파일이 곧 원본이라 지우면 같이 사라진다). 같은 시각의 사진은 한 번만 둔다.
 *
 * 보내는 묶음 = [MARK] · 찍은 시각(i64) · 위도(f64, 없으면 NaN) · 경도(f64) · JPEG. 리틀 엔디언.
 */
@Singleton
class PhonePhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, DIR)

    /** 받은 사진이 하나라도 있는가. */
    fun any(): Boolean = dir.list()?.any { it.startsWith(PREFIX) } == true

    /** 찍은 시각이 범위 안인 받은 사진 (최신부터). [GalleryItem.uri]는 파일 경로, id는 음수(기기 갤러리 id와 겹치지 않게). */
    fun list(fromMs: Long, toMs: Long): List<GalleryItem> =
        dir.listFiles().orEmpty().mapNotNull { f ->
            val p = f.name.removeSuffix(".jpg").split('_')
            if (p.size != 4 || p[0] != "p") return@mapNotNull null
            val t = p[1].toLongOrNull()?.takeIf { it in fromMs..toMs } ?: return@mapNotNull null
            GalleryItem(-t, f.absolutePath, t, p[2].toLongOrNull()?.div(1e6), p[3].toLongOrNull()?.div(1e6))
        }.sortedByDescending { it.takenMs }

    /** 받은 묶음을 풀어 저장한다. 반환: 새로 저장했는가 (이미 있거나 묶음이 깨졌으면 false). */
    fun save(payload: ByteArray): Boolean {
        if (payload.size <= HEAD || payload[0] != MARK) return false
        val b = ByteBuffer.wrap(payload, 1, HEAD - 1).order(ByteOrder.LITTLE_ENDIAN)
        val t = b.long
        val (lat, lon) = b.double to b.double
        if (t <= 0 || dir.list()?.any { it.startsWith("$PREFIX${t}_") } == true) return false
        val part = { v: Double -> if (v.isNaN()) "n" else Math.round(v * 1e6).toString() }
        return runCatching {
            dir.mkdirs()
            File(dir, "$PREFIX${t}_${part(lat)}_${part(lon)}.jpg").outputStream().use { it.write(payload, HEAD, payload.size - HEAD) }
            true
        }.onFailure { Log.w(TAG, "받은 사진 저장 실패", it) }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "EodiGallery"
        private const val DIR = "phone_photos"
        private const val PREFIX = "p_"
        const val MARK: Byte = 'P'.code.toByte()
        private const val HEAD = 1 + 8 + 8 + 8

        /** 보내는 묶음 만들기 (폰). */
        fun pack(takenMs: Long, lat: Double?, lon: Double?, jpeg: ByteArray): ByteArray =
            ByteBuffer.allocate(HEAD + jpeg.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(MARK).putLong(takenMs).putDouble(lat ?: Double.NaN).putDouble(lon ?: Double.NaN).put(jpeg).array()
    }
}
