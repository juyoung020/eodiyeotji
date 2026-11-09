package app.eodiyeotji.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.place.PlacePhotoRepository
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.eodiyeotji.core.rust.ShareCard as RShareCard
import app.eodiyeotji.core.rust.ShareKind as RShareKind

/*
 * 공유 이미지 카드의 재료 (core-rs/src/share.rs). 제목·날짜 글·계절·날씨·들른 곳·길 그림·거리·보낼 글은 모두 Rust가 만들고
 * (집·회사 근처 거르기 포함), 여기서는 옮겨 담고 카드에 넣을 사진만 받는다.
 */

/** 무엇을 공유하나. */
sealed interface ShareTarget {
    /** 하루 ("YYYY-MM-DD") */
    data class Day(val day: String) : ShareTarget

    /** 나들이·여행 한 번 (기억 에이전트 카드) */
    data class Episode(val episodeId: Long) : ShareTarget

    /** 커뮤니티 드라이브 코스 */
    data class Course(val postId: String) : ShareTarget
}

enum class ShareCardKind { DAY, EPISODE, COURSE }

/** 길 그림 칸 안 위치 (0~1, 북쪽이 위). */
@Immutable
data class SketchPos(val x: Float, val y: Float)

@Immutable
data class ShareCardStop(val order: Int, val name: String, val category: String, val address: String, val lat: Double, val lon: Double, val at: SketchPos)

/** 카드 한 장의 내용 (모두 Rust가 계산한 값). [lines]의 좌표는 [SKETCH_ASPECT] 칸에 맞춰져 있다. */
@Immutable
data class ShareCardData(
    val kind: ShareCardKind,
    val title: String,
    val day: String,
    val dateText: String,
    val season: String,
    val weather: String?,
    val region: String,
    val stops: List<ShareCardStop>,
    val lines: List<List<SketchPos>>,
    val straight: Boolean,
    val distanceM: Double,
    val durationMs: Long,
    val hiddenCount: Int,
    val shareText: String,
)

/** 카드의 길 그림 칸 가로/세로 (ShareCardView의 칸과 같은 값 — Rust가 이 비율에 맞춰 좌표를 준다). */
const val SKETCH_ASPECT = 320f / 150f

/** 카드에 넣는 사진 수 (첫 장은 크게, 나머지는 작게). */
const val CARD_PHOTOS = 3

suspend fun EodiCore.shareCard(target: ShareTarget): ShareCardData = shareCardRaw(target).toModel()

/** Rust 카드 그대로 (차 → 폰으로 보낼 때 묶어 보낸다). */
suspend fun EodiCore.shareCardRaw(target: ShareTarget): RShareCard = withContext(Dispatchers.IO) {
    val a = SKETCH_ASPECT.toDouble()
    when (target) {
        is ShareTarget.Day -> rust.shareCardDay(target.day, a)
        is ShareTarget.Episode -> rust.shareCardEpisode(target.episodeId, a)
        is ShareTarget.Course -> rust.shareCardCourse(target.postId, a)
    }
}

internal fun RShareCard.toModel() = ShareCardData(
    kind = when (kind) {
        RShareKind.DAY -> ShareCardKind.DAY
        RShareKind.EPISODE -> ShareCardKind.EPISODE
        RShareKind.COURSE -> ShareCardKind.COURSE
    },
    title = title,
    day = day,
    dateText = dateText,
    season = season,
    weather = weather,
    region = region,
    stops = stops.map { ShareCardStop(it.order.toInt(), it.name, it.category, it.address, it.lat, it.lon, SketchPos(it.at.x, it.at.y)) },
    lines = lines.map { l -> l.points.map { SketchPos(it.x, it.y) } },
    straight = straight,
    distanceM = distanceM,
    durationMs = durationMs,
    hiddenCount = hiddenCount.toInt(),
    shareText = shareText,
)

/** 공유 버튼(Compose 함수)에서 Hilt 싱글턴을 꺼낸다. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ShareEntryPoint {
    fun core(): EodiCore
    fun photos(): PlacePhotoRepository
    fun handoff(): app.eodiyeotji.handoff.HandoffRepository
}

/**
 * 카드 앞쪽 [CARD_PHOTOS]곳의 대표 사진 (순서 번호 → 사진). 구글 Places 요청을 아끼려고:
 * - 장소 → 사진 이름은 Rust가 기기에 저장해 둔 값을 쓴다 (같은 곳은 다시 묻지 않는다).
 * - 이미지는 기기에 받아 둔 사진을 먼저 쓴다 ([PlacePhotoRepository.imageRequest]). 없을 때만 한 장 받는다.
 * 사진이 없거나 못 받은 곳은 빠진다 (카드는 사진 칸 없이 그린다). 전체 [PHOTO_TIMEOUT_MS] 안에 끝낸다.
 */
internal suspend fun loadCardPhotos(context: Context, repo: PlacePhotoRepository, card: ShareCardData): Map<Int, ImageBitmap> =
    withTimeoutOrNull(PHOTO_TIMEOUT_MS) {
        coroutineScope {
            card.stops.take(CARD_PHOTOS).map { s ->
                async(Dispatchers.IO) {
                    val photo = repo.photos(s.name, s.address, s.lat, s.lon).firstOrNull() ?: return@async null
                    val bmp = fetch(context, repo.imageRequest(photo))
                    bmp?.let { s.order to it.asImageBitmap() }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    } ?: emptyMap()

private suspend fun fetch(context: Context, photo: ImageRequest): Bitmap? {
    val req = photo.newBuilder(context)
        .allowHardware(false) // 화면 밖에서 소프트웨어 Canvas로 그린다
        .build()
    return ((context.imageLoader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
}

private const val PHOTO_TIMEOUT_MS = 8_000L
