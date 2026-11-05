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
import app.eodiyeotji.core.rust.DayEnrichment as RDayEnrichment
import app.eodiyeotji.core.rust.DayFacts as RDayFacts
import app.eodiyeotji.core.rust.Episode as REpisode
import app.eodiyeotji.core.rust.EpisodeFacts as REpisodeFacts
import app.eodiyeotji.core.rust.EpisodeKind as REpisodeKind
import app.eodiyeotji.core.rust.EpisodeLabel as REpisodeLabel
import app.eodiyeotji.core.rust.MapBounds as RMapBounds
import app.eodiyeotji.core.rust.MemoryQuery as RMemoryQuery
import app.eodiyeotji.core.rust.StopFact as RStopFact
import app.eodiyeotji.core.rust.NaviHintKind as RNaviHintKind
import app.eodiyeotji.core.rust.NaviPlaceHint as RNaviPlaceHint
import app.eodiyeotji.core.rust.PlaceLabel as RPlaceLabel
import app.eodiyeotji.core.rust.PlaceName as RPlaceName
import app.eodiyeotji.core.rust.PlaceSource as RPlaceSource
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
        DayMapData(
            day = m.day,
            geojson = m.geojson,
            bounds = m.bounds?.toModel(),
            entryBounds = m.entries.associate { it.key to it.bounds.toModel() },
        )
    }

    // ---- 장소 이름 (core-rs/src/places.rs) ----

    /** 이름이 필요한 지점 (최신순, 가까운 지점은 Rust가 하나로 묶어 준다). */
    suspend fun pendingPlaces(limit: Int = 20): List<PlaceQuery> = withContext(Dispatchers.IO) {
        store.pendingPlaces(limit.toUInt()).map { PlaceQuery(it.id, it.lat, it.lon, it.isStay) }
    }

    /** 찾은 이름 저장. 더 높은 출처(Navi > Kakao > Osm) 이름이 이미 있으면 Rust가 무시한다. */
    suspend fun setPlaceName(id: Long, label: PlaceLabel) = withContext(Dispatchers.IO) {
        store.setPlaceName(RPlaceName(id, label.toRust()))
        _changes.tryEmit(Unit)
    }

    /** 이름을 못 찾음 (네트워크 없음·결과 없음). [retryAfterMs] 뒤에 다시 pendingPlaces에 나온다. */
    suspend fun markPlaceUnresolved(id: Long, retryAfterMs: Long) = withContext(Dispatchers.IO) {
        store.markPlaceUnresolved(id, retryAfterMs)
    }

    /** 차량 내비 목적지·경유지 힌트. Rust가 가까운 머문 곳·도착 지점의 이름으로 쓴다 (출처 Navi). */
    suspend fun addNaviHint(hint: NaviHint) = withContext(Dispatchers.IO) {
        store.addNaviHint(
            RNaviPlaceHint(hint.name, hint.lat, hint.lon, hint.tMs, if (hint.isWaypoint) RNaviHintKind.WAYPOINT else RNaviHintKind.DESTINATION, hint.address)
        )
        _changes.tryEmit(Unit)
    }

    // ---- 기억: 에피소드·AI 라벨·검색·코스 (core-rs/src/memory.rs, docs/70_기억_검색_설계.md) ----

    /** 집·회사 위치 (내비 즐겨찾기 HOME/OFFICE). 모르면 null → Rust가 기록에서 추정. */
    suspend fun setHomeOffice(home: LatLon?, office: LatLon?) = withContext(Dispatchers.IO) {
        store.setHomeOffice(home?.let { RGeoPoint(it.lat, it.lon) }, office?.let { RGeoPoint(it.lat, it.lon) })
    }

    /** LLM 라벨링이 필요한 날 (최신순). */
    suspend fun pendingDayEnrichments(limit: Int = 5): List<DayFacts> = withContext(Dispatchers.IO) {
        store.pendingDayEnrichments(limit.toUInt()).map { it.toModel() }
    }

    suspend fun setDayEnrichment(e: DayEnrichment) = withContext(Dispatchers.IO) {
        store.setDayEnrichment(
            RDayEnrichment(e.day, e.title, e.summary, e.tags, e.episodes.map { REpisodeLabel(it.episodeId, it.title, it.tags) })
        )
        _changes.tryEmit(Unit)
    }

    suspend fun markDayEnrichmentFailed(day: String, retryAfterMs: Long) = withContext(Dispatchers.IO) {
        store.markDayEnrichmentFailed(day, retryAfterMs)
    }

    suspend fun episodesForDay(day: String): List<Episode> = withContext(Dispatchers.IO) {
        store.episodesForDay(day).map { it.toModel() }
    }

    /** 기억 검색 (기기 안). */
    suspend fun searchMemories(q: MemoryQuery): List<MemoryHit> = withContext(Dispatchers.IO) {
        store.searchMemories(RMemoryQuery(q.terms, q.fromMs, q.toMs, q.kinds.map { it.toRust() }, q.limit.toUInt()))
            .map { MemoryHit(it.episode.toModel(), it.dayTitle, it.score, it.bestStop?.toModel()) }
    }

    /** 코스 다시 달리기 경로 (optimize면 from에서 가장 짧은 순서). 내비 경유지 2개 제한에 맞춘 구간 포함. */
    suspend fun courseRoute(episodeId: Long, from: LatLon?, optimize: Boolean): CourseRoute = withContext(Dispatchers.Default) {
        val r = store.courseRoute(episodeId, from?.let { RGeoPoint(it.lat, it.lon) }, optimize)
        CourseRoute(
            stops = r.stops.map { it.toModel() },
            legs = r.legs.map { CourseLeg(it.waypoints.map { w -> w.toModel() }, it.destination.toModel()) },
            distanceM = r.distanceM,
        )
    }

    /** 시연용 1년치 기록 만들기 (디버그 빌드에서만 부른다). */
    suspend fun seedDemoData(todayMs: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        store.seedDemoData(todayMs).toInt().also { _changes.tryEmit(Unit) }
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
    /** 그날 이름 있는 머문 곳 앞 3개 (도착 순서). 예: ["경포해변", "초당순두부마을", "안목해변"] */
    val stopNames: List<String> = emptyList(),
    /** 하루 제목 (LLM, 예: "비 오는 날 강릉 순두부 원정"; 평범한 날은 규칙 제목). 아직 없으면 null. */
    val title: String? = null,
    val tags: List<String> = emptyList(),
)

// ---- 기억 모델 (core-rs/src/memory.rs와 같은 의미) ----

enum class EpisodeKind { COMMUTE_TO_WORK, COMMUTE_HOME, ERRAND, OUTING, TRIP }

data class StopFact(
    val name: String, val category: String, val address: String,
    val arriveMs: Long, val leaveMs: Long, val lat: Double, val lon: Double, val firstVisit: Boolean,
)

data class EpisodeFacts(
    val id: Long, val day: String, val kind: EpisodeKind, val ruleLabel: String,
    val startMs: Long, val endMs: Long, val distanceM: Double,
    val fromName: String, val toName: String, val stops: List<StopFact>, val region: String,
)

data class DayFacts(val day: String, val weekday: String, val episodes: List<EpisodeFacts>, val totalDistanceM: Double)

data class EpisodeLabel(val episodeId: Long, val title: String, val tags: List<String>)

data class DayEnrichment(val day: String, val title: String, val summary: String, val tags: List<String>, val episodes: List<EpisodeLabel>)

data class Episode(val facts: EpisodeFacts, val title: String?, val tags: List<String>)

data class MemoryQuery(
    val terms: List<String>,
    val fromMs: Long? = null,
    val toMs: Long? = null,
    val kinds: List<EpisodeKind> = emptyList(),
    val limit: Int = 10,
)

data class MemoryHit(val episode: Episode, val dayTitle: String?, val score: Double, val bestStop: StopFact?)

data class CourseLeg(val waypoints: List<StopFact>, val destination: StopFact)

data class CourseRoute(val stops: List<StopFact>, val legs: List<CourseLeg>, val distanceM: Double)

data class TripSummary(
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val start: LatLon,
    val end: LatLon,
    val path: List<LatLon>,
    /** 출발·도착 장소 이름 (아직 모르면 null) */
    val startPlace: PlaceLabel? = null,
    val endPlace: PlaceLabel? = null,
)

/** 장소 이름 출처. 우선순위 NAVI > KAKAO > OSM. */
enum class PlaceSource { NAVI, KAKAO, OSM }

/** 화면·지도에 붙는 장소 이름. category·address는 모르면 빈 문자열. */
data class PlaceLabel(val name: String, val category: String, val address: String, val source: PlaceSource)

/** 이름이 필요한 지점. [id]는 답할 때 그대로 돌려준다. [isStay]면 가게·건물 이름을 우선 찾는다. */
data class PlaceQuery(val id: Long, val lat: Double, val lon: Double, val isStay: Boolean)

/** 차량 내비 목적지·경유지 힌트. */
data class NaviHint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val tMs: Long,
    val isWaypoint: Boolean,
    val address: String = "",
)

/** 머문 곳. 하루 마지막 도착지처럼 아직 떠나지 않았으면 leaveMs == arriveMs ([isOpen]). [place]는 아직 모르면 null. */
data class StaySummary(
    val center: LatLon,
    val arriveMs: Long,
    val leaveMs: Long,
    val place: PlaceLabel? = null,
    /** 그날 머문 곳 중 몇 번째인지 (1부터, 지도 핀 번호와 같다). */
    val order: Int = 0,
) {
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

/**
 * 하루치 지도 데이터 (Rust가 만든 GeoJSON 문자열 + 범위, 그릴 것이 없으면 bounds = null).
 * [entryBounds]: 타임라인 항목 키 → 그 항목의 범위 (항목을 누르면 지도를 옮길 때 쓴다).
 */
data class DayMapData(
    val day: String,
    val geojson: String,
    val bounds: MapBoundsLatLon?,
    val entryBounds: Map<String, MapBoundsLatLon> = emptyMap(),
)

private fun RGeoPoint.toModel() = LatLon(lat, lon)

private fun RMapBounds.toModel() = MapBoundsLatLon(minLat, minLon, maxLat, maxLon)

private fun RDaySummary.toModel() = DaySummary(
    day = day,
    tripCount = tripCount.toInt(),
    stayCount = stayCount.toInt(),
    distanceM = distanceM,
    driveMillis = driveMillis,
    firstMs = firstMs,
    lastMs = lastMs,
    stopNames = stopNames,
    title = title,
    tags = tags,
)

private fun RStopFact.toModel() = StopFact(name, category, address, arriveMs, leaveMs, lat, lon, firstVisit)

private fun REpisodeKind.toModel() = when (this) {
    REpisodeKind.COMMUTE_TO_WORK -> EpisodeKind.COMMUTE_TO_WORK
    REpisodeKind.COMMUTE_HOME -> EpisodeKind.COMMUTE_HOME
    REpisodeKind.ERRAND -> EpisodeKind.ERRAND
    REpisodeKind.OUTING -> EpisodeKind.OUTING
    REpisodeKind.TRIP -> EpisodeKind.TRIP
}

private fun EpisodeKind.toRust() = when (this) {
    EpisodeKind.COMMUTE_TO_WORK -> REpisodeKind.COMMUTE_TO_WORK
    EpisodeKind.COMMUTE_HOME -> REpisodeKind.COMMUTE_HOME
    EpisodeKind.ERRAND -> REpisodeKind.ERRAND
    EpisodeKind.OUTING -> REpisodeKind.OUTING
    EpisodeKind.TRIP -> REpisodeKind.TRIP
}

private fun REpisodeFacts.toModel() = EpisodeFacts(
    id, day, kind.toModel(), ruleLabel, startMs, endMs, distanceM, fromName, toName, stops.map { it.toModel() }, region,
)

private fun RDayFacts.toModel() = DayFacts(day, weekday, episodes.map { it.toModel() }, totalDistanceM)

private fun REpisode.toModel() = Episode(facts.toModel(), title, tags)

private fun RTripSummary.toModel() = TripSummary(
    startMs, endMs, distanceM, start.toModel(), end.toModel(), path.map { it.toModel() },
    startPlace?.toModel(), endPlace?.toModel(),
)

private fun RStaySummary.toModel() = StaySummary(center.toModel(), arriveMs, leaveMs, place?.toModel(), order.toInt())

private fun RPlaceLabel.toModel() = PlaceLabel(
    name, category, address,
    when (source) {
        RPlaceSource.NAVI -> PlaceSource.NAVI
        RPlaceSource.KAKAO -> PlaceSource.KAKAO
        RPlaceSource.OSM -> PlaceSource.OSM
    },
)

private fun PlaceLabel.toRust() = RPlaceLabel(
    name, category, address,
    when (source) {
        PlaceSource.NAVI -> RPlaceSource.NAVI
        PlaceSource.KAKAO -> RPlaceSource.KAKAO
        PlaceSource.OSM -> RPlaceSource.OSM
    },
)

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
