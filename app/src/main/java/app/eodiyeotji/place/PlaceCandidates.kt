package app.eodiyeotji.place

import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.PlaceCandidate
import app.eodiyeotji.core.PlaceLabel
import app.eodiyeotji.core.PlaceSource
import app.eodiyeotji.core.rankCandidates
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * "이 장소 이름 고치기"에 보여 줄 근처 이름 후보 (카카오 로컬 분류 검색, 반경 [RADIUS_M] m).
 * 분류마다 한 번씩 한꺼번에 묻고 [TIMEOUT_MS] 안에 온 것만 쓴다. 고르기(거리순 · 같은 이름 하나)는 Rust.
 * 카카오 키가 없거나 네트워크가 안 되면 빈 목록 → 화면은 직접 입력만 보여 준다.
 */
@Singleton
class PlaceCandidates @Inject constructor() {
    private val key = BuildConfig.KAKAO_REST_API_KEY
    private val http: HttpGetter = PlatformHttp

    val available: Boolean get() = key.isNotBlank()

    suspend fun near(lat: Double, lon: Double): List<PlaceLabel> {
        if (!available) return emptyList()
        val headers = mapOf("Authorization" to "KakaoAK $key")
        // 막는 HTTP는 떼어 놓고 기다림만 [TIMEOUT_MS]에서 끊는다 (오프라인 DNS 대기로 이름 고치기 창이 멈추지 않게)
        val found: List<PlaceCandidate> = coroutineScope {
            CODES.map { code ->
                async {
                    try {
                        blockingWithTimeout(TIMEOUT_MS) { KakaoParse.places(http.get(url(code, lat, lon), headers)) }
                            .orEmpty()
                            .map { PlaceCandidate(it.label.copy(source = PlaceSource.KAKAO), it.distanceM) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "후보 찾기 실패 ($code): ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        return rankCandidates(found, LIMIT)
    }

    private fun url(code: String, lat: Double, lon: Double) =
        "${KakaoLocalProvider.BASE}/v2/local/search/category.json?category_group_code=$code" +
            "&x=${coord(lon)}&y=${coord(lat)}&radius=$RADIUS_M&sort=distance&size=5"

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.6f", v)

    private companion object {
        const val TAG = "EodiPlaceCandidates"
        const val RADIUS_M = 150
        const val TIMEOUT_MS = 3_000L
        const val LIMIT = 8

        /** 목적지로 쓸 만한 분류 (음식점 · 카페 · 관광명소 · 문화시설 · 숙박 · 마트 · 병원 · 학교 · 편의점 · 주차장 · 주유소 · 은행) */
        val CODES = listOf("FD6", "CE7", "AT4", "CT1", "AD5", "MT1", "HP8", "SC4", "CS2", "PK6", "OL7", "BK9")
    }
}
