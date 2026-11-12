package app.eodiyeotji.place

import app.eodiyeotji.BuildConfig
import java.util.Locale

/**
 * 카카오 · 구글을 어떻게 부를지 한곳에서 정한다.
 * - 중계 서버 주소(`RELAY_URL`)가 있으면 그 서버에 묻는다 (`server/relay`). 키는 서버에만 있고 APK에는 없다.
 * - 없으면 `local.properties`의 키로 직접 묻는다 (개발용).
 */
object ApiAccess {
    private val relay = BuildConfig.RELAY_URL.trimEnd('/')
    private val viaRelay = relay.isNotEmpty()
    private val relayHeaders = mapOf(APP_HEADER to BuildConfig.RELAY_TOKEN)

    /** 카카오 로컬 · 길찾기를 쓸 수 있나 (중계 또는 키) */
    val kakaoAvailable: Boolean = viaRelay || BuildConfig.KAKAO_REST_API_KEY.isNotBlank()

    /** 구글 Places 사진을 쓸 수 있나 (중계 또는 키) */
    val googleAvailable: Boolean = viaRelay || BuildConfig.GOOGLE_PLACES_API_KEY.isNotBlank()

    /** 카카오 로컬 주소 바탕 (`/v2/local/...` 앞) */
    val kakaoBase: String = if (viaRelay) "$relay/kakao" else KAKAO

    val kakaoHeaders: Map<String, String> =
        if (viaRelay) relayHeaders else mapOf("Authorization" to "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")

    /** Rust가 만든 카카오모빌리티 길찾기 주소를 중계로 돌린다 (직접이면 그대로). */
    fun kakaoNavi(url: String): String = if (viaRelay) url.replace(KAKAO_NAVI, "$relay/kakaonavi") else url

    /** 구글 Places (New) 주소 바탕 (`/places:searchText` 앞) */
    val googleBase: String = if (viaRelay) "$relay/google/v1" else GOOGLE

    fun googleHeaders(fieldMask: String): Map<String, String> =
        (if (viaRelay) relayHeaders else mapOf("X-Goog-Api-Key" to BuildConfig.GOOGLE_PLACES_API_KEY)) + ("X-Goog-FieldMask" to fieldMask)

    /** 구글 사진 한 장 주소 (중계면 서버가 키를 붙이고 30일 캐시한다). 이미지 로더는 머리글을 못 붙이는 곳도 있어 확인 값은 주소에 둔다. */
    fun googleMediaUrl(photoName: String, px: Int): String =
        if (viaRelay) {
            String.format(Locale.ROOT, "%s/%s/media?maxWidthPx=%d&maxHeightPx=%d&app=%s", googleBase, photoName, px, px, BuildConfig.RELAY_TOKEN)
        } else {
            String.format(Locale.ROOT, "%s/%s/media?maxWidthPx=%d&maxHeightPx=%d&key=%s", GOOGLE, photoName, px, px, BuildConfig.GOOGLE_PLACES_API_KEY)
        }

    private const val APP_HEADER = "X-Eodi-App"
    private const val KAKAO = "https://dapi.kakao.com"
    private const val KAKAO_NAVI = "https://apis-navi.kakaomobility.com"
    private const val GOOGLE = "https://places.googleapis.com/v1"
}
