package app.eodiyeotji.core

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 기억 지도 입구 (core-rs/src/memmap.rs). 경로 줄이기 · 자주 간 곳 · 합계는 Rust가 하고, 여기서는 옮겨 담기만 한다.
 */

/** 기억 지도 데이터. GeoJSON은 MapLibre 소스에 그대로 넣는다. */
@Immutable
data class MemoryMap(
    /** 주행 LineString, properties `m` = 달 번호 ([months] 인덱스) */
    val routesGeojson: String,
    /** 자주 간 곳 Point, properties `n`(날 수) · `w`(0~1 밝기) · `m`(처음 간 달) */
    val visitsGeojson: String,
    /** 많이 간 곳 빛기둥 (팔각형, fill-extrusion) */
    val pillarsGeojson: String,
    val bounds: MapBoundsLatLon?,
    /** "2025년 11월" … */
    val months: List<String>,
    val distanceKm: Int,
    val driveCount: Int,
    val driveDays: Int,
    /** 360일 이상이면 해 수, 아니면 0 */
    val spanYears: Int,
    val spanMonths: Int,
)

/** 기억 지도 ([from] ~ [to], 양 끝 포함). 기본 = 오늘까지 1년. */
suspend fun EodiCore.memoryMap(
    to: LocalDate = LocalDate.now(),
    from: LocalDate = to.minusYears(1).plusDays(1),
): MemoryMap = withContext(Dispatchers.IO) {
    val m = rust.memoryMap(from.toString(), to.toString())
    MemoryMap(
        routesGeojson = m.routesGeojson,
        visitsGeojson = m.visitsGeojson,
        pillarsGeojson = m.pillarsGeojson,
        bounds = m.bounds?.let { MapBoundsLatLon(it.minLat, it.minLon, it.maxLat, it.maxLon) },
        months = m.months,
        distanceKm = m.distanceKm.toInt(),
        driveCount = m.driveCount.toInt(),
        driveDays = m.driveDays.toInt(),
        spanYears = m.spanYears.toInt(),
        spanMonths = m.spanMonths.toInt(),
    )
}
