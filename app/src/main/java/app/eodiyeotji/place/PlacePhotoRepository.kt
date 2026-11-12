package app.eodiyeotji.place

import android.content.Context
import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.PlacePhoto
import app.eodiyeotji.core.StopFact
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 들른 곳 대표 사진 — 구글 Places API (New). 구글 지도에서 가게를 찾으면 뜨는 그 사진들.
 *
 * - 장소 찾기: Text Search (`places:searchText`, 필드 `places.id,places.photos`)를 들른 곳 좌표 주변([BIAS_RADIUS_M])으로 좁혀 부른다.
 * - 저장: 결과(사진 이름·올린 사람)는 Rust가 기기 DB에 30일 둔다 → 같은 곳은 다시 묻지 않는다 ([EodiCore.placePhotos]).
 * - 이미지: [imageRequest]를 화면(Coil)이 받아 디스크에 둔다. 한 번 받은 사진은 그대로 다시 쓴다.
 *   구글 약관대로 사진 곁에 올린 사람 이름을 보여 준다.
 *
 * 키는 `local.properties`의 `GOOGLE_PLACES_API_KEY` (구글 클라우드 프로젝트 eodiyeotji, Places API (New)만 허용).
 * 키가 없거나 네트워크가 안 되면 빈 목록 — 화면은 사진 칸 없이 그대로 보인다.
 */
@Singleton
class PlacePhotoRepository @Inject constructor(
    private val core: EodiCore,
    @ApplicationContext private val context: Context,
) {

    suspend fun photos(stop: StopFact): List<PlacePhoto> = photos(stop.name, stop.address, stop.lat, stop.lon)

    /**
     * 이름·주소·좌표로 찾은 그곳 사진 (정차한 곳마다 부른다). 이름이 없으면(아직 장소를 못 찾음) 빈 목록.
     * 찾기는 화면과 따로 앱 범위에서 끝까지 한다: 화면을 넘겨도 찾던 결과가 버려지지 않고 저장돼 다음엔 바로 뜬다.
     * 같은 곳을 여러 화면(지도 카드·목록 사진)이 동시에 물으면 구글에는 한 번만 묻는다.
     */
    suspend fun photos(name: String, address: String, lat: Double, lon: Double): List<PlacePhoto> {
        if (!ApiAccess.googleAvailable || name.isBlank()) return emptyList()
        val id = String.format(Locale.ROOT, "%s@%.4f,%.4f", name, lat, lon)
        val job = inflight.getOrPut(id) {
            scope.async {
                runCatching {
                    core.placePhotos(name, address, lat, lon) { query -> search(query, lat, lon) }.photos
                }.onFailure { Log.w(TAG, "사진 못 받음: $name: ${it.message}") }.getOrDefault(emptyList())
            }.also { d -> d.invokeOnCompletion { inflight.remove(id, d) } }
        }
        return job.await()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inflight = ConcurrentHashMap<String, Deferred<List<PlacePhoto>>>()

    /**
     * 화면에 그릴 사진 요청 (Coil). 구글은 같은 사진도 물을 때마다 다른 사진 이름(주소)을 주므로, 기기 저장 이름표는
     * 바뀌지 않는 값(장소 id · 올린 사람 · 원본 크기)으로 붙인다 → 한 번 받은 사진은 장소를 다시 찾아도 그대로 쓴다.
     * 모든 화면이 같은 크기([PHOTO_PX]) 한 장을 같이 쓴다 (구글은 크기마다 따로 하루 한도를 센다). 작은 칸은 Coil이 줄여 그린다.
     */
    fun imageRequest(photo: PlacePhoto): ImageRequest {
        val id = "${photo.name.substringBefore("/photos/")}/${photo.author}/${photo.widthPx}x${photo.heightPx}"
        return ImageRequest.Builder(context)
            .data(ApiAccess.googleMediaUrl(photo.name, PHOTO_PX))
            .diskCacheKey("gphoto:$id:$PHOTO_PX")
            .memoryCacheKey("gphoto:$id:$PHOTO_PX")
            .build()
    }

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
            "${ApiAccess.googleBase}/places:searchText",
            body.toString(),
            ApiAccess.googleHeaders("places.id,places.photos"),
        )
    }

    private companion object {
        const val TAG = "EodiPhoto"
        const val BIAS_RADIUS_M = 300.0

        /** 새로 받을 때의 긴 변(px): 카드 위쪽 넓게 보기·공유 카드까지 충분한 크기. 모든 화면이 이 한 장을 같이 쓴다. */
        const val PHOTO_PX = 1080

    }
}
