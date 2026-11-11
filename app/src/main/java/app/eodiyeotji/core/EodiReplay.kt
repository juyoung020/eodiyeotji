package app.eodiyeotji.core

import androidx.compose.runtime.Immutable
import app.eodiyeotji.core.rust.DayReplay as RDayReplay
import app.eodiyeotji.core.rust.SkyPhase as RSkyPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 그날로 돌아가기 (core-rs/src/replay.rs). 장면 고르기 · 글 · 지도 그리기 단계(GeoJSON)는 Rust가 만들고, 여기서는 옮겨 담기만 한다.
 */

/** 장면 하나 = 들른 곳 하나. */
@Immutable
data class ReplayScene(
    val stop: StopFact,
    val order: Int,
    /** "09:51" */
    val timeText: String,
    /** "1시간 52분 머묾" (없으면 빈 글) */
    val stayText: String,
    /** 이 장면으로 오는 구간 전까지 지나온 길 + 들른 곳 */
    val doneGeojson: String,
    /** 이 장면으로 오는 구간을 조금씩 그리는 단계 (선 + 차) */
    val legFrames: List<String>,
    /** 이 구간을 따라가는 3D 카메라 비행 (구간이 없으면 빈 목록) */
    val legFlight: List<FlightFrame>,
    /** 도착 시각의 하늘 때 (Rust가 현지 시각으로 정한다) */
    val sky: SkyPhase,
    /** 이 곳의 사진을 띄울지 (집 · 회사 · 회사 건물이면 false → 지도만, Rust가 정한다) */
    val showPhoto: Boolean,
)

/** 하늘 때: 3D 지도 색 · 하늘 그라데이션을 고른다. */
enum class SkyPhase { DAWN, DAY, GOLDEN, BLUE, NIGHT }

@Immutable
data class DayReplay(
    val day: String,
    val dateText: String,
    val yearsAgo: Int,
    /** "1년 전 오늘" (1년이 안 됐으면 빈 글) */
    val stamp: String,
    val title: String,
    /** "가을 · 강원 강릉시" */
    val subtitle: String,
    val scenes: List<ReplayScene>,
    val finalGeojson: String,
    val bounds: MapBoundsLatLon?,
    /** "그날, 우리는 384km를 달렸어요" */
    val summaryLine: String,
    /** "들른 곳 5곳 · 운전 4시간 12분" */
    val statsLine: String,
    val distanceM: Double,
    val episodeId: Long?,
    /** 끝 화면 "그 코스로 안내"를 보일지 (대표 기록이 있고 출퇴근길이 아님, Rust가 정한다) */
    val canGuide: Boolean,
    /** 시작 · 끝 화면 하늘 (마지막 들른 곳의 때) */
    val sky: SkyPhase,
)

/** 그날 다시 보기 재료. */
suspend fun EodiCore.dayReplay(day: String, nowMs: Long = System.currentTimeMillis()): DayReplay = withContext(Dispatchers.IO) {
    rust.dayReplay(day, nowMs).toModel()
}

private fun RDayReplay.toModel() = DayReplay(
    day, dateText, yearsAgo.toInt(), stamp, title, subtitle,
    scenes.map { ReplayScene(it.stop.toModel(), it.order.toInt(), it.timeText, it.stayText, it.doneGeojson, it.legFrames, it.legFlight.map { f -> FlightFrame(f.lat, f.lon, f.bearing, f.zoom) }, it.sky.toModel(), it.showPhoto) },
    finalGeojson,
    bounds?.let { MapBoundsLatLon(it.minLat, it.minLon, it.maxLat, it.maxLon) },
    summaryLine, statsLine, distanceM, episodeId, canGuide, sky.toModel(),
)

private fun RSkyPhase.toModel() = when (this) {
    RSkyPhase.DAWN -> SkyPhase.DAWN
    RSkyPhase.DAY -> SkyPhase.DAY
    RSkyPhase.GOLDEN -> SkyPhase.GOLDEN
    RSkyPhase.BLUE -> SkyPhase.BLUE
    RSkyPhase.NIGHT -> SkyPhase.NIGHT
}
