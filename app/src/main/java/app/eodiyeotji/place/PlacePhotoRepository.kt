package app.eodiyeotji.place

import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.PlacePhoto
import app.eodiyeotji.core.StopFact
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 들른 곳 대표 사진 — 구글 Places API (New). 구글 지도에서 가게를 찾으면 뜨는 그 사진들.
 *
 * - 장소 찾기: Text Search (`places:searchText`, 필드 `places.id,places.photos`)를 들른 곳 좌표 주변([BIAS_RADIUS_M])으로 좁혀 부른다.
 * - 저장: 결과(사진 이름·올린 사람)는 Rust가 기기 DB에 30일 둔다 → 같은 곳은 다시 묻지 않는다 ([EodiCore.placePhotos]).
 * - 이미지: [imageUrl]을 화면(Coil)이 받아 디스크에 캐시한다. 구글 약관대로 사진 곁에 올린 사람 이름을 보여 준다.
 *
 * 키는 `local.properties`의 `GOOGLE_PLACES_API_KEY` (구글 클라우드 프로젝트 eodiyeotji, Places API (New)만 허용).
 * 키가 없거나 네트워크가 안 되면 빈 목록 — 화면은 사진 칸 없이 그대로 보인다.
 */
@Singleton
class PlacePhotoRepository @Inject constructor(private val core: EodiCore) {
    private val key = BuildConfig.GOOGLE_PLACES_API_KEY

    suspend fun photos(stop: StopFact): List<PlacePhoto> {
        if (key.isBlank() || stop.name.isBlank()) return emptyList()
        return runCatching {
            core.placePhotos(stop.name, stop.address, stop.lat, stop.lon) { query -> search(query, stop.lat, stop.lon) }.photos
        }.onFailure { Log.w(TAG, "사진 못 받음: ${stop.name}: ${it.message}") }.getOrDefault(emptyList())
    }

    /** 화면에 그릴 이미지 주소 (구글이 실제 이미지 주소로 넘겨 준다). [maxPx]는 긴 변 기준. */
    fun imageUrl(photo: PlacePhoto, maxPx: Int): String =
        String.format(Locale.ROOT, "%s/%s/media?maxWidthPx=%d&maxHeightPx=%d&key=%s", BASE, photo.name, maxPx, maxPx, key)

    private suspend fun search(query: String, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("textQuery", query)
            .put("languageCode", "ko")
            .put("regionCode", "KR")
            .put("pageSize", 1)
            .put(
                "locationBias",
                JSONObject().put(
                    "circle",
                    JSONObject().put("center", JSONObject().put("latitude", lat).put("longitude", lon)).put("radius", BIAS_RADIUS_M),
                ),
            )
        PlatformHttp.postJson(
            "$BASE/places:searchText",
            body.toString(),
            mapOf("X-Goog-Api-Key" to key, "X-Goog-FieldMask" to "places.id,places.photos"),
        )
    }

    private companion object {
        const val TAG = "EodiPhoto"
        const val BASE = "https://places.googleapis.com/v1"
        const val BIAS_RADIUS_M = 300.0
    }
}
