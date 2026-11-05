> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/entry

# WaypointChangedInfo

@Serializable

data class [WaypointChangedInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/entry)(val index: [WaypointIndex](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/entry), val cmdType: [WaypointCmdType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-cmd-type/entry), val routeStartInfo: [RouteStartInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/entry))

Data class representing information about a waypoint change in a navigation system.

## Constructors

[WaypointChangedInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/-waypoint-changed-info)

Link copied to clipboard

constructor(index: [WaypointIndex](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/entry), cmdType: [WaypointCmdType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-cmd-type/entry), routeStartInfo: [RouteStartInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/entry))

## Properties

[cmdType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/cmd-type)

Link copied to clipboard

val [cmdType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/cmd-type): [WaypointCmdType](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-cmd-type/entry)

Type of waypoint change command. @See WaypointCmdType

[index](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/--index--)

Link copied to clipboard

val [index](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/--index--): [WaypointIndex](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/entry)

Index of the waypoint in the route sequence. @See WaypointIndex

[routeStartInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/route-start-info)

Link copied to clipboard

val [routeStartInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/route-start-info): [RouteStartInfo](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/entry)

Updated route information after waypoint change. @See RouteStartInfo
