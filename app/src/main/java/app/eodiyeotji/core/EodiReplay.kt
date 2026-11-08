package app.eodiyeotji.core

import androidx.compose.runtime.Immutable
import app.eodiyeotji.core.rust.DayReplay as RDayReplay
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
)

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
)

/** 그날 다시 보기 재료. */
suspend fun EodiCore.dayReplay(day: String, nowMs: Long = System.currentTimeMillis()): DayReplay = withContext(Dispatchers.IO) {
    rust.dayReplay(day, nowMs).toModel()
}

private fun RDayReplay.toModel() = DayReplay(
    day, dateText, yearsAgo.toInt(), stamp, title, subtitle,
    scenes.map { ReplayScene(it.stop.toModel(), it.order.toInt(), it.timeText, it.stayText, it.doneGeojson, it.legFrames, it.legFlight.map { f -> FlightFrame(f.lat, f.lon, f.bearing, f.zoom) }) },
    finalGeojson,
    bounds?.let { MapBoundsLatLon(it.minLat, it.minLon, it.maxLat, it.maxLon) },
    summaryLine, statsLine, distanceM, episodeId,
)
