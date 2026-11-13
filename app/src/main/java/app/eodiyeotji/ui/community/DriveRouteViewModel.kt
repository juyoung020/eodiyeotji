package app.eodiyeotji.ui.community

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.CourseLeg
import app.eodiyeotji.core.DriveRouteItem
import app.eodiyeotji.core.DriveRouteSort
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.FlightFrame
import app.eodiyeotji.core.FlightSight
import app.eodiyeotji.core.driveRouteFlightSights
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.RouteCameraSpec
import app.eodiyeotji.core.driveRouteCamera
import app.eodiyeotji.core.driveRouteFlight
import app.eodiyeotji.core.driveRouteLegs
import app.eodiyeotji.core.driveRouteMap
import app.eodiyeotji.core.driveRoutes
import app.eodiyeotji.core.lastKnownPosition
import app.eodiyeotji.core.toggleDriveRouteHeart
import app.eodiyeotji.pleos.CourseGuide
import app.eodiyeotji.pleos.CourseProgress
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.map.ScenicMapStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 고른 드라이브 코스의 상세 (3D 지도 스타일·카메라·비행 장면·안내 구간은 뒤따라 채운다). */
@Immutable
data class DriveRouteDetail(
    val item: DriveRouteItem,
    val style: String? = null,
    val camera: RouteCameraSpec? = null,
    val frames: List<FlightFrame> = emptyList(),
    /** 비행 중 "지금: 주문진항"처럼 보일 풍경 */
    val sights: List<FlightSight> = emptyList(),
    val legs: List<CourseLeg> = emptyList(),
    val flying: Boolean = false,
    /** 카드의 "3D로 미리 달리기"로 열었음 → 지도 · 처음 카메라 · 비행 장면이 다 오면 비행을 시작한다 */
    val flyWhenReady: Boolean = false,
    val naviFailed: Boolean = false,
) {
    /** 달리는 시간: 길 1km에 약 0.3초, 8~15초 (일정한 빠르기, 처음 · 끝만 천천히). */
    val flightMs: Long get() = (item.route.distanceM * 0.3).toLong().coerceIn(8_000L, 15_000L)
}

@Immutable
data class DriveRouteUiState(
    val sort: DriveRouteSort = DriveRouteSort.POPULAR,
    /** 내 근처를 골랐는데 차 위치를 모르면 false (인기순으로 보여 주고 알린다) */
    val nearKnown: Boolean = true,
    val list: Load<DriveRouteItem> = Load.Loading,
    val detail: DriveRouteDetail? = null,
    /** 방금 길을 올렸으면 true (알림 한 줄) */
    val justShared: Boolean = false,
)

/**
 * 커뮤니티 > 드라이브 코스(경치 길). 순위·거리·카메라·비행 경로·안내 구간은 Rust가 정하고, 여기서는 받은 대로 둔다.
 * 하트는 누르자마자 반영하고(낙관적) Rust가 돌려준 수로 맞춘다.
 */
@HiltViewModel
class DriveRouteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
    private val guide: CourseGuide,
    driving: DrivingStatePort,
) : ViewModel() {
    private val _state = MutableStateFlow(DriveRouteUiState())
    val state: StateFlow<DriveRouteUiState> = _state.asStateFlow()

    /** 진행 중인 코스 안내 (이 길이면 상세 버튼이 "끝점으로 바로 안내"로 바뀐다). */
    val progress: StateFlow<CourseProgress?> = guide.progress

    /** 주행 중이면 3D 지도·비행 대신 그림 한 장만 (CarUxRestrictions / 폰 주행 감지). */
    val isDriving: StateFlow<Boolean> = driving.isDriving
    private var loadJob: Job? = null
    private var cameraJob: Job? = null

    init {
        reload()
        viewModelScope.launch { isDriving.collect { if (it) stopFlight() } }
    }

    fun setSort(sort: DriveRouteSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            if (s.list !is Load.Loaded) _state.update { it.copy(list = Load.Loading) }
            val result = try {
                // 거리는 언제나 같이 보여 주고(위치를 알면), 순서만 정렬 기준을 따른다
                val here: LatLon? = core.lastKnownPosition()
                _state.update { it.copy(nearKnown = s.sort != DriveRouteSort.NEAREST || here != null) }
                Load.Loaded(core.driveRoutes(s.sort, here))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { it.copy(list = result) }
        }
    }

    /** 상세를 연다. [fly]면 비행 장면이 오는 대로 미리보기 비행을 시작한다 (카드의 "3D로 미리 달리기"). */
    fun open(item: DriveRouteItem, fly: Boolean = false) {
        _state.update { it.copy(detail = DriveRouteDetail(item, flyWhenReady = fly && !isDriving.value)) }
        val id = item.route.id
        viewModelScope.launch {
            // 지도 · 비행 장면 · 구간은 서로 상관없다 → 동시에
            val framesJob = async { runCatching { core.driveRouteFlight(id, flightFrames(item.route.distanceM)) }.getOrDefault(emptyList()) }
            val legsJob = async { runCatching { core.driveRouteLegs(id) }.getOrDefault(emptyList()) }
            val sightsJob = async { runCatching { core.driveRouteFlightSights(id) }.getOrDefault(emptyList()) }
            val style = runCatching { ScenicMapStyle.build(context, item.route.theme, core.driveRouteMap(id).geojson) }
                .onFailure { Log.w(TAG, "3D 지도 실패 $id", it) }
                .getOrNull()
            val (frames, legs) = framesJob.await() to legsJob.await()
            val sights = sightsJob.await()
            updateDetail(id) { it.copy(style = style, frames = frames, sights = sights, legs = legs) }
            startFlightWhenReady(id)
        }
    }

    /** 지도 칸 크기(dp)를 알면 그 크기에 길이 꼭 들어오는 카메라를 Rust에 묻는다. */
    fun onMapSize(widthDp: Float, heightDp: Float) {
        val id = _state.value.detail?.item?.route?.id ?: return
        cameraJob?.cancel()
        cameraJob = viewModelScope.launch {
            val cam = runCatching { core.driveRouteCamera(id, widthDp, heightDp, MAP_PADDING_DP) }.getOrNull() ?: return@launch
            updateDetail(id) { it.copy(camera = cam) }
            startFlightWhenReady(id)
        }
    }

    /**
     * 카드에서 "3D로 미리 달리기"로 열었으면 스타일 · 처음 카메라 · 비행 장면이 다 온 뒤 비행을 켠다.
     * 처음 카메라와 같은 순간에 켜면 지도가 처음 카메라로 옮기면서 첫 장면으로 내려가던 비행을 끊어 바로 멈췄다 → 한 박자 뒤에 켠다.
     */
    private suspend fun startFlightWhenReady(id: String) {
        val d = _state.value.detail?.takeIf { it.item.route.id == id } ?: return
        if (!d.flyWhenReady || d.style == null || d.camera == null || d.frames.size < 2) return
        updateDetail(id) { it.copy(flyWhenReady = false) }
        delay(FLY_AFTER_MS)
        if (!isDriving.value) updateDetail(id) { it.copy(flying = true) }
    }

    fun closeDetail() {
        _state.update { it.copy(detail = null) }
    }

    /** 미리보기 비행 시작/멈춤 (주행 중에는 시작하지 않는다). */
    fun toggleFlight() {
        if (isDriving.value) return
        _state.update { s -> s.copy(detail = s.detail?.let { it.copy(flying = !it.flying && it.frames.size >= 2) }) }
    }

    fun stopFlight() {
        _state.update { s -> s.copy(detail = s.detail?.copy(flying = false)) }
    }

    /**
     * 이 코스로 안내: ① 시작점까지 (경유지 없음) → 도착하면 [CourseGuide]가 ② 끝점까지 알아서 이어 보낸다.
     * 이미 이 길을 안내 중이면 도착을 기다리지 않고 끝점으로 넘긴다.
     */
    fun navigate() {
        val d = _state.value.detail ?: return
        if (d.legs.isEmpty()) return
        val id = d.item.route.id
        val active = guide.progress.value
        if (active != null && active.courseId == id && !active.finished) {
            guide.skipToNextLeg()
            return
        }
        viewModelScope.launch {
            val ok = guide.startShared(id, d.item.route.title, d.legs)
            updateDetail(id) { it.copy(naviFailed = !ok) }
        }
    }

    fun toggleHeart(id: String) {
        _state.update { it.flip(id, null) }
        viewModelScope.launch {
            try {
                val n = core.toggleDriveRouteHeart(id)
                _state.update { it.flip(id, n, apply = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "하트 실패 $id", e)
                _state.update { it.flip(id, null) } // 되돌리기
            }
        }
    }

    /** 길을 올린 뒤: 최신순으로 바꿔 내 길이 맨 위에 보이게. */
    fun onShared() {
        _state.update { it.copy(sort = DriveRouteSort.RECENT, justShared = true) }
        reload()
    }

    fun dismissShared() {
        _state.update { it.copy(justShared = false) }
    }

    private fun updateDetail(id: String, f: (DriveRouteDetail) -> DriveRouteDetail) {
        _state.update { s -> if (s.detail?.item?.route?.id == id) s.copy(detail = f(s.detail)) else s }
    }

    private companion object {
        const val TAG = "EodiDriveRoutes"
        const val MAP_PADDING_DP = 72f
        const val FLY_AFTER_MS = 600L

        /** 비행 장면 수: 약 20m마다 한 장면 (매 화면 섞어 읽어도 뚝뚝 끊기지 않게) */
        fun flightFrames(distanceM: Double): Int = (distanceM / 20.0).toInt().coerceIn(300, 4_000)
    }
}

/** [id] 길의 하트를 뒤집는다(apply) 또는 Rust가 준 수 [count]로 맞춘다. 목록과 상세를 같이 바꾼다. */
private fun DriveRouteUiState.flip(id: String, count: Int?, apply: Boolean = true): DriveRouteUiState {
    fun DriveRouteItem.fix(): DriveRouteItem {
        if (route.id != id) return this
        val r = if (apply) route.copy(heartedByMe = !route.heartedByMe, hearts = route.hearts + if (route.heartedByMe) -1 else 1) else route
        return copy(route = if (count != null) r.copy(hearts = count) else r)
    }
    val l = list
    return copy(
        list = if (l is Load.Loaded) Load.Loaded(l.items.map { it.fix() }) else l,
        detail = detail?.let { it.copy(item = it.item.fix()) },
    )
}
