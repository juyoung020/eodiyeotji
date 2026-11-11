package app.eodiyeotji.core

import android.content.Context
import app.eodiyeotji.core.rust.DriveEvent
import app.eodiyeotji.core.rust.EodiStore
import app.eodiyeotji.core.rust.GeoFix
import app.eodiyeotji.core.rust.coreSelfCheck
import app.eodiyeotji.core.rust.parseGooglePlacePhotos
import app.eodiyeotji.core.rust.placePhotoKey
import app.eodiyeotji.core.rust.placePhotoQuery
import app.eodiyeotji.core.rust.PlacePhotos as RPlacePhotos
import app.eodiyeotji.core.rust.TimeOfDay as RTimeOfDay
import app.eodiyeotji.core.rust.WithWhom as RWithWhom
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import app.eodiyeotji.core.rust.CommunitySort as RCommunitySort
import app.eodiyeotji.core.rust.CoursePost as RCoursePost
import app.eodiyeotji.core.rust.NearFilter as RNearFilter
import app.eodiyeotji.core.rust.PlaceReview as RPlaceReview
import app.eodiyeotji.core.rust.PopularPlace as RPopularPlace
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
    /** Rust 저장소 (같은 파일의 확장 함수·기억 에이전트가 같은 연결을 쓰게). 앱 코드는 EodiCore 함수를 거친다. */
    internal val rust: EodiStore get() = store

    /** 다른 곳(에이전트 라벨 저장 등)에서 기록이 바뀌었을 때 화면을 다시 읽게 알린다. */
    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    private val store: EodiStore by lazy {
        val db = File(context.filesDir, "eodi.db").absolutePath
        val tzMinutes = TimeZone.getDefault().rawOffset / 60_000
        EodiStore.open(db, tzMinutes)
    }

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 기록이 바뀔 때마다(ingest·주행 신호) 한 번씩 알린다. 타임라인 화면이 이걸 보고 다시 읽는다. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun selfCheck(): CoreStatus = coreCall("selfCheck") {
        val c = coreSelfCheck()
        CoreStatus(
            version = c.version,
            pointsIn = c.pointsIn.toInt(),
            pointsOut = c.pointsOut.toInt(),
            elapsedMicros = c.elapsedMicros.toLong(),
        )
    }

    /** 위치 묶음 저장. 반환: 실제 저장된 점 수. */
    suspend fun ingest(fixes: List<LocationFix>): Int = coreCall("ingest") {
        val stored = store.ingest(fixes.map { GeoFix(it.lat, it.lon, it.tMs, it.accuracyM) }).toInt()
        _changes.tryEmit(Unit)
        stored
    }

    suspend fun markDriveStart(tMs: Long) = driveEvent(DriveEvent.START, tMs)
    suspend fun markDriveEnd(tMs: Long) = driveEvent(DriveEvent.END, tMs)

    private suspend fun driveEvent(e: DriveEvent, tMs: Long) = coreCall("driveEvent") {
        store.markDriveEvent(e, tMs)
        _changes.tryEmit(Unit)
    }

    /** 지금 주차 중이면 주차한 자리 (+ 이번에 가려던 곳). 달리는 중이면 null. */
    suspend fun parkedCar(): app.eodiyeotji.core.rust.ParkedCar? = coreCall("parkedCar") { store.parkedCar() }

    /** 지금 탄 인원 기록 (좌석 센서, 주행 중). */
    suspend fun setDrivePeople(tMs: Long, people: Int) = coreCall("setDrivePeople") { store.setDrivePeople(tMs, people.toUInt()) }

    /** 배터리 · 연료 잔량 기록 (출발 · 도착 때). */
    suspend fun setDriveEnergy(tMs: Long, pct: Double, ev: Boolean) = coreCall("setDriveEnergy") { store.setDriveEnergy(tMs, pct, ev) }

    /** 차 계기 기록 (주행거리계 · 와이퍼 · 남은 주행 가능 거리). */
    suspend fun setDriveReading(tMs: Long, kind: app.eodiyeotji.core.rust.ReadingKind, value: Double) =
        coreCall("setDriveReading") { store.setDriveReading(tMs, kind, value) }

    /** 그 달("YYYY-MM") 업무용 운행기록부. */
    suspend fun logbook(month: String): app.eodiyeotji.core.rust.Logbook = coreCall("logbook") { store.logbook(month) }

    /** 운행기록부 → 엑셀용 CSV 글 (UTF-8 BOM). */
    fun logbookCsv(book: app.eodiyeotji.core.rust.Logbook): String =
        app.eodiyeotji.core.rust.logbookCsv(book, java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000)

    /** 코스 · 리뷰가 있는 지역 (활동 많은 순, `tourism.rs`). */
    suspend fun tourismRegions(): List<app.eodiyeotji.core.rust.RegionActivity> = coreCall("tourismRegions") { store.tourismRegions() }

    /** 지역("강원 강릉시") 관광 제휴 리포트. */
    suspend fun tourismReport(region: String): app.eodiyeotji.core.rust.TourismReport = coreCall("tourismReport") { store.tourismReport(region) }

    /** 그 달("YYYY-MM") 출장 (평일 업무 시간에 집 · 회사에서 멀리 다녀온 나들이, `biztrip.rs`). */
    suspend fun bizTrips(month: String): app.eodiyeotji.core.rust.BizMonth = coreCall("bizTrips") { store.bizTrips(month) }

    /** 하루를 출장 / 출장 아님으로 정한다 (null = 자동 판정으로). */
    suspend fun setBizMark(day: String, mark: Boolean?) = coreCall("setBizMark") { store.setBizMark(day, mark) }

    /** 출장 정산서 → 엑셀용 CSV 글 (UTF-8 BOM). */
    fun bizReportCsv(month: app.eodiyeotji.core.rust.BizMonth): String =
        app.eodiyeotji.core.rust.bizReportCsv(month, java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000)

    /** 코스 길이 어림 (km): [from]에서 구간의 곳들을 차례로 지나는 직선거리 × 도로 우회율. */
    fun courseKm(from: LatLon?, legs: List<CourseLeg>): Double {
        fun s(f: StopFact) = RStopFact(f.name, f.category, f.address, f.arriveMs, f.leaveMs, f.lat, f.lon, f.firstVisit)
        return app.eodiyeotji.core.rust.courseKm(
            from?.let { RGeoPoint(it.lat, it.lon) },
            legs.map { app.eodiyeotji.core.rust.CourseLeg(it.waypoints.map(::s), s(it.destination)) },
        )
    }

    /** 남은 주행 가능 거리로 코스를 갈 수 있는가. */
    fun rangeCheck(rangeKm: Double, routeKm: Double, ev: Boolean): app.eodiyeotji.core.rust.RangeCheck =
        app.eodiyeotji.core.rust.rangeCheck(rangeKm, routeKm, ev)

    /** 그 하루 가장 많이 탄 인원 (모르면 0). */
    suspend fun dayPeople(day: String): Int = coreCall("dayPeople") { store.dayPeople(day).toInt() }

    /** 기록이 있는 날, 최신순. */
    suspend fun daySummaries(limit: Int = 400): List<DaySummary> = coreCall("daySummaries") {
        store.daySummaries(limit.toUInt()).map { it.toModel() }
    }

    /** 하루 상세 ("YYYY-MM-DD"). */
    suspend fun dayDetail(day: String): DayDetail = coreCall("dayDetail") {
        val d = store.dayDetail(day).toModel()
        val people = store.dayPeople(day).toInt()
        val e = store.dayEnergy(day)
        val rain = store.dayRain(day)
        d.copy(summary = d.summary.copy(people = people, rain = rain, energy = if (e.known) DayEnergyUse(e.ev, e.usedPct, e.refills.toInt()) else null))
    }

    /**
     * 하루치 지도 데이터. [DayMapData.geojson]은 MapLibre GeoJsonSource에 그대로 넣는다
     * (주행 LineString·머문 곳 Point, properties.kind = "trip"|"stay", properties.key = 타임라인 항목 키).
     */
    suspend fun dayMap(day: String): DayMapData = coreCall("dayMap") {
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
    suspend fun pendingPlaces(limit: Int = 20): List<PlaceQuery> = coreCall("pendingPlaces") {
        store.pendingPlaces(limit.toUInt()).map { PlaceQuery(it.id, it.lat, it.lon, it.isStay) }
    }

    /** 찾은 이름 저장. 더 높은 출처(Navi > Kakao > Osm) 이름이 이미 있으면 Rust가 무시한다. */
    suspend fun setPlaceName(id: Long, label: PlaceLabel) = coreCall("setPlaceName") {
        store.setPlaceName(RPlaceName(id, label.toRust()))
        _changes.tryEmit(Unit)
    }

    /** 이름을 못 찾음 (네트워크 없음·결과 없음). [retryAfterMs] 뒤에 다시 pendingPlaces에 나온다. */
    suspend fun markPlaceUnresolved(id: Long, retryAfterMs: Long) = coreCall("markPlaceUnresolved") {
        store.markPlaceUnresolved(id, retryAfterMs)
    }

    /** 차량 내비 목적지·경유지 힌트. Rust가 가까운 머문 곳·도착 지점의 이름으로 쓴다 (출처 Navi). */
    suspend fun addNaviHint(hint: NaviHint) = coreCall("addNaviHint") {
        store.addNaviHint(
            RNaviPlaceHint(hint.name, hint.lat, hint.lon, hint.tMs, if (hint.isWaypoint) RNaviHintKind.WAYPOINT else RNaviHintKind.DESTINATION, hint.address)
        )
        _changes.tryEmit(Unit)
    }

    // ---- 기억: 에피소드·AI 라벨·검색·코스 (core-rs/src/memory.rs, docs/70_기억_검색_설계.md) ----

    /** 집·회사 위치 (내비 즐겨찾기 HOME/OFFICE). 모르면 null → Rust가 기록에서 추정. */
    suspend fun setHomeOffice(home: LatLon?, office: LatLon?) = coreCall("setHomeOffice") {
        store.setHomeOffice(home?.let { RGeoPoint(it.lat, it.lon) }, office?.let { RGeoPoint(it.lat, it.lon) })
    }

    /** LLM 라벨링이 필요한 날 (최신순). */
    suspend fun pendingDayEnrichments(limit: Int = 5): List<DayFacts> = coreCall("pendingDayEnrichments") {
        store.pendingDayEnrichments(limit.toUInt()).map { it.toModel() }
    }

    suspend fun setDayEnrichment(e: DayEnrichment) = coreCall("setDayEnrichment") {
        store.setDayEnrichment(
            RDayEnrichment(e.day, e.title, e.summary, e.tags, e.episodes.map { REpisodeLabel(it.episodeId, it.title, it.tags) })
        )
        _changes.tryEmit(Unit)
    }

    suspend fun markDayEnrichmentFailed(day: String, retryAfterMs: Long) = coreCall("markDayEnrichmentFailed") {
        store.markDayEnrichmentFailed(day, retryAfterMs)
    }

    suspend fun episodesForDay(day: String): List<Episode> = coreCall("episodesForDay") {
        store.episodesForDay(day).map { it.toModel() }
    }

    /** 기억 검색 (기기 안). */
    suspend fun searchMemories(q: MemoryQuery): List<MemoryHit> = coreCall("searchMemories") {
        store.searchMemories(RMemoryQuery(q.terms, q.fromMs, q.toMs, q.kinds.map { it.toRust() }, q.limit.toUInt()))
            .map { MemoryHit(it.episode.toModel(), it.dayTitle, it.score, it.bestStop?.toModel(), it.matchedTags) }
    }

    /** 코스 다시 달리기 경로 (optimize면 from에서 가장 짧은 순서). 내비 경유지 2개 제한에 맞춘 구간 포함. */
    suspend fun courseRoute(episodeId: Long, from: LatLon?, optimize: Boolean): CourseRoute = coreCall("courseRoute") {
        val r = store.courseRoute(episodeId, from?.let { RGeoPoint(it.lat, it.lon) }, optimize)
        CourseRoute(
            stops = r.stops.map { it.toModel() },
            legs = r.legs.map { CourseLeg(it.waypoints.map { w -> w.toModel() }, it.destination.toModel()) },
            distanceM = r.distanceM,
        )
    }

    /** 시연용 1년치 기록 만들기 (디버그 빌드에서만 부른다). */
    suspend fun seedDemoData(todayMs: Long = System.currentTimeMillis()): Int = coreCall("seedDemoData") {
        store.seedDemoData(todayMs).toInt().also { _changes.tryEmit(Unit) }
    }

    suspend fun fixCount(): Long = coreCall("fixCount") { store.fixCount().toLong() }

    /**
     * 들른 곳 대표 사진 (구글 Places). 기기에 저장해 둔 것(30일 이내)이 있으면 그것을, 없으면 [fetch]로
     * 구글 Text Search 응답(JSON)을 받아 Rust가 골라 저장한 뒤 돌려준다. [fetch]에는 검색어가 넘어간다.
     * 키·검색어 만들기, 응답 고르기, 저장은 모두 Rust(`core-rs/src/photos.rs`).
     */
    suspend fun placePhotos(
        name: String, address: String, lat: Double, lon: Double,
        fetch: suspend (query: String) -> String,
    ): PlacePhotos {
        val key = placePhotoKey(name, lat, lon)
        coreCall("placePhotos") { store.cachedPlacePhotos(key, System.currentTimeMillis()) }?.let { return it.toModel() }
        val json = fetch(placePhotoQuery(name, address))
        return coreCall("placePhotos") {
            val parsed = parseGooglePlacePhotos(json)
            store.savePlacePhotos(key, parsed, System.currentTimeMillis())
            parsed.toModel()
        }
    }
}

// ---- 커뮤니티 · 여행 리뷰 (core-rs/src/community.rs). 순위·하트 집계는 Rust, 여기는 옮겨 담기만 ----

/** 커뮤니티 코스 피드. [near]가 있으면 그 근처 [radiusKm] 안만. */
suspend fun EodiCore.communityCourses(sort: CommunitySort, near: LatLon? = null, radiusKm: Double = 30.0, limit: Int = 50): List<CoursePost> =
    coreCall("communityCourses") { rust.communityCourses(sort.toRust(), near?.let { RNearFilter(RGeoPoint(it.lat, it.lon), radiusKm) }, limit.toUInt()).map { it.toModel() } }

/** 여행 리뷰 피드. */
suspend fun EodiCore.placeReviews(sort: CommunitySort, near: LatLon? = null, radiusKm: Double = 30.0, limit: Int = 50): List<PlaceReview> =
    coreCall("placeReviews") { rust.placeReviews(sort.toRust(), near?.let { RNearFilter(RGeoPoint(it.lat, it.lon), radiusKm) }, limit.toUInt()).map { it.toModel() } }

/** 인기 장소 순위. */
suspend fun EodiCore.popularPlaces(near: LatLon? = null, radiusKm: Double = 30.0, limit: Int = 30): List<PopularPlace> =
    coreCall("popularPlaces") {
        rust.popularPlaces(near?.let { RNearFilter(RGeoPoint(it.lat, it.lon), radiusKm) }, limit.toUInt())
            .map { PopularPlace(it.place.toModel(), it.hearts.toInt(), it.reviewCount.toInt(), it.avgRating, it.topKeyword) }
    }

/** 하트 누르기/취소 → 바뀐 하트 수. */
suspend fun EodiCore.toggleCourseHeart(id: String): Int = coreCall("toggleCourseHeart") { rust.toggleCourseHeart(id).toInt() }
suspend fun EodiCore.toggleReviewHeart(id: String): Int = coreCall("toggleReviewHeart") { rust.toggleReviewHeart(id).toInt() }

/** 내 나들이·여행 에피소드를 커뮤니티에 코스로 공유. */
suspend fun EodiCore.shareCourse(episodeId: Long, title: String, body: String): CoursePost =
    coreCall("shareCourse") { rust.shareCourse(episodeId, title, body, System.currentTimeMillis()).toModel() }

/** 들른 곳 리뷰 쓰기. */
suspend fun EodiCore.addPlaceReview(place: StopFact, rating: Int, text: String): PlaceReview =
    coreCall("addPlaceReview") {
        rust.addPlaceReview(
            RStopFact(place.name, place.category, place.address, place.arriveMs, place.leaveMs, place.lat, place.lon, place.firstVisit),
            rating.coerceIn(1, 5).toUByte(), text, System.currentTimeMillis(),
        ).toModel()
    }

/** 시연용 커뮤니티 데이터 (디버그 빌드에서만). */
suspend fun EodiCore.seedCommunityDemo(): Int = coreCall("seedCommunityDemo") { rust.seedCommunityDemo(System.currentTimeMillis()).toInt() }

enum class CommunitySort { POPULAR, RECENT }

data class CoursePost(
    val id: String, val title: String, val body: String, val author: String, val mine: Boolean, val region: String,
    val tags: List<String>, val stops: List<StopFact>, val distanceM: Double, val durationMs: Long, val createdMs: Long,
    val hearts: Int, val heartedByMe: Boolean,
    /** 다녀온 날(YYYY-MM-DD) · 계절("가을 · 단풍철") · 그날 날씨("맑음 · 14°C") */
    val visitedDay: String = "", val season: String = "", val weather: String? = null,
)

/**
 * 여행 리뷰 하나. [keywords] = 고른 키워드 ("뷰가 좋아요"), [withWhom] · [timeOfDay] = 누구와 · 다녀온 때 (모르면 null),
 * [stayMin] = 머문 분 (모르면 0), [visitNth] = 글쓴이의 몇 번째 방문 (모르면 0). 내 기록에서 채우는 값은 Rust가 정한다.
 */
data class PlaceReview(
    val id: String, val place: StopFact, val author: String, val mine: Boolean, val rating: Int, val text: String,
    val createdMs: Long, val hearts: Int, val heartedByMe: Boolean,
    val keywords: List<String> = emptyList(),
    val withWhom: WithWhom? = null,
    val timeOfDay: TimeOfDay? = null,
    val stayMin: Int = 0,
    val visitNth: Int = 0,
)

/** 누구와 갔는지 (리뷰 쓸 때 고른다). */
enum class WithWhom { ALONE, PARTNER, FAMILY, KIDS, FRIENDS, PET }

/** 다녀온 때 (도착 시각으로 Rust가 정한다). */
enum class TimeOfDay { MORNING, DAY, EVENING, NIGHT }

/** 인기 장소. [topKeyword] = 리뷰에서 가장 많이 고른 키워드 (없으면 null). */
data class PopularPlace(val place: StopFact, val hearts: Int, val reviewCount: Int, val avgRating: Float, val topKeyword: String? = null)

private fun CommunitySort.toRust() = when (this) {
    CommunitySort.POPULAR -> RCommunitySort.POPULAR
    CommunitySort.RECENT -> RCommunitySort.RECENT
}

internal fun RCoursePost.toModel() = CoursePost(
    id, title, body, author, mine, region, tags, stops.map { it.toModel() }, distanceM, durationMs, createdMs, hearts.toInt(), heartedByMe,
    visitedDay, season, weather,
)

internal fun RPlaceReview.toModel() = PlaceReview(
    id, place.toModel(), author, mine, rating.toInt(), text, createdMs, hearts.toInt(), heartedByMe,
    keywords = keywords,
    withWhom = withWhom?.toModel(),
    timeOfDay = timeOfDay?.toModel(),
    stayMin = stayMin.toInt(),
    visitNth = visitNth.toInt(),
)

private fun RWithWhom.toModel() = when (this) {
    RWithWhom.ALONE -> WithWhom.ALONE
    RWithWhom.PARTNER -> WithWhom.PARTNER
    RWithWhom.FAMILY -> WithWhom.FAMILY
    RWithWhom.KIDS -> WithWhom.KIDS
    RWithWhom.FRIENDS -> WithWhom.FRIENDS
    RWithWhom.PET -> WithWhom.PET
}

internal fun WithWhom.toR() = when (this) {
    WithWhom.ALONE -> RWithWhom.ALONE
    WithWhom.PARTNER -> RWithWhom.PARTNER
    WithWhom.FAMILY -> RWithWhom.FAMILY
    WithWhom.KIDS -> RWithWhom.KIDS
    WithWhom.FRIENDS -> RWithWhom.FRIENDS
    WithWhom.PET -> RWithWhom.PET
}

private fun RTimeOfDay.toModel() = when (this) {
    RTimeOfDay.MORNING -> TimeOfDay.MORNING
    RTimeOfDay.DAY -> TimeOfDay.DAY
    RTimeOfDay.EVENING -> TimeOfDay.EVENING
    RTimeOfDay.NIGHT -> TimeOfDay.NIGHT
}

private fun RPlacePhotos.toModel() =
    PlacePhotos(found, photos.map { PlacePhoto(it.name, it.widthPx.toInt(), it.heightPx.toInt(), it.author) })

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
    /** 타임라인 주행 줄 수 (짧은 주행 숨김 · 같은 길 반복 한 줄, Rust) */
    val tripCount: Int,
    /** 들른 곳 수 = 하루 출발 · 도착(대개 집)을 뺀 머문 곳 (Rust, 그날로 돌아가기와 같은 값) */
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
    /** 그날로 돌아가기로 다시 볼 것이 있다 (이름 있는 머문 곳 1곳 이상 또는 5km 이상, Rust `is_replayable`). */
    val replayable: Boolean = false,
    /** 그날 가장 많이 탄 인원 (좌석 센서, 하루 상세에서만 채운다). 모르면 0. */
    val people: Int = 0,
    /** 그날 쓴 배터리 · 연료 (하루 상세에서만, 모르면 null). */
    val energy: DayEnergyUse? = null,
    /** 그날 와이퍼가 돌았다 (비 온 날, 하루 상세에서만). */
    val rain: Boolean = false,
)

/** 하루에 쓴 배터리(전기차) 또는 연료: 쓴 양 %p, 충전 · 주유 횟수. */
data class DayEnergyUse(val ev: Boolean, val usedPct: Double, val refills: Int)

/** 들른 곳 사진 한 장 ([name] = 구글 사진 리소스 이름, [author] = 올린 사람 — 사진과 함께 보여 준다). */
data class PlacePhoto(val name: String, val widthPx: Int, val heightPx: Int, val author: String)

/** 한 장소의 사진들. [found]가 false면 구글에서 그 장소를 못 찾음. */
data class PlacePhotos(val found: Boolean, val photos: List<PlacePhoto>)

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

/** [matchedTags]: 질문과 맞은 AI 꼬리표 (LLM 하루 라벨링 태그, 최대 3개). */
data class MemoryHit(val episode: Episode, val dayTitle: String?, val score: Double, val bestStop: StopFact?, val matchedTags: List<String> = emptyList())

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

/** 장소 이름 출처. 우선순위 USER(사용자가 고친 이름) > NAVI > KAKAO > OSM. */
enum class PlaceSource { USER, NAVI, KAKAO, OSM }

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
    /** [repeat] = 같은 길을 이어서 달린 횟수 (Rust가 한 줄로 묶음, 1 = 한 번) */
    data class Trip(val trip: TripSummary, val repeat: Int = 1) : DayEntry
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
    replayable = replayable,
)

internal fun RStopFact.toModel() = StopFact(name, category, address, arriveMs, leaveMs, lat, lon, firstVisit)

private fun REpisodeKind.toModel() = when (this) {
    REpisodeKind.COMMUTE_TO_WORK -> EpisodeKind.COMMUTE_TO_WORK
    REpisodeKind.COMMUTE_HOME -> EpisodeKind.COMMUTE_HOME
    REpisodeKind.ERRAND -> EpisodeKind.ERRAND
    REpisodeKind.OUTING -> EpisodeKind.OUTING
    REpisodeKind.TRIP -> EpisodeKind.TRIP
}

internal fun EpisodeKind.toRust() = when (this) {
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

internal fun RPlaceLabel.toModel() = PlaceLabel(
    name, category, address,
    when (source) {
        RPlaceSource.USER -> PlaceSource.USER
        RPlaceSource.NAVI -> PlaceSource.NAVI
        RPlaceSource.KAKAO -> PlaceSource.KAKAO
        RPlaceSource.OSM -> PlaceSource.OSM
    },
)

internal fun PlaceLabel.toRust() = RPlaceLabel(
    name, category, address,
    when (source) {
        PlaceSource.USER -> RPlaceSource.USER
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
            is RDayEntry.Trip -> DayEntry.Trip(it.trip.toModel(), it.repeat.toInt())
            is RDayEntry.Stay -> DayEntry.Stay(it.stay.toModel())
        }
    },
    trips = trips.map { it.toModel() },
    stays = stays.map { it.toModel() },
)
