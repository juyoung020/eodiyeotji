package app.eodiyeotji.core

import android.content.Context
import app.eodiyeotji.core.rust.DriveEvent
import app.eodiyeotji.core.rust.EodiStore
import app.eodiyeotji.core.rust.GeoFix
import app.eodiyeotji.core.rust.coreSelfCheck
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import app.eodiyeotji.core.rust.DayDetail as RDayDetail
import app.eodiyeotji.core.rust.DayEntry as RDayEntry
import app.eodiyeotji.core.rust.DaySummary as RDaySummary
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint
import app.eodiyeotji.core.rust.StaySummary as RStaySummary
import app.eodiyeotji.core.rust.TripSummary as RTripSummary

/**
 * Rust 코어(eodi-core) 입구. 앱의 다른 코드는 Rust 바인딩(app.eodiyeotji.core.rust)을 직접 부르지 않고 여기를 거친다.
 *
 * - 모든 호출은 Dispatchers.Default(계산) / IO(DB)에서 한다 → UI 스레드를 막지 않는다.
 * - 여기서는 값 옮겨 담기만 한다. 계산·정렬·필터는 Rust에서 끝내고 온다.
 */
@Singleton
class EodiCore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: EodiStore by lazy {
        val db = File(context.filesDir, "eodi.db").absolutePath
        val tzMinutes = TimeZone.getDefault().rawOffset / 60_000
        EodiStore.open(db, tzMinutes)
    }

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 기록이 바뀔 때마다(ingest·주행 신호) 한 번씩 알린다. 타임라인 화면이 이걸 보고 다시 읽는다. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun selfCheck(): CoreStatus = withContext(Dispatchers.Default) {
        val c = coreSelfCheck()
        CoreStatus(
            version = c.version,
            pointsIn = c.pointsIn.toInt(),
            pointsOut = c.pointsOut.toInt(),
            elapsedMicros = c.elapsedMicros.toLong(),
        )
    }

    /** 위치 묶음 저장. 반환: 실제 저장된 점 수. */
    suspend fun ingest(fixes: List<LocationFix>): Int = withContext(Dispatchers.IO) {
        val stored = store.ingest(fixes.map { GeoFix(it.lat, it.lon, it.tMs, it.accuracyM) }).toInt()
        _changes.tryEmit(Unit)
        stored
    }

    suspend fun markDriveStart(tMs: Long) = driveEvent(DriveEvent.START, tMs)
    suspend fun markDriveEnd(tMs: Long) = driveEvent(DriveEvent.END, tMs)

    private suspend fun driveEvent(e: DriveEvent, tMs: Long) = withContext(Dispatchers.IO) {
        store.markDriveEvent(e, tMs)
        _changes.tryEmit(Unit)
    }

    /** 기록이 있는 날, 최신순. */
    suspend fun daySummaries(limit: Int = 400): List<DaySummary> = withContext(Dispatchers.IO) {
        store.daySummaries(limit.toUInt()).map { it.toModel() }
    }

    /** 하루 상세 ("YYYY-MM-DD"). */
    suspend fun dayDetail(day: String): DayDetail = withContext(Dispatchers.IO) {
        store.dayDetail(day).toModel()
    }

    /**
     * 하루치 지도 데이터. [DayMapData.geojson]은 MapLibre GeoJsonSource에 그대로 넣는다
     * (주행 LineString·머문 곳 Point, properties.kind = "trip"|"stay", properties.key = 타임라인 항목 키).
     */
    suspend fun dayMap(day: String): DayMapData = withContext(Dispatchers.IO) {
        val m = store.dayMap(day)
        DayMapData(m.day, m.geojson, m.bounds?.let { MapBoundsLatLon(it.minLat, it.minLon, it.maxLat, it.maxLon) })
    }

    suspend fun fixCount(): Long = withContext(Dispatchers.IO) { store.fixCount().toLong() }
}

// ---- 화면·서비스에 넘기는 불변 값 (Rust 바인딩의 record는 var라서 그대로 쓰지 않는다) ----

data class CoreStatus(
    val version: String,
    val pointsIn: Int,
    val pointsOut: Int,
    val elapsedMicros: Long,
)

data class LocationFix(val lat: Double, val lon: Double, val tMs: Long, val accuracyM: Float)

data class LatLon(val lat: Double, val lon: Double)

data class DaySummary(
    val day: String,
    val tripCount: Int,
    val stayCount: Int,
    val distanceM: Double,
    val driveMillis: Long,
    val firstMs: Long,
    val lastMs: Long,
)

data class TripSummary(
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val start: LatLon,
    val end: LatLon,
    val path: List<LatLon>,
)

/** 머문 곳. 하루 마지막 도착지처럼 아직 떠나지 않았으면 leaveMs == arriveMs ([isOpen]). */
data class StaySummary(val center: LatLon, val arriveMs: Long, val leaveMs: Long) {
    val isOpen: Boolean get() = leaveMs == arriveMs
}

/** 하루 상세 한 줄. Rust가 시간순으로 섞어서 준다. */
sealed interface DayEntry {
    data class Trip(val trip: TripSummary) : DayEntry
    data class Stay(val stay: StaySummary) : DayEntry
}

/** 하루 상세. [entries]는 주행·머문 곳을 시간순으로 섞은 목록, [trips]·[stays]는 종류별 목록(지도용). */
data class DayDetail(
    val day: String,
    val summary: DaySummary,
    val entries: List<DayEntry>,
    val trips: List<TripSummary>,
    val stays: List<StaySummary>,
)

/** 지도 화면 맞춤 범위. */
data class MapBoundsLatLon(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double)

/** 하루치 지도 데이터 (Rust가 만든 GeoJSON 문자열 + 범위, 그릴 것이 없으면 bounds = null). */
data class DayMapData(val day: String, val geojson: String, val bounds: MapBoundsLatLon?)

private fun RGeoPoint.toModel() = LatLon(lat, lon)

private fun RDaySummary.toModel() = DaySummary(
    day = day,
    tripCount = tripCount.toInt(),
    stayCount = stayCount.toInt(),
    distanceM = distanceM,
    driveMillis = driveMillis,
    firstMs = firstMs,
    lastMs = lastMs,
)

private fun RTripSummary.toModel() =
    TripSummary(startMs, endMs, distanceM, start.toModel(), end.toModel(), path.map { it.toModel() })

private fun RStaySummary.toModel() = StaySummary(center.toModel(), arriveMs, leaveMs)

private fun RDayDetail.toModel() = DayDetail(
    day = day,
    summary = summary.toModel(),
    entries = entries.map {
        when (it) {
            is RDayEntry.Trip -> DayEntry.Trip(it.trip.toModel())
            is RDayEntry.Stay -> DayEntry.Stay(it.stay.toModel())
        }
    },
    trips = trips.map { it.toModel() },
    stays = stays.map { it.toModel() },
)
