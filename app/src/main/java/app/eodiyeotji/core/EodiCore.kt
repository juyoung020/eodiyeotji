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
import app.eodiyeotji.core.rust.DaySummary as RDaySummary
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint

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

data class StaySummary(val center: LatLon, val arriveMs: Long, val leaveMs: Long)

data class DayDetail(val day: String, val trips: List<TripSummary>, val stays: List<StaySummary>)

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

private fun RDayDetail.toModel() = DayDetail(
    day = day,
    trips = trips.map {
        TripSummary(it.startMs, it.endMs, it.distanceM, it.start.toModel(), it.end.toModel(), it.path.map { p -> p.toModel() })
    },
    stays = stays.map { StaySummary(it.center.toModel(), it.arriveMs, it.leaveMs) },
)
