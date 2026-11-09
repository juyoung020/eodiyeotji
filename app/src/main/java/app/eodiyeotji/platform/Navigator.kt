package app.eodiyeotji.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 내비가 알려 준 장소 하나 (목적지 또는 경유지). 좌표를 모르면 [lat]·[lon]이 null
 * (Pleos 에뮬레이터 내비는 경유지 좌표를 0,0으로 준다. 이름·주소는 준다).
 */
data class NaviPoi(
    val name: String,
    val address: String,
    val lat: Double?,
    val lon: Double?,
    /** 내비 지도 데이터의 장소 ID·제공자 (모르면 빈 문자열). [Navigator.openPoiDetail]에 그대로 넘긴다. */
    val poiId: String = "",
    val poiProvider: String = "",
)

/** 내비에 경로를 요청할 때 넘기는 지점. */
data class RouteStop(val name: String, val lat: Double, val lon: Double, val address: String = "")

/** 우리가 쓰는 내비 이벤트만 추린 것. 제한속도·TBT·카메라 같은 잦은 이벤트는 내보내지 않는다. */
sealed interface NaviEvent {
    /** 내비 서비스에 연결됨. 이어서 현재 목적지·경유지 조회 결과가 온다. */
    data object Connected : NaviEvent

    /** 경로 안내 시작 ([name] = 다음에 도착할 곳 이름, 경유지가 있으면 첫 경유지). 어댑터가 곧바로 목적지·경유지를 다시 조회한다. */
    data class RouteStarted(val name: String) : NaviEvent

    /** 경로 안내 취소. */
    data object RouteCancelled : NaviEvent

    /** 목적지 조회 결과. 목적지가 없으면 null. */
    data class Destination(val poi: NaviPoi?) : NaviEvent

    /** 경유지 조회 결과 (순서대로, 최대 2개). */
    data class Waypoints(val pois: List<NaviPoi>) : NaviEvent

    /** 목적지 도착. SDK가 좌표를 주지 않는다 → 좌표는 앞서 받은 [Destination]에서 이름으로 찾는다. */
    data class DestinationArrived(val name: String, val address: String) : NaviEvent

    /** 경유지 도착. [index]: 0 = 첫째, 1 = 둘째. 좌표는 앞서 받은 [Waypoints]에서 찾는다. */
    data class WaypointArrived(val index: Int, val name: String, val address: String) : NaviEvent
}

/**
 * 길 안내를 맡기는 곳.
 * - 차(`car`): Pleos NaviHelper로 차량 내비에 경로를 보내고, 목적지·경유지·도착 이벤트를 듣는다.
 * - 폰(`phone`): 앱 안에서 직접 안내한다 (카카오모빌리티 경로 + Rust `nav.rs` 진행 계산 + 안내 화면).
 *   도착·경유지 이벤트를 똑같이 내보내 코스가 다음 구간으로 알아서 넘어간다.
 *
 * 경유지는 구간마다 2개까지 (Rust `course::split_legs`가 나눈 그대로 보낸다). 반환값은 "요청을 넘겼는지"이고 경로 탐색 성공 여부는 아니다.
 */
interface Navigator {
    /** 목적지·도착 이벤트([events])를 들을 수 있는지. 패키지 조회라서 백그라운드 스레드에서 부른다. */
    fun eventsAvailable(): Boolean

    /** 내비 이벤트 흐름. 들을 수 없으면 바로 끝난다. [refresh]가 값을 낼 때 목적지·경유지를 다시 조회한다. */
    fun events(refresh: Flow<Unit> = emptyFlow()): Flow<NaviEvent>

    /** "그곳으로 안내". */
    suspend fun requestRoute(name: String, lat: Double, lon: Double, address: String = ""): Boolean

    /** 경유지(최대 2개)를 거쳐 목적지로 안내. */
    suspend fun requestRouteWithWaypoints(destination: RouteStop, waypoints: List<RouteStop>): Boolean

    /** 장소 검색 화면을 [keyword]로 연다. */
    suspend fun openPlaceSearch(keyword: String): Boolean

    /** 내비의 장소 상세를 연다 (내비가 준 장소 ID가 있을 때만, 폰은 false). */
    suspend fun openPoiDetail(poiId: String, poiProvider: String = ""): Boolean

    /** 안내 취소 (폰은 지도 앱을 다룰 수 없어 false). */
    suspend fun cancelRoute(): Boolean
}
