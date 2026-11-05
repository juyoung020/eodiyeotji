# Pleos Connect SDK API 전체 목록

> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/ (2026-11-05 수집)  
> 각 항목의 상세 문서(파라미터, 예제 코드)는 `refs/pleos-docs/connect-sdk/` 아래 같은 경로의 .md 파일에 있습니다.

## 요약

| SDK | 패키지 수 | 클래스/인터페이스 수 | 멤버(메서드·프로퍼티) 수 |
|---|---|---|---|
| FusedLocation | 3 | 7 | 35 |
| LLM | 4 | 8 | 32 |
| NaviHelper | 4 | 48 | 434 |
| SpeechToText | 4 | 7 | 52 |
| TextToSpeech | 3 | 6 | 45 |
| Vehicle | 13 | 137 | 1018 |


---

## FusedLocation SDK

**사용 가이드 문서**: [Declaring permission](../refs/pleos-docs/connect-sdk/FusedLocation/declaring-permissions.md), [FusedLocation](../refs/pleos-docs/connect-sdk/FusedLocation/entry.md), [오류 처리](../refs/pleos-docs/connect-sdk/FusedLocation/error-handling.md), [Initialization / Release](../refs/pleos-docs/connect-sdk/FusedLocation/initialization-and-release.md), [Introduction](../refs/pleos-docs/connect-sdk/FusedLocation/intro.md), [위치 데이터 접근](../refs/pleos-docs/connect-sdk/FusedLocation/location-data-access.md), [Importing FusedLocation SDK](../refs/pleos-docs/connect-sdk/FusedLocation/project-setup.md)

### `ai.pleos.playground.location.fused`

#### FusedLocation
`class FusedLocation(context: Context)`  
FusedLocation

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/-fused-location.md) | `constructor(context: Context)` |  |  |
| [addProximityAlert](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/add-proximity-alert.md) | `fun addProximityAlert(request: ProximityAlertRequest, listener: ProximityAlertListener)` | Registers a proximity alert for the specified area and transitions. |  |
| [getLastKnownLocation](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/get-last-known-location.md) | `fun getLastKnownLocation(callback: FusedLocationListener)` | Gets the last known location. |  |
| [initialize](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/initialize.md) | `fun initialize()` | Initializes the SDK. |  |
| [isSupported](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/is-supported.md) | `fun isSupported(): Boolean` | Returns whether the Fused Location Service package is present on Pleos Connect. |  |
| [registerFusedLocationCallback](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/register-fused-location-callback.md) | `fun registerFusedLocationCallback(listener: FusedLocationListener)` | Registers a callback to receive location updates. |  |
| [release](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/release.md) | `fun release()` | Releases the SDK. |  |
| [removeProximityAlert](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/remove-proximity-alert.md) | `fun removeProximityAlert(alertId: String)` | Removes a previously registered proximity alert. |  |
| [showLocationAccessBlockedPopup](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/show-location-access-blocked-popup.md) | `fun showLocationAccessBlockedPopup()` | Displays a popup message informing the user that location access is blocked due to their current privacy settings. |  |
| [unregisterFusedLocationCallback](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/unregister-fused-location-callback.md) | `fun unregisterFusedLocationCallback(listener: FusedLocationListener)` | Stops receiving location updates through the specified listener. |  |

### `ai.pleos.playground.location.fused.listener`

#### FusedLocationListener
`interface FusedLocationListener`  
Defines the listener interface for receiving location updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onChanged](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/on-changed.md) | `abstract fun onChanged(location: LocationData?)` | Invoked when a location update is available. |  |
| [onError](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/fused-location-listener/on-error.md) | `abstract fun onError(exception: Exception)` |  |  |

#### ProximityAlertListener
`interface ProximityAlertListener`  
Receives events and errors for a registered proximity alert.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onError](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-error.md) | `abstract fun onError(exception: Exception)` | Invoked when the alert cannot be registered or processed. |  |
| [onRegistered](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-registered.md) | `abstract fun onRegistered(alertId: String)` | Invoked when the alert is registered successfully. |  |
| [onTransition](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.listener/proximity-alert-listener/on-transition.md) | `abstract fun onTransition(event: ProximityAlertEvent)` | Invoked when a configured proximity transition is detected. |  |

### `ai.pleos.playground.location.fused.model`

#### LocationData
`data class LocationData(val latitude: Double, val longitude: Double, val altitude: Double, val heading: Float, val confidence: Float, val timestampMillis: Long)`  
LocationData

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/-location-data.md) | `constructor(latitude: Double, longitude: Double, altitude: Double, heading: Float, confidence: Float, timestampMillis: Long)` |  |  |
| [altitude](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/altitude.md) | `val altitude: Double` |  |  |
| [confidence](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/confidence.md) | `val confidence: Float` |  |  |
| [heading](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/heading.md) | `val heading: Float` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/longitude.md) | `val longitude: Double` |  |  |
| [timestampMillis](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/location-data/timestamp-millis.md) | `val timestampMillis: Long` |  |  |

#### ProximityAlertEvent
`data class ProximityAlertEvent(val alertId: String, val transition: ProximityTransition)`  
Describes a transition detected for a registered proximity alert.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-event/-proximity-alert-event.md) | `constructor(alertId: String, transition: ProximityTransition)` |  |  |
| [alertId](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-event/alert-id.md) | `val alertId: String` |  |  |
| [transition](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-event/transition.md) | `val transition: ProximityTransition` |  |  |

#### ProximityAlertRequest
`data class ProximityAlertRequest(val latitude: Double, val longitude: Double, val radiusMeters: Float, val transitionTypes: Set<ProximityTransition>)`  
Defines an area and the transitions to monitor for a proximity alert.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-request/-proximity-alert-request.md) | `constructor(latitude: Double, longitude: Double, radiusMeters: Float, transitionTypes: Set<ProximityTransition>)` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-request/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-request/longitude.md) | `val longitude: Double` |  |  |
| [radiusMeters](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-request/radius-meters.md) | `val radiusMeters: Float` |  |  |
| [transitionTypes](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-alert-request/transition-types.md) | `val transitionTypes: Set<ProximityTransition>` |  |  |

#### ProximityTransition
`enum ProximityTransition : Enum<ProximityTransition>`  
Describes a transition that can trigger a proximity alert.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-transition/entries.md) | `val entries: EnumEntries<ProximityTransition>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-transition/value-of.md) | `fun valueOf(value: String): ProximityTransition` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-transition/values.md) | `fun values(): Array<ProximityTransition>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ENTER](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-transition/e-n-t-e-r/entry.md) | `val name: String` | ordinal |  |
| [EXIT](../refs/pleos-docs/connect-sdk/FusedLocation/fused-location/ai.pleos.playground.location.fused.model/proximity-transition/e-x-i-t/entry.md) | `val name: String` | ordinal |  |


---

## LLM SDK

**사용 가이드 문서**: [LLM](../refs/pleos-docs/connect-sdk/LLM/entry.md)

### `ai.pleos.playground.llm`

#### LLM
`class LLM(context: Context, mode: Mode = Mode.SERVER)`  
Supports API and callback for using LLM

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/-l-l-m.md) | `constructor(context: Context, mode: Mode = Mode.SERVER)` |  |  |
| [generateContent](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/generate-content.md) | `fun generateContent(prompt: String, listener: ResultListener)` | Generates content |  |
| [getOnDeviceModelPromptsContents](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/get-on-device-model-prompts-contents.md) | `fun getOnDeviceModelPromptsContents(): String` | Gets the prompt content of the Model This API is only supported when the application is in ON_DEVICE mode. |  |
| [initialize](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/initialize.md) | `fun initialize()` | Initialize LLM SDK When the app starts or is created, it should call the initialization of SDK |  |
| [registerApp](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/register-app.md) | `fun registerApp(clientID: String, clientSecret: String, listener: OnServerConnectionListener)` | Registers the app in SERVER mode. |  |
| [release](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/release.md) | `fun release()` | Release LLM SDK When the app terminates or is destroyed, it should call the release of SDK |  |
| [sendMessage](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/send-message.md) | `fun sendMessage(inputContent: LLMContent, listener: ResultListener)` | Sends messages in the conversation Users can receive responses to messages |  |
| [setModelParameter](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/set-model-parameter.md) | `fun setModelParameter(type: ModelParameter, value: String, listener: ResultListener)` | Sets the model parameters which control the behavior of the LLM model in SERVER mode. This API is only supported when the application is in SERVER mode. |  |
| [setServerType](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/set-server-type.md) | `fun setServerType(type: Int)` |  |  |
| [startChat](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/start-chat.md) | `fun startChat(history: List<LLMContent> = listOf(), onSuccess: () -> Unit, onFailed: (reason: String) -> Unit)` | Start a chat conversation in a new session |  |
| [ERROR_IS_NOT_RUNNING](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/companion/-e-r-r-o-r_-i-s_-n-o-t_-r-u-n-n-i-n-g.md) | `const val ERROR_IS_NOT_RUNNING: String` |  |  |
| [Companion](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm/l-l-m/companion/entry.md) | `object Companion` | ERROR_IS_NOT_RUNNING |  |

### `ai.pleos.playground.llm.constant`

#### Mode
`enum Mode : Enum<Mode>`  
SERVER

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.constant/mode/entries.md) | `val entries: EnumEntries<Mode>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.constant/mode/value-of.md) | `fun valueOf(value: String): Mode` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.constant/mode/values.md) | `fun values(): Array<Mode>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ON_DEVICE](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.constant/mode/o-n_-d-e-v-i-c-e/entry.md) | `val name: String` | ordinal |  |
| [SERVER](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.constant/mode/s-e-r-v-e-r/entry.md) | `val name: String` | ordinal |  |

### `ai.pleos.playground.llm.data`

#### LLMContent
`data class LLMContent(val role: String = "user", var parts: MutableList<Part> = arrayListOf())`  
Multi-turn data class.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/-l-l-m-content.md) | `constructor(role: String = "user", parts: MutableList<Part> = arrayListOf())` |  |  |
| [parts](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/parts.md) | `var parts: MutableList<Part>` |  |  |
| [role](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/l-l-m-content/role.md) | `val role: String` |  |  |

#### ModelParameter
`enum ModelParameter : Enum<ModelParameter>`  
Enum class representing the available model parameters for the LLM service. This class defines the types of parameters that can be set via the setModelParameter function.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/entries.md) | `val entries: EnumEntries<ModelParameter>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/value-of.md) | `fun valueOf(value: String): ModelParameter` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/values.md) | `fun values(): Array<ModelParameter>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [MAX_TOKENS](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/m-a-x_-t-o-k-e-n-s/entry.md) | `val name: String` | ordinal |  |
| [MODEL_NAME](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/m-o-d-e-l_-n-a-m-e/entry.md) | `val name: String` | ordinal |  |
| [TEMPERATURE](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/model-parameter/t-e-m-p-e-r-a-t-u-r-e/entry.md) | `val name: String` | ordinal |  |

#### Part
`interface Part`  
Represents data sent to and received from requests.

#### TextPart
`class TextPart(val text: String) : Part`  
Represents text- or string- based data sent to and received from requests.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/text-part/-text-part.md) | `constructor(text: String)` |  |  |
| [text](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.data/text-part/text.md) | `val text: String` |  |  |

### `ai.pleos.playground.llm.listener`

#### OnServerConnectionListener
`interface OnServerConnectionListener`  
EIC server connection result listener

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onConnected](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/on-connected.md) | `abstract fun onConnected()` | Called when connected to the server |  |
| [onFailed](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.listener/on-server-connection-listener/on-failed.md) | `abstract fun onFailed(msg: String)` | Called when connecting to the server fails |  |

#### ResultListener
`interface ResultListener`  
LLM data listener.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onError](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/on-error.md) | `abstract fun onError(reason: String)` | Called when an error occurs. |  |
| [onResponse](../refs/pleos-docs/connect-sdk/LLM/l-l-m/ai.pleos.playground.llm.listener/result-listener/on-response.md) | `abstract fun onResponse(response: String, completed: Boolean)` | Called when results are updated. |  |


---

## NaviHelper SDK

**사용 가이드 문서**: [Declaring permissions](../refs/pleos-docs/connect-sdk/NaviHelper/declaring-permissions.md), [Destinations](../refs/pleos-docs/connect-sdk/NaviHelper/destinations.md), [NaviHelper](../refs/pleos-docs/connect-sdk/NaviHelper/entry.md), [Error handling](../refs/pleos-docs/connect-sdk/NaviHelper/error-handling.md), [Event listeners](../refs/pleos-docs/connect-sdk/NaviHelper/event-listeners.md), [Importing NaviHelper SDK](../refs/pleos-docs/connect-sdk/NaviHelper/importing-sdk.md), [Initialization](../refs/pleos-docs/connect-sdk/NaviHelper/initialization-and-release.md), [Introduction](../refs/pleos-docs/connect-sdk/NaviHelper/intro.md), [Route requests with waypoints](../refs/pleos-docs/connect-sdk/NaviHelper/route-with-waypoints.md), [Routes](../refs/pleos-docs/connect-sdk/NaviHelper/routes.md), [Waypoints](../refs/pleos-docs/connect-sdk/NaviHelper/waypoint-settings.md)

### `ai.pleos.playground.navi.constants`

#### BookmarkType
`enum BookmarkType : Enum<BookmarkType>`  
HOME

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/bookmark-type/entries.md) | `val entries: EnumEntries<BookmarkType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/bookmark-type/value-of.md) | `fun valueOf(value: String): BookmarkType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/bookmark-type/values.md) | `fun values(): Array<BookmarkType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [DEFAULT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/bookmark-type/d-e-f-a-u-l-t/entry.md) | `` |  |  |
| [EV_CHARGING_STATION](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/bookmark-type/e-v_-c-h-a-r-g-i-n-g_-s-t-a-t-i-o-n/entry.md) | `` |  |  |
| [HOME](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/bookmark-type/h-o-m-e/entry.md) | `` |  |  |
| [OFFICE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/bookmark-type/o-f-f-i-c-e/entry.md) | `` |  |  |

#### CameraType
`enum CameraType : Enum<CameraType>`  
SPEED_CAMERA_FIX

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/entries.md) | `val entries: EnumEntries<CameraType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/value-of.md) | `fun valueOf(value: String): CameraType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/values.md) | `fun values(): Array<CameraType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [SECTION_SPEED_ALERT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-e-c-t-i-o-n_-s-p-e-e-d_-a-l-e-r-t/entry.md) | `` |  |  |
| [SPEED_CAMERA_FIX](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-p-e-e-d_-c-a-m-e-r-a_-f-i-x/entry.md) | `` |  |  |
| [SPEED_CAMERA_MOV](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-p-e-e-d_-c-a-m-e-r-a_-m-o-v/entry.md) | `` |  |  |
| [SPEED_CAMERA_REAR](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-p-e-e-d_-c-a-m-e-r-a_-r-e-a-r/entry.md) | `` |  |  |
| [SPEED_CAMERA_SCHOOL_ZONE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-p-e-e-d_-c-a-m-e-r-a_-s-c-h-o-o-l_-z-o-n-e/entry.md) | `` |  |  |
| [SPEED_CAMERA_SCHOOL_ZONE_TRAFFIC_LIGHT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-p-e-e-d_-c-a-m-e-r-a_-s-c-h-o-o-l_-z-o-n-e_-t-r-a-f-f-i-c_-l-i-g-h-t/entry.md) | `` |  |  |
| [SPEED_CAMERA_SECTION_END](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-p-e-e-d_-c-a-m-e-r-a_-s-e-c-t-i-o-n_-e-n-d/entry.md) | `` |  |  |
| [SPEED_CAMERA_SECTION_START](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-p-e-e-d_-c-a-m-e-r-a_-s-e-c-t-i-o-n_-s-t-a-r-t/entry.md) | `` |  |  |
| [SPEED_CAMERA_TRAFFIC_LIGHT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/s-p-e-e-d_-c-a-m-e-r-a_-t-r-a-f-f-i-c_-l-i-g-h-t/entry.md) | `` |  |  |
| [VARIABLE_SPEED_CAMERA](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/camera-type/v-a-r-i-a-b-l-e_-s-p-e-e-d_-c-a-m-e-r-a/entry.md) | `` |  |  |

#### CautionPointType
`enum CautionPointType : Enum<CautionPointType>`  
SPEED_BUMP

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/entries.md) | `val entries: EnumEntries<CautionPointType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/value-of.md) | `fun valueOf(value: String): CautionPointType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/values.md) | `fun values(): Array<CautionPointType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ACCIDENT_PRONE_AREA](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/a-c-c-i-d-e-n-t_-p-r-o-n-e_-a-r-e-a/entry.md) | `` |  |  |
| [BLOCK_INTERSECTION_ENFORCEMENT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/b-l-o-c-k_-i-n-t-e-r-s-e-c-t-i-o-n_-e-n-f-o-r-c-e-m-e-n-t/entry.md) | `` |  |  |
| [BUS_LANE_AVAILABLE_NOTICE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/b-u-s_-l-a-n-e_-a-v-a-i-l-a-b-l-e_-n-o-t-i-c-e/entry.md) | `` |  |  |
| [BUS_LANE_UNAVAILABLE_NOTICE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/b-u-s_-l-a-n-e_-u-n-a-v-a-i-l-a-b-l-e_-n-o-t-i-c-e/entry.md) | `` |  |  |
| [BUS_LANE_VIOLATION_ENFORCEMENT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/b-u-s_-l-a-n-e_-v-i-o-l-a-t-i-o-n_-e-n-f-o-r-c-e-m-e-n-t/entry.md) | `` |  |  |
| [CUT_IN_VIOLATION_ENFORCEMENT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/c-u-t_-i-n_-v-i-o-l-a-t-i-o-n_-e-n-f-o-r-c-e-m-e-n-t/entry.md) | `` |  |  |
| [DOWNHILL_CAUTION_AREA](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/d-o-w-n-h-i-l-l_-c-a-u-t-i-o-n_-a-r-e-a/entry.md) | `` |  |  |
| [FALLING_ROCK_AREA](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/f-a-l-l-i-n-g_-r-o-c-k_-a-r-e-a/entry.md) | `` |  |  |
| [FOG_AREA](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/f-o-g_-a-r-e-a/entry.md) | `` |  |  |
| [HIGHWAY_CONSTRUCTION](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/h-i-g-h-w-a-y_-c-o-n-s-t-r-u-c-t-i-o-n/entry.md) | `` |  |  |
| [LANE_CHANGE_CAMERA_ALERT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/l-a-n-e_-c-h-a-n-g-e_-c-a-m-e-r-a_-a-l-e-r-t/entry.md) | `` |  |  |
| [NARROW_ROAD](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/n-a-r-r-o-w_-r-o-a-d/entry.md) | `` |  |  |
| [NO_PARKING_ZONE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/n-o_-p-a-r-k-i-n-g_-z-o-n-e/entry.md) | `` |  |  |
| [RAILROAD_CROSSING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/r-a-i-l-r-o-a-d_-c-r-o-s-s-i-n-g/entry.md) | `` |  |  |
| [ROAD_DAMAGE_ALERT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/r-o-a-d_-d-a-m-a-g-e_-a-l-e-r-t/entry.md) | `` |  |  |
| [SCHOOL_ZONE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/s-c-h-o-o-l_-z-o-n-e/entry.md) | `` |  |  |
| [SIGN_MALFUNCION](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/s-i-g-n_-m-a-l-f-u-n-c-i-o-n/entry.md) | `` |  |  |
| [SLIPPERY_ROAD_ALERT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/s-l-i-p-p-e-r-y_-r-o-a-d_-a-l-e-r-t/entry.md) | `` |  |  |
| [SPEED_BUMP](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/s-p-e-e-d_-b-u-m-p/entry.md) | `` |  |  |
| [TRAFFIC_INFO_COLLECTION](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/t-r-a-f-f-i-c_-i-n-f-o_-c-o-l-l-e-c-t-i-o-n/entry.md) | `` |  |  |
| [TRAFFIC_LIGHT_VIOLATION_ENFORCEMENT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/t-r-a-f-f-i-c_-l-i-g-h-t_-v-i-o-l-a-t-i-o-n_-e-n-f-o-r-c-e-m-e-n-t/entry.md) | `` |  |  |
| [WILDLIFE_CROSSING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/w-i-l-d-l-i-f-e_-c-r-o-s-s-i-n-g/entry.md) | `` |  |  |
| [WRONG_WAY_DRIVING_ALERT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/caution-point-type/w-r-o-n-g_-w-a-y_-d-r-i-v-i-n-g_-a-l-e-r-t/entry.md) | `` |  |  |

#### NaviErrorCode
`enum NaviErrorCode : Enum<NaviErrorCode>`  
CAUSE_LOW_BATTERY

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/entries.md) | `val entries: EnumEntries<NaviErrorCode>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/value-of.md) | `fun valueOf(value: String): NaviErrorCode` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/values.md) | `fun values(): Array<NaviErrorCode>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [CAUSE_LOW_BATTERY](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/c-a-u-s-e_-l-o-w_-b-a-t-t-e-r-y/entry.md) | `` |  |  |
| [CAUSE_SEARCH_ROUTE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/c-a-u-s-e_-s-e-a-r-c-h_-r-o-u-t-e/entry.md) | `` |  |  |
| [CAUSE_SEARCH_ROUTE_OVER_DISTANCE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/c-a-u-s-e_-s-e-a-r-c-h_-r-o-u-t-e_-o-v-e-r_-d-i-s-t-a-n-c-e/entry.md) | `` |  |  |
| [CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/c-a-u-s-e_-s-e-a-r-c-h_-r-o-u-t-e_-t-o-o_-s-h-o-r-t_-d-i-s-t-a-n-c-e/entry.md) | `` |  |  |
| [CAUSE_SEARCH_TRIP](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-error-code/c-a-u-s-e_-s-e-a-r-c-h_-t-r-i-p/entry.md) | `` |  |  |

#### NaviMode
`enum NaviMode : Enum<NaviMode>`  
NOT_WORKING

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/entries.md) | `val entries: EnumEntries<NaviMode>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/value-of.md) | `fun valueOf(value: String): NaviMode` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/values.md) | `fun values(): Array<NaviMode>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [NOT_WORKING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/n-o-t_-w-o-r-k-i-n-g/entry.md) | `` |  |  |
| [ROUTE_GUIDING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/r-o-u-t-e_-g-u-i-d-i-n-g/entry.md) | `` |  |  |
| [SAFE_GUIDING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/s-a-f-e_-g-u-i-d-i-n-g/entry.md) | `` |  |  |
| [SIMULATION_GUIDING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/s-i-m-u-l-a-t-i-o-n_-g-u-i-d-i-n-g/entry.md) | `` |  |  |

#### NavigationStatus
`enum NavigationStatus : Enum<NavigationStatus>`  
INACTIVE

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/entries.md) | `val entries: EnumEntries<NavigationStatus>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/value-of.md) | `fun valueOf(value: String): NavigationStatus` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/values.md) | `fun values(): Array<NavigationStatus>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [FREE_DRIVE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/f-r-e-e_-d-r-i-v-e/entry.md) | `` |  |  |
| [INACTIVE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/i-n-a-c-t-i-v-e/entry.md) | `` |  |  |
| [INITIALIZING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/i-n-i-t-i-a-l-i-z-i-n-g/entry.md) | `` |  |  |
| [ROUTE_DRIVE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navigation-status/r-o-u-t-e_-d-r-i-v-e/entry.md) | `` |  |  |

#### RouteOption
`enum RouteOption : Enum<RouteOption>`  
RECOMMENDED

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/entries.md) | `val entries: EnumEntries<RouteOption>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/value-of.md) | `fun valueOf(value: String): RouteOption` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/values.md) | `fun values(): Array<RouteOption>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [COMFORTABLE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/c-o-m-f-o-r-t-a-b-l-e/entry.md) | `` |  |  |
| [FREE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/f-r-e-e/entry.md) | `` |  |  |
| [HIGHWAY](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/h-i-g-h-w-a-y/entry.md) | `` |  |  |
| [RECOMMENDED](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/r-e-c-o-m-m-e-n-d-e-d/entry.md) | `` |  |  |
| [SCHOOL_ZONE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/s-c-h-o-o-l_-z-o-n-e/entry.md) | `` |  |  |
| [SHORT_DISTANCE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/s-h-o-r-t_-d-i-s-t-a-n-c-e/entry.md) | `` |  |  |
| [SHORT_TIME](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-option/s-h-o-r-t_-t-i-m-e/entry.md) | `` |  |  |

#### RoutePointPoiType
`enum RoutePointPoiType : Enum<RoutePointPoiType>`  
NORMAL

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-poi-type/entries.md) | `val entries: EnumEntries<RoutePointPoiType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-poi-type/value-of.md) | `fun valueOf(value: String): RoutePointPoiType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-poi-type/values.md) | `fun values(): Array<RoutePointPoiType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [EV_CHARGING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-poi-type/e-v_-c-h-a-r-g-i-n-g/entry.md) | `` |  |  |
| [NORMAL](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-poi-type/n-o-r-m-a-l/entry.md) | `` |  |  |

#### RoutePointSettingType
`enum RoutePointSettingType : Enum<RoutePointSettingType>`  
AUTO

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-setting-type/entries.md) | `val entries: EnumEntries<RoutePointSettingType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-setting-type/value-of.md) | `fun valueOf(value: String): RoutePointSettingType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-setting-type/values.md) | `fun values(): Array<RoutePointSettingType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [AUTO](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-setting-type/a-u-t-o/entry.md) | `` |  |  |
| [MANUAL](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-setting-type/m-a-n-u-a-l/entry.md) | `` |  |  |

#### RoutePointType
`enum RoutePointType : Enum<RoutePointType>`  
ORIGIN

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-type/entries.md) | `val entries: EnumEntries<RoutePointType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-type/value-of.md) | `fun valueOf(value: String): RoutePointType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-type/values.md) | `fun values(): Array<RoutePointType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [DESTINATION](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-type/d-e-s-t-i-n-a-t-i-o-n/entry.md) | `` |  |  |
| [ORIGIN](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-type/o-r-i-g-i-n/entry.md) | `` |  |  |
| [WAYPOINT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/route-point-type/w-a-y-p-o-i-n-t/entry.md) | `` |  |  |

#### TrafficCongestionType
`enum TrafficCongestionType : Enum<TrafficCongestionType>`  
UNKNOWN

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-congestion-type/entries.md) | `val entries: EnumEntries<TrafficCongestionType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-congestion-type/value-of.md) | `fun valueOf(value: String): TrafficCongestionType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-congestion-type/values.md) | `fun values(): Array<TrafficCongestionType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [FAST](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-congestion-type/f-a-s-t/entry.md) | `` |  |  |
| [HEAVY](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-congestion-type/h-e-a-v-y/entry.md) | `` |  |  |
| [MODERATE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-congestion-type/m-o-d-e-r-a-t-e/entry.md) | `` |  |  |
| [SLOW](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-congestion-type/s-l-o-w/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-congestion-type/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### TrafficEventType
`enum TrafficEventType : Enum<TrafficEventType>`  
ETC

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/entries.md) | `val entries: EnumEntries<TrafficEventType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/value-of.md) | `fun valueOf(value: String): TrafficEventType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/values.md) | `fun values(): Array<TrafficEventType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ACCIDENT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/a-c-c-i-d-e-n-t/entry.md) | `` |  |  |
| [DISASTER](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/d-i-s-a-s-t-e-r/entry.md) | `` |  |  |
| [ETC](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/e-t-c/entry.md) | `` |  |  |
| [EVENT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/e-v-e-n-t/entry.md) | `` |  |  |
| [REPAIRING](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/r-e-p-a-i-r-i-n-g/entry.md) | `` |  |  |
| [ROAD_DAMAGE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/traffic-event-type/r-o-a-d_-d-a-m-a-g-e/entry.md) | `` |  |  |

#### TurnType
`enum TurnType : Enum<TurnType>`  
TURN_UNKNOWN

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/entries.md) | `val entries: EnumEntries<TurnType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/value-of.md) | `fun valueOf(value: String): TurnType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [value](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/value.md) | `val value: Long` |  |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/values.md) | `fun values(): Array<TurnType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [TURN_ARRIVE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-a-r-r-i-v-e/entry.md) | `` |  |  |
| [TURN_AVOID_OVER_C](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-a-v-o-i-d_-o-v-e-r_-c/entry.md) | `` |  |  |
| [TURN_AVOID_OVER_L](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-a-v-o-i-d_-o-v-e-r_-l/entry.md) | `` |  |  |
| [TURN_AVOID_OVER_R](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-a-v-o-i-d_-o-v-e-r_-r/entry.md) | `` |  |  |
| [TURN_AVOID_UNDER_C](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-a-v-o-i-d_-u-n-d-e-r_-c/entry.md) | `` |  |  |
| [TURN_AVOID_UNDER_L](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-a-v-o-i-d_-u-n-d-e-r_-l/entry.md) | `` |  |  |
| [TURN_AVOID_UNDER_R](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-a-v-o-i-d_-u-n-d-e-r_-r/entry.md) | `` |  |  |
| [TURN_DEVIDE_MERGE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-d-e-v-i-d-e_-m-e-r-g-e/entry.md) | `` |  |  |
| [TURN_ENTER_IC_C](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-i-c_-c/entry.md) | `` |  |  |
| [TURN_ENTER_IC_L](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-i-c_-l/entry.md) | `` |  |  |
| [TURN_ENTER_IC_R](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-i-c_-r/entry.md) | `` |  |  |
| [TURN_ENTER_OVER_C](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-o-v-e-r_-c/entry.md) | `` |  |  |
| [TURN_ENTER_OVER_L](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-o-v-e-r_-l/entry.md) | `` |  |  |
| [TURN_ENTER_OVER_R](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-o-v-e-r_-r/entry.md) | `` |  |  |
| [TURN_ENTER_ROUNDABOUT_02](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-r-o-u-n-d-a-b-o-u-t_02/entry.md) | `` |  |  |
| [TURN_ENTER_ROUNDABOUT_03](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-r-o-u-n-d-a-b-o-u-t_03/entry.md) | `` |  |  |
| [TURN_ENTER_ROUNDABOUT_04](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-r-o-u-n-d-a-b-o-u-t_04/entry.md) | `` |  |  |
| [TURN_ENTER_ROUNDABOUT_06](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-r-o-u-n-d-a-b-o-u-t_06/entry.md) | `` |  |  |
| [TURN_ENTER_ROUNDABOUT_08](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-r-o-u-n-d-a-b-o-u-t_08/entry.md) | `` |  |  |
| [TURN_ENTER_ROUNDABOUT_09](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-r-o-u-n-d-a-b-o-u-t_09/entry.md) | `` |  |  |
| [TURN_ENTER_ROUNDABOUT_10](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-r-o-u-n-d-a-b-o-u-t_10/entry.md) | `` |  |  |
| [TURN_ENTER_ROUNDABOUT_12](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-r-o-u-n-d-a-b-o-u-t_12/entry.md) | `` |  |  |
| [TURN_ENTER_SERVICEAREA](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-s-e-r-v-i-c-e-a-r-e-a/entry.md) | `` |  |  |
| [TURN_ENTER_UNDER_C](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-u-n-d-e-r_-c/entry.md) | `` |  |  |
| [TURN_ENTER_UNDER_L](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-u-n-d-e-r_-l/entry.md) | `` |  |  |
| [TURN_ENTER_UNDER_R](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-n-t-e-r_-u-n-d-e-r_-r/entry.md) | `` |  |  |
| [TURN_EXIT_IC_C](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-x-i-t_-i-c_-c/entry.md) | `` |  |  |
| [TURN_EXIT_IC_L](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-x-i-t_-i-c_-l/entry.md) | `` |  |  |
| [TURN_EXIT_IC_R](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-x-i-t_-i-c_-r/entry.md) | `` |  |  |
| [TURN_EXIT_ROUNDABOUT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-e-x-i-t_-r-o-u-n-d-a-b-o-u-t/entry.md) | `` |  |  |
| [TURN_KEEP_L](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-k-e-e-p_-l/entry.md) | `` |  |  |
| [TURN_KEEP_R](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-k-e-e-p_-r/entry.md) | `` |  |  |
| [TURN_LEFT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-l-e-f-t/entry.md) | `` |  |  |
| [TURN_RESTAREA](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-r-e-s-t-a-r-e-a/entry.md) | `` |  |  |
| [TURN_RIGHT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-r-i-g-h-t/entry.md) | `` |  |  |
| [TURN_SHARP_L](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-s-h-a-r-p_-l/entry.md) | `` |  |  |
| [TURN_SHARP_R](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-s-h-a-r-p_-r/entry.md) | `` |  |  |
| [TURN_STRAIGHT](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-s-t-r-a-i-g-h-t/entry.md) | `` |  |  |
| [TURN_TOLL](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-t-o-l-l/entry.md) | `` |  |  |
| [TURN_TUNNEL](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-t-u-n-n-e-l/entry.md) | `` |  |  |
| [TURN_UNKNOWN](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-u-n-k-n-o-w-n/entry.md) | `` |  |  |
| [TURN_UTURN](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-u-t-u-r-n/entry.md) | `` |  |  |
| [TURN_WAY](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/turn-type/t-u-r-n_-w-a-y/entry.md) | `` |  |  |

#### WaypointCmdType
`enum WaypointCmdType : Enum<WaypointCmdType>`  
ADD

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-cmd-type/entries.md) | `val entries: EnumEntries<WaypointCmdType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-cmd-type/value-of.md) | `fun valueOf(value: String): WaypointCmdType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-cmd-type/values.md) | `fun values(): Array<WaypointCmdType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ADD](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-cmd-type/a-d-d/entry.md) | `` |  |  |
| [REMOVE](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-cmd-type/r-e-m-o-v-e/entry.md) | `` |  |  |

#### WaypointIndex
`enum WaypointIndex : Enum<WaypointIndex>`  
FIRST

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/entries.md) | `val entries: EnumEntries<WaypointIndex>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/value-of.md) | `fun valueOf(value: String): WaypointIndex` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/values.md) | `fun values(): Array<WaypointIndex>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ALL](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/a-l-l/entry.md) | `` |  |  |
| [FIRST](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/f-i-r-s-t/entry.md) | `` |  |  |
| [SECOND](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/waypoint-index/s-e-c-o-n-d/entry.md) | `` |  |  |

### `ai.pleos.playground.navi.data`

#### BookmarkInfo
`data class BookmarkInfo(val latitude: Double, val longitude: Double, val displayLatitude: Double? = null, val displayLongitude: Double? = null, val name: String, val poiId: String, val poiSubId: String?, val address: String, val category: BookmarkType, val poiProvider: String = "")`  
Data class representing bookmark information for a specific location.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/-bookmark-info.md) | `constructor(latitude: Double, longitude: Double, displayLatitude: Double? = null, displayLongitude: Double? = null, name: String, poiId: String, poiSubId: String?, address: String, category: BookmarkType, poiProvider: String = "")` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/address.md) | `val address: String` |  |  |
| [category](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/category.md) | `val category: BookmarkType` |  |  |
| [displayLatitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/display-latitude.md) | `val displayLatitude: Double? = null` |  |  |
| [displayLongitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/display-longitude.md) | `val displayLongitude: Double? = null` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/longitude.md) | `val longitude: Double` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/name.md) | `val name: String` |  |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/poi-id.md) | `val poiId: String` |  |  |
| [poiProvider](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/poi-provider.md) | `val poiProvider: String` |  |  |
| [poiSubId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/bookmark-info/poi-sub-id.md) | `val poiSubId: String?` |  |  |

#### CameraAlert
`data class CameraAlert(val id: String = "", val type: CameraType, val status: Boolean, val speedLimit: Int, val remainDistance: Int, val sectionAvrSpeed: Int, val isOverSpeed: Boolean = false, val isOverSectionAvrSpeed: Boolean = false)`  
Data class representing a camera alert, typically used in navigation systems.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/-camera-alert.md) | `constructor(id: String = "", type: CameraType, status: Boolean, speedLimit: Int, remainDistance: Int, sectionAvrSpeed: Int, isOverSpeed: Boolean = false, isOverSectionAvrSpeed: Boolean = false)` |  |  |
| [id](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/id.md) | `val id: String` |  |  |
| [isOverSectionAvrSpeed](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/is-over-section-avr-speed.md) | `val isOverSectionAvrSpeed: Boolean = false` |  |  |
| [isOverSpeed](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/is-over-speed.md) | `val isOverSpeed: Boolean = false` |  |  |
| [remainDistance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/remain-distance.md) | `val remainDistance: Int` |  |  |
| [sectionAvrSpeed](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/section-avr-speed.md) | `val sectionAvrSpeed: Int` |  |  |
| [speedLimit](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/speed-limit.md) | `val speedLimit: Int` |  |  |
| [status](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/status.md) | `val status: Boolean` |  |  |
| [type](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/camera-alert/type.md) | `val type: CameraType` |  |  |

#### CautionPoint
`data class CautionPoint(val id: String, val type: CautionPointType, val remainDistance: Int)`  
Data class representing caution point alerts.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/caution-point/-caution-point.md) | `constructor(id: String, type: CautionPointType, remainDistance: Int)` |  |  |
| [id](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/caution-point/id.md) | `val id: String` |  |  |
| [remainDistance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/caution-point/remain-distance.md) | `val remainDistance: Int` |  |  |
| [type](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/caution-point/type.md) | `val type: CautionPointType` |  |  |

#### ChargerOperator
`data class ChargerOperator(val id: String, val name: String)`  
Data class representing an electric vehicle (EV) charger operator.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/charger-operator/-charger-operator.md) | `constructor(id: String, name: String)` |  |  |
| [id](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/charger-operator/id.md) | `val id: String` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/charger-operator/name.md) | `val name: String` |  |  |

#### ChargingPlan
`data class ChargingPlan(val duration: Int = 0, val startSoc: Int = 0, val endSoc: Int = 0)`  
Data class representing the specific charging plan session at a route point.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/charging-plan/-charging-plan.md) | `constructor(duration: Int = 0, startSoc: Int = 0, endSoc: Int = 0)` |  |  |
| [duration](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/charging-plan/duration.md) | `val duration: Int = 0` |  |  |
| [endSoc](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/charging-plan/end-soc.md) | `val endSoc: Int = 0` |  |  |
| [startSoc](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/charging-plan/start-soc.md) | `val startSoc: Int = 0` |  |  |

#### CurrentLocationInfo
`data class CurrentLocationInfo(val latitude: Double, val longitude: Double, val heading: Double = 0.0, val address: String, val routeId: String, val currentRouteIndex: Int?)`  
Data class representing the current location information in a navigation system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/current-location-info/-current-location-info.md) | `constructor(latitude: Double, longitude: Double, heading: Double = 0.0, address: String, routeId: String, currentRouteIndex: Int?)` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/current-location-info/address.md) | `val address: String` |  |  |
| [currentRouteIndex](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/current-location-info/current-route-index.md) | `val currentRouteIndex: Int?` |  |  |
| [heading](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/current-location-info/heading.md) | `val heading: Double = 0.0` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/current-location-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/current-location-info/longitude.md) | `val longitude: Double` |  |  |
| [routeId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/current-location-info/route-id.md) | `val routeId: String` |  |  |

#### DestinationArrivedInfo
`data class DestinationArrivedInfo(val duration: Int, val distance: Int, val name: String, val address: String)`  
Data class representing information when the destination is reached in a navigation system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-arrived-info/-destination-arrived-info.md) | `constructor(duration: Int, distance: Int, name: String, address: String)` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-arrived-info/address.md) | `val address: String` |  |  |
| [distance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-arrived-info/distance.md) | `val distance: Int` |  |  |
| [duration](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-arrived-info/duration.md) | `val duration: Int` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-arrived-info/name.md) | `val name: String` |  |  |

#### DestinationInfo
`data class DestinationInfo(val latitude: Double, val longitude: Double, val displayLatitude: Double? = null, val displayLongitude: Double? = null, val name: String, val address: String, val remainDistance: Int, val remainTime: Int, val tollFare: Int, val poiId: String, val poiSubId: String?, val poiProvider: String = "")`  
Data class representing destination information in a navigation system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/-destination-info.md) | `constructor(latitude: Double, longitude: Double, displayLatitude: Double? = null, displayLongitude: Double? = null, name: String, address: String, remainDistance: Int, remainTime: Int, tollFare: Int, poiId: String, poiSubId: String?, poiProvider: String = "")` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/address.md) | `val address: String` |  |  |
| [displayLatitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/display-latitude.md) | `val displayLatitude: Double? = null` |  |  |
| [displayLongitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/display-longitude.md) | `val displayLongitude: Double? = null` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/longitude.md) | `val longitude: Double` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/name.md) | `val name: String` |  |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/poi-id.md) | `val poiId: String` |  |  |
| [poiProvider](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/poi-provider.md) | `val poiProvider: String` |  |  |
| [poiSubId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/poi-sub-id.md) | `val poiSubId: String?` |  |  |
| [remainDistance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/remain-distance.md) | `val remainDistance: Int` |  |  |
| [remainTime](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/remain-time.md) | `val remainTime: Int` |  |  |
| [tollFare](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/toll-fare.md) | `val tollFare: Int` |  |  |

#### DrivingInfo
`data class DrivingInfo(val destination: DrivingState, val waypoints: List<DrivingState>)`  
Data class representing driving information, including the destination and waypoints.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-info/-driving-info.md) | `constructor(destination: DrivingState, waypoints: List<DrivingState>)` |  |  |
| [destination](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-info/destination.md) | `val destination: DrivingState` |  |  |
| [waypoints](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-info/waypoints.md) | `val waypoints: List<DrivingState>` |  |  |

#### DrivingState
`data class DrivingState(val name: String, val duration: Int, val distance: Int, val remainDestinationChargingValue: Int, val isChargingStation: Boolean = false, val isRecommendedChargingStation: Boolean = false, val maxChargingPower: Int = 0, val targetSoc: Int = 0, val chargerCount: Int = 0, val chargingPlan: ChargingPlan? = null)`  
Data class representing the driving state in a route.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/-driving-state.md) | `constructor(name: String, duration: Int, distance: Int, remainDestinationChargingValue: Int, isChargingStation: Boolean = false, isRecommendedChargingStation: Boolean = false, maxChargingPower: Int = 0, targetSoc: Int = 0, chargerCount: Int = 0, chargingPlan: ChargingPlan? = null)` |  |  |
| [chargerCount](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/charger-count.md) | `val chargerCount: Int = 0` |  |  |
| [chargingPlan](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/charging-plan.md) | `val chargingPlan: ChargingPlan? = null` |  |  |
| [distance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/distance.md) | `val distance: Int` |  |  |
| [duration](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/duration.md) | `val duration: Int` |  |  |
| [isChargingStation](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/is-charging-station.md) | `val isChargingStation: Boolean = false` |  |  |
| [isRecommendedChargingStation](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/is-recommended-charging-station.md) | `val isRecommendedChargingStation: Boolean = false` |  |  |
| [maxChargingPower](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/max-charging-power.md) | `val maxChargingPower: Int = 0` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/name.md) | `val name: String` |  |  |
| [remainDestinationChargingValue](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/remain-destination-charging-value.md) | `val remainDestinationChargingValue: Int` |  |  |
| [targetSoc](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/driving-state/target-soc.md) | `val targetSoc: Int = 0` |  |  |

#### NavigationScreenRequest
`sealed class NavigationScreenRequest`  
Sealed class representing a request to switch the navigation screen.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [AddHomeFavorite](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/add-home-favorite/entry.md) | `data object AddHomeFavorite : NavigationScreenRequest` | Add to home favorites screen |  |
| [AddWorkFavorite](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/add-work-favorite/entry.md) | `data object AddWorkFavorite : NavigationScreenRequest` | Add to work favorites screen |  |
| [EditRoute](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/edit-route/entry.md) | `data object EditRoute : NavigationScreenRequest` | Route editing screen |  |
| [FavoriteMain](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/favorite-main/entry.md) | `data object FavoriteMain : NavigationScreenRequest` | Main favorites screen |  |
| [Foreground](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/foreground/entry.md) | `data object Foreground : NavigationScreenRequest` | Navigation Foreground |  |
| [MoveToCurrentLocationOnMap](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/move-to-current-location-on-map/entry.md) | `data object MoveToCurrentLocationOnMap : NavigationScreenRequest` | Current location screen on map |  |
| [NearbyChargingStations](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/nearby-charging-stations/entry.md) | `data object NearbyChargingStations : NavigationScreenRequest` | Nearby charging stations search screen |  |
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/overview-route/-overview-route.md) | `constructor(info: RouteOverviewShowInfo)` |  |  |
| [OverviewRoute](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/overview-route/entry.md) | `data class OverviewRoute(val info: RouteOverviewShowInfo) : NavigationScreenRequest` | View entire route |  |
| [info](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/overview-route/info.md) | `val info: RouteOverviewShowInfo` |  |  |
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/-p-o-i-detail-popup.md) | `constructor(poiId: String, poiProvider: String = "")` |  |  |
| [POIDetailPopup](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/entry.md) | `data class POIDetailPopup(val poiId: String, val poiProvider: String = "") : NavigationScreenRequest` | POI details popup screen |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/poi-id.md) | `val poiId: String` |  |  |
| [poiProvider](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/p-o-i-detail-popup/poi-provider.md) | `val poiProvider: String` |  |  |
| [RecentDestinations](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/recent-destinations/entry.md) | `data object RecentDestinations : NavigationScreenRequest` | Recent destinations screen |  |
| [RouteOptionList](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/route-option-list/entry.md) | `data object RouteOptionList : NavigationScreenRequest` | Route option list screen |  |
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/search-place/-search-place.md) | `constructor(keyword: String)` |  |  |
| [SearchPlace](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/search-place/entry.md) | `data class SearchPlace(val keyword: String) : NavigationScreenRequest` | Place search screen |  |
| [keyword](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/search-place/keyword.md) | `val keyword: String` |  |  |
| [Settings](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/navigation-screen-request/settings/entry.md) | `data object Settings : NavigationScreenRequest` | Navigation settings screen |  |

#### RecentDestinationInfo
`data class RecentDestinationInfo(val latitude: Double, val longitude: Double, val displayLatitude: Double? = null, val displayLongitude: Double? = null, val name: String, val address: String, val poiId: String, val poiSubId: String?, val poiProvider: String = "")`  
Data class representing information about a recently visited destination.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/-recent-destination-info.md) | `constructor(latitude: Double, longitude: Double, displayLatitude: Double? = null, displayLongitude: Double? = null, name: String, address: String, poiId: String, poiSubId: String?, poiProvider: String = "")` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/address.md) | `val address: String` |  |  |
| [displayLatitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/display-latitude.md) | `val displayLatitude: Double? = null` |  |  |
| [displayLongitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/display-longitude.md) | `val displayLongitude: Double? = null` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/longitude.md) | `val longitude: Double` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/name.md) | `val name: String` |  |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/poi-id.md) | `val poiId: String` |  |  |
| [poiProvider](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/poi-provider.md) | `val poiProvider: String` |  |  |
| [poiSubId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/recent-destination-info/poi-sub-id.md) | `val poiSubId: String?` |  |  |

#### RequestWaypointInfo
`data class RequestWaypointInfo(val latitude: Double, val longitude: Double, val poiName: String?, val waypointIndex: WaypointIndex, val poiId: String, val address: String, val poiSubId: String, val poiProvider: String = "")`  
Data class representing a waypoint request in a navigation system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/-request-waypoint-info.md) | `constructor(latitude: Double, longitude: Double, poiName: String?, waypointIndex: WaypointIndex, poiId: String, address: String, poiSubId: String, poiProvider: String = "")` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/address.md) | `val address: String` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/longitude.md) | `val longitude: Double` |  |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/poi-id.md) | `val poiId: String` |  |  |
| [poiName](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/poi-name.md) | `val poiName: String?` |  |  |
| [poiProvider](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/poi-provider.md) | `val poiProvider: String` |  |  |
| [poiSubId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/poi-sub-id.md) | `val poiSubId: String` |  |  |
| [waypointIndex](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/request-waypoint-info/waypoint-index.md) | `val waypointIndex: WaypointIndex` |  |  |

#### RouteInfo
`data class RouteInfo(val latitude: Double, val longitude: Double, val displayLatitude: Double? = null, val displayLongitude: Double? = null, val poiName: String, val poiId: String, val address: String, val poiSubId: String, val routeOption: RouteOption = RouteOption.RECOMMENDED, val poiProvider: String = "", val isMobileRequest: Boolean = false)`  
Data class representing navigation route information.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/-route-info.md) | `constructor(latitude: Double, longitude: Double, displayLatitude: Double? = null, displayLongitude: Double? = null, poiName: String, poiId: String, address: String, poiSubId: String, routeOption: RouteOption = RouteOption.RECOMMENDED, poiProvider: String = "", isMobileRequest: Boolean = false)` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/address.md) | `val address: String` |  |  |
| [displayLatitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/display-latitude.md) | `val displayLatitude: Double? = null` |  |  |
| [displayLongitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/display-longitude.md) | `val displayLongitude: Double? = null` |  |  |
| [isMobileRequest](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/is-mobile-request.md) | `val isMobileRequest: Boolean = false` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/longitude.md) | `val longitude: Double` |  |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/poi-id.md) | `val poiId: String` |  |  |
| [poiName](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/poi-name.md) | `val poiName: String` |  |  |
| [poiProvider](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/poi-provider.md) | `val poiProvider: String` |  |  |
| [poiSubId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/poi-sub-id.md) | `val poiSubId: String` |  |  |
| [routeOption](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/route-option.md) | `val routeOption: RouteOption` |  |  |

#### RouteOverviewShowInfo
`data class RouteOverviewShowInfo(val durationMs: Int = 5000)`  
Data class representing the route overview display settings.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-overview-show-info/-route-overview-show-info.md) | `constructor(durationMs: Int = 5000)` |  |  |
| [durationMs](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-overview-show-info/duration-ms.md) | `val durationMs: Int = 5000` |  |  |

#### RoutePointInfo
`data class RoutePointInfo(val latitude: Double, val longitude: Double, val poiName: String, val poiId: String, val ratioOnRoute: Double, val poiType: RoutePointPoiType, val pointType: RoutePointType, val settingType: RoutePointSettingType)`  
Data class representing destination information in a navigation system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/-route-point-info.md) | `constructor(latitude: Double, longitude: Double, poiName: String, poiId: String, ratioOnRoute: Double, poiType: RoutePointPoiType, pointType: RoutePointType, settingType: RoutePointSettingType)` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/longitude.md) | `val longitude: Double` |  |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/poi-id.md) | `val poiId: String` |  |  |
| [poiName](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/poi-name.md) | `val poiName: String` |  |  |
| [poiType](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/poi-type.md) | `val poiType: RoutePointPoiType` |  |  |
| [pointType](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/point-type.md) | `val pointType: RoutePointType` |  |  |
| [ratioOnRoute](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/ratio-on-route.md) | `val ratioOnRoute: Double` |  |  |
| [settingType](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-point-info/setting-type.md) | `val settingType: RoutePointSettingType` |  |  |

#### RouteStartInfo
`data class RouteStartInfo(val duration: Int, val distance: Int, val tollFare: Int, val name: String, val remainDestinationChargingValue: Int)`  
Data class representing information when starting a route in a navigation system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/-route-start-info.md) | `constructor(duration: Int, distance: Int, tollFare: Int, name: String, remainDestinationChargingValue: Int)` |  |  |
| [distance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/distance.md) | `val distance: Int` |  |  |
| [duration](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/duration.md) | `val duration: Int` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/name.md) | `val name: String` |  |  |
| [remainDestinationChargingValue](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/remain-destination-charging-value.md) | `val remainDestinationChargingValue: Int` |  |  |
| [tollFare](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-start-info/toll-fare.md) | `val tollFare: Int` |  |  |

#### RouteStateInfo
`data class RouteStateInfo(val termsAgreement: Boolean, val naviMode: NaviMode, val isRouting: Boolean, val routeOption: RouteOption, val remainDestinationChargingInt: Int, val remainWaypointChargingInt: List<Int>, val trafficStatus: TrafficStatus?)`  
Data class representing the current state of a navigation route.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/-route-state-info.md) | `constructor(termsAgreement: Boolean, naviMode: NaviMode, isRouting: Boolean, routeOption: RouteOption, remainDestinationChargingInt: Int, remainWaypointChargingInt: List<Int>, trafficStatus: TrafficStatus?)` |  |  |
| [isRouting](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/is-routing.md) | `val isRouting: Boolean` |  |  |
| [naviMode](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/navi-mode.md) | `val naviMode: NaviMode` |  |  |
| [remainDestinationChargingInt](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/remain-destination-charging-int.md) | `val remainDestinationChargingInt: Int` |  |  |
| [remainWaypointChargingInt](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/remain-waypoint-charging-int.md) | `val remainWaypointChargingInt: List<Int>` |  |  |
| [routeOption](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/route-option.md) | `val routeOption: RouteOption` |  |  |
| [termsAgreement](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/terms-agreement.md) | `val termsAgreement: Boolean` |  |  |
| [trafficStatus](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-state-info/traffic-status.md) | `val trafficStatus: TrafficStatus?` |  |  |

#### RouteWithWaypointsRequest
`data class RouteWithWaypointsRequest(val destination: RouteInfo, val waypoints: List<RequestWaypointInfo>)`  
Data class representing a route request, including the destination and waypoints.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-with-waypoints-request/-route-with-waypoints-request.md) | `constructor(destination: RouteInfo, waypoints: List<RequestWaypointInfo>)` |  |  |
| [destination](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-with-waypoints-request/destination.md) | `val destination: RouteInfo` |  |  |
| [waypoints](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-with-waypoints-request/waypoints.md) | `val waypoints: List<RequestWaypointInfo>` |  |  |

#### TBTInfo
`data class TBTInfo(val type: TurnType, val distance: Double, val description: String)`  
Data class representing Turn-By-Turn (TBT) navigation information.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/t-b-t-info/-t-b-t-info.md) | `constructor(type: TurnType, distance: Double, description: String)` |  |  |
| [description](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/t-b-t-info/description.md) | `val description: String` |  |  |
| [distance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/t-b-t-info/distance.md) | `val distance: Double` |  |  |
| [type](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/t-b-t-info/type.md) | `val type: TurnType` |  |  |

#### TrafficCongestionInfo
`data class TrafficCongestionInfo(val offset: Double, val distance: Double, val trafficCongestionTime: Long, val trafficCongestionType: TrafficCongestionType, val avrSpeed: Double, val roadName: String, val startCrossName: String, val endCrossName: String)`  
Data class representing traffic congestion information.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/-traffic-congestion-info.md) | `constructor(offset: Double, distance: Double, trafficCongestionTime: Long, trafficCongestionType: TrafficCongestionType, avrSpeed: Double, roadName: String, startCrossName: String, endCrossName: String)` |  |  |
| [avrSpeed](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/avr-speed.md) | `val avrSpeed: Double` |  |  |
| [distance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/distance.md) | `val distance: Double` |  |  |
| [endCrossName](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/end-cross-name.md) | `val endCrossName: String` |  |  |
| [offset](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/offset.md) | `val offset: Double` |  |  |
| [roadName](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/road-name.md) | `val roadName: String` |  |  |
| [startCrossName](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/start-cross-name.md) | `val startCrossName: String` |  |  |
| [trafficCongestionTime](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/traffic-congestion-time.md) | `val trafficCongestionTime: Long` |  |  |
| [trafficCongestionType](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-info/traffic-congestion-type.md) | `val trafficCongestionType: TrafficCongestionType` |  |  |

#### TrafficCongestionSummary
`data class TrafficCongestionSummary(val totalCongestionDist: Double, val totalCongestionTime: Long, val totalCongestionAvrSpeed: Double)`  
Data class representing a summary of traffic congestion.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/-traffic-congestion-summary.md) | `constructor(totalCongestionDist: Double, totalCongestionTime: Long, totalCongestionAvrSpeed: Double)` |  |  |
| [totalCongestionAvrSpeed](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-avr-speed.md) | `val totalCongestionAvrSpeed: Double` |  |  |
| [totalCongestionDist](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-dist.md) | `val totalCongestionDist: Double` |  |  |
| [totalCongestionTime](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-time.md) | `val totalCongestionTime: Long` |  |  |

#### TrafficEventInfo
`data class TrafficEventInfo(val latitude: Double, val longitude: Double, val eventType: TrafficEventType, val startTime: String, val endTime: String, val description: String)`  
Data class representing traffic information.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-event-info/-traffic-event-info.md) | `constructor(latitude: Double, longitude: Double, eventType: TrafficEventType, startTime: String, endTime: String, description: String)` |  |  |
| [description](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-event-info/description.md) | `val description: String` |  |  |
| [endTime](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-event-info/end-time.md) | `val endTime: String` |  |  |
| [eventType](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-event-info/event-type.md) | `val eventType: TrafficEventType` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-event-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-event-info/longitude.md) | `val longitude: Double` |  |  |
| [startTime](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-event-info/start-time.md) | `val startTime: String` |  |  |

#### TrafficInfo
`data class TrafficInfo(val congestionType: TrafficCongestionType, val startRatio: Int, val endRatio: Int)`  
Data class representing traffic congestion information.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-info/-traffic-info.md) | `constructor(congestionType: TrafficCongestionType, startRatio: Int, endRatio: Int)` |  |  |
| [congestionType](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-info/congestion-type.md) | `val congestionType: TrafficCongestionType` |  |  |
| [endRatio](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-info/end-ratio.md) | `val endRatio: Int` |  |  |
| [startRatio](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-info/start-ratio.md) | `val startRatio: Int` |  |  |

#### TrafficStatus
`data class TrafficStatus(val summary: TrafficCongestionSummary, val congestionItemList: List<TrafficCongestionInfo>, val trafficEventList: List<TrafficEventInfo>)`  
Data class representing the overall traffic status, including congestion and traffic events.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-status/-traffic-status.md) | `constructor(summary: TrafficCongestionSummary, congestionItemList: List<TrafficCongestionInfo>, trafficEventList: List<TrafficEventInfo>)` |  |  |
| [congestionItemList](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-status/congestion-item-list.md) | `val congestionItemList: List<TrafficCongestionInfo>` |  |  |
| [summary](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-status/summary.md) | `val summary: TrafficCongestionSummary` |  |  |
| [trafficEventList](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-status/traffic-event-list.md) | `val trafficEventList: List<TrafficEventInfo>` |  |  |

#### TripInfo
`data class TripInfo(val distance: Int, val eta: Int, val estimatedSocAtDest: Int, val energyConsumption: Double, val summary: TripSummary)`  
Data class representing trip information.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-info/-trip-info.md) | `constructor(distance: Int, eta: Int, estimatedSocAtDest: Int, energyConsumption: Double, summary: TripSummary)` |  |  |
| [distance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-info/distance.md) | `val distance: Int` |  |  |
| [energyConsumption](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-info/energy-consumption.md) | `val energyConsumption: Double` |  |  |
| [estimatedSocAtDest](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-info/estimated-soc-at-dest.md) | `val estimatedSocAtDest: Int` |  |  |
| [eta](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-info/eta.md) | `val eta: Int` |  |  |
| [summary](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-info/summary.md) | `val summary: TripSummary` |  |  |

#### TripRequest
`data class TripRequest(val latitude: Double, val longitude: Double, val poiId: String, val poiName: String, val poiSubId: String?)`  
Data class representing a trip request.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-request/-trip-request.md) | `constructor(latitude: Double, longitude: Double, poiId: String, poiName: String, poiSubId: String?)` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-request/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-request/longitude.md) | `val longitude: Double` |  |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-request/poi-id.md) | `val poiId: String` |  |  |
| [poiName](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-request/poi-name.md) | `val poiName: String` |  |  |
| [poiSubId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-request/poi-sub-id.md) | `val poiSubId: String?` |  |  |

#### TripSummary
`data class TripSummary(val trafficConditions: List<TrafficInfo>, val routePoints: List<RoutePointInfo>)`  
Data class representing driving route information.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-summary/-trip-summary.md) | `constructor(trafficConditions: List<TrafficInfo>, routePoints: List<RoutePointInfo>)` |  |  |
| [routePoints](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-summary/route-points.md) | `val routePoints: List<RoutePointInfo>` |  |  |
| [trafficConditions](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/trip-summary/traffic-conditions.md) | `val trafficConditions: List<TrafficInfo>` |  |  |

#### WaypointArrivedInfo
`data class WaypointArrivedInfo(val duration: Int, val distance: Int, val index: WaypointIndex, val name: String, val address: String)`  
Data class representing information when arriving at a waypoint during navigation.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [INDEX](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-arrived-info/--index--.md) | `val index: WaypointIndex` |  |  |
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-arrived-info/-waypoint-arrived-info.md) | `constructor(duration: Int, distance: Int, index: WaypointIndex, name: String, address: String)` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-arrived-info/address.md) | `val address: String` |  |  |
| [distance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-arrived-info/distance.md) | `val distance: Int` |  |  |
| [duration](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-arrived-info/duration.md) | `val duration: Int` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-arrived-info/name.md) | `val name: String` |  |  |

#### WaypointChangedInfo
`data class WaypointChangedInfo(val index: WaypointIndex, val cmdType: WaypointCmdType, val routeStartInfo: RouteStartInfo)`  
Data class representing information about a waypoint change in a navigation system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [INDEX](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/--index--.md) | `val index: WaypointIndex` |  |  |
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/-waypoint-changed-info.md) | `constructor(index: WaypointIndex, cmdType: WaypointCmdType, routeStartInfo: RouteStartInfo)` |  |  |
| [cmdType](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/cmd-type.md) | `val cmdType: WaypointCmdType` |  |  |
| [routeStartInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-changed-info/route-start-info.md) | `val routeStartInfo: RouteStartInfo` |  |  |

#### WaypointInfo
`data class WaypointInfo(val name: String, val address: String, val latitude: Double, val longitude: Double, val displayLatitude: Double? = null, val displayLongitude: Double? = null, val remainDistance: Int, val remainTime: Int, val poiId: String, val poiSubId: String?, val poiProvider: String = "")`  
Data class representing information about a waypoint in a navigation system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/-waypoint-info.md) | `constructor(name: String, address: String, latitude: Double, longitude: Double, displayLatitude: Double? = null, displayLongitude: Double? = null, remainDistance: Int, remainTime: Int, poiId: String, poiSubId: String?, poiProvider: String = "")` |  |  |
| [address](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/address.md) | `val address: String` |  |  |
| [displayLatitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/display-latitude.md) | `val displayLatitude: Double? = null` |  |  |
| [displayLongitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/display-longitude.md) | `val displayLongitude: Double? = null` |  |  |
| [latitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/latitude.md) | `val latitude: Double` |  |  |
| [longitude](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/longitude.md) | `val longitude: Double` |  |  |
| [name](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/name.md) | `val name: String` |  |  |
| [poiId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/poi-id.md) | `val poiId: String` |  |  |
| [poiProvider](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/poi-provider.md) | `val poiProvider: String` |  |  |
| [poiSubId](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/poi-sub-id.md) | `val poiSubId: String?` |  |  |
| [remainDistance](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/remain-distance.md) | `val remainDistance: Int` |  |  |
| [remainTime](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.data/waypoint-info/remain-time.md) | `val remainTime: Int` |  |  |

### `ai.pleos.playground.navi.helper`

#### NaviHelper
`class NaviHelper(context: Context)`  
The entry point of the NaviHelper SDK. Create a NaviHelper instance with an Android Context to call NaviHelper APIs directly and register event-listener callbacks.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/-navi-helper.md) | `constructor(context: Context)` |  |  |
| [addListener](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/add-listener.md) | `fun addListener(listener: NaviHelperEventListener)` | Adds a listener. Registers a listener for NaviHelper callback function |  |
| [addWaypoint](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/add-waypoint.md) | `fun addWaypoint(requestWaypointInfo: RequestWaypointInfo)` | Adds waypoint. |  |
| [cancelRoute](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/cancel-route.md) | `fun cancelRoute()` | Cancels route. |  |
| [changeRouteOption](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/change-route-option.md) | `fun changeRouteOption(routeOption: RouteOption)` | Changes route option. |  |
| [getBookmarkInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-bookmark-info.md) | `fun getBookmarkInfo()` | Gets bookmark info. |  |
| [getChargerOperatorInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-charger-operator-info.md) | `fun getChargerOperatorInfo()` | Gets Charger Operator info. |  |
| [getCurrentLocationInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-current-location-info.md) | `fun getCurrentLocationInfo()` | Gets current location info. |  |
| [getDestinationInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-destination-info.md) | `fun getDestinationInfo()` | Gets destination info. |  |
| [getRecentDestinationInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-recent-destination-info.md) | `fun getRecentDestinationInfo()` | Gets recent destination info. |  |
| [getRouteStateInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-route-state-info.md) | `fun getRouteStateInfo()` | Gets route state info. |  |
| [getTBTInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-t-b-t-info.md) | `fun getTBTInfo()` | Gets TBT info. |  |
| [getWaypointInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-waypoint-info.md) | `fun getWaypointInfo()` | Gets waypoint info. |  |
| [initialize](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/initialize.md) | `fun initialize()` | Initializes NaviHelper SDK. When the app starts or is created, it should call the initialization of SDK. |  |
| [release](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/release.md) | `fun release()` | Releases NaviHelper SDK. When the app terminates or is destroyed, it should call the release of SDK |  |
| [removeListener](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/remove-listener.md) | `fun removeListener(listener: NaviHelperEventListener)` | Removes a listener. |  |
| [removeWaypoint](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/remove-waypoint.md) | `fun removeWaypoint(waypointIndex: WaypointIndex)` | Removes waypoint. |  |
| [requestNavigationScreen](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-navigation-screen.md) | `fun requestNavigationScreen(requestScreen: NavigationScreenRequest)` | Requests navigation screen. |  |
| [requestReRoute](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-re-route.md) | `fun requestReRoute()` | Requests re-route. |  |
| [requestRouteWithWaypoints](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-route-with-waypoints.md) | `fun requestRouteWithWaypoints(request: RouteWithWaypointsRequest)` | Requests a route with waypoints. |  |
| [requestRoute](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-route.md) | `fun requestRoute(routeInfo: RouteInfo)` | Requests route. |  |
| [requestTrip](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/request-trip.md) | `fun requestTrip(tripRequest: TripRequest)` | Requests trip. |  |

### `ai.pleos.playground.navi.helper.listener`

#### NaviHelperEventListener
`interface NaviHelperEventListener`  
Interface definition for callback data received from the Navi application.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onBookMarkChanged](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-book-mark-changed.md) | `open fun onBookMarkChanged()` | Called when arriving at or removing the bookmark. |  |
| [onBookmarkInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-bookmark-info.md) | `open fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>)` | Called when the bookmark information is changed. |  |
| [onCameraAlert](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-camera-alert.md) | `open fun onCameraAlert(cameraAlertList: List<CameraAlert>)` | Called when camera alerts are notified. Received when speeding camera exists. |  |
| [onCautionPointInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-caution-point-info.md) | `open fun onCautionPointInfo(cautionPointList: List<CautionPoint>)` | Called when caution point information is notified. |  |
| [onChargerOperatorInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-charger-operator-info.md) | `open fun onChargerOperatorInfo(chargerOperatorList: List<ChargerOperator>)` | Called when charger operator information is notified. |  |
| [onCurrentLocationInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-current-location-info.md) | `open fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo)` | Called when the current location information is changed. |  |
| [onCurrentRoadSpeedLimit](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-current-road-speed-limit.md) | `open fun onCurrentRoadSpeedLimit(speedLimit: Int)` | Called when the road speed limit is notified. Received every second after the event listener registration. |  |
| [onDestinationArrived](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-arrived.md) | `open fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo)` | Called when arriving at the destination. |  |
| [onDestinationChanged](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-changed.md) | `open fun onDestinationChanged()` | Called when arriving at or removing the destination. |  |
| [onDestinationInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-destination-info.md) | `open fun onDestinationInfo(destinationInfo: DestinationInfo)` | Called when the destination information is changed. |  |
| [onDrivingInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-driving-info.md) | `open fun onDrivingInfo(drivingInfo: DrivingInfo)` | Called when driving information is notified. |  |
| [onError](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-error.md) | `open fun onError(errorCode: NaviErrorCode)` | Called when a navigation error occurs. |  |
| [onNavigationStatus](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-navigation-status.md) | `open fun onNavigationStatus(status: NavigationStatus)` | Called when the navigation status is notified. Received whenever the driving status changes |  |
| [onReRouteEnded](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-re-route-ended.md) | `open fun onReRouteEnded()` | Called when navigation re-route is completed. |  |
| [onRecentDestinationInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-recent-destination-info.md) | `open fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>)` | Called when the recent destination information is changed. |  |
| [onRouteCancelled](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-cancelled.md) | `open fun onRouteCancelled()` | Called when the current route is canceled. |  |
| [onRouteOptionChanged](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-option-changed.md) | `open fun onRouteOptionChanged(routeOption: RouteOption)` | Called when the route type is changed. |  |
| [onRouteStarted](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-started.md) | `open fun onRouteStarted(routeStartInfo: RouteStartInfo)` | Called when navigation route search begins. |  |
| [onRouteStateInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-route-state-info.md) | `open fun onRouteStateInfo(routeStateInfo: RouteStateInfo)` | Called when the route state information is changed. |  |
| [onRoutingCompleted](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-routing-completed.md) | `open fun onRoutingCompleted()` | Called when navigation route search is completed. |  |
| [onTBTInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-t-b-t-info.md) | `open fun onTBTInfo(tbtInfoList: List<TBTInfo>)` | Called when the TBT information is notified. |  |
| [onTripInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-trip-info.md) | `open fun onTripInfo(tripInfo: TripInfo)` | Called when trip information is notified. |  |
| [onWayPointArrived](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-way-point-arrived.md) | `open fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo)` | Called when arriving at the waypoint. |  |
| [onWayPointInfo](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-way-point-info.md) | `open fun onWayPointInfo(waypointInfoList: List<WaypointInfo>)` | Called when the waypoint information is changed. |  |
| [onWaypointChanged](../refs/pleos-docs/connect-sdk/NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/on-waypoint-changed.md) | `open fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo)` | Called when arriving at or removing the waypoint. |  |


---

## SpeechToText SDK

**사용 가이드 문서**: [App registration](../refs/pleos-docs/connect-sdk/SpeechToText/app-registration.md), [Declaring permissions](../refs/pleos-docs/connect-sdk/SpeechToText/declaring-permissions.md), [SpeechToText](../refs/pleos-docs/connect-sdk/SpeechToText/entry.md), [Error code](../refs/pleos-docs/connect-sdk/SpeechToText/error-code.md), [Importing SpeechToText SDK](../refs/pleos-docs/connect-sdk/SpeechToText/importing-sdk.md), [Initialization](../refs/pleos-docs/connect-sdk/SpeechToText/initialization-and-release.md), [Introduction](../refs/pleos-docs/connect-sdk/SpeechToText/intro.md), [Event listeners](../refs/pleos-docs/connect-sdk/SpeechToText/result-listeners.md), [Sending audio data](../refs/pleos-docs/connect-sdk/SpeechToText/sending-audio-data.md), [Set locale](../refs/pleos-docs/connect-sdk/SpeechToText/set-locale.md), [Start and stop recognition](../refs/pleos-docs/connect-sdk/SpeechToText/start-and-stop.md)

### `ai.pleos.playground.stt`

#### SpeechToText
`class SpeechToText(context: Context, mode: Mode = Mode.HYBRID)`  
Provides access to the SpeechToText service. This SDK allows access to the SpeechToText.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/-speech-to-text.md) | `constructor(context: Context, mode: Mode = Mode.HYBRID)` | Application context |  |
| [addListener](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/add-listener.md) | `fun addListener(listener: ResultListener)` | Adds a listener that will be notified of various events related to SpeechToText Requires the caller to have STT_PERMISSION. |  |
| [completeAudioSend](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/complete-audio-send.md) | `fun completeAudioSend()` | Completes the external audio buffer input started by sendAudio and finishes recognition. Available only in Mode.ON_DEVICE. In other modes the call is ignored. Requires the caller to have STT_PERMISSION. |  |
| [initialize](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/initialize.md) | `fun initialize()` | Initializes SDK to use SpeechToText whether the application is created or not. Requires the caller to have STT_PERMISSION. |  |
| [registerApp](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/register-app.md) | `fun registerApp(clientID: String, clientSecret: String, listener: OnServerConnectionListener)` | Registers the application to use the server-oriented or hybrid SpeechToText service. Requires the caller to have STT_PERMISSION. |  |
| [release](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/release.md) | `fun release()` | Releases SDK whether the application is destroyed or not. Requires the caller to have STT_PERMISSION. |  |
| [removeListener](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/remove-listener.md) | `fun removeListener(listener: ResultListener)` | Removes a previously registered listener Requires the caller to have STT_PERMISSION. |  |
| [request](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/request.md) | `fun request()` | Requests for starting speech recognition. After the application invoke request(), microphone will be open, and then you can stream your audio through microphone. |  |
| [sendAudio](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/send-audio.md) | `fun sendAudio(bytes: ByteArray)` | Sends an external audio buffer (PCM) for recognition. Available only in Mode.ON_DEVICE. In other modes the call is ignored and ResultListener.onError is not called. Recognition starts when the first buffer is sent; calling request is not required. In this path, ResultListener.onReady and ResultListener.onUpdatedRms are not called. Call completeAudioSend after sending the last buffer. Requires the caller to have STT_PERMISSION. |  |
| [setLocale](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/set-locale.md) | `fun setLocale(locale: Locale)` | Updates the locale that the user wants to set. Requires the caller to have STT_PERMISSION. |  |
| [stop](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/stop.md) | `fun stop()` | Stops speech recognition. After the application invoke stop(), the microphone will be disabled. |  |
| [Companion](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/companion/entry.md) | `object Companion` | version |  |
| [version](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/companion/version.md) | `val version: String` |  |  |

### `ai.pleos.playground.stt.constant`

#### ErrorType
`enum ErrorType : Enum<ErrorType>`  
Represents the types of errors that can occur within the system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/entries.md) | `val entries: EnumEntries<ErrorType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/value-of.md) | `fun valueOf(value: String): ErrorType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/values.md) | `fun values(): Array<ErrorType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [AUDIO_ERROR](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/a-u-d-i-o_-e-r-r-o-r/entry.md) | `` |  |  |
| [INVALID_REQUEST_ERROR](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/i-n-v-a-l-i-d_-r-e-q-u-e-s-t_-e-r-r-o-r/entry.md) | `` |  |  |
| [MIC_ERROR](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/m-i-c_-e-r-r-o-r/entry.md) | `` |  |  |
| [NETWORK_ERROR](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/n-e-t-w-o-r-k_-e-r-r-o-r/entry.md) | `` |  |  |
| [NOT_SUPPORTED_API](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/n-o-t_-s-u-p-p-o-r-t-e-d_-a-p-i/entry.md) | `` |  |  |
| [ON_DEVICE_ENGINE_ERROR](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/o-n_-d-e-v-i-c-e_-e-n-g-i-n-e_-e-r-r-o-r/entry.md) | `` |  |  |
| [PERMISSION_DENIED](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/p-e-r-m-i-s-s-i-o-n_-d-e-n-i-e-d/entry.md) | `` |  |  |
| [SERVER_INTERNAL_ERROR](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/s-e-r-v-e-r_-i-n-t-e-r-n-a-l_-e-r-r-o-r/entry.md) | `` |  |  |
| [TIMEOUT](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/t-i-m-e-o-u-t/entry.md) | `` |  |  |
| [UNAUTHENTICATED](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-a-u-t-h-e-n-t-i-c-a-t-e-d/entry.md) | `` |  |  |
| [UNAVAILABLE_LANGUAGE](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-a-v-a-i-l-a-b-l-e_-l-a-n-g-u-a-g-e/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/error-type/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### Mode
`enum Mode : Enum<Mode>`  
This class provides SpeechToText mode.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/entries.md) | `val entries: EnumEntries<Mode>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/value-of.md) | `fun valueOf(value: String): Mode` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/values.md) | `fun values(): Array<Mode>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [HYBRID](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/h-y-b-r-i-d/entry.md) | `` |  |  |
| [ON_DEVICE](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/o-n_-d-e-v-i-c-e/entry.md) | `` |  |  |
| [SERVER](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.constant/mode/s-e-r-v-e-r/entry.md) | `` |  |  |

### `ai.pleos.playground.stt.data`

#### InferenceSource
`enum InferenceSource : Enum<InferenceSource>`  
ON_DEVICE

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.data/inference-source/entries.md) | `val entries: EnumEntries<InferenceSource>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.data/inference-source/value-of.md) | `fun valueOf(value: String): InferenceSource` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.data/inference-source/values.md) | `fun values(): Array<InferenceSource>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [DEFAULT](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.data/inference-source/companion/-d-e-f-a-u-l-t.md) | `val DEFAULT: InferenceSource` |  |  |
| [Companion](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.data/inference-source/companion/entry.md) | `object Companion` | DEFAULT |  |
| [ON_DEVICE](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.data/inference-source/o-n_-d-e-v-i-c-e/entry.md) | `` |  |  |
| [SERVER](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.data/inference-source/s-e-r-v-e-r/entry.md) | `` |  |  |

### `ai.pleos.playground.stt.listener`

#### OnInitializeListener
`interface OnInitializeListener`  
Listener for the result of SpeechToText SDK initialization.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onFailure](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/on-initialize-listener/on-failure.md) | `abstract fun onFailure(errorType: ErrorType)` | Called when the SDK initialization has failed. |  |
| [onSuccess](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/on-initialize-listener/on-success.md) | `abstract fun onSuccess()` | Called when the SDK has been successfully initialized and bound to the STT service. |  |

#### OnServerConnectionListener
`interface OnServerConnectionListener`  
Receives events from SpeechToText when the server is connected successfully.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onConnected](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/on-server-connection-listener/on-connected.md) | `abstract fun onConnected()` | Called when connected to the server |  |
| [onFailed](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/on-server-connection-listener/on-failed.md) | `abstract fun onFailed(errorType: ErrorType)` | Called when failure to connection to server |  |

#### ResultListener
`interface ResultListener`  
Receives events from SpeechToText when recognition-related events occur. Adds and removes with addListener/removeListener APIs respectively.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onEndedRecognition](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-ended-recognition.md) | `abstract fun onEndedRecognition()` | Called when caller recognition is finished. |  |
| [onError](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-error.md) | `abstract fun onError(errorType: ErrorType)` | Called when caller recognition fails. |  |
| [onReady](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-ready.md) | `abstract fun onReady()` | Called when listener recognition is ready. |  |
| [onStartedRecognition](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-started-recognition.md) | `abstract fun onStartedRecognition()` | Called when caller recognition is started. |  |
| [onUpdatedEpdData](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated-epd-data.md) | `abstract fun onUpdatedEpdData(on: Long, off: Long)` | Called when the SpeechToText EPD result is updated. |  |
| [onUpdatedRms](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated-rms.md) | `abstract fun onUpdatedRms(rms: Double)` | Called when RMS is updated. |  |
| [onUpdated](../refs/pleos-docs/connect-sdk/SpeechToText/speech-to-text/ai.pleos.playground.stt.listener/result-listener/on-updated.md) | `abstract fun onUpdated(stt: String, completed: Boolean)` | Called when the SpeechToText result is updated. |  |


---

## TextToSpeech SDK

**사용 가이드 문서**: [App registration](../refs/pleos-docs/connect-sdk/TextToSpeech/app-registration.md), [Declaring permissions](../refs/pleos-docs/connect-sdk/TextToSpeech/declaring-permissions.md), [TextToSpeech](../refs/pleos-docs/connect-sdk/TextToSpeech/entry.md), [Error code](../refs/pleos-docs/connect-sdk/TextToSpeech/error-code.md), [Event listeners](../refs/pleos-docs/connect-sdk/TextToSpeech/event-listeners.md), [Importing TextToSpeech SDK](../refs/pleos-docs/connect-sdk/TextToSpeech/importing-sdk.md), [Initialization](../refs/pleos-docs/connect-sdk/TextToSpeech/initialization-and-release.md), [Introduction](../refs/pleos-docs/connect-sdk/TextToSpeech/intro.md), [Set locale](../refs/pleos-docs/connect-sdk/TextToSpeech/set-locale.md), [Start and stop synthesis](../refs/pleos-docs/connect-sdk/TextToSpeech/start-and-stop.md)

### `ai.pleos.playground.tts`

#### TextToSpeech
`class TextToSpeech(context: Context, mode: Mode = Mode.HYBRID)`  
Provides access to the TextToSpeech service. This SDK allows access to the TextToSpeech.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/-text-to-speech.md) | `constructor(context: Context, mode: Mode = Mode.HYBRID)` | Application context |  |
| [addEventListener](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/add-event-listener.md) | `fun addEventListener(listener: EventListener)` | Adds a listener that will be notified of various events related to TextToSpeech |  |
| [initialize](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize.md) | `fun initialize()` | Initializes TextToSpeech SDK When the app starts or is created, it should call the initialization of SDK |  |
| [registerApp](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/register-app.md) | `fun registerApp(clientID: String, clientSecret: String, listener: OnServerConnectionListener)` | Register app |  |
| [release](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/release.md) | `fun release()` | Releases TextToSpeech SDK When the app terminates or is destroyed, it should call the release of SDK |  |
| [removeEventListener](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/remove-event-listener.md) | `fun removeEventListener(listener: EventListener)` | Removes a previously registered listener |  |
| [setAudioAttributes](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-audio-attributes.md) | `fun setAudioAttributes(audioAttributes: AudioAttributes)` | Sets the audio attributes to be used for TextToSpeech SDK |  |
| [setAudioZoneId](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-audio-zone-id.md) | `fun setAudioZoneId(zoneId: Int)` | Update audio zone id that user want to set. Requires the caller to have TTS_PERMISSION. |  |
| [setLocale](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-locale.md) | `fun setLocale(locale: Locale)` | Updates the locale that the user wants to set. Requires the caller to have TTS_PERMISSION. |  |
| [setSpeakerType](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/set-speaker-type.md) | `fun setSpeakerType(type: String)` | Updates the speaker type (voice) used for speech synthesis. Requires the caller to have TTS_PERMISSION. If this method is not called, the speaker previously saved for the app is used. If no speaker has been saved for the app, the Gleo AI speaker setting is used. If neither is set, `haeun` is used. |  |
| [speak](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/speak.md) | `fun speak(text: String, isFinal: Boolean = true)` | Converts Text-To-Speech and speaks the text |  |
| [stop](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/stop.md) | `fun stop()` | Interrupts the current TextToSpeech playback |  |
| [Companion](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/companion/entry.md) | `object Companion` | version |  |
| [version](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/companion/version.md) | `val version: String` |  |  |

### `ai.pleos.playground.tts.constant`

#### ErrorType
`enum ErrorType : Enum<ErrorType>`  
Represents the types of errors that can occur within the system.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/entries.md) | `val entries: EnumEntries<ErrorType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/value-of.md) | `fun valueOf(value: String): ErrorType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/values.md) | `fun values(): Array<ErrorType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [AUDIO_ERROR](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/a-u-d-i-o_-e-r-r-o-r/entry.md) | `` |  |  |
| [INVALID_REQUEST_ERROR](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/i-n-v-a-l-i-d_-r-e-q-u-e-s-t_-e-r-r-o-r/entry.md) | `` |  |  |
| [MIC_ERROR](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/m-i-c_-e-r-r-o-r/entry.md) | `` |  |  |
| [NETWORK_ERROR](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/n-e-t-w-o-r-k_-e-r-r-o-r/entry.md) | `` |  |  |
| [NOT_SUPPORTED_API](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/n-o-t_-s-u-p-p-o-r-t-e-d_-a-p-i/entry.md) | `` |  |  |
| [ON_DEVICE_ENGINE_ERROR](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/o-n_-d-e-v-i-c-e_-e-n-g-i-n-e_-e-r-r-o-r/entry.md) | `` |  |  |
| [PERMISSION_DENIED](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/p-e-r-m-i-s-s-i-o-n_-d-e-n-i-e-d/entry.md) | `` |  |  |
| [SERVER_INTERNAL_ERROR](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/s-e-r-v-e-r_-i-n-t-e-r-n-a-l_-e-r-r-o-r/entry.md) | `` |  |  |
| [TIMEOUT](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/t-i-m-e-o-u-t/entry.md) | `` |  |  |
| [UNAUTHENTICATED](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/u-n-a-u-t-h-e-n-t-i-c-a-t-e-d/entry.md) | `` |  |  |
| [UNAVAILABLE_LANGUAGE](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/u-n-a-v-a-i-l-a-b-l-e_-l-a-n-g-u-a-g-e/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/error-type/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### Mode
`enum Mode : Enum<Mode>`  
SERVER

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/entries.md) | `val entries: EnumEntries<Mode>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/value-of.md) | `fun valueOf(value: String): Mode` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/values.md) | `fun values(): Array<Mode>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [HYBRID](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/h-y-b-r-i-d/entry.md) | `` |  |  |
| [ON_DEVICE](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/o-n_-d-e-v-i-c-e/entry.md) | `` |  |  |
| [SERVER](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.constant/mode/s-e-r-v-e-r/entry.md) | `` |  |  |

### `ai.pleos.playground.tts.listener`

#### EventListener
`interface EventListener`  
Listener for events relating to the progress of Text-To-Speech. Adds and removes with addEventListener/removeEventListener APIs respectively.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onDone](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-done.md) | `abstract fun onDone()` | Called when TextToSpeech has successfully completed processing. |  |
| [onError](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-error.md) | `abstract fun onError(errorType: ErrorType)` | Called when an error has occurred during processing. |  |
| [onReady](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-ready.md) | `abstract fun onReady()` | Called when TextToSpeech speak has been called. |  |
| [onStart](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-start.md) | `abstract fun onStart()` | Called when TextToSpeech starts as perceived by the caller. |  |
| [onStop](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-stop.md) | `abstract fun onStop()` | Called when TextToSpeech stops while in progress. |  |
| [onUpdatedRms](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/event-listener/on-updated-rms.md) | `abstract fun onUpdatedRms(rms: Double)` | Called when RMS is updated. |  |

#### OnInitializeListener
`interface OnInitializeListener`  
Listener for the result of TextToSpeech SDK initialization.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onFailure](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/on-failure.md) | `abstract fun onFailure(errorType: ErrorType)` | Called when the SDK initialization has failed. |  |
| [onSuccess](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-initialize-listener/on-success.md) | `abstract fun onSuccess()` | Called when the SDK has been successfully initialized and bound to the TTS service. |  |

#### OnServerConnectionListener
`interface OnServerConnectionListener`  
Server connection result listener

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onConnected](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-server-connection-listener/on-connected.md) | `abstract fun onConnected()` | Called when connected to the server |  |
| [onFailed](../refs/pleos-docs/connect-sdk/TextToSpeech/text-to-speech/ai.pleos.playground.tts.listener/on-server-connection-listener/on-failed.md) | `abstract fun onFailed(errorType: ErrorType)` | Called when failure to connection to server |  |


---

## Vehicle SDK

**사용 가이드 문서**: [Callback](../refs/pleos-docs/connect-sdk/Vehicle/callback.md), [Capability](../refs/pleos-docs/connect-sdk/Vehicle/check-capability.md), [Vehicle](../refs/pleos-docs/connect-sdk/Vehicle/entry.md), [Get](../refs/pleos-docs/connect-sdk/Vehicle/get-property.md), [Introduction](../refs/pleos-docs/connect-sdk/Vehicle/intro.md), [Obtaining a domain object](../refs/pleos-docs/connect-sdk/Vehicle/obtain-domain-object.md), [Importing Vehicle SDK](../refs/pleos-docs/connect-sdk/Vehicle/project-setup.md), [Set](../refs/pleos-docs/connect-sdk/Vehicle/set-property.md), [Error code](../refs/pleos-docs/connect-sdk/Vehicle/vehicle-error-code.md), [Declaring permission](../refs/pleos-docs/connect-sdk/Vehicle/vehicle-permission.md)

### `ai.pleos.playground.vehicle`

#### Vehicle
`class Vehicle(context: Context)`  
The entry point of the Vehicle SDK.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/-vehicle.md) | `constructor(context: Context)` | Android android.content.Context used to bind to the vehicle service. |  |
| [getBrake](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-brake.md) | `fun getBrake(): Brake` | Get the object of Brake to use Brake APIs. |  |
| [getCarInfo](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-car-info.md) | `fun getCarInfo(): CarInfo` | Get the object of CarInfo to use CarInfo APIs. |  |
| [getDisplay](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-display.md) | `fun getDisplay(): Display` | Get the object of Display to use Display APIs. |  |
| [getDoor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-door.md) | `fun getDoor(): Door` | Get the object of Door to use Door APIs. |  |
| [getDrivingMode](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-driving-mode.md) | `fun getDrivingMode(): DrivingMode` | Get the object of DrivingMode to use DrivingMode APIs. |  |
| [getEvBattery](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-ev-battery.md) | `fun getEvBattery(): EvBattery` | Get the object of EvBattery to use EvBattery APIs. |  |
| [getFuel](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-fuel.md) | `fun getFuel(): Fuel` | Get the object of Fuel to use Fuel APIs. |  |
| [getHVAC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-h-v-a-c.md) | `fun getHVAC(): HVAC` | Get the object of HVAC to use HVAC APIs. |  |
| [getLight](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-light.md) | `fun getLight(): Light` | Get the object of Light to use Light APIs. |  |
| [getOdometer](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-odometer.md) | `fun getOdometer(): Odometer` | Get the object of Odometer to use Odometer APIs. |  |
| [getSafety](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-safety.md) | `fun getSafety(): Safety` | Get the object of Safety to use Safety APIs. |  |
| [getSeat](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-seat.md) | `fun getSeat(): Seat` | Get the object of Seat to use Seat APIs. |  |
| [getSideMirror](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-side-mirror.md) | `fun getSideMirror(): SideMirror` | Get the object of SideMirror to use SideMirror APIs. |  |
| [getSteeringWheel](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-steering-wheel.md) | `fun getSteeringWheel(): SteeringWheel` | Get the object of SteeringWheel to use SteeringWheel APIs. |  |
| [getTire](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-tire.md) | `fun getTire(): Tire` | Get the object of Tire to use Tire APIs. |  |
| [getToll](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-toll.md) | `fun getToll(): Toll` | Get the object of Toll to use Toll APIs. |  |
| [getTurnSignal](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-turn-signal.md) | `fun getTurnSignal(): TurnSignal` | Get the object of TurnSignal to use TurnSignal APIs. |  |
| [getWindow](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-window.md) | `fun getWindow(): Window` | Get the object of Window to use Window APIs. |  |
| [getWiper](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/get-wiper.md) | `fun getWiper(): Wiper` | Get the object of Wiper to use Wiper APIs. |  |
| [initialize](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/initialize.md) | `fun initialize()` | Initialize sdk. It is called while the application is created. |  |
| [release](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/release.md) | `fun release()` | Release sdk. It is called while the application is destroyed. |  |

### `ai.pleos.playground.vehicle.api`

#### Brake
`interface Brake`  
Provides APIs for brake pedal status.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkBrakePedalStatusCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/check-brake-pedal-status-capabilities.md) | `abstract fun checkBrakePedalStatusCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets the brake pedal status capabilities. | CAR_DRIVE (protection level: normal) |
| [getCurrentBrakePedalStatus](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/get-current-brake-pedal-status.md) | `abstract fun getCurrentBrakePedalStatus(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the brake pedal status. If true, the brake pedal is being pressed. | CAR_DRIVE (protection level: normal) |
| [registerBrakePedalStatus](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/register-brake-pedal-status.md) | `abstract fun registerBrakePedalStatus(listener: BrakePedalStatusListener)` | Registers a listener for brake pedal status property updates. | CAR_DRIVE (protection level: normal) |
| [unregisterBrakePedalStatus](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/unregister-brake-pedal-status.md) | `abstract fun unregisterBrakePedalStatus(listener: BrakePedalStatusListener)` | Unregisters the listener so brake pedal status property updates stop. | CAR_DRIVE (protection level: normal) |

#### CarInfo
`interface CarInfo`  
Provides APIs for vehicle identification information and capability info.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkCRNInfoCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/check-c-r-n-info-capabilities.md) | `abstract fun ~~checkCRNInfoCapabilities~~(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Use checkVINInfoCapabilities to check VIN capability. | CAR_INFO (protection level: normal) |
| [checkVINInfoCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/check-v-i-n-info-capabilities.md) | `abstract fun checkVINInfoCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets VIN info capabilities. | CAR_INFO (protection level: normal) |
| [getCRNInfo](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/get-c-r-n-info.md) | `abstract fun ~~getCRNInfo~~(onComplete: (String?) -> Unit, onFailed: (Exception) -> Unit)` | Use getVINInfo to retrieve the Vehicle Identification Number (VIN). | CAR_INFO (protection level: normal) |
| [getVINInfo](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/car-info/get-v-i-n-info.md) | `abstract fun getVINInfo(onComplete: (String?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the Vehicle Identification Number (VIN). | CAR_INFO (protection level: normal) |

#### Display
`interface Display`  
Provides APIs for temperature display unit.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkTemperatureDisplayUnitCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/display/check-temperature-display-unit-capabilities.md) | `abstract fun checkTemperatureDisplayUnitCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets temperature display unit capabilities. | CAR_INFO (protection level: normal) |
| [getCurrentTemperatureDisplayUnit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/display/get-current-temperature-display-unit.md) | `abstract fun getCurrentTemperatureDisplayUnit(onComplete: (TemperatureUnit?) -> Unit, onFailed: (Exception) -> Unit)` | Gets current temperature units for display. | CAR_INFO (protection level: normal) |
| [registerTemperatureDisplayUnit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/display/register-temperature-display-unit.md) | `abstract fun registerTemperatureDisplayUnit(listener: TemperatureDisplayUnitListener)` | Registers TemperatureDisplayUnit to get property updates. | CAR_INFO (protection level: normal) |
| [unregisterTemperatureDisplayUnit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/display/unregister-temperature-display-unit.md) | `abstract fun unregisterTemperatureDisplayUnit(listener: TemperatureDisplayUnitListener)` | Stops getting property updates for the given TemperatureDisplayUnit. | CAR_INFO (protection level: normal) |

#### Door
`interface Door`  
Provides APIs for door lock, door position, and glove box.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkDoorLockCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-lock-capabilities.md) | `abstract fun checkDoorLockCapabilities(onComplete: (CapabilityConfig, List<DoorArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets DoorLock capabilities. | CAR_DOORS (protection level: normal) |
| [checkDoorPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-position-capabilities.md) | `abstract fun checkDoorPositionCapabilities(onComplete: (CapabilityConfig, Map<DoorArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets DoorPosition capabilities. | CAR_DOORS (protection level: normal) |
| [checkGloveBoxLockCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-glove-box-lock-capabilities.md) | `abstract fun checkGloveBoxLockCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets the glove box lock capabilities. | CAR_DOORS (protection level: normal) |
| [getCurrentDoorPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-door-position.md) | `abstract fun getCurrentDoorPosition(area: DoorArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current door position state. | CAR_DOORS (protection level: normal) |
| [getCurrentGloveBoxLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-glove-box-lock-state.md) | `abstract fun getCurrentGloveBoxLockState(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current glove lock state. | CAR_DOORS (protection level: normal) |
| [getDoorLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-door-lock-state.md) | `abstract fun getDoorLockState(area: DoorArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets current door lock state. | CAR_DOORS (protection level: normal) |
| [registerDoorLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-door-lock-state.md) | `abstract fun registerDoorLockState(area: DoorArea, listener: DoorLockStateListener)` | Registers the door lock state to get property updates. | CAR_DOORS (protection level: normal) |
| [registerDoorPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-door-position.md) | `abstract fun registerDoorPosition(area: DoorArea, listener: DoorPositionListener)` | Registers the door area to get property updates. | CAR_DOORS (protection level: normal) |
| [registerGloveBoxLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/register-glove-box-lock-state.md) | `abstract fun registerGloveBoxLockState(listener: GloveBoxLockStateListener)` | Registers the glove box lock state to get property updates. | CAR_DOORS (protection level: normal) |
| [setDoorLock](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-door-lock.md) | `abstract fun setDoorLock(area: DoorArea, value: Boolean, onComplete: (DoorArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the current door lock state. | CONTROL_CAR_DOORS (protection level: normal) |
| [setDoorPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-door-position.md) | `abstract fun setDoorPosition(area: DoorArea, value: Int, onComplete: (DoorArea, Int?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the current door position state. The maximum (fully open) value is vehicle-dependent and is not fixed. Call checkDoorPositionCapabilities first. That API's callback returns CapabilityConfig plus a Map<DoorArea, AreaBounds<Int>>; read each area's AreaBounds.minValue/AreaBounds.maxValue from that map (not from CapabilityConfig). Min is typically 0 for fully closed. | CONTROL_CAR_DOORS (protection level: normal) |
| [setGloveBoxLock](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-glove-box-lock.md) | `abstract fun setGloveBoxLock(value: Boolean, onComplete: (Boolean?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the current glove lock state. | CONTROL_CAR_DOORS (protection level: normal) |
| [unregisterDoorLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-door-lock-state.md) | `abstract fun unregisterDoorLockState(area: DoorArea, listener: DoorLockStateListener)` | Stops getting property updates for the given door lock state. | CAR_DOORS (protection level: normal) |
| [unregisterDoorPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-door-position.md) | `abstract fun unregisterDoorPosition(area: DoorArea, listener: DoorPositionListener)` | Stops getting property updates for the given door area. | CAR_DOORS (protection level: normal) |
| [unregisterGloveBoxLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/unregister-glove-box-lock-state.md) | `abstract fun unregisterGloveBoxLockState(listener: GloveBoxLockStateListener)` | Stops getting property updates for the given glove box lock state. | CAR_DOORS (protection level: normal) |

#### DrivingMode
`interface DrivingMode`  
Provides APIs for driving mode and cruise control.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkCruiseControlStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/check-cruise-control-state-capabilities.md) | `abstract fun checkCruiseControlStateCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets CruiseControlState capabilities. | CAR_DRIVE (protection level: normal) |
| [checkCruiseControlTargetSpeedCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/check-cruise-control-target-speed-capabilities.md) | `abstract fun checkCruiseControlTargetSpeedCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets CruiseControlTargetSpeed capabilities. | CAR_DRIVE (protection level: normal) |
| [checkDrivingModeStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/check-driving-mode-state-capabilities.md) | `abstract fun checkDrivingModeStateCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets DrivingModeState capabilities. | CAR_DRIVE (protection level: normal) |
| [getCruiseControlState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/get-cruise-control-state.md) | `abstract fun getCruiseControlState(onComplete: (CruiseControl?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current cruise control state. | CAR_DRIVE (protection level: normal) |
| [getCruiseControlTargetSpeed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/get-cruise-control-target-speed.md) | `abstract fun getCruiseControlTargetSpeed(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current cruise control target speed in meters per second (m/s). | CAR_DRIVE (protection level: normal) |
| [getCurrentDrivingModeState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/get-current-driving-mode-state.md) | `abstract fun getCurrentDrivingModeState(onComplete: (DrivingModeType?) -> Unit, onFailed: (Exception) -> Unit)` | Gets current driving mode state. | CAR_DRIVE (protection level: normal) |
| [registerCruiseControlState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/register-cruise-control-state.md) | `abstract fun registerCruiseControlState(listener: CruiseControlStateListener)` | Registers CruiseControlState to get property updates. | CAR_DRIVE (protection level: normal) |
| [registerCruiseControlTargetSpeed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/register-cruise-control-target-speed.md) | `abstract fun registerCruiseControlTargetSpeed(listener: CruiseControlTargetSpeedListener)` | Registers a listener for cruise control target speed updates in meters per second (m/s). | CAR_DRIVE (protection level: normal) |
| [registerDrivingModeState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/register-driving-mode-state.md) | `abstract fun registerDrivingModeState(listener: DrivingModeStateListener)` | Registers DrivingModeState to get property updates. | CAR_DRIVE (protection level: normal) |
| [unregisterCruiseControlState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/unregister-cruise-control-state.md) | `abstract fun unregisterCruiseControlState(listener: CruiseControlStateListener)` | Stops getting property updates for the given CruiseControlState. | CAR_DRIVE (protection level: normal) |
| [unregisterCruiseControlTargetSpeed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/unregister-cruise-control-target-speed.md) | `abstract fun unregisterCruiseControlTargetSpeed(listener: CruiseControlTargetSpeedListener)` | Stops getting property updates for the given CruiseControlTargetSpeed. | CAR_DRIVE (protection level: normal) |
| [unregisterDrivingModeState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/unregister-driving-mode-state.md) | `abstract fun unregisterDrivingModeState(listener: DrivingModeStateListener)` | Stops getting property updates for the given DrivingModeState. | CAR_DRIVE (protection level: normal) |

#### EvBattery
`interface EvBattery`  
Provides APIs for EV battery efficiency.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkCumulativeEvEfficiencyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-cumulative-ev-efficiency-capabilities.md) | `abstract fun checkCumulativeEvEfficiencyCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Cumulative EV Efficiency capabilities. | CAR_ENERGY (protection level: normal) |
| [checkCurrentEvEfficiencyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-current-ev-efficiency-capabilities.md) | `abstract fun checkCurrentEvEfficiencyCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Current EV Efficiency capabilities. | CAR_ENERGY (protection level: normal) |
| [checkEvBatteryStateOfChargeCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-ev-battery-state-of-charge-capabilities.md) | `abstract fun checkEvBatteryStateOfChargeCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets EV Battery State Of Charge capabilities. | CAR_ENERGY (protection level: normal) |
| [checkEvChargeCurrentDrawLimitCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-ev-charge-current-draw-limit-capabilities.md) | `abstract fun checkEvChargeCurrentDrawLimitCapabilities(onComplete: (CapabilityConfig, List<Float>) -> Unit, onFailed: (Exception) -> Unit)` | Gets capabilities for EV charge current draw limits in amperes (A). | CAR_ENERGY (protection level: normal) |
| [checkEvChargePercentLimitCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-ev-charge-percent-limit-capabilities.md) | `abstract fun checkEvChargePercentLimitCapabilities(onComplete: (CapabilityConfig, List<Float>) -> Unit, onFailed: (Exception) -> Unit)` | Gets capabilities for EV charge limits, each expressed as a percentage (%). | CAR_ENERGY (protection level: normal) |
| [checkEvChargePortOpenCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-ev-charge-port-open-capabilities.md) | `abstract fun checkEvChargePortOpenCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets EvChargePortOpen capabilities. | CAR_ENERGY (protection level: normal) |
| [checkEvChargeSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-ev-charge-switch-capabilities.md) | `abstract fun checkEvChargeSwitchCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets EvChargeSwitch capabilities. | CAR_ENERGY (protection level: normal) |
| [checkInstantaneousEvEfficiencyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-instantaneous-ev-efficiency-capabilities.md) | `abstract fun checkInstantaneousEvEfficiencyCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Instantaneous EV Efficiency capabilities. | CAR_ENERGY (protection level: normal) |
| [checkSinceChargeEvEfficiencyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/check-since-charge-ev-efficiency-capabilities.md) | `abstract fun checkSinceChargeEvEfficiencyCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets EV Efficiency Since Charge capabilities. | CAR_ENERGY (protection level: normal) |
| [getCumulativeEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-cumulative-ev-efficiency.md) | `abstract fun getCumulativeEvEfficiency(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets cumulative EV efficiency. | CAR_ENERGY (protection level: normal) |
| [getCurrentEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-current-ev-efficiency.md) | `abstract fun getCurrentEvEfficiency(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets current EV efficiency. | CAR_ENERGY (protection level: normal) |
| [getEvBatteryStateOfCharge](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-battery-state-of-charge.md) | `abstract fun getEvBatteryStateOfCharge(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets EV battery state of charge as a percentage (%). | CAR_ENERGY (protection level: normal) |
| [getEvChargeCurrentDrawLimit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-charge-current-draw-limit.md) | `abstract fun getEvChargeCurrentDrawLimit(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the EV charge current draw limit in amperes (A). | CAR_ENERGY (protection level: normal) |
| [getEvChargeSwitchState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-charge-switch-state.md) | `abstract fun getEvChargeSwitchState(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current EV battery charging state. | CAR_ENERGY (protection level: normal) |
| [getInstantaneousEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-instantaneous-ev-efficiency.md) | `abstract fun getInstantaneousEvEfficiency(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets instantaneous EV efficiency. | CAR_ENERGY (protection level: normal) |
| [getSinceChargeEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-since-charge-ev-efficiency.md) | `abstract fun getSinceChargeEvEfficiency(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets EV efficiency since charge. | CAR_ENERGY (protection level: normal) |
| [registerCumulativeEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/register-cumulative-ev-efficiency.md) | `abstract fun registerCumulativeEvEfficiency(listener: EvBatteryEfficiencyListener)` | Registers Cumulative EV Efficiency listener. | CAR_ENERGY (protection level: normal) |
| [registerCurrentEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/register-current-ev-efficiency.md) | `abstract fun registerCurrentEvEfficiency(listener: EvBatteryEfficiencyListener)` | Registers Current EV Efficiency listener. | CAR_ENERGY (protection level: normal) |
| [registerEvBatteryStateOfCharge](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/register-ev-battery-state-of-charge.md) | `abstract fun registerEvBatteryStateOfCharge(listener: EvBatteryStateOfChargeListener)` | Registers a listener for EV battery state of charge updates expressed as a percentage (%). | CAR_ENERGY (protection level: normal) |
| [registerInstantaneousEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/register-instantaneous-ev-efficiency.md) | `abstract fun registerInstantaneousEvEfficiency(listener: EvBatteryEfficiencyListener)` | Registers Instantaneous EV Efficiency listener. | CAR_ENERGY (protection level: normal) |
| [registerSinceChargeEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/register-since-charge-ev-efficiency.md) | `abstract fun registerSinceChargeEvEfficiency(listener: EvBatteryEfficiencyListener)` | Registers EV Efficiency Since Charge listener. | CAR_ENERGY (protection level: normal) |
| [setEvChargeCurrentDrawLimit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/set-ev-charge-current-draw-limit.md) | `abstract fun setEvChargeCurrentDrawLimit(value: Float, onComplete: (Float?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the EV charge current draw limit in amperes (A). | CONTROL_CAR_ENERGY (protection level: normal) |
| [setEvChargePercentLimit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/set-ev-charge-percent-limit.md) | `abstract fun setEvChargePercentLimit(value: Float, onComplete: (Float?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the battery state-of-charge limit for EV charging, expressed as a percentage (%). | CONTROL_CAR_ENERGY (protection level: normal) |
| [setEvChargePortOpen](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/set-ev-charge-port-open.md) | `abstract fun setEvChargePortOpen(value: Boolean, onComplete: (Boolean?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the EV charge port open state to On or Off. | CONTROL_CAR_ENERGY (protection level: normal) |
| [setEvChargeSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/set-ev-charge-switch.md) | `abstract fun setEvChargeSwitch(value: Boolean, onComplete: (Boolean?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets whether to start or stop EV battery charging. | CONTROL_CAR_ENERGY (protection level: normal) |
| [unregisterCumulativeEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/unregister-cumulative-ev-efficiency.md) | `abstract fun unregisterCumulativeEvEfficiency(listener: EvBatteryEfficiencyListener)` | Unregisters Cumulative EV Efficiency listener. | CAR_ENERGY (protection level: normal) |
| [unregisterCurrentEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/unregister-current-ev-efficiency.md) | `abstract fun unregisterCurrentEvEfficiency(listener: EvBatteryEfficiencyListener)` | Unregisters Current EV Efficiency listener. | CAR_ENERGY (protection level: normal) |
| [unregisterEvBatteryStateOfCharge](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/unregister-ev-battery-state-of-charge.md) | `abstract fun unregisterEvBatteryStateOfCharge(listener: EvBatteryStateOfChargeListener)` | Unregisters EV Battery State Of Charge listener. | CAR_ENERGY (protection level: normal) |
| [unregisterInstantaneousEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/unregister-instantaneous-ev-efficiency.md) | `abstract fun unregisterInstantaneousEvEfficiency(listener: EvBatteryEfficiencyListener)` | Unregisters Instantaneous EV Efficiency listener. | CAR_ENERGY (protection level: normal) |
| [unregisterSinceChargeEvEfficiency](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/unregister-since-charge-ev-efficiency.md) | `abstract fun unregisterSinceChargeEvEfficiency(listener: EvBatteryEfficiencyListener)` | Unregisters EV Efficiency Since Charge listener. | CAR_ENERGY (protection level: normal) |

#### Fuel
`interface Fuel`  
Provides APIs for fuel economy.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkCumulativeFuelEconomyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/check-cumulative-fuel-economy-capabilities.md) | `abstract fun checkCumulativeFuelEconomyCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Cumulative Fuel Economy capabilities. | CAR_ENERGY (protection level: normal) |
| [checkCurrentFuelEconomyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/check-current-fuel-economy-capabilities.md) | `abstract fun checkCurrentFuelEconomyCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Current Fuel Economy capabilities. | CAR_ENERGY (protection level: normal) |
| [checkFuelPercentCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/check-fuel-percent-capabilities.md) | `abstract fun checkFuelPercentCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Fuel Percent capabilities. | CAR_ENERGY (protection level: normal) |
| [checkInstantaneousFuelEconomyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/check-instantaneous-fuel-economy-capabilities.md) | `abstract fun checkInstantaneousFuelEconomyCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Instantaneous Fuel Economy capabilities. | CAR_ENERGY (protection level: normal) |
| [checkSinceChargeFuelEconomyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/check-since-charge-fuel-economy-capabilities.md) | `abstract fun checkSinceChargeFuelEconomyCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Fuel Economy Since Refuel capabilities. | CAR_ENERGY (protection level: normal) |
| [getCumulativeFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-cumulative-fuel-economy.md) | `abstract fun getCumulativeFuelEconomy(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets cumulative fuel economy. | CAR_ENERGY (protection level: normal) |
| [getCurrentFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-current-fuel-economy.md) | `abstract fun getCurrentFuelEconomy(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets current fuel economy. | CAR_ENERGY (protection level: normal) |
| [getFuelPercent](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-fuel-percent.md) | `abstract fun getFuelPercent(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the fuel level as a percentage (%). | CAR_ENERGY (protection level: normal) |
| [getInstantaneousFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-instantaneous-fuel-economy.md) | `abstract fun getInstantaneousFuelEconomy(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets instantaneous fuel economy. | CAR_ENERGY (protection level: normal) |
| [getSinceChargeFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/get-since-charge-fuel-economy.md) | `abstract fun getSinceChargeFuelEconomy(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets fuel economy since refuel. | CAR_ENERGY (protection level: normal) |
| [registerCumulativeFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/register-cumulative-fuel-economy.md) | `abstract fun registerCumulativeFuelEconomy(listener: FuelEconomyListener)` | Registers Cumulative Fuel Economy listener. | CAR_ENERGY (protection level: normal) |
| [registerCurrentFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/register-current-fuel-economy.md) | `abstract fun registerCurrentFuelEconomy(listener: FuelEconomyListener)` | Registers Current Fuel Economy listener. | CAR_ENERGY (protection level: normal) |
| [registerFuelPercent](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/register-fuel-percent.md) | `abstract fun registerFuelPercent(listener: FuelPercentListener)` | Registers a listener for fuel level updates expressed as a percentage (%). | CAR_ENERGY (protection level: normal) |
| [registerInstantaneousFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/register-instantaneous-fuel-economy.md) | `abstract fun registerInstantaneousFuelEconomy(listener: FuelEconomyListener)` | Registers Instantaneous Fuel Economy listener. | CAR_ENERGY (protection level: normal) |
| [registerSinceChargeFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/register-since-charge-fuel-economy.md) | `abstract fun registerSinceChargeFuelEconomy(listener: FuelEconomyListener)` | Registers Fuel Economy Since Refuel listener. | CAR_ENERGY (protection level: normal) |
| [unregisterCumulativeFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/unregister-cumulative-fuel-economy.md) | `abstract fun unregisterCumulativeFuelEconomy(listener: FuelEconomyListener)` | Unregisters Cumulative Fuel Economy listener. | CAR_ENERGY (protection level: normal) |
| [unregisterCurrentFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/unregister-current-fuel-economy.md) | `abstract fun unregisterCurrentFuelEconomy(listener: FuelEconomyListener)` | Unregisters Current Fuel Economy listener. | CAR_ENERGY (protection level: normal) |
| [unregisterFuelPercent](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/unregister-fuel-percent.md) | `abstract fun unregisterFuelPercent(listener: FuelPercentListener)` | Unregisters Fuel Percent listener. | CAR_ENERGY (protection level: normal) |
| [unregisterInstantaneousFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/unregister-instantaneous-fuel-economy.md) | `abstract fun unregisterInstantaneousFuelEconomy(listener: FuelEconomyListener)` | Unregisters Instantaneous Fuel Economy listener. | CAR_ENERGY (protection level: normal) |
| [unregisterSinceChargeFuelEconomy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/fuel/unregister-since-charge-fuel-economy.md) | `abstract fun unregisterSinceChargeFuelEconomy(listener: FuelEconomyListener)` | Unregisters Fuel Economy Since Refuel listener. | CAR_ENERGY (protection level: normal) |

#### HVAC
`interface HVAC`  
Provides APIs for climate control (HVAC).

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkHvacAcCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-ac-capabilities.md) | `abstract fun checkHvacAcCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HvacAC capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacAirCleanerCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-air-cleaner-capabilities.md) | `abstract fun checkHvacAirCleanerCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC air cleaner capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacAutoCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-auto-capabilities.md) | `abstract fun checkHvacAutoCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HvacAuto capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacAutoRecirculationCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-auto-recirculation-capabilities.md) | `abstract fun checkHvacAutoRecirculationCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC auto recirculation capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacDefrosterCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-defroster-capabilities.md) | `abstract fun checkHvacDefrosterCapabilities(onComplete: (CapabilityConfig, List<WindowArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC defroster capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacDualCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-dual-capabilities.md) | `abstract fun checkHvacDualCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC dual capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacFanDirectionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-fan-direction-capabilities.md) | `abstract fun checkHvacFanDirectionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, List<FanDirectionType>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC fan direction capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacFanSpeedCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-fan-speed-capabilities.md) | `abstract fun checkHvacFanSpeedCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC fan speed capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacMaxAcCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-max-ac-capabilities.md) | `abstract fun checkHvacMaxAcCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC max AC capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacMaxDefrostCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-max-defrost-capabilities.md) | `abstract fun checkHvacMaxDefrostCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC max defrost capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacPowerCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-power-capabilities.md) | `abstract fun checkHvacPowerCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC power capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacRecirculationCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-recirculation-capabilities.md) | `abstract fun checkHvacRecirculationCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC recirculation capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkHvacTemperatureCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-temperature-capabilities.md) | `abstract fun checkHvacTemperatureCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Float>>, HvacTemperatureConfigArray?) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC temperature capabilities including supported temperature ranges in degrees Celsius (°C) and the vehicle-specific temperature conversion config array. | CAR_CLIMATE (protection level: normal) |
| [checkHvacTemperatureCurrentCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/check-hvac-temperature-current-capabilities.md) | `abstract fun checkHvacTemperatureCurrentCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets HVAC temperature current capabilities. | CAR_CLIMATE (protection level: normal) |
| [getHvacAcState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-ac-state.md) | `abstract fun getHvacAcState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current AC state. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacAirCleanerState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-air-cleaner-state.md) | `abstract fun getHvacAirCleanerState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current air cleaner state. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacAutoRecirculationState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-auto-recirculation-state.md) | `abstract fun getHvacAutoRecirculationState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current automatic recirculation. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacAutoState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-auto-state.md) | `abstract fun getHvacAutoState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current automatic mode. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacDefrosterState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-defroster-state.md) | `abstract fun getHvacDefrosterState(area: WindowArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current defroster state. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacDualState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-dual-state.md) | `abstract fun getHvacDualState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current temperature coupling state between areas. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacFanDirection](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-fan-direction.md) | `abstract fun getHvacFanDirection(area: SeatArea, onComplete: (FanDirectionType?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current fan direction. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacFanSpeed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-fan-speed.md) | `abstract fun getHvacFanSpeed(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current fan speed. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacMaxAcState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-max-ac-state.md) | `abstract fun getHvacMaxAcState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current max AC state. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacMaxDefrostState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-max-defrost-state.md) | `abstract fun getHvacMaxDefrostState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current max defrost state. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacPowerState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-power-state.md) | `abstract fun getHvacPowerState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current global power state. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacRecirculationState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-recirculation-state.md) | `abstract fun getHvacRecirculationState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current recirculation state. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [getHvacTemperatureCurrent](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-temperature-current.md) | `abstract fun getHvacTemperatureCurrent(area: SeatArea, onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current internal temperature of the vehicle in degrees Celsius (°C). | CAR_CLIMATE (protection level: normal) |
| [getHvacTemperature](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-temperature.md) | `abstract fun getHvacTemperature(area: SeatArea, unit: TemperatureUnit, onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the target temperature in the specified `unit`. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [registerHvacAcState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-ac-state.md) | `abstract fun registerHvacAcState(area: SeatArea, listener: HvacAcStateListener)` | Registers the HvacAC state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacAirCleanerState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-air-cleaner-state.md) | `abstract fun registerHvacAirCleanerState(area: SeatArea, listener: HvacAirCleanerStateListener)` | Registers the HvacAirCleaner state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacAutoRecirculationState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-auto-recirculation-state.md) | `abstract fun registerHvacAutoRecirculationState(area: SeatArea, listener: HvacRecirculationStateListener)` | Registers the HvacAutoRecirculation state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacAutoState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-auto-state.md) | `abstract fun registerHvacAutoState(area: SeatArea, listener: HvacAutoStateListener)` | Registers the HvacAuto state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacDefrosterState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-defroster-state.md) | `abstract fun registerHvacDefrosterState(area: WindowArea, listener: HvacDefrosterStateListener)` | Registers the HvacDefroster state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacDualState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-dual-state.md) | `abstract fun registerHvacDualState(area: SeatArea, listener: HvacDualStateListener)` | Registers the HvacDual state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacFanSpeed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-fan-speed.md) | `abstract fun registerHvacFanSpeed(area: SeatArea, listener: HvacFanSpeedListener)` | Registers the HvacFanSpeed to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacMaxAcState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-max-ac-state.md) | `abstract fun registerHvacMaxAcState(area: SeatArea, listener: HvacMaxAcStateListener)` | Registers the HvacMaxAc state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacMaxDefrostState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-max-defrost-state.md) | `abstract fun registerHvacMaxDefrostState(area: SeatArea, listener: HvacMaxDefrostStateListener)` | Registers the HvacMaxDefrost state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacPowerState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-power-state.md) | `abstract fun registerHvacPowerState(area: SeatArea, listener: HvacPowerStateListener)` | Registers the HvacPower state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacRecirculationState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-recirculation-state.md) | `abstract fun registerHvacRecirculationState(area: SeatArea, listener: HvacRecirculationStateListener)` | Registers the HvacRecirculation to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerHvacTemperatureCurrent](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-temperature-current.md) | `abstract fun registerHvacTemperatureCurrent(area: SeatArea, listener: HvacTemperatureListener)` | Registers a listener for current interior temperature updates in degrees Celsius (°C). | CAR_CLIMATE (protection level: normal) |
| [registerHvacTemperature](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/register-hvac-temperature.md) | `abstract fun registerHvacTemperature(area: SeatArea, listener: HvacTemperatureListener)` | Registers a listener for target temperature updates in degrees Celsius (°C) only. | CAR_CLIMATE (protection level: normal) |
| [setHvacAc](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-ac.md) | `abstract fun setHvacAc(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets AC to On/Off for the designated areaId. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacAirCleaner](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-air-cleaner.md) | `abstract fun setHvacAirCleaner(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the air cleaner to On/Off. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacAutoRecirculation](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-auto-recirculation.md) | `abstract fun setHvacAutoRecirculation(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the automatic recirculation to On/Off. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacAuto](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-auto.md) | `abstract fun setHvacAuto(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets automatic mode to On/Off. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacDefroster](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-defroster.md) | `abstract fun setHvacDefroster(area: WindowArea, value: Boolean, onComplete: (WindowArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the defroster to On/Off. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacDual](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-dual.md) | `abstract fun setHvacDual(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Enable temperature coupling between areas. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacFanDirection](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-fan-direction.md) | `abstract fun setHvacFanDirection(area: SeatArea, value: FanDirectionType, onComplete: (SeatArea, FanDirectionType?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets fan direction. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacFanSpeed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-fan-speed.md) | `abstract fun setHvacFanSpeed(area: SeatArea, value: Int, onComplete: (SeatArea, Int?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the fan speed. The supported fan speed range is vehicle-dependent and is not fixed. Call checkHvacFanSpeedCapabilities first. That API's callback returns CapabilityConfig plus a Map<SeatArea, AreaBounds<Int>>; read each area's AreaBounds.minValue/AreaBounds.maxValue from that map (not from CapabilityConfig). | CONTROL_CAR_CLIMATE (protection level: normal) |
| [setHvacMaxAc](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-max-ac.md) | `abstract fun setHvacMaxAc(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the max AC to On/Off. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacMaxDefrost](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-max-defrost.md) | `abstract fun setHvacMaxDefrost(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the max defrost to On/Off. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacPower](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-power.md) | `abstract fun setHvacPower(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the global power state to On/Off. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacRecirculation](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-recirculation.md) | `abstract fun setHvacRecirculation(area: SeatArea, value: Boolean, onComplete: (SeatArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets recirculation to On/Off. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [setHvacTemperature](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/set-hvac-temperature.md) | `abstract fun setHvacTemperature(area: SeatArea, value: Float, unit: TemperatureUnit, onComplete: (SeatArea, Float?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the target temperature in the specified `unit`. The supported temperature range is vehicle-dependent and is not fixed. Call checkHvacTemperatureCapabilities first. That API's callback returns CapabilityConfig, a Map<SeatArea, AreaBounds<Float>>, and optional HvacTemperatureConfigArray; read each area's AreaBounds.minValue/AreaBounds.maxValue from that map (not from CapabilityConfig). AreaBounds are in degrees Celsius (°C); use the config array when converting to Fahrenheit. | CONTROL_CAR_CLIMATE (protection level: normal) |
| [unregisterHvacAcState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-ac-state.md) | `abstract fun unregisterHvacAcState(area: SeatArea, listener: HvacAcStateListener)` | Stops getting property updates for the given HvacAC state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacAirCleanerState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-air-cleaner-state.md) | `abstract fun unregisterHvacAirCleanerState(area: SeatArea, listener: HvacAirCleanerStateListener)` | Stops getting property updates for the given HvacAirCleaner state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacAutoRecirculationState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-auto-recirculation-state.md) | `abstract fun unregisterHvacAutoRecirculationState(area: SeatArea, listener: HvacRecirculationStateListener)` | Stops getting property updates for the given HvacAutoRecirculation state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacAutoState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-auto-state.md) | `abstract fun unregisterHvacAutoState(area: SeatArea, listener: HvacAutoStateListener)` | Stops getting property updates for the given HvacAuto state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacDefrosterState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-defroster-state.md) | `abstract fun unregisterHvacDefrosterState(area: WindowArea, listener: HvacDefrosterStateListener)` | Stops getting property updates for the given HvacDefroster state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacDualState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-dual-state.md) | `abstract fun unregisterHvacDualState(area: SeatArea, listener: HvacDualStateListener)` | Stops getting property updates for the given HvacDual state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacFanSpeed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-fan-speed.md) | `abstract fun unregisterHvacFanSpeed(area: SeatArea, listener: HvacFanSpeedListener)` | Stops getting property updates for the given HvacFanSpeed. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacMaxAcState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-max-ac-state.md) | `abstract fun unregisterHvacMaxAcState(area: SeatArea, listener: HvacMaxAcStateListener)` | Stop getting property updates for the given HvacMaxAc state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacMaxDefrostState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-max-defrost-state.md) | `abstract fun unregisterHvacMaxDefrostState(area: SeatArea, listener: HvacMaxDefrostStateListener)` | Stops getting property updates for the given HvacMaxDefrost state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacPowerState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-power-state.md) | `abstract fun unregisterHvacPowerState(area: SeatArea, listener: HvacPowerStateListener)` | Stops getting property updates for the given HvacPower state. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacRecirculationState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-recirculation-state.md) | `abstract fun unregisterHvacRecirculationState(area: SeatArea, listener: HvacRecirculationStateListener)` | Stops getting property updates for the given HvacRecirculation. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacTemperatureCurrent](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-temperature-current.md) | `abstract fun unregisterHvacTemperatureCurrent(area: SeatArea, listener: HvacTemperatureListener)` | Stops getting property updates for the given HvacTemperatureCurrent. | CAR_CLIMATE (protection level: normal) |
| [unregisterHvacTemperature](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/unregister-hvac-temperature.md) | `abstract fun unregisterHvacTemperature(area: SeatArea, listener: HvacTemperatureListener)` | Stops getting property updates for the given HvacTemperature. | CAR_CLIMATE (protection level: normal) |

#### Light
`interface Light`  
Provides APIs for exterior, cabin, and ambient lights.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkAmbientColorAutoByDriveModeEnabledCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-ambient-color-auto-by-drive-mode-enabled-capabilities.md) | `abstract fun checkAmbientColorAutoByDriveModeEnabledCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Ambient Color Auto By Drive Mode Enabled capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkAmbientLightBrightnessCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-ambient-light-brightness-capabilities.md) | `abstract fun checkAmbientLightBrightnessCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets ambient light brightness capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkAmbientLightBrightnessColorCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-ambient-light-brightness-color-capabilities.md) | `abstract fun checkAmbientLightBrightnessColorCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets ambient light brightness color capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkAmbientLightStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-ambient-light-state-capabilities.md) | `abstract fun checkAmbientLightStateCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, List<LightState>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets ambient light state capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkAmbientLightSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-ambient-light-switch-capabilities.md) | `abstract fun checkAmbientLightSwitchCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, List<LightSwitch>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets ambient light switch capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkCabinLightStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-cabin-light-state-capabilities.md) | `abstract fun checkCabinLightStateCapabilities(onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets cabin light state capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkCabinLightSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-cabin-light-switch-capabilities.md) | `abstract fun checkCabinLightSwitchCapabilities(onComplete: (CapabilityConfig, List<LightSwitch>) -> Unit, onFailed: (Exception) -> Unit)` | Gets cabin light switch capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkFrontFogLightStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-front-fog-light-state-capabilities.md) | `abstract fun checkFrontFogLightStateCapabilities(onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets front fog light state capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkFrontFogLightSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-front-fog-light-switch-capabilities.md) | `abstract fun checkFrontFogLightSwitchCapabilities(onComplete: (CapabilityConfig, List<LightSwitch>) -> Unit, onFailed: (Exception) -> Unit)` | Gets front fog light switch capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkHighBeamLightStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-high-beam-light-state-capabilities.md) | `abstract fun checkHighBeamLightStateCapabilities(onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets high beam light state capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkHighBeamLightSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-high-beam-light-switch-capabilities.md) | `abstract fun checkHighBeamLightSwitchCapabilities(onComplete: (CapabilityConfig, List<LightSwitch>) -> Unit, onFailed: (Exception) -> Unit)` | Gets high beam light switch capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkLowBeamLightStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-low-beam-light-state-capabilities.md) | `abstract fun checkLowBeamLightStateCapabilities(onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets low beam light state capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkLowBeamLightSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-low-beam-light-switch-capabilities.md) | `abstract fun checkLowBeamLightSwitchCapabilities(onComplete: (CapabilityConfig, List<LightSwitch>) -> Unit, onFailed: (Exception) -> Unit)` | Gets low beam light switch capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkReadingLightStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-reading-light-state-capabilities.md) | `abstract fun checkReadingLightStateCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, List<LightState>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets reading light state capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkReadingLightSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-reading-light-switch-capabilities.md) | `abstract fun checkReadingLightSwitchCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, List<LightSwitch>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets reading light switch capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkRearFogLightStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-rear-fog-light-state-capabilities.md) | `abstract fun checkRearFogLightStateCapabilities(onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets rear fog light state capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkRearFogLightSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-rear-fog-light-switch-capabilities.md) | `abstract fun checkRearFogLightSwitchCapabilities(onComplete: (CapabilityConfig, List<LightSwitch>) -> Unit, onFailed: (Exception) -> Unit)` | Gets rear fog light switch capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkTailLampLightStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-tail-lamp-light-state-capabilities.md) | `abstract fun checkTailLampLightStateCapabilities(onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets tail lamp light state capabilities. | CAR_LIGHTS (protection level: normal) |
| [checkTailLampLightSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/check-tail-lamp-light-switch-capabilities.md) | `abstract fun checkTailLampLightSwitchCapabilities(onComplete: (CapabilityConfig, List<LightSwitch>) -> Unit, onFailed: (Exception) -> Unit)` | Gets tail lamp light switch capabilities. | CAR_LIGHTS (protection level: normal) |
| [getAmbientColorAutoByDriveModeEnabled](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-ambient-color-auto-by-drive-mode-enabled.md) | `abstract fun getAmbientColorAutoByDriveModeEnabled(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets whether Ambient Color Auto By Drive Mode is enabled. | CAR_LIGHTS (protection level: normal) |
| [getAmbientLightBrightnessColor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-ambient-light-brightness-color.md) | `abstract fun getAmbientLightBrightnessColor(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current Ambient Light Brightness Color (32-bit ARGB) for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [getAmbientLightBrightness](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-ambient-light-brightness.md) | `abstract fun getAmbientLightBrightness(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current Ambient Light Brightness for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [getAmbientLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-ambient-light-state.md) | `abstract fun getAmbientLightState(area: SeatArea, onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current Ambient Light State for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [getAmbientLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-ambient-light-switch.md) | `abstract fun getAmbientLightSwitch(area: SeatArea, onComplete: (LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current Ambient Light Switch for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [getCabinLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-cabin-light-switch.md) | `abstract fun getCabinLightSwitch(onComplete: (LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current cabin light switch. | CAR_LIGHTS (protection level: normal) |
| [getCurrentCabinLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-current-cabin-light-state.md) | `abstract fun getCurrentCabinLightState(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current cabin light state. | CAR_LIGHTS (protection level: normal) |
| [getCurrentFrontFogLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-current-front-fog-light-state.md) | `abstract fun getCurrentFrontFogLightState(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current front fog light state. | CAR_LIGHTS (protection level: normal) |
| [getCurrentHighBeamLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-current-high-beam-light-state.md) | `abstract fun getCurrentHighBeamLightState(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current high beam light state. | CAR_LIGHTS (protection level: normal) |
| [getCurrentLowBeamLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-current-low-beam-light-state.md) | `abstract fun getCurrentLowBeamLightState(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current low beam light state. | CAR_LIGHTS (protection level: normal) |
| [getCurrentReadingLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-current-reading-light-state.md) | `abstract fun getCurrentReadingLightState(area: SeatArea, onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current reading light state. | CAR_LIGHTS (protection level: normal) |
| [getCurrentRearFogLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-current-rear-fog-light-state.md) | `abstract fun getCurrentRearFogLightState(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current rear fog light state. | CAR_LIGHTS (protection level: normal) |
| [getCurrentTailLampLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-current-tail-lamp-light-state.md) | `abstract fun getCurrentTailLampLightState(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current tail lamp light state. | CAR_LIGHTS (protection level: normal) |
| [getFrontFogLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-front-fog-light-switch.md) | `abstract fun getFrontFogLightSwitch(onComplete: (LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current front fog light switch. | CAR_LIGHTS (protection level: normal) |
| [getHighBeamLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-high-beam-light-switch.md) | `abstract fun getHighBeamLightSwitch(onComplete: (LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current high beam light switch. | CAR_LIGHTS (protection level: normal) |
| [getLowBeamLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-low-beam-light-switch.md) | `abstract fun getLowBeamLightSwitch(onComplete: (LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current low beam light switch. | CAR_LIGHTS (protection level: normal) |
| [getReadingLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-reading-light-switch.md) | `abstract fun getReadingLightSwitch(area: SeatArea, onComplete: (LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current reading light switch. | CAR_LIGHTS (protection level: normal) |
| [getRearFogLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-rear-fog-light-switch.md) | `abstract fun getRearFogLightSwitch(onComplete: (LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current rear fog light switch. | CAR_LIGHTS (protection level: normal) |
| [getTailLampLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/get-tail-lamp-light-switch.md) | `abstract fun getTailLampLightSwitch(onComplete: (LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current tail lamp light switch. | CAR_LIGHTS (protection level: normal) |
| [registerAmbientColorAutoByDriveModeEnabled](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-ambient-color-auto-by-drive-mode-enabled.md) | `abstract fun registerAmbientColorAutoByDriveModeEnabled(listener: AmbientColorAutoByDriveModeEnabledListener)` | Registers a listener to receive Ambient Color Auto By Drive Mode Enabled updates. | CAR_LIGHTS (protection level: normal) |
| [registerAmbientLightBrightnessColorListener](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-ambient-light-brightness-color-listener.md) | `abstract fun registerAmbientLightBrightnessColorListener(area: SeatArea, listener: AmbientLightBrightnessColorListener)` | Registers a listener to receive Ambient Light Brightness Color updates for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [registerAmbientLightBrightnessListener](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-ambient-light-brightness-listener.md) | `abstract fun registerAmbientLightBrightnessListener(area: SeatArea, listener: AmbientLightBrightnessListener)` | Registers a listener to receive Ambient Light Brightness updates for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [registerAmbientLightStateListener](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-ambient-light-state-listener.md) | `abstract fun registerAmbientLightStateListener(area: SeatArea, listener: LightStateListener)` | Registers a listener to receive Ambient Light State updates for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [registerCabinLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-cabin-light-state.md) | `abstract fun registerCabinLightState(listener: LightStateListener)` | Registers the CabinLightState to get property updates. | CAR_LIGHTS (protection level: normal) |
| [registerFrontFogLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-front-fog-light-state.md) | `abstract fun registerFrontFogLightState(listener: LightStateListener)` | Registers the FrontFogLightState to get property updates. | CAR_LIGHTS (protection level: normal) |
| [registerHighBeamLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-high-beam-light-state.md) | `abstract fun registerHighBeamLightState(listener: LightStateListener)` | Registers the HighBeamLightState to get property updates. | CAR_LIGHTS (protection level: normal) |
| [registerLowBeamLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-low-beam-light-state.md) | `abstract fun registerLowBeamLightState(listener: LightStateListener)` | Registers the LowBeamLightState to get property updates. | CAR_LIGHTS (protection level: normal) |
| [registerReadingLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-reading-light-state.md) | `abstract fun registerReadingLightState(area: SeatArea, listener: LightStateListener)` | Registers the ReadingLightState to get property updates. | CAR_LIGHTS (protection level: normal) |
| [registerRearFogLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-rear-fog-light-state.md) | `abstract fun registerRearFogLightState(listener: LightStateListener)` | Registers the RearFogLightState to get property updates. | CAR_LIGHTS (protection level: normal) |
| [registerTailLampLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/register-tail-lamp-light-state.md) | `abstract fun registerTailLampLightState(listener: LightStateListener)` | Registers the TailLampLightState to get property updates. | CAR_LIGHTS (protection level: normal) |
| [setAmbientColorAutoByDriveModeEnabled](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-ambient-color-auto-by-drive-mode-enabled.md) | `abstract fun setAmbientColorAutoByDriveModeEnabled(value: Boolean, onComplete: (Boolean?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets whether Ambient Color Auto By Drive Mode is enabled. | CONTROL_CAR_LIGHTS (protection level: normal) |
| [setAmbientLightBrightnessColor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-ambient-light-brightness-color.md) | `abstract fun setAmbientLightBrightnessColor(area: SeatArea, value: Int, onComplete: (SeatArea, Int) -> Unit, onFailed: (Exception) -> Unit)` | Sets the Ambient Light Brightness Color for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [setAmbientLightBrightness](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-ambient-light-brightness.md) | `abstract fun setAmbientLightBrightness(area: SeatArea, value: Int, onComplete: (SeatArea, Int) -> Unit, onFailed: (Exception) -> Unit)` | Sets the Ambient Light Brightness for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [setAmbientLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-ambient-light-switch.md) | `abstract fun setAmbientLightSwitch(area: SeatArea, value: LightSwitch, onComplete: (SeatArea, LightSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Sets the Ambient Light Switch for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [setCabinLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-cabin-light-switch.md) | `abstract fun setCabinLightSwitch(value: LightSwitch, onComplete: (LightSwitch?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the cabin light switch. | CONTROL_CAR_LIGHTS (protection level: normal) |
| [setFrontFogLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-front-fog-light-switch.md) | `abstract fun setFrontFogLightSwitch(value: LightSwitch, onComplete: (LightSwitch?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the front fog light switch. | CONTROL_CAR_LIGHTS (protection level: normal) |
| [setHighBeamLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-high-beam-light-switch.md) | `abstract fun setHighBeamLightSwitch(value: LightSwitch, onComplete: (LightSwitch?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the high beam light switch. | CONTROL_CAR_LIGHTS (protection level: normal) |
| [setLowBeamLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-low-beam-light-switch.md) | `abstract fun setLowBeamLightSwitch(value: LightSwitch, onComplete: (LightSwitch?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the low beam light switch. | CONTROL_CAR_LIGHTS (protection level: normal) |
| [setReadingLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-reading-light-switch.md) | `abstract fun setReadingLightSwitch(area: SeatArea, value: LightSwitch, onComplete: (LightSwitch?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the reading light switch. | CONTROL_CAR_LIGHTS (protection level: normal) |
| [setRearFogLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-rear-fog-light-switch.md) | `abstract fun setRearFogLightSwitch(value: LightSwitch, onComplete: (LightSwitch?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the rear fog light switch. | CONTROL_CAR_LIGHTS (protection level: normal) |
| [setTailLampLightSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-tail-lamp-light-switch.md) | `abstract fun setTailLampLightSwitch(value: LightSwitch, onComplete: (LightSwitch?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets the tail lamp light switch. | CONTROL_CAR_LIGHTS (protection level: normal) |
| [unregisterAmbientColorAutoByDriveModeEnabled](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-ambient-color-auto-by-drive-mode-enabled.md) | `abstract fun unregisterAmbientColorAutoByDriveModeEnabled(listener: AmbientColorAutoByDriveModeEnabledListener)` | Unregisters a previously registered Ambient Color Auto By Drive Mode Enabled listener. | CAR_LIGHTS (protection level: normal) |
| [unregisterAmbientLightBrightnessColorListener](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-ambient-light-brightness-color-listener.md) | `abstract fun unregisterAmbientLightBrightnessColorListener(area: SeatArea, listener: AmbientLightBrightnessColorListener)` | Unregisters a previously registered Ambient Light Brightness Color listener for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [unregisterAmbientLightBrightnessListener](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-ambient-light-brightness-listener.md) | `abstract fun unregisterAmbientLightBrightnessListener(area: SeatArea, listener: AmbientLightBrightnessListener)` | Unregisters a previously registered Ambient Light Brightness listener for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [unregisterAmbientLightStateListener](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-ambient-light-state-listener.md) | `abstract fun unregisterAmbientLightStateListener(area: SeatArea, listener: LightStateListener)` | Unregisters a previously registered Ambient Light State listener for the specified seat area. | CAR_LIGHTS (protection level: normal) |
| [unregisterCabinLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-cabin-light-state.md) | `abstract fun unregisterCabinLightState(listener: LightStateListener)` | Stops getting property updates for the given CabinLightState. | CAR_LIGHTS (protection level: normal) |
| [unregisterFrontFogLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-front-fog-light-state.md) | `abstract fun unregisterFrontFogLightState(listener: LightStateListener)` | Stops getting property updates for the given FrontFogLightState. | CAR_LIGHTS (protection level: normal) |
| [unregisterHighBeamLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-high-beam-light-state.md) | `abstract fun unregisterHighBeamLightState(listener: LightStateListener)` | Stops getting property updates for the given HighBeamLightState. | CAR_LIGHTS (protection level: normal) |
| [unregisterLowBeamLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-low-beam-light-state.md) | `abstract fun unregisterLowBeamLightState(listener: LightStateListener)` | Stops getting property updates for the given LowBeamLightState. | CAR_LIGHTS (protection level: normal) |
| [unregisterReadingLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-reading-light-state.md) | `abstract fun unregisterReadingLightState(area: SeatArea, listener: LightStateListener)` | Stops getting property updates for the given ReadingLightState. | CAR_LIGHTS (protection level: normal) |
| [unregisterRearFogLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-rear-fog-light-state.md) | `abstract fun unregisterRearFogLightState(listener: LightStateListener)` | Stops getting property updates for the given RearFogLightState. | CAR_LIGHTS (protection level: normal) |
| [unregisterTailLampLightState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/unregister-tail-lamp-light-state.md) | `abstract fun unregisterTailLampLightState(listener: LightStateListener)` | Stops getting property updates for the given TailLampLightState. | CAR_LIGHTS (protection level: normal) |

#### Odometer
`interface Odometer`  
Provides APIs for odometer and trip data.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkCurrentDistanceDrivenCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-current-distance-driven-capabilities.md) | `abstract fun checkCurrentDistanceDrivenCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets current distance driven capabilities. | CAR_DRIVE (protection level: normal) |
| [checkTripCumulativeDrivingDistanceCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-cumulative-driving-distance-capabilities.md) | `abstract fun checkTripCumulativeDrivingDistanceCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Trip Cumulative Driving Distance capabilities. | CAR_DRIVE (protection level: normal) |
| [checkTripCumulativeDrivingTimeCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-cumulative-driving-time-capabilities.md) | `abstract fun checkTripCumulativeDrivingTimeCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Trip Cumulative Driving Time capabilities. | CAR_DRIVE (protection level: normal) |
| [checkTripCurrentDrivingDistanceCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-current-driving-distance-capabilities.md) | `abstract fun checkTripCurrentDrivingDistanceCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Trip Current Driving Distance capabilities. | CAR_DRIVE (protection level: normal) |
| [checkTripCurrentDrivingTimeCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-current-driving-time-capabilities.md) | `abstract fun checkTripCurrentDrivingTimeCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Trip Current Driving Time capabilities. | CAR_DRIVE (protection level: normal) |
| [checkTripDrivingDistanceCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-driving-distance-capabilities.md) | `abstract fun checkTripDrivingDistanceCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Trip Driving Distance capabilities. | CAR_DRIVE (protection level: normal) |
| [checkTripDrivingTimeCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-driving-time-capabilities.md) | `abstract fun checkTripDrivingTimeCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Trip Driving Time capabilities. | CAR_DRIVE (protection level: normal) |
| [checkTripInstantaneousDrivingDistanceCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-instantaneous-driving-distance-capabilities.md) | `abstract fun ~~checkTripInstantaneousDrivingDistanceCapabilities~~(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated because it uses TripStatisticType.INSTANTANEOUS. | CAR_DRIVE (protection level: normal) |
| [checkTripSinceRefillDrivingDistanceCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-since-refill-driving-distance-capabilities.md) | `abstract fun checkTripSinceRefillDrivingDistanceCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Trip Since Refuel Driving Distance capabilities. | CAR_DRIVE (protection level: normal) |
| [checkTripSinceRefillDrivingTimeCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/check-trip-since-refill-driving-time-capabilities.md) | `abstract fun checkTripSinceRefillDrivingTimeCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets Trip Since Refuel Driving Time capabilities. | CAR_DRIVE (protection level: normal) |
| [getCurrentDistanceDriven](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-current-distance-driven.md) | `abstract fun getCurrentDistanceDriven(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Current odometer value of the vehicle in kilometers (km). | CAR_DRIVE (protection level: normal) |
| [getTripCumulativeDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-cumulative-driving-distance.md) | `abstract fun getTripCumulativeDrivingDistance(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets trip cumulative driving distance in meters (m). | CAR_DRIVE (protection level: normal) |
| [getTripCumulativeDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-cumulative-driving-time.md) | `abstract fun getTripCumulativeDrivingTime(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets trip cumulative driving time. | CAR_DRIVE (protection level: normal) |
| [getTripCurrentDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-current-driving-distance.md) | `abstract fun getTripCurrentDrivingDistance(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets trip current driving distance in meters (m). | CAR_DRIVE (protection level: normal) |
| [getTripCurrentDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-current-driving-time.md) | `abstract fun getTripCurrentDrivingTime(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets trip current driving time. | CAR_DRIVE (protection level: normal) |
| [getTripInstantaneousDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-instantaneous-driving-distance.md) | `abstract fun ~~getTripInstantaneousDrivingDistance~~(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated because it uses TripStatisticType.INSTANTANEOUS. | CAR_DRIVE (protection level: normal) |
| [getTripSinceRefillDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-since-refill-driving-distance.md) | `abstract fun getTripSinceRefillDrivingDistance(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets trip since refuel driving distance in meters (m). | CAR_DRIVE (protection level: normal) |
| [getTripSinceRefillDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/get-trip-since-refill-driving-time.md) | `abstract fun getTripSinceRefillDrivingTime(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets trip since refuel driving time. | CAR_DRIVE (protection level: normal) |
| [registerDistanceDriven](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-distance-driven.md) | `abstract fun registerDistanceDriven(listener: DistanceDrivenListener)` | Registers a listener for odometer updates in kilometers (km). | CAR_DRIVE (protection level: normal) |
| [registerTripCumulativeDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-cumulative-driving-distance.md) | `abstract fun registerTripCumulativeDrivingDistance(listener: DistanceDrivenListener)` | Registers a listener for trip cumulative driving distance updates in meters (m). | CAR_DRIVE (protection level: normal) |
| [registerTripCumulativeDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-cumulative-driving-time.md) | `abstract fun registerTripCumulativeDrivingTime(listener: DrivingTimeListener)` | Registers Trip Cumulative Driving Time listener. | CAR_DRIVE (protection level: normal) |
| [registerTripCurrentDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-current-driving-distance.md) | `abstract fun registerTripCurrentDrivingDistance(listener: DistanceDrivenListener)` | Registers a listener for trip current driving distance updates in meters (m). | CAR_DRIVE (protection level: normal) |
| [registerTripCurrentDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-current-driving-time.md) | `abstract fun registerTripCurrentDrivingTime(listener: DrivingTimeListener)` | Registers Trip Current Driving Time listener. | CAR_DRIVE (protection level: normal) |
| [registerTripInstantaneousDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-instantaneous-driving-distance.md) | `abstract fun ~~registerTripInstantaneousDrivingDistance~~(listener: DistanceDrivenListener)` | This API is deprecated because it uses TripStatisticType.INSTANTANEOUS. | CAR_DRIVE (protection level: normal) |
| [registerTripSinceRefillDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-since-refill-driving-distance.md) | `abstract fun registerTripSinceRefillDrivingDistance(listener: DistanceDrivenListener)` | Registers a listener for trip since refuel driving distance updates in meters (m). | CAR_DRIVE (protection level: normal) |
| [registerTripSinceRefillDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/register-trip-since-refill-driving-time.md) | `abstract fun registerTripSinceRefillDrivingTime(listener: DrivingTimeListener)` | Registers Trip Since Refuel Driving Time listener. | CAR_DRIVE (protection level: normal) |
| [setRangeRemainingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/set-range-remaining-distance.md) | `abstract fun ~~setRangeRemainingDistance~~(value: Float, onComplete: (Float?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | This API is deprecated |  |
| [unregisterDistanceDriven](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-distance-driven.md) | `abstract fun unregisterDistanceDriven(listener: DistanceDrivenListener)` | Stops getting property updates for the given DistanceDriven. | CAR_DRIVE (protection level: normal) |
| [unregisterTripCumulativeDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-cumulative-driving-distance.md) | `abstract fun unregisterTripCumulativeDrivingDistance(listener: DistanceDrivenListener)` | Unregisters Trip Cumulative Driving Distance listener. | CAR_DRIVE (protection level: normal) |
| [unregisterTripCumulativeDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-cumulative-driving-time.md) | `abstract fun unregisterTripCumulativeDrivingTime(listener: DrivingTimeListener)` | Unregisters Trip Cumulative Driving Time listener. | CAR_DRIVE (protection level: normal) |
| [unregisterTripCurrentDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-current-driving-distance.md) | `abstract fun unregisterTripCurrentDrivingDistance(listener: DistanceDrivenListener)` | Unregisters Trip Current Driving Distance listener. | CAR_DRIVE (protection level: normal) |
| [unregisterTripCurrentDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-current-driving-time.md) | `abstract fun unregisterTripCurrentDrivingTime(listener: DrivingTimeListener)` | Unregisters Trip Current Driving Time listener. | CAR_DRIVE (protection level: normal) |
| [unregisterTripInstantaneousDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-instantaneous-driving-distance.md) | `abstract fun ~~unregisterTripInstantaneousDrivingDistance~~(listener: DistanceDrivenListener)` | This API is deprecated because it uses TripStatisticType.INSTANTANEOUS. | CAR_DRIVE (protection level: normal) |
| [unregisterTripSinceRefillDrivingDistance](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-since-refill-driving-distance.md) | `abstract fun unregisterTripSinceRefillDrivingDistance(listener: DistanceDrivenListener)` | Unregisters Trip Since Refuel Driving Distance listener. | CAR_DRIVE (protection level: normal) |
| [unregisterTripSinceRefillDrivingTime](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/unregister-trip-since-refill-driving-time.md) | `abstract fun unregisterTripSinceRefillDrivingTime(listener: DrivingTimeListener)` | Unregisters Trip Since Refuel Driving Time listener. | CAR_DRIVE (protection level: normal) |

#### Safety
`interface Safety`  
Provides APIs for airbags, warnings, fluid levels, and telltales.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkAirbagCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-airbag-capabilities.md) | `abstract fun checkAirbagCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets airbag capabilities. | CAR_SAFETY (protection level: normal) |
| [checkAirbagDeployedCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-airbag-deployed-capabilities.md) | `abstract fun checkAirbagDeployedCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets airbag deployed capabilities. | CAR_SAFETY (protection level: normal) |
| [checkBrakeFluidLevelStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-brake-fluid-level-state-capabilities.md) | `abstract fun checkBrakeFluidLevelStateCapabilities(onComplete: (CapabilityConfig, List<OilLevelState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets brake fluid level state capabilities. | CAR_SAFETY (protection level: normal) |
| [checkEngineOilLevelStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-engine-oil-level-state-capabilities.md) | `abstract fun checkEngineOilLevelStateCapabilities(onComplete: (CapabilityConfig, List<OilLevelState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets engine oil level state capabilities. | CAR_SAFETY (protection level: normal) |
| [checkLowWasherFluidWarningStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-low-washer-fluid-warning-state-capabilities.md) | `abstract fun checkLowWasherFluidWarningStateCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets low washer fluid warning state capabilities. | CAR_SAFETY (protection level: normal) |
| [checkPowerDownWarningIndicatorCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-power-down-warning-indicator-capabilities.md) | `abstract fun checkPowerDownWarningIndicatorCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets power down warning indicator capabilities. | CAR_SAFETY (protection level: normal) |
| [checkSeatBuckledCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-seat-buckled-capabilities.md) | `abstract fun checkSeatBuckledCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat buckled capabilities. | CAR_SAFETY (protection level: normal) |
| [checkSeatNotBuckledWarningCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-seat-not-buckled-warning-capabilities.md) | `abstract fun checkSeatNotBuckledWarningCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat not buckled warning capabilities. | CAR_SAFETY (protection level: normal) |
| [checkTelltalesCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-telltales-capabilities.md) | `abstract fun checkTelltalesCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets telltales capabilities. | PRIVATE_CAR_SAFETY (protection level: signature|privileged) |
| [checkTransmissionOilLevelStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/check-transmission-oil-level-state-capabilities.md) | `abstract fun checkTransmissionOilLevelStateCapabilities(onComplete: (CapabilityConfig, List<OilLevelState>) -> Unit, onFailed: (Exception) -> Unit)` | Gets transmission oil level state capabilities. | CAR_SAFETY (protection level: normal) |
| [getBrakeFluidLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-brake-fluid-level-state.md) | `abstract fun getBrakeFluidLevelState(onComplete: (OilLevelState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current brake fluid level. | CAR_SAFETY (protection level: normal) |
| [getEngineOilLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-engine-oil-level-state.md) | `abstract fun getEngineOilLevelState(onComplete: (OilLevelState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current engine oil level. | CAR_SAFETY (protection level: normal) |
| [getTelltalesInfo](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-telltales-info.md) | `abstract fun getTelltalesInfo(onComplete: (telltales: Telltales) -> Unit, onFailed: (Exception) -> Unit)` | Gets telltales info. | PRIVATE_CAR_SAFETY (protection level: signature|privileged) |
| [getTransmissionOilLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-transmission-oil-level-state.md) | `abstract fun getTransmissionOilLevelState(onComplete: (OilLevelState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current transmission oil level. | CAR_SAFETY (protection level: normal) |
| [hasLowWasherFluidWarning](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/has-low-washer-fluid-warning.md) | `abstract fun hasLowWasherFluidWarning(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Checks the current washer fluid low warning status. If the status is true, there is an issue with the washer fluid | CAR_SAFETY (protection level: normal) |
| [hasPowerDownWarningIndicator](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/has-power-down-warning-indicator.md) | `abstract fun hasPowerDownWarningIndicator(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current power down warning indicator state. If the state is true, there is a problem with the engine. | CAR_SAFETY (protection level: normal) |
| [hasSeatAirbagBeenDeployed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/has-seat-airbag-been-deployed.md) | `abstract fun hasSeatAirbagBeenDeployed(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current airbag deployment status. If the state is true, the airbag is deployed. | CAR_SAFETY (protection level: normal) |
| [hasSeatAirbagEnabled](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/has-seat-airbag-enabled.md) | `abstract fun hasSeatAirbagEnabled(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the seat airbag enabled state. If the state is true, there is a problem with the seat airbag in the `area`. | CAR_SAFETY (protection level: normal) |
| [hasSeatBeltNotBuckledWarning](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/has-seat-belt-not-buckled-warning.md) | `abstract fun hasSeatBeltNotBuckledWarning(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the seat belt buckled warning state. If the state is true, the seat belt in the `area` is not buckled. | CAR_SAFETY (protection level: normal) |
| [registerBrakeFluidLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-brake-fluid-level-state.md) | `abstract fun registerBrakeFluidLevelState(listener: OilLevelStateListener)` | Registers the BrakeFluidLevelState to get property updates. | CAR_SAFETY (protection level: normal) |
| [registerEngineOilLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-engine-oil-level-state.md) | `abstract fun registerEngineOilLevelState(listener: OilLevelStateListener)` | Registers the EngineOilLevelState to get property updates. | CAR_SAFETY (protection level: normal) |
| [registerLowWasherFluidWaringState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-low-washer-fluid-waring-state.md) | `abstract fun registerLowWasherFluidWaringState(listener: LowWasherFluidWaringListener)` | Registers the LowWasherFluidWaringState to get property updates. | CAR_SAFETY (protection level: normal) |
| [registerPowerDownWarningIndicator](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-power-down-warning-indicator.md) | `abstract fun registerPowerDownWarningIndicator(listener: PowerDownWarningIndicatorListener)` | Registers the PowerDownWarningIndicator to get property updates. | CAR_SAFETY (protection level: normal) |
| [registerSeatAirbagDeployed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-seat-airbag-deployed.md) | `abstract fun registerSeatAirbagDeployed(area: SeatArea, listener: SeatAirbagDeployedListener)` | Registers the SeatAirbagDeployed to get property updates. | CAR_SAFETY (protection level: normal) |
| [registerSeatAirbagEnabled](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-seat-airbag-enabled.md) | `abstract fun registerSeatAirbagEnabled(area: SeatArea, listener: SeatAirbagEnabledListener)` | Registers the SeatAirbagEnabled to get property updates. | CAR_SAFETY (protection level: normal) |
| [registerSeatBeltNotBuckledWarning](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-seat-belt-not-buckled-warning.md) | `abstract fun registerSeatBeltNotBuckledWarning(area: SeatArea, listener: SeatBeltNotBuckledWarningListener)` | Registers the SeatBeltNotBuckledWarning to get property updates. | CAR_SAFETY (protection level: normal) |
| [registerTelltales](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-telltales.md) | `abstract fun registerTelltales(listener: TelltaleInfoListener)` | Registers a Telltales listener to receive telltale information updates. | PRIVATE_CAR_SAFETY (protection level: signature|privileged) |
| [registerTransmissionOilLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/register-transmission-oil-level-state.md) | `abstract fun registerTransmissionOilLevelState(listener: OilLevelStateListener)` | Registers the TransmissionOilLevel to get property updates. | CAR_SAFETY (protection level: normal) |
| [unregisterBrakeFluidLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-brake-fluid-level-state.md) | `abstract fun unregisterBrakeFluidLevelState(listener: OilLevelStateListener)` | Stops getting property updates for the given BrakeFluidLevelState. | CAR_SAFETY (protection level: normal) |
| [unregisterEngineOilLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-engine-oil-level-state.md) | `abstract fun unregisterEngineOilLevelState(listener: OilLevelStateListener)` | Stops getting property updates for the given EngineOilLevelState. | CAR_SAFETY (protection level: normal) |
| [unregisterLowWasherFluidWaringState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-low-washer-fluid-waring-state.md) | `abstract fun unregisterLowWasherFluidWaringState(listener: LowWasherFluidWaringListener)` | Stops getting property updates for the given LowWasherFluidWaringState. | CAR_SAFETY (protection level: normal) |
| [unregisterPowerDownWarningIndicator](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-power-down-warning-indicator.md) | `abstract fun unregisterPowerDownWarningIndicator(listener: PowerDownWarningIndicatorListener)` | Stops getting property updates for the given PowerDownWarningIndicator. | CAR_SAFETY (protection level: normal) |
| [unregisterSeatAirbagDeployed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-seat-airbag-deployed.md) | `abstract fun unregisterSeatAirbagDeployed(area: SeatArea, listener: SeatAirbagDeployedListener)` | Stops getting property updates for the given SeatAirbagDeployed. | CAR_SAFETY (protection level: normal) |
| [unregisterSeatAirbagEnabled](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-seat-airbag-enabled.md) | `abstract fun unregisterSeatAirbagEnabled(area: SeatArea, listener: SeatAirbagEnabledListener)` | Stops getting property updates for the given SeatAirbagEnabled. | CAR_SAFETY (protection level: normal) |
| [unregisterSeatBeltNotBuckledWarning](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-seat-belt-not-buckled-warning.md) | `abstract fun unregisterSeatBeltNotBuckledWarning(area: SeatArea, listener: SeatBeltNotBuckledWarningListener)` | Stops getting property updates for the given SeatBeltNotBuckledWarning. | CAR_SAFETY (protection level: normal) |
| [unregisterTelltales](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-telltales.md) | `abstract fun unregisterTelltales(listener: TelltaleInfoListener)` | Unregisters a previously registered Telltales listener. | PRIVATE_CAR_SAFETY (protection level: signature|privileged) |
| [unregisterTransmissionOilLevelState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/unregister-transmission-oil-level-state.md) | `abstract fun unregisterTransmissionOilLevelState(listener: OilLevelStateListener)` | Stops getting property updates for the given TransmissionOilLevel. | CAR_SAFETY (protection level: normal) |

#### Seat
`interface Seat`  
Provides APIs for seat position, occupancy, seat belt, and seat climate.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkMoveSeatBackrestAngleCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-backrest-angle-capabilities.md) | `abstract fun checkMoveSeatBackrestAngleCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat BackrestAngle capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatBeltHeightCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-belt-height-capabilities.md) | `abstract fun checkMoveSeatBeltHeightCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat belt height capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatDepthCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-depth-capabilities.md) | `abstract fun checkMoveSeatDepthCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat depth capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatForeAftCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-fore-aft-capabilities.md) | `abstract fun checkMoveSeatForeAftCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat ForeAft capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatHeadrestAngleCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-headrest-angle-capabilities.md) | `abstract fun checkMoveSeatHeadrestAngleCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat headrest angle capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatHeadrestForeAftCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-headrest-fore-aft-capabilities.md) | `abstract fun checkMoveSeatHeadrestForeAftCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat HeadrestForeAft capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatHeadrestHeightCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-headrest-height-capabilities.md) | `abstract fun checkMoveSeatHeadrestHeightCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat headrest height capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatHeightCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-height-capabilities.md) | `abstract fun checkMoveSeatHeightCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat height capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatLumbarForeAftCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-lumbar-fore-aft-capabilities.md) | `abstract fun checkMoveSeatLumbarForeAftCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat lumbar ForeAft capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatLumbarSideSupportCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-lumbar-side-support-capabilities.md) | `abstract fun checkMoveSeatLumbarSideSupportCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat lumbar side support capabilities. | CAR_SEATS (protection level: normal) |
| [checkMoveSeatTiltCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-move-seat-tilt-capabilities.md) | `abstract fun checkMoveSeatTiltCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move seat tilt capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatBackrestAngleCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-backrest-angle-capabilities.md) | `abstract fun ~~checkSeatBackrestAngleCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatBackrestAnglePositionCapabilities instead. |  |
| [checkSeatBackrestAnglePositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-backrest-angle-position-capabilities.md) | `abstract fun checkSeatBackrestAnglePositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat BackrestAnglePosition capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatBeltBuckledCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-belt-buckled-capabilities.md) | `abstract fun checkSeatBeltBuckledCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat belt buckled capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatBeltHeightCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-belt-height-capabilities.md) | `abstract fun ~~checkSeatBeltHeightCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatBeltHeightPositionCapabilities instead. |  |
| [checkSeatBeltHeightPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-belt-height-position-capabilities.md) | `abstract fun checkSeatBeltHeightPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat belt height position capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatDepthCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-depth-capabilities.md) | `abstract fun ~~checkSeatDepthCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatDepthPositionCapabilities instead. |  |
| [checkSeatDepthPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-depth-position-capabilities.md) | `abstract fun checkSeatDepthPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat depth position capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatForeAftCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-fore-aft-capabilities.md) | `abstract fun ~~checkSeatForeAftCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatForeAftPositionCapabilities instead. |  |
| [checkSeatForeAftPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-fore-aft-position-capabilities.md) | `abstract fun checkSeatForeAftPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat ForeAft position capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatHeadrestAngleCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-headrest-angle-capabilities.md) | `abstract fun ~~checkSeatHeadrestAngleCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatHeadrestAnglePositionCapabilities instead. |  |
| [checkSeatHeadrestAnglePositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-headrest-angle-position-capabilities.md) | `abstract fun checkSeatHeadrestAnglePositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat HeadrestAnglePosition capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatHeadrestForeAftCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-headrest-fore-aft-capabilities.md) | `abstract fun ~~checkSeatHeadrestForeAftCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatHeadrestForeAftPositionCapabilities instead. |  |
| [checkSeatHeadrestForeAftPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-headrest-fore-aft-position-capabilities.md) | `abstract fun checkSeatHeadrestForeAftPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat HeadrestForeAftPosition capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatHeadrestHeightCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-headrest-height-capabilities.md) | `abstract fun ~~checkSeatHeadrestHeightCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatHeadrestHeightPositionCapabilities instead. |  |
| [checkSeatHeadrestHeightPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-headrest-height-position-capabilities.md) | `abstract fun checkSeatHeadrestHeightPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat HeadrestHeightPosition capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatHeightCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-height-capabilities.md) | `abstract fun ~~checkSeatHeightCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatHeightPositionCapabilities instead. |  |
| [checkSeatHeightPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-height-position-capabilities.md) | `abstract fun checkSeatHeightPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat height position capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatLumbarForeAftCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-lumbar-fore-aft-capabilities.md) | `abstract fun ~~checkSeatLumbarForeAftCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatLumbarForeAftPositionCapabilities instead. |  |
| [checkSeatLumbarForeAftPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-lumbar-fore-aft-position-capabilities.md) | `abstract fun checkSeatLumbarForeAftPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat LumbarForeAftPosition capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatLumbarSideSupportCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-lumbar-side-support-capabilities.md) | `abstract fun ~~checkSeatLumbarSideSupportCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatLumbarSideSupportPositionCapabilities instead. |  |
| [checkSeatLumbarSideSupportPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-lumbar-side-support-position-capabilities.md) | `abstract fun checkSeatLumbarSideSupportPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat LumbarSideSupportPosition capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatMemoryCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-memory-capabilities.md) | `abstract fun checkSeatMemoryCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat memory capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatOccupancyCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-occupancy-capabilities.md) | `abstract fun checkSeatOccupancyCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat occupancy capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatTemperatureCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-temperature-capabilities.md) | `abstract fun checkSeatTemperatureCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat temperature capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatTiltCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-tilt-capabilities.md) | `abstract fun ~~checkSeatTiltCapabilities~~(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkSeatTiltPositionCapabilities instead. |  |
| [checkSeatTiltPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-tilt-position-capabilities.md) | `abstract fun checkSeatTiltPositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat tilt position capabilities. | CAR_SEATS (protection level: normal) |
| [checkSeatVentilationCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/check-seat-ventilation-capabilities.md) | `abstract fun checkSeatVentilationCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets seat ventilation capabilities. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatBackrestAnglePosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-backrest-angle-position.md) | `abstract fun getCurrentSeatBackrestAnglePosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat backrest angle position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatBeltBuckledState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-belt-buckled-state.md) | `abstract fun getCurrentSeatBeltBuckledState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat belt buckled state. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatBeltHeightPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-belt-height-position.md) | `abstract fun getCurrentSeatBeltHeightPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat belt height position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatDepthPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-depth-position.md) | `abstract fun getCurrentSeatDepthPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat depth position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-fore-aft-position.md) | `abstract fun getCurrentSeatForeAftPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat fore/aft position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatHeadrestAnglePosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-headrest-angle-position.md) | `abstract fun getCurrentSeatHeadrestAnglePosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat headrest angle position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatHeadrestForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-headrest-fore-aft-position.md) | `abstract fun getCurrentSeatHeadrestForeAftPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets current seat headrest fore/aft position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatHeadrestHeightAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-headrest-height-aft-position.md) | `abstract fun getCurrentSeatHeadrestHeightAftPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat headrest height aft position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatHeightPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-height-position.md) | `abstract fun getCurrentSeatHeightPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat height position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatLumbarForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-lumbar-fore-aft-position.md) | `abstract fun getCurrentSeatLumbarForeAftPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat lumbar fore/aft position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatLumbarSideSupportPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-lumbar-side-support-position.md) | `abstract fun getCurrentSeatLumbarSideSupportPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the seat lumbar side support position. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatOccupancy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-occupancy.md) | `abstract fun getCurrentSeatOccupancy(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets current seat occupancy. | CAR_SEATS (protection level: normal) |
| [getCurrentSeatTiltPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-tilt-position.md) | `abstract fun getCurrentSeatTiltPosition(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat tilt position. | CAR_SEATS (protection level: normal) |
| [getHvacSeatTemperature](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-hvac-seat-temperature.md) | `abstract fun getHvacSeatTemperature(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat heating/cooling temperature. | CAR_SEATS (protection level: normal) |
| [getHvacSeatVentilationState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-hvac-seat-ventilation-state.md) | `abstract fun getHvacSeatVentilationState(area: SeatArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current seat ventilation state. | CAR_SEATS (protection level: normal) |
| [moveSeatBackrestAngle](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-backrest-angle.md) | `abstract fun moveSeatBackrestAngle(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Move seat backrestAngle. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatBeltHeight](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-belt-height.md) | `abstract fun moveSeatBeltHeight(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves the seat belt height. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatDepth](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-depth.md) | `abstract fun moveSeatDepth(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves the seat depth. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatForeAft](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-fore-aft.md) | `abstract fun moveSeatForeAft(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves the seat fore/aft. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatHeadrestAngle](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-headrest-angle.md) | `abstract fun moveSeatHeadrestAngle(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves the seat headrest angle. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatHeadrestForeAft](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-headrest-fore-aft.md) | `abstract fun moveSeatHeadrestForeAft(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves seat headrest fore/aft. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatHeadrestHeight](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-headrest-height.md) | `abstract fun moveSeatHeadrestHeight(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves seat headrest height. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatHeight](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-height.md) | `abstract fun moveSeatHeight(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves the seat height. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatLumbarForeAft](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-lumbar-fore-aft.md) | `abstract fun moveSeatLumbarForeAft(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves seat lumbar fore/aft. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatLumbarSideSupport](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-lumbar-side-support.md) | `abstract fun moveSeatLumbarSideSupport(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves the seat lumbar side support. | CONTROL_CAR_SEATS (protection level: normal) |
| [moveSeatTilt](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-tilt.md) | `abstract fun moveSeatTilt(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Moves seat tilt. | CONTROL_CAR_SEATS (protection level: normal) |
| [registerHvacSeatTemperature](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-hvac-seat-temperature.md) | `abstract fun registerHvacSeatTemperature(area: SeatArea, listener: HvacSeatTemperatureListener)` | Registers the HvacSeatTemperature to get property updates. | CAR_SEATS (protection level: normal) |
| [registerHvacSeatVentilation](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-hvac-seat-ventilation.md) | `abstract fun registerHvacSeatVentilation(area: SeatArea, listener: HvacSeatVentilationListener)` | Registers the HvacSeatVentilation to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatBackrestAnglePosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-backrest-angle-position.md) | `abstract fun registerSeatBackrestAnglePosition(area: SeatArea, listener: SeatBackrestAnglePositionListener)` | Registers the SeatBackrestAnglePosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatBeltBuckledState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-belt-buckled-state.md) | `abstract fun registerSeatBeltBuckledState(area: SeatArea, listener: SeatBeltBuckledStateListener)` | Registers the SeatBeltBuckledState to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatBeltHeightPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-belt-height-position.md) | `abstract fun registerSeatBeltHeightPosition(area: SeatArea, listener: SeatBeltHeightPositionListener)` | Registers the SeatBeltHeightPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatDepthPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-depth-position.md) | `abstract fun registerSeatDepthPosition(area: SeatArea, listener: SeatDepthPositionListener)` | Registers the SeatDepthPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-fore-aft-position.md) | `abstract fun registerSeatForeAftPosition(area: SeatArea, listener: SeatForeAftPositionListener)` | Registers the SeatForeAftPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatHeadrestAnglePosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-headrest-angle-position.md) | `abstract fun registerSeatHeadrestAnglePosition(area: SeatArea, listener: SeatHeadrestAnglePositionListener)` | Registers the SeatHeadrestAnglePosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatHeadrestForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-headrest-fore-aft-position.md) | `abstract fun registerSeatHeadrestForeAftPosition(area: SeatArea, listener: SeatHeadrestForeAftPositionListener)` | Registers the SeatHeadrestForeAftPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatHeadrestHeightAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-headrest-height-aft-position.md) | `abstract fun registerSeatHeadrestHeightAftPosition(area: SeatArea, listener: SeatHeadrestHeightAftPositionListener)` | Registers the SeatHeadrestHeightAftPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatHeightPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-height-position.md) | `abstract fun registerSeatHeightPosition(area: SeatArea, listener: SeatHeightPositionListener)` | Registers the SeatHeightPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatLumbarForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-lumbar-fore-aft-position.md) | `abstract fun registerSeatLumbarForeAftPosition(area: SeatArea, listener: SeatLumbarForeAftPositionListener)` | Registers the SeatLumbarForeAftPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatLumbarSideSupportPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-lumbar-side-support-position.md) | `abstract fun registerSeatLumbarSideSupportPosition(area: SeatArea, listener: SeatLumbarSideSupportPositionListener)` | Registers the SeatLumbarSideSupportPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatOccupancy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-occupancy.md) | `abstract fun registerSeatOccupancy(area: SeatArea, listener: SeatOccupancyListener)` | Registers the SeatOccupancy to get property updates. | CAR_SEATS (protection level: normal) |
| [registerSeatTiltPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/register-seat-tilt-position.md) | `abstract fun registerSeatTiltPosition(area: SeatArea, listener: SeatTiltPositionListener)` | Registers the SeatTiltPosition to get property updates. | CAR_SEATS (protection level: normal) |
| [selectSeatMemory](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/select-seat-memory.md) | `abstract fun selectSeatMemory(area: SeatArea, value: Int, onFailed: (Exception) -> Unit = {})` | Selects seat memory. | CONTROL_CAR_SEATS (protection level: normal) |
| [setHvacSeatTemperature](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/set-hvac-seat-temperature.md) | `abstract fun setHvacSeatTemperature(area: SeatArea, value: Int, onComplete: (SeatArea, Int?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the seat heating/cooling temperature. | CONTROL_CAR_SEATS (protection level: normal) |
| [setHvacSeatVentilation](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/set-hvac-seat-ventilation.md) | `abstract fun setHvacSeatVentilation(area: SeatArea, value: Int, onComplete: (SeatArea, Int?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the seat ventilation. | CONTROL_CAR_SEATS (protection level: normal) |
| [unregisterHvacSeatTemperature](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-hvac-seat-temperature.md) | `abstract fun unregisterHvacSeatTemperature(area: SeatArea, listener: HvacSeatTemperatureListener)` | Stops getting property updates for the given HvacSeatTemperature. | CAR_SEATS (protection level: normal) |
| [unregisterHvacSeatVentilation](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-hvac-seat-ventilation.md) | `abstract fun unregisterHvacSeatVentilation(area: SeatArea, listener: HvacSeatVentilationListener)` | Stops getting property updates for the given HvacSeatVentilation. | CAR_SEATS (protection level: normal) |
| [unregisterSeatBackrestAnglePosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-backrest-angle-position.md) | `abstract fun unregisterSeatBackrestAnglePosition(area: SeatArea, listener: SeatBackrestAnglePositionListener)` | Stops getting property updates for the given SeatBackrestAnglePosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatBeltBuckledState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-belt-buckled-state.md) | `abstract fun unregisterSeatBeltBuckledState(area: SeatArea, listener: SeatBeltBuckledStateListener)` | Stops getting property updates for the given SeatBeltBuckledState. | CAR_SEATS (protection level: normal) |
| [unregisterSeatBeltHeightPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-belt-height-position.md) | `abstract fun unregisterSeatBeltHeightPosition(area: SeatArea, listener: SeatBeltHeightPositionListener)` | Stops getting property updates for the given SeatBeltHeightPosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatDepthPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-depth-position.md) | `abstract fun unregisterSeatDepthPosition(area: SeatArea, listener: SeatDepthPositionListener)` | Stops getting property updates for the given SeatDepthPosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-fore-aft-position.md) | `abstract fun unregisterSeatForeAftPosition(area: SeatArea, listener: SeatForeAftPositionListener)` | Stops getting property updates for the given SeatForeAftPosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatHeadrestAnglePosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-headrest-angle-position.md) | `abstract fun unregisterSeatHeadrestAnglePosition(area: SeatArea, listener: SeatHeadrestAnglePositionListener)` | Stops getting property updates for the given SeatHeadrestAnglePosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatHeadrestForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-headrest-fore-aft-position.md) | `abstract fun unregisterSeatHeadrestForeAftPosition(area: SeatArea, listener: SeatHeadrestForeAftPositionListener)` | Stops getting property updates for the given SeatHeadrestForeAftPosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatHeadrestHeightAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-headrest-height-aft-position.md) | `abstract fun unregisterSeatHeadrestHeightAftPosition(area: SeatArea, listener: SeatHeadrestHeightAftPositionListener)` | Stops getting property updates for the given SeatHeadrestHeightAftPosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatHeightPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-height-position.md) | `abstract fun unregisterSeatHeightPosition(area: SeatArea, listener: SeatHeightPositionListener)` | Stops getting property updates for the given SeatHeightPosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatLumbarForeAftPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-lumbar-fore-aft-position.md) | `abstract fun unregisterSeatLumbarForeAftPosition(area: SeatArea, listener: SeatLumbarForeAftPositionListener)` | Stops getting property updates for the given SeatLumbarForeAftPosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatLumbarSideSupportPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-lumbar-side-support-position.md) | `abstract fun unregisterSeatLumbarSideSupportPosition(area: SeatArea, listener: SeatLumbarSideSupportPositionListener)` | Stops getting property updates for the given SeatLumbarSideSupportPosition. | CAR_SEATS (protection level: normal) |
| [unregisterSeatOccupancy](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-occupancy.md) | `abstract fun unregisterSeatOccupancy(area: SeatArea, listener: SeatOccupancyListener)` | Stops getting property updates for the given SeatOccupancy. | CAR_SEATS (protection level: normal) |
| [unregisterSeatTiltPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/unregister-seat-tilt-position.md) | `abstract fun unregisterSeatTiltPosition(area: SeatArea, listener: SeatTiltPositionListener)` | Stops getting property updates for the given SeatTiltPosition. | CAR_SEATS (protection level: normal) |

#### SideMirror
`interface SideMirror`  
Provides APIs for side mirror heat.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkHvacSideMirrorHeatCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/side-mirror/check-hvac-side-mirror-heat-capabilities.md) | `abstract fun checkHvacSideMirrorHeatCapabilities(onComplete: (CapabilityConfig, Map<MirrorArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets hvac side mirror heat capabilities. | CAR_MIRRORS (protection level: normal) |
| [getHvacSideMirrorHeatState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/side-mirror/get-hvac-side-mirror-heat-state.md) | `abstract fun getHvacSideMirrorHeatState(area: MirrorArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current side mirror heat state. | CAR_MIRRORS (protection level: normal) |
| [registerHvacSideMirrorHeatState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/side-mirror/register-hvac-side-mirror-heat-state.md) | `abstract fun registerHvacSideMirrorHeatState(area: MirrorArea, listener: HvacSideMirrorHeatStateListener)` | Registers HvacSideMirrorHeatState state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [setHvacSideMirrorHeat](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/side-mirror/set-hvac-side-mirror-heat.md) | `abstract fun setHvacSideMirrorHeat(area: MirrorArea, value: Int, onComplete: (MirrorArea, Int?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the side mirror heat state. | CONTROL_CAR_MIRRORS (protection level: normal) |
| [unregisterHvacSideMirrorHeatState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/side-mirror/unregister-hvac-side-mirror-heat-state.md) | `abstract fun unregisterHvacSideMirrorHeatState(area: MirrorArea, listener: HvacSideMirrorHeatStateListener)` | Stops getting property updates for the given HvacSideMirrorHeatState state. | CAR_CLIMATE (protection level: normal) |

#### SteeringWheel
`interface SteeringWheel`  
Provides APIs for the front-wheel steering angle and steering wheel heating.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkHvacSteeringWheelHeatCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/check-hvac-steering-wheel-heat-capabilities.md) | `abstract fun checkHvacSteeringWheelHeatCapabilities(onComplete: (CapabilityConfig, AreaBounds<Int>) -> Unit, onFailed: (Exception) -> Unit)` | Gets hvac steering wheel heat capabilities. | CAR_CLIMATE (protection level: normal) |
| [checkSteeringWheelAngleCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/check-steering-wheel-angle-capabilities.md) | `abstract fun checkSteeringWheelAngleCapabilities(onComplete: (CapabilityConfig, AreaBounds<Float>) -> Unit, onFailed: (Exception) -> Unit)` | Gets the capabilities for the front-wheel steering angle. | CAR_DRIVE (protection level: normal) |
| [getCurrentSteeringWheelAngle](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/get-current-steering-wheel-angle.md) | `abstract fun getCurrentSteeringWheelAngle(onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current front-wheel steering angle in degrees. | CAR_DRIVE (protection level: normal) |
| [getHvacSteeringWheelHeatState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/get-hvac-steering-wheel-heat-state.md) | `abstract fun getHvacSteeringWheelHeatState(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current SteeringWheel heating/cooling state. **Permission:** PleosPermission.CAR_CLIMATE (protection level: normal) |  |
| [registerHvacSteeringWheelHeatState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/register-hvac-steering-wheel-heat-state.md) | `abstract fun registerHvacSteeringWheelHeatState(listener: HvacSteeringWheelHeatStateListener)` | Registers Hvac SteeringWheel heat state to get property updates. | CAR_CLIMATE (protection level: normal) |
| [registerSteeringWheelAngle](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/register-steering-wheel-angle.md) | `abstract fun registerSteeringWheelAngle(listener: SteeringWheelAngleListener)` | Registers a listener to receive front-wheel steering angle updates. | CAR_DRIVE (protection level: normal) |
| [setHvacSteeringWheelHeat](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/set-hvac-steering-wheel-heat.md) | `abstract fun setHvacSteeringWheelHeat(value: Int, onComplete: (Int?) -> Unit = {}, onFailed: (Exception) -> Unit = {})` | Sets SteeringWheel heating/cooling temperature. **Permission:** PleosPermission.CONTROL_CAR_CLIMATE (protection level: normal) |  |
| [unregisterHvacSteeringWheelHeatState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/unregister-hvac-steering-wheel-heat-state.md) | `abstract fun unregisterHvacSteeringWheelHeatState(listener: HvacSteeringWheelHeatStateListener)` | Stops getting property updates for the given Hvac SteeringWheel heat state. | CAR_CLIMATE (protection level: normal) |
| [unregisterSteeringWheelAngle](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/steering-wheel/unregister-steering-wheel-angle.md) | `abstract fun unregisterSteeringWheelAngle(listener: SteeringWheelAngleListener)` | Unregisters the given listener from front-wheel steering angle updates. | CAR_DRIVE (protection level: normal) |

#### Tire
`interface Tire`  
Provides APIs for tire pressure.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkLowTirePressureCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/check-low-tire-pressure-capabilities.md) | `abstract fun checkLowTirePressureCapabilities(onComplete: (CapabilityConfig, List<WheelArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets low tire pressure capabilities. | CAR_SAFETY (protection level: normal) |
| [checkWheelAreaCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/check-wheel-area-capabilities.md) | `abstract fun ~~checkWheelAreaCapabilities~~(onComplete: (List<WheelArea>) -> Unit, onFailed: (Exception) -> Unit)` | This API is deprecated, use checkLowTirePressureCapabilities instead. |  |
| [getLowTirePressure](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/get-low-tire-pressure.md) | `abstract fun getLowTirePressure(area: WheelArea, onComplete: (Float?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the critically low tire pressure threshold in kilopascals (kPa). | CAR_SAFETY (protection level: normal) |

#### Toll
`interface Toll`  
Provides APIs for ETCS voice guidance volume.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkEtcsVoiceGuidanceVolumeCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/check-etcs-voice-guidance-volume-capabilities.md) | `abstract fun checkEtcsVoiceGuidanceVolumeCapabilities(onComplete: (CapabilityConfig, AreaBounds<Int>) -> Unit, onFailed: (Exception) -> Unit)` | Gets ETCS voice guidance volume capabilities. | CAR_TOLL (protection level: normal) |
| [getCurrentEtcsVoiceGuidanceVolume](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/get-current-etcs-voice-guidance-volume.md) | `abstract fun getCurrentEtcsVoiceGuidanceVolume(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets current ETCS voice guidance volume. | CAR_TOLL (protection level: normal) |
| [registerEtcsVoiceGuidanceVolume](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/register-etcs-voice-guidance-volume.md) | `abstract fun registerEtcsVoiceGuidanceVolume(listener: EtcsVoiceGuidanceVolumeListener)` | Registers ETCS voice guidance volume updates. | CAR_TOLL (protection level: normal) |
| [setEtcsVoiceGuidanceVolume](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/set-etcs-voice-guidance-volume.md) | `abstract fun setEtcsVoiceGuidanceVolume(value: Int, onComplete: () -> Unit, onFailed: (Exception) -> Unit)` | Sets ETCS voice guidance volume. | CONTROL_CAR_TOLL (protection level: normal) |
| [unregisterEtcsVoiceGuidanceVolume](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/toll/unregister-etcs-voice-guidance-volume.md) | `abstract fun unregisterEtcsVoiceGuidanceVolume(listener: EtcsVoiceGuidanceVolumeListener)` | Stops ETCS voice guidance volume updates. | CAR_TOLL (protection level: normal) |

#### TurnSignal
`interface TurnSignal`  
Provides APIs for turn signal state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkTurnSignalStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/check-turn-signal-state-capabilities.md) | `abstract fun checkTurnSignalStateCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets turn signal state capabilities. | CAR_LIGHTS (protection level: normal) |
| [getCurrentTurnSignalState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/get-current-turn-signal-state.md) | `abstract fun getCurrentTurnSignalState(onComplete: (TurnSignalValue?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current turn signal state. | CAR_LIGHTS (protection level: normal) |
| [registerTurnSignalState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/register-turn-signal-state.md) | `abstract fun registerTurnSignalState(listener: TurnSignalStateListener)` | Registers the turn signal state to get property updates. | CAR_LIGHTS (protection level: normal) |
| [unregisterTurnSignalState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/unregister-turn-signal-state.md) | `abstract fun unregisterTurnSignalState(listener: TurnSignalStateListener)` | Stops getting property updates for the given turn signal state. | CAR_LIGHTS (protection level: normal) |

#### Window
`interface Window`  
Provides APIs for window position, lock, blinds, and smart glass.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkMoveWindowBlindCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/check-move-window-blind-capabilities.md) | `abstract fun checkMoveWindowBlindCapabilities(onComplete: (CapabilityConfig, List<WindowArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets move window blind capabilities. | CAR_WINDOWS (protection level: normal) |
| [checkSmartGlassLevelCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/check-smart-glass-level-capabilities.md) | `abstract fun checkSmartGlassLevelCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets smart glass level capabilities. | CAR_WINDOWS (protection level: normal) |
| [checkSmartGlassStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/check-smart-glass-state-capabilities.md) | `abstract fun checkSmartGlassStateCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)` | Gets smart glass state capabilities. | CAR_WINDOWS (protection level: normal) |
| [checkSmartGlassZoneStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/check-smart-glass-zone-state-capabilities.md) | `abstract fun checkSmartGlassZoneStateCapabilities(onComplete: (CapabilityConfig, List<WindowArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets smart glass zone state capabilities. | CAR_WINDOWS (protection level: normal) |
| [checkWindowBlindPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/check-window-blind-position-capabilities.md) | `abstract fun checkWindowBlindPositionCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets window blind position capabilities. | CAR_WINDOWS (protection level: normal) |
| [checkWindowLockCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/check-window-lock-capabilities.md) | `abstract fun checkWindowLockCapabilities(onComplete: (CapabilityConfig, List<WindowArea>) -> Unit, onFailed: (Exception) -> Unit)` | Gets window lock capabilities. | CAR_WINDOWS (protection level: normal) |
| [checkWindowPositionCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/check-window-position-capabilities.md) | `abstract fun checkWindowPositionCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets window position capabilities. | CAR_WINDOWS (protection level: normal) |
| [getCurrentSmartGlassLevel](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-current-smart-glass-level.md) | `abstract fun getCurrentSmartGlassLevel(area: WindowArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current Smart Glass Level for the specified window area. | CAR_WINDOWS (protection level: normal) |
| [getCurrentSmartGlassState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-current-smart-glass-state.md) | `abstract fun getCurrentSmartGlassState(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current Smart Glass state. | CAR_WINDOWS (protection level: normal) |
| [getCurrentWindowBlindPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-current-window-blind-position.md) | `abstract fun getCurrentWindowBlindPosition(area: WindowArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current Window Blind Position for the specified window area. | CAR_WINDOWS (protection level: normal) |
| [getCurrentWindowLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-current-window-lock-state.md) | `abstract fun getCurrentWindowLockState(area: WindowArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current window lock state. | CAR_WINDOWS (protection level: normal) |
| [getCurrentWindowPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-current-window-position.md) | `abstract fun getCurrentWindowPosition(area: WindowArea, onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current window position. | CAR_WINDOWS (protection level: normal) |
| [getSmartGlassConfig](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-smart-glass-config.md) | `abstract fun getSmartGlassConfig(area: WindowArea, onComplete: (SmartGlassConfig?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the Smart Glass configuration for the specified window area. | CAR_WINDOWS (protection level: normal) |
| [moveWindowBlind](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/move-window-blind.md) | `abstract fun moveWindowBlind(area: WindowArea, value: MoveControl, onComplete: (WindowArea, MoveControl?) -> Unit, onFailed: (Exception) -> Unit)` | Moves the Window Blind for the specified window area. | CONTROL_CAR_WINDOWS (protection level: normal) |
| [registerSmartGlassLevel](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/register-smart-glass-level.md) | `abstract fun registerSmartGlassLevel(area: WindowArea, listener: SmartGlassLevelListener)` | Registers a listener to receive Smart Glass Level updates for the specified window area. | CAR_WINDOWS (protection level: normal) |
| [registerSmartGlassState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/register-smart-glass-state.md) | `abstract fun registerSmartGlassState(listener: SmartGlassStateListener)` | Registers Smart Glass state listener. | CAR_WINDOWS (protection level: normal) |
| [registerWindowBlindPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/register-window-blind-position.md) | `abstract fun registerWindowBlindPosition(area: WindowArea, listener: WindowPositionListener)` | Registers a listener to receive Window Blind Position updates for the specified window area. | CAR_WINDOWS (protection level: normal) |
| [registerWindowLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/register-window-lock-state.md) | `abstract fun registerWindowLockState(area: WindowArea, listener: WindowLockStateListener)` | Registers the window lock state to get property updates. | CAR_WINDOWS (protection level: normal) |
| [registerWindowPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/register-window-position.md) | `abstract fun registerWindowPosition(area: WindowArea, listener: WindowPositionListener)` | Registers the window area to get property updates. | CAR_WINDOWS (protection level: normal) |
| [setSmartGlassLevel](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-smart-glass-level.md) | `abstract fun setSmartGlassLevel(area: WindowArea, value: Int, onComplete: (WindowArea, Int?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the Smart Glass Level for the specified window area. | CONTROL_CAR_WINDOWS (protection level: normal) |
| [setSmartGlassState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-smart-glass-state.md) | `abstract fun setSmartGlassState(value: Boolean, onComplete: (Boolean?) -> Unit = { _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the Smart Glass state. | CONTROL_CAR_WINDOWS (protection level: signature|privileged) |
| [setSmartGlassZoneState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-smart-glass-zone-state.md) | `abstract fun setSmartGlassZoneState(area: WindowArea, value: List<SmartGlassZoneCommand>, onComplete: (WindowArea, List<SmartGlassZoneCommand>?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the Smart Glass zone state by submitting one command per smart glass panel for the specified window area. | CONTROL_CAR_WINDOWS (protection level: normal) |
| [setWindowLock](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-window-lock.md) | `abstract fun setWindowLock(area: WindowArea, value: Boolean, onComplete: (WindowArea, Boolean?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the current window lock state. | CONTROL_CAR_WINDOWS (protection level: normal) |
| [setWindowPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/set-window-position.md) | `abstract fun setWindowPosition(area: WindowArea, value: Int, onComplete: (WindowArea, Int?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the current window position state. | CONTROL_CAR_WINDOWS (protection level: normal) |
| [unregisterSmartGlassLevel](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/unregister-smart-glass-level.md) | `abstract fun unregisterSmartGlassLevel(area: WindowArea, listener: SmartGlassLevelListener)` | Unregisters a previously registered Smart Glass Level listener for the specified window area. | CAR_WINDOWS (protection level: normal) |
| [unregisterSmartGlassState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/unregister-smart-glass-state.md) | `abstract fun unregisterSmartGlassState(listener: SmartGlassStateListener)` | Unregisters Smart Glass state listener. | CAR_WINDOWS (protection level: normal) |
| [unregisterWindowBlindPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/unregister-window-blind-position.md) | `abstract fun unregisterWindowBlindPosition(area: WindowArea, listener: WindowPositionListener)` | Unregisters a previously registered Window Blind Position listener for the specified window area. | CAR_WINDOWS (protection level: normal) |
| [unregisterWindowLockState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/unregister-window-lock-state.md) | `abstract fun unregisterWindowLockState(area: WindowArea, listener: WindowLockStateListener)` | Stops getting property updates for the given window lock state. | CAR_WINDOWS (protection level: normal) |
| [unregisterWindowPosition](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/unregister-window-position.md) | `abstract fun unregisterWindowPosition(area: WindowArea, listener: WindowPositionListener)` | Stops getting property updates for the given window position. | CAR_WINDOWS (protection level: normal) |

#### Wiper
`interface Wiper`  
Provides APIs for wiper state and switch.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [checkWiperStateCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/check-wiper-state-capabilities.md) | `abstract fun checkWiperStateCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, List<WiperState>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets wiper state capabilities. | CAR_WINDOWS (protection level: normal) |
| [checkWiperSwitchCapabilities](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/check-wiper-switch-capabilities.md) | `abstract fun checkWiperSwitchCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, List<WiperSwitch>>) -> Unit, onFailed: (Exception) -> Unit)` | Gets wiper switch capabilities. | CAR_WINDOWS (protection level: normal) |
| [getCurrentWiperState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/get-current-wiper-state.md) | `abstract fun getCurrentWiperState(area: WindowArea, onComplete: (WiperState?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current wiper mode state. | CAR_WINDOWS (protection level: normal) |
| [getCurrentWiperSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/get-current-wiper-switch.md) | `abstract fun getCurrentWiperSwitch(area: WindowArea, onComplete: (WiperSwitch?) -> Unit, onFailed: (Exception) -> Unit)` | Gets the current Wiper Switch state for the specified window area. | CAR_WINDOWS (protection level: normal) |
| [registerWiperState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/register-wiper-state.md) | `abstract fun registerWiperState(area: WindowArea, listener: WiperStateListener)` | Registers the wiper state to get property updates. | CAR_WINDOWS (protection level: normal) |
| [setWiperSwitch](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/set-wiper-switch.md) | `abstract fun setWiperSwitch(area: WindowArea, value: WiperSwitch, onComplete: (WindowArea, WiperSwitch?) -> Unit = { _, _ -> }, onFailed: (Exception) -> Unit = {})` | Sets the current wiper switch. | CONTROL_CAR_WINDOWS (protection level: normal) |
| [unregisterWiperState](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api/wiper/unregister-wiper-state.md) | `abstract fun unregisterWiperState(area: WindowArea, listener: WiperStateListener)` | Stops getting property updates for the given wiper state. | CAR_WINDOWS (protection level: normal) |

### `ai.pleos.playground.vehicle.api.model.area`

#### Area
`sealed class Area`  
Base type for vehicle areas.

#### DoorArea
`class DoorArea(val values: DoorValue) : Area`  
Represents one or more door areas.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/-door-area.md) | `constructor(vararg values: DoorValue)` |  |  |
| [equals](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/equals.md) | `` |  |  |
| [hashCode](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/hash-code.md) | `open override fun hashCode(): Int` |  |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/door-area/values.md) | `val values: Array<out DoorValue>` |  |  |

#### MirrorArea
`class MirrorArea(val values: MirrorValue) : Area`  
Represents one or more mirror areas.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/mirror-area/-mirror-area.md) | `constructor(vararg values: MirrorValue)` |  |  |
| [equals](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/mirror-area/equals.md) | `` |  |  |
| [hashCode](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/mirror-area/hash-code.md) | `open override fun hashCode(): Int` |  |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/mirror-area/values.md) | `val values: Array<out MirrorValue>` |  |  |

#### SeatArea
`class SeatArea(val values: SeatValue) : Area`  
Represents one or more seat areas.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/-seat-area.md) | `constructor(vararg values: SeatValue)` |  |  |
| [equals](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/equals.md) | `` |  |  |
| [hashCode](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/hash-code.md) | `open override fun hashCode(): Int` |  |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/seat-area/values.md) | `val values: Array<out SeatValue>` |  |  |

#### TurnSignalArea
`class TurnSignalArea(val values: TurnSignalValue) : Area`  
Represents one or more turn signal areas.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/turn-signal-area/-turn-signal-area.md) | `constructor(vararg values: TurnSignalValue)` |  |  |
| [equals](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/turn-signal-area/equals.md) | `` |  |  |
| [hashCode](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/turn-signal-area/hash-code.md) | `open override fun hashCode(): Int` |  |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/turn-signal-area/values.md) | `val values: Array<out TurnSignalValue>` |  |  |

#### WheelArea
`class WheelArea(val values: WheelValue) : Area`  
Represents one or more wheel areas.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/wheel-area/-wheel-area.md) | `constructor(vararg values: WheelValue)` |  |  |
| [equals](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/wheel-area/equals.md) | `` |  |  |
| [hashCode](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/wheel-area/hash-code.md) | `open override fun hashCode(): Int` |  |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/wheel-area/values.md) | `val values: Array<out WheelValue>` |  |  |

#### WindowArea
`class WindowArea(val values: WindowValue) : Area`  
Represents one or more window areas.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/window-area/-window-area.md) | `constructor(vararg values: WindowValue)` |  |  |
| [equals](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/window-area/equals.md) | `` |  |  |
| [hashCode](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/window-area/hash-code.md) | `open override fun hashCode(): Int` |  |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.area/window-area/values.md) | `val values: Array<out WindowValue>` |  |  |

### `ai.pleos.playground.vehicle.api.model.capability`

#### AccessType
`enum AccessType : Enum<AccessType>`  
Represents the access level for a capability.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/access-type/entries.md) | `val entries: EnumEntries<AccessType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/access-type/value-of.md) | `fun valueOf(value: String): AccessType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/access-type/values.md) | `fun values(): Array<AccessType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [NONE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/access-type/n-o-n-e/entry.md) | `` |  |  |
| [READ](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/access-type/r-e-a-d/entry.md) | `` |  |  |
| [READ_WRITE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/access-type/r-e-a-d_-w-r-i-t-e/entry.md) | `` |  |  |
| [WRITE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/access-type/w-r-i-t-e/entry.md) | `` |  |  |

#### AreaBounds
`data class AreaBounds<T>(val minValue: T, val maxValue: T)`  
Represents the available value range for an area.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/-area-bounds.md) | `constructor(minValue: T, maxValue: T)` |  |  |
| [maxValue](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/max-value.md) | `val maxValue: T` |  |  |
| [minValue](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/area-bounds/min-value.md) | `val minValue: T` |  |  |

#### CapabilityConfig
`data class CapabilityConfig(val accessType: AccessType = AccessType.NONE)`  
Represents the configuration for a specific capability.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/-capability-config.md) | `constructor(accessType: AccessType = AccessType.NONE)` |  |  |
| [accessType](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/capability-config/access-type.md) | `val accessType: AccessType` |  |  |

#### HvacTemperatureConfigArray
`data class HvacTemperatureConfigArray(val minCelsius: Float, val maxCelsius: Float, val incrementCelsius: Float, val minFahrenheit: Float, val maxFahrenheit: Float, val incrementFahrenheit: Float, val raw: List<Int>)`  
HVAC temperature conversion table derived from the vehicle temperature setting config array.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/hvac-temperature-config-array/-hvac-temperature-config-array.md) | `constructor(minCelsius: Float, maxCelsius: Float, incrementCelsius: Float, minFahrenheit: Float, maxFahrenheit: Float, incrementFahrenheit: Float, raw: List<Int>)` |  |  |
| [incrementCelsius](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/hvac-temperature-config-array/increment-celsius.md) | `val incrementCelsius: Float` |  |  |
| [incrementFahrenheit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/hvac-temperature-config-array/increment-fahrenheit.md) | `val incrementFahrenheit: Float` |  |  |
| [maxCelsius](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/hvac-temperature-config-array/max-celsius.md) | `val maxCelsius: Float` |  |  |
| [maxFahrenheit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/hvac-temperature-config-array/max-fahrenheit.md) | `val maxFahrenheit: Float` |  |  |
| [minCelsius](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/hvac-temperature-config-array/min-celsius.md) | `val minCelsius: Float` |  |  |
| [minFahrenheit](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/hvac-temperature-config-array/min-fahrenheit.md) | `val minFahrenheit: Float` |  |  |
| [raw](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/hvac-temperature-config-array/raw.md) | `val raw: List<Int>` |  |  |

#### SmartGlassConfig
`data class SmartGlassConfig(val zoneCount: Int, val levelCount: Int)`  
Describes the smart glass zones and levels for a window area.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/-smart-glass-config.md) | `constructor(zoneCount: Int, levelCount: Int)` |  |  |
| [levelCount](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/level-count.md) | `val levelCount: Int` |  |  |
| [zoneCount](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.capability/smart-glass-config/zone-count.md) | `val zoneCount: Int` |  |  |

### `ai.pleos.playground.vehicle.api.model.common`

#### TripStatistics
`data class TripStatistics<T>(val current: T? = null, val instantaneous: T? = null, val cumulative: T? = null, val sinceRefill: T? = null, val raw: List<T?> = emptyList())`  
Represents trip statistics grouped by type.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.common/trip-statistics/-trip-statistics.md) | `constructor(current: T? = null, instantaneous: T? = null, cumulative: T? = null, sinceRefill: T? = null, raw: List<T?> = emptyList())` |  |  |
| [cumulative](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.common/trip-statistics/cumulative.md) | `val cumulative: T? = null` |  |  |
| [current](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.common/trip-statistics/current.md) | `val current: T? = null` |  |  |
| [get](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.common/trip-statistics/get.md) | `fun get(type: TripStatisticType): T?` |  |  |
| [instantaneous](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.common/trip-statistics/instantaneous.md) | `val instantaneous: T? = null` |  |  |
| [raw](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.common/trip-statistics/raw.md) | `val raw: List<T?>` |  |  |
| [sinceRefill](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.common/trip-statistics/since-refill.md) | `val sinceRefill: T? = null` |  |  |

### `ai.pleos.playground.vehicle.api.model.telltale`

#### TelltaleData
`data class TelltaleData(val type: TelltaleType, val state: TelltaleState, val color: Int, val priority: Int, val warningCount: Int)`  
Represents a single telltale item.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltale-data/-telltale-data.md) | `constructor(type: TelltaleType, state: TelltaleState, color: Int, priority: Int, warningCount: Int)` |  |  |
| [color](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltale-data/color.md) | `val color: Int` |  |  |
| [priority](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltale-data/priority.md) | `val priority: Int` |  |  |
| [state](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltale-data/state.md) | `val state: TelltaleState` |  |  |
| [type](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltale-data/type.md) | `val type: TelltaleType` |  |  |
| [warningCount](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltale-data/warning-count.md) | `val warningCount: Int` |  |  |

#### Telltales
`data class Telltales(val telltales: List<TelltaleData> = arrayListOf())`  
Represents a collection of telltales.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltales/-telltales.md) | `constructor(telltales: List<TelltaleData> = arrayListOf())` |  |  |
| [telltales](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.api.model.telltale/telltales/telltales-property.md) | `val telltales: List<TelltaleData>` |  |  |

### `ai.pleos.playground.vehicle.callback.exception`

#### VehicleException
`class VehicleException(error: String? = null) : Exception`  
Exception type used by the vehicle SDK.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [Constructor](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/-vehicle-exception.md) | `constructor(error: String? = null)` | The error value used to create the exception. |  |
| [errorCode](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/error-code.md) | `val errorCode: ErrorCode` |  |  |
| [message](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.callback.exception/vehicle-exception/message.md) | `open override val message: String` |  |  |

### `ai.pleos.playground.vehicle.constant`

#### ErrorCode
`enum ErrorCode : Enum<ErrorCode>`  
ERROR_INTERNAL_SERVICE

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/entries.md) | `val entries: EnumEntries<ErrorCode>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/id.md) | `val id: Int` |  |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/value-of.md) | `fun valueOf(value: String): ErrorCode` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/values.md) | `fun values(): Array<ErrorCode>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [Companion](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/companion/entry.md) | `object Companion` | findById |  |
| [findById](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/companion/find-by-id.md) | `fun findById(id: Int): ErrorCode?` |  |  |
| [ERROR_DRIVING_RESTRICTED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-d-r-i-v-i-n-g_-r-e-s-t-r-i-c-t-e-d/entry.md) | `` |  |  |
| [ERROR_EMPTY_RESULT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-e-m-p-t-y_-r-e-s-u-l-t/entry.md) | `` |  |  |
| [ERROR_INTERNAL_SERVICE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-i-n-t-e-r-n-a-l_-s-e-r-v-i-c-e/entry.md) | `` |  |  |
| [ERROR_NOT_SUPPORTED_AREA](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-n-o-t_-s-u-p-p-o-r-t-e-d_-a-r-e-a/entry.md) | `` |  |  |
| [ERROR_NOT_SUPPORTED_PROPERTY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-n-o-t_-s-u-p-p-o-r-t-e-d_-p-r-o-p-e-r-t-y/entry.md) | `` |  |  |
| [ERROR_NOT_SUPPORTED_VALUE_TYPE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-n-o-t_-s-u-p-p-o-r-t-e-d_-v-a-l-u-e_-t-y-p-e/entry.md) | `` |  |  |
| [ERROR_SECURITY_PERMISSION](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-s-e-c-u-r-i-t-y_-p-e-r-m-i-s-s-i-o-n/entry.md) | `` |  |  |
| [ERROR_SERVICE_CONNECTION_FAILURE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-s-e-r-v-i-c-e_-c-o-n-n-e-c-t-i-o-n_-f-a-i-l-u-r-e/entry.md) | `` |  |  |
| [ERROR_UNDEFINED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/error-code/e-r-r-o-r_-u-n-d-e-f-i-n-e-d/entry.md) | `` |  |  |

#### PleosPermission
`object PleosPermission`  
CAR_CLIMATE

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [CAR_CLIMATE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-c-l-i-m-a-t-e.md) | `const val CAR_CLIMATE: String` |  |  |
| [CAR_DOORS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-d-o-o-r-s.md) | `const val CAR_DOORS: String` |  |  |
| [CAR_DRIVE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-d-r-i-v-e.md) | `const val CAR_DRIVE: String` |  |  |
| [CAR_ENERGY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-e-n-e-r-g-y.md) | `const val CAR_ENERGY: String` |  |  |
| [CAR_INFO](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-i-n-f-o.md) | `const val CAR_INFO: String` |  |  |
| [CAR_LIGHTS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-l-i-g-h-t-s.md) | `const val CAR_LIGHTS: String` |  |  |
| [CAR_MIRRORS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-m-i-r-r-o-r-s.md) | `const val CAR_MIRRORS: String` |  |  |
| [CAR_SAFETY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-s-a-f-e-t-y.md) | `const val CAR_SAFETY: String` |  |  |
| [CAR_SEATS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-s-e-a-t-s.md) | `const val CAR_SEATS: String` |  |  |
| [CAR_TOLL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-t-o-l-l.md) | `const val CAR_TOLL: String` |  |  |
| [CAR_WINDOWS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-a-r_-w-i-n-d-o-w-s.md) | `const val CAR_WINDOWS: String` |  |  |
| [CONTROL_CAR_CLIMATE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-c-l-i-m-a-t-e.md) | `const val CONTROL_CAR_CLIMATE: String` |  |  |
| [CONTROL_CAR_DOORS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-d-o-o-r-s.md) | `const val CONTROL_CAR_DOORS: String` |  |  |
| [CONTROL_CAR_DRIVE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-d-r-i-v-e.md) | `const val CONTROL_CAR_DRIVE: String` |  |  |
| [CONTROL_CAR_ENERGY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-e-n-e-r-g-y.md) | `const val CONTROL_CAR_ENERGY: String` |  |  |
| [CONTROL_CAR_INFO](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-i-n-f-o.md) | `const val CONTROL_CAR_INFO: String` |  |  |
| [CONTROL_CAR_LIGHTS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-l-i-g-h-t-s.md) | `const val CONTROL_CAR_LIGHTS: String` |  |  |
| [CONTROL_CAR_MIRRORS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-m-i-r-r-o-r-s.md) | `const val CONTROL_CAR_MIRRORS: String` |  |  |
| [CONTROL_CAR_SAFETY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-s-a-f-e-t-y.md) | `const val CONTROL_CAR_SAFETY: String` |  |  |
| [CONTROL_CAR_SEATS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-s-e-a-t-s.md) | `const val CONTROL_CAR_SEATS: String` |  |  |
| [CONTROL_CAR_TOLL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-t-o-l-l.md) | `const val CONTROL_CAR_TOLL: String` |  |  |
| [CONTROL_CAR_WINDOWS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-c-o-n-t-r-o-l_-c-a-r_-w-i-n-d-o-w-s.md) | `const val CONTROL_CAR_WINDOWS: String` |  |  |
| [PRIVATE_CAR_CLIMATE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-c-l-i-m-a-t-e.md) | `const val PRIVATE_CAR_CLIMATE: String` |  |  |
| [PRIVATE_CAR_DOORS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-d-o-o-r-s.md) | `const val PRIVATE_CAR_DOORS: String` |  |  |
| [PRIVATE_CAR_DRIVE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-d-r-i-v-e.md) | `const val PRIVATE_CAR_DRIVE: String` |  |  |
| [PRIVATE_CAR_ENERGY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-e-n-e-r-g-y.md) | `const val PRIVATE_CAR_ENERGY: String` |  |  |
| [PRIVATE_CAR_INFO](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-i-n-f-o.md) | `const val PRIVATE_CAR_INFO: String` |  |  |
| [PRIVATE_CAR_LIGHTS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-l-i-g-h-t-s.md) | `const val PRIVATE_CAR_LIGHTS: String` |  |  |
| [PRIVATE_CAR_MIRRORS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-m-i-r-r-o-r-s.md) | `const val PRIVATE_CAR_MIRRORS: String` |  |  |
| [PRIVATE_CAR_SAFETY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-s-a-f-e-t-y.md) | `const val PRIVATE_CAR_SAFETY: String` |  |  |
| [PRIVATE_CAR_SEATS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-s-e-a-t-s.md) | `const val PRIVATE_CAR_SEATS: String` |  |  |
| [PRIVATE_CAR_TOLL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-t-o-l-l.md) | `const val PRIVATE_CAR_TOLL: String` |  |  |
| [PRIVATE_CAR_WINDOWS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant/pleos-permission/-p-r-i-v-a-t-e_-c-a-r_-w-i-n-d-o-w-s.md) | `const val PRIVATE_CAR_WINDOWS: String` |  |  |

### `ai.pleos.playground.vehicle.constant.area`

#### AreaValue
`interface AreaValue`  
DoorValue

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/area-value/id.md) | `abstract val id: Int` |  |  |

#### DoorValue
`enum DoorValue : Enum<DoorValue> , AreaValue`  
Represents available door area values.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/entries.md) | `val entries: EnumEntries<DoorValue>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/id.md) | `open override val id: Int` |  |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/value-of.md) | `fun valueOf(value: String): DoorValue` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/values.md) | `fun values(): Array<DoorValue>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [HOOD](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/h-o-o-d/entry.md) | `` |  |  |
| [REAR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/r-e-a-r/entry.md) | `` |  |  |
| [ROW_1_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/r-o-w_1_-l-e-f-t/entry.md) | `` |  |  |
| [ROW_1_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/r-o-w_1_-r-i-g-h-t/entry.md) | `` |  |  |
| [ROW_2_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/r-o-w_2_-l-e-f-t/entry.md) | `` |  |  |
| [ROW_2_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/r-o-w_2_-r-i-g-h-t/entry.md) | `` |  |  |
| [ROW_3_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/r-o-w_3_-l-e-f-t/entry.md) | `` |  |  |
| [ROW_3_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/door-value/r-o-w_3_-r-i-g-h-t/entry.md) | `` |  |  |

#### MirrorValue
`enum MirrorValue : Enum<MirrorValue> , AreaValue`  
Represents available mirror area values.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/mirror-value/entries.md) | `val entries: EnumEntries<MirrorValue>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/mirror-value/id.md) | `open override val id: Int` |  |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/mirror-value/value-of.md) | `fun valueOf(value: String): MirrorValue` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/mirror-value/values.md) | `fun values(): Array<MirrorValue>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [DRIVER_CENTER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/mirror-value/d-r-i-v-e-r_-c-e-n-t-e-r/entry.md) | `` |  |  |
| [DRIVER_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/mirror-value/d-r-i-v-e-r_-l-e-f-t/entry.md) | `` |  |  |
| [DRIVER_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/mirror-value/d-r-i-v-e-r_-r-i-g-h-t/entry.md) | `` |  |  |

#### SeatValue
`enum SeatValue : Enum<SeatValue> , AreaValue`  
Represents available seat area values.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/entries.md) | `val entries: EnumEntries<SeatValue>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/id.md) | `open override val id: Int` |  |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/value-of.md) | `fun valueOf(value: String): SeatValue` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/values.md) | `fun values(): Array<SeatValue>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ROW_1_CENTER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_1_-c-e-n-t-e-r/entry.md) | `` |  |  |
| [ROW_1_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_1_-l-e-f-t/entry.md) | `` |  |  |
| [ROW_1_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_1_-r-i-g-h-t/entry.md) | `` |  |  |
| [ROW_2_CENTER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_2_-c-e-n-t-e-r/entry.md) | `` |  |  |
| [ROW_2_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_2_-l-e-f-t/entry.md) | `` |  |  |
| [ROW_2_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_2_-r-i-g-h-t/entry.md) | `` |  |  |
| [ROW_3_CENTER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_3_-c-e-n-t-e-r/entry.md) | `` |  |  |
| [ROW_3_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_3_-l-e-f-t/entry.md) | `` |  |  |
| [ROW_3_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/seat-value/r-o-w_3_-r-i-g-h-t/entry.md) | `` |  |  |

#### TurnSignalValue
`enum TurnSignalValue : Enum<TurnSignalValue> , AreaValue`  
Represents available turn signal area values.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/turn-signal-value/entries.md) | `val entries: EnumEntries<TurnSignalValue>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/turn-signal-value/id.md) | `open override val id: Int` |  |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/turn-signal-value/value-of.md) | `fun valueOf(value: String): TurnSignalValue` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/turn-signal-value/values.md) | `fun values(): Array<TurnSignalValue>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/turn-signal-value/l-e-f-t/entry.md) | `` |  |  |
| [OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/turn-signal-value/o-f-f/entry.md) | `` |  |  |
| [RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/turn-signal-value/r-i-g-h-t/entry.md) | `` |  |  |

#### WheelValue
`enum WheelValue : Enum<WheelValue> , AreaValue`  
Represents available wheel area values.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/entries.md) | `val entries: EnumEntries<WheelValue>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/id.md) | `open override val id: Int` |  |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/value-of.md) | `fun valueOf(value: String): WheelValue` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/values.md) | `fun values(): Array<WheelValue>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [LEFT_FRONT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/l-e-f-t_-f-r-o-n-t/entry.md) | `` |  |  |
| [LEFT_REAR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/l-e-f-t_-r-e-a-r/entry.md) | `` |  |  |
| [RIGHT_FRONT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/r-i-g-h-t_-f-r-o-n-t/entry.md) | `` |  |  |
| [RIGHT_REAR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/r-i-g-h-t_-r-e-a-r/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/wheel-value/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### WindowValue
`enum WindowValue : Enum<WindowValue> , AreaValue`  
Represents available window area values.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/entries.md) | `val entries: EnumEntries<WindowValue>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/id.md) | `open override val id: Int` |  |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/value-of.md) | `fun valueOf(value: String): WindowValue` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/values.md) | `fun values(): Array<WindowValue>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [FRONT_WINDSHIELD](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/f-r-o-n-t_-w-i-n-d-s-h-i-e-l-d/entry.md) | `` |  |  |
| [REAR_WINDSHIELD](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/r-e-a-r_-w-i-n-d-s-h-i-e-l-d/entry.md) | `` |  |  |
| [ROOF_TOP_1](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/r-o-o-f_-t-o-p_1/entry.md) | `` |  |  |
| [ROOF_TOP_2](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/r-o-o-f_-t-o-p_2/entry.md) | `` |  |  |
| [ROW_1_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/r-o-w_1_-l-e-f-t/entry.md) | `` |  |  |
| [ROW_1_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/r-o-w_1_-r-i-g-h-t/entry.md) | `` |  |  |
| [ROW_2_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/r-o-w_2_-l-e-f-t/entry.md) | `` |  |  |
| [ROW_2_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.area/window-value/r-o-w_2_-r-i-g-h-t/entry.md) | `` |  |  |

### `ai.pleos.playground.vehicle.constant.control`

#### CruiseControl
`enum CruiseControl : Enum<CruiseControl>`  
Represents the cruise control state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/entries.md) | `val entries: EnumEntries<CruiseControl>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/value-of.md) | `fun valueOf(value: String): CruiseControl` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/values.md) | `fun values(): Array<CruiseControl>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ACTIVATED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/a-c-t-i-v-a-t-e-d/entry.md) | `` |  |  |
| [ENABLED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/e-n-a-b-l-e-d/entry.md) | `` |  |  |
| [FORCED_DEACTIVATION_WARNING](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/f-o-r-c-e-d_-d-e-a-c-t-i-v-a-t-i-o-n_-w-a-r-n-i-n-g/entry.md) | `` |  |  |
| [OTHER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/o-t-h-e-r/entry.md) | `` |  |  |
| [SUSPENDED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/s-u-s-p-e-n-d-e-d/entry.md) | `` |  |  |
| [USER_OVERRIDE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/cruise-control/u-s-e-r_-o-v-e-r-r-i-d-e/entry.md) | `` |  |  |

#### DrivingModeType
`enum DrivingModeType : Enum<DrivingModeType>`  
Represents the driving mode.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/driving-mode-type/entries.md) | `val entries: EnumEntries<DrivingModeType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/driving-mode-type/value-of.md) | `fun valueOf(value: String): DrivingModeType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/driving-mode-type/values.md) | `fun values(): Array<DrivingModeType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ECO](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/driving-mode-type/e-c-o/entry.md) | `` |  |  |
| [NORMAL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/driving-mode-type/n-o-r-m-a-l/entry.md) | `` |  |  |
| [SPORT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/driving-mode-type/s-p-o-r-t/entry.md) | `` |  |  |

#### EvChargeState
`enum EvChargeState : Enum<EvChargeState>`  
Represents the EV charge state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-charge-state/entries.md) | `val entries: EnumEntries<EvChargeState>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-charge-state/value-of.md) | `fun valueOf(value: String): EvChargeState` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-charge-state/values.md) | `fun values(): Array<EvChargeState>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [CHARGING](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-charge-state/c-h-a-r-g-i-n-g/entry.md) | `` |  |  |
| [ERROR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-charge-state/e-r-r-o-r/entry.md) | `` |  |  |
| [FULLY_CHARGED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-charge-state/f-u-l-l-y_-c-h-a-r-g-e-d/entry.md) | `` |  |  |
| [NOT_CHARGING](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-charge-state/n-o-t_-c-h-a-r-g-i-n-g/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-charge-state/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### EvConnectorType
`enum EvConnectorType : Enum<EvConnectorType>`  
Represents the EV charging connector type.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/entries.md) | `val entries: EnumEntries<EvConnectorType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/value-of.md) | `fun valueOf(value: String): EvConnectorType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/values.md) | `fun values(): Array<EvConnectorType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [GBT_AC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/g-b-t_-a-c/entry.md) | `` |  |  |
| [GBT_DC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/g-b-t_-d-c/entry.md) | `` |  |  |
| [IEC_TYPE_1_AC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/i-e-c_-t-y-p-e_1_-a-c/entry.md) | `` |  |  |
| [IEC_TYPE_1_CCS_DC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/i-e-c_-t-y-p-e_1_-c-c-s_-d-c/entry.md) | `` |  |  |
| [IEC_TYPE_2_AC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/i-e-c_-t-y-p-e_2_-a-c/entry.md) | `` |  |  |
| [IEC_TYPE_2_CCS_DC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/i-e-c_-t-y-p-e_2_-c-c-s_-d-c/entry.md) | `` |  |  |
| [IEC_TYPE_3_AC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/i-e-c_-t-y-p-e_3_-a-c/entry.md) | `` |  |  |
| [IEC_TYPE_4_DC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/i-e-c_-t-y-p-e_4_-d-c/entry.md) | `` |  |  |
| [OTHER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/o-t-h-e-r/entry.md) | `` |  |  |
| [TESLA_HPWC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/t-e-s-l-a_-h-p-w-c/entry.md) | `` |  |  |
| [TESLA_ROADSTER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/t-e-s-l-a_-r-o-a-d-s-t-e-r/entry.md) | `` |  |  |
| [TESLA_SUPERCHARGER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/t-e-s-l-a_-s-u-p-e-r-c-h-a-r-g-e-r/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-connector-type/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### EvRegenerativeBrakingState
`enum EvRegenerativeBrakingState : Enum<EvRegenerativeBrakingState>`  
Represents the EV regenerative braking state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-regenerative-braking-state/entries.md) | `val entries: EnumEntries<EvRegenerativeBrakingState>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-regenerative-braking-state/value-of.md) | `fun valueOf(value: String): EvRegenerativeBrakingState` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-regenerative-braking-state/values.md) | `fun values(): Array<EvRegenerativeBrakingState>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [DISABLED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-regenerative-braking-state/d-i-s-a-b-l-e-d/entry.md) | `` |  |  |
| [FULLY_ENABLED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-regenerative-braking-state/f-u-l-l-y_-e-n-a-b-l-e-d/entry.md) | `` |  |  |
| [PARTIALLY_ENABLED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-regenerative-braking-state/p-a-r-t-i-a-l-l-y_-e-n-a-b-l-e-d/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/ev-regenerative-braking-state/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### FanDirectionType
`enum FanDirectionType : Enum<FanDirectionType>`  
Represents the HVAC fan direction.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/entries.md) | `val entries: EnumEntries<FanDirectionType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/value-of.md) | `fun valueOf(value: String): FanDirectionType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/values.md) | `fun values(): Array<FanDirectionType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [DEFROST](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/d-e-f-r-o-s-t/entry.md) | `` |  |  |
| [DEFROST_AND_FLOOR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/d-e-f-r-o-s-t_-a-n-d_-f-l-o-o-r/entry.md) | `` |  |  |
| [FACE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/f-a-c-e/entry.md) | `` |  |  |
| [FACE_AND_DEFROST](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/f-a-c-e_-a-n-d_-d-e-f-r-o-s-t/entry.md) | `` |  |  |
| [FACE_AND_FLOOR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/f-a-c-e_-a-n-d_-f-l-o-o-r/entry.md) | `` |  |  |
| [FACE_AND_FLOOR_AND_DEFROST](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/f-a-c-e_-a-n-d_-f-l-o-o-r_-a-n-d_-d-e-f-r-o-s-t/entry.md) | `` |  |  |
| [FLOOR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/f-l-o-o-r/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fan-direction-type/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### FuelType
`enum FuelType : Enum<FuelType>`  
Represents the fuel type.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/entries.md) | `val entries: EnumEntries<FuelType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/value-of.md) | `fun valueOf(value: String): FuelType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/values.md) | `fun values(): Array<FuelType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [BIODIESEL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/b-i-o-d-i-e-s-e-l/entry.md) | `` |  |  |
| [CNG](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/c-n-g/entry.md) | `` |  |  |
| [DIESEL_1](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/d-i-e-s-e-l_1/entry.md) | `` |  |  |
| [DIESEL_2](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/d-i-e-s-e-l_2/entry.md) | `` |  |  |
| [ELECTRIC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/e-l-e-c-t-r-i-c/entry.md) | `` |  |  |
| [E85](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/e85/entry.md) | `` |  |  |
| [HYDROGEN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/h-y-d-r-o-g-e-n/entry.md) | `` |  |  |
| [LEADED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/l-e-a-d-e-d/entry.md) | `` |  |  |
| [LNG](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/l-n-g/entry.md) | `` |  |  |
| [LPG](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/l-p-g/entry.md) | `` |  |  |
| [OTHER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/o-t-h-e-r/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/u-n-k-n-o-w-n/entry.md) | `` |  |  |
| [UNLEADED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/fuel-type/u-n-l-e-a-d-e-d/entry.md) | `` |  |  |

#### GsrComplianceType
`enum GsrComplianceType : Enum<GsrComplianceType>`  
Represents the GSR compliance type.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/gsr-compliance-type/entries.md) | `val entries: EnumEntries<GsrComplianceType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/gsr-compliance-type/value-of.md) | `fun valueOf(value: String): GsrComplianceType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/gsr-compliance-type/values.md) | `fun values(): Array<GsrComplianceType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [GSR_COMPLIANCE_TYPE_NOT_REQUIRED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/gsr-compliance-type/g-s-r_-c-o-m-p-l-i-a-n-c-e_-t-y-p-e_-n-o-t_-r-e-q-u-i-r-e-d/entry.md) | `` |  |  |
| [GSR_COMPLIANCE_TYPE_REQUIRED_V1](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/gsr-compliance-type/g-s-r_-c-o-m-p-l-i-a-n-c-e_-t-y-p-e_-r-e-q-u-i-r-e-d_-v1/entry.md) | `` |  |  |

#### LightState
`enum LightState : Enum<LightState>`  
Represents the light state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-state/entries.md) | `val entries: EnumEntries<LightState>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-state/value-of.md) | `fun valueOf(value: String): LightState` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-state/values.md) | `fun values(): Array<LightState>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [DAYTIME_RUNNING](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-state/d-a-y-t-i-m-e_-r-u-n-n-i-n-g/entry.md) | `` |  |  |
| [OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-state/o-f-f/entry.md) | `` |  |  |
| [ON](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-state/o-n/entry.md) | `` |  |  |

#### LightSwitch
`enum LightSwitch : Enum<LightSwitch>`  
Represents the light switch state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-switch/entries.md) | `val entries: EnumEntries<LightSwitch>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-switch/value-of.md) | `fun valueOf(value: String): LightSwitch` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-switch/values.md) | `fun values(): Array<LightSwitch>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [AUTOMATIC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-switch/a-u-t-o-m-a-t-i-c/entry.md) | `` |  |  |
| [DAYTIME_RUNNING](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-switch/d-a-y-t-i-m-e_-r-u-n-n-i-n-g/entry.md) | `` |  |  |
| [OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-switch/o-f-f/entry.md) | `` |  |  |
| [ON](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/light-switch/o-n/entry.md) | `` |  |  |

#### MoveControl
`enum MoveControl : Enum<MoveControl>`  
Represents the direction used for movement control.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/move-control/entries.md) | `val entries: EnumEntries<MoveControl>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/move-control/value-of.md) | `fun valueOf(value: String): MoveControl` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/move-control/values.md) | `fun values(): Array<MoveControl>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [BACKWARD](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/move-control/b-a-c-k-w-a-r-d/entry.md) | `` |  |  |
| [FORWARD](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/move-control/f-o-r-w-a-r-d/entry.md) | `` |  |  |
| [STOP](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/move-control/s-t-o-p/entry.md) | `` |  |  |

#### OilLevelState
`enum OilLevelState : Enum<OilLevelState>`  
Represents the oil level state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/oil-level-state/entries.md) | `val entries: EnumEntries<OilLevelState>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/oil-level-state/value-of.md) | `fun valueOf(value: String): OilLevelState` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/oil-level-state/values.md) | `fun values(): Array<OilLevelState>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [CRITICALLY_LOW](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/oil-level-state/c-r-i-t-i-c-a-l-l-y_-l-o-w/entry.md) | `` |  |  |
| [ERROR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/oil-level-state/e-r-r-o-r/entry.md) | `` |  |  |
| [HIGH](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/oil-level-state/h-i-g-h/entry.md) | `` |  |  |
| [LOW](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/oil-level-state/l-o-w/entry.md) | `` |  |  |
| [NORMAL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/oil-level-state/n-o-r-m-a-l/entry.md) | `` |  |  |

#### PortLocationType
`enum PortLocationType : Enum<PortLocationType>`  
Represents the charge port location.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/entries.md) | `val entries: EnumEntries<PortLocationType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/value-of.md) | `fun valueOf(value: String): PortLocationType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/values.md) | `fun values(): Array<PortLocationType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [FRONT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/f-r-o-n-t/entry.md) | `` |  |  |
| [FRONT_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/f-r-o-n-t_-l-e-f-t/entry.md) | `` |  |  |
| [FRONT_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/f-r-o-n-t_-r-i-g-h-t/entry.md) | `` |  |  |
| [REAR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/r-e-a-r/entry.md) | `` |  |  |
| [REAR_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/r-e-a-r_-l-e-f-t/entry.md) | `` |  |  |
| [REAR_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/r-e-a-r_-r-i-g-h-t/entry.md) | `` |  |  |
| [UNKNOWN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/port-location-type/u-n-k-n-o-w-n/entry.md) | `` |  |  |

#### SmartGlassZoneCommand
`enum SmartGlassZoneCommand : Enum<SmartGlassZoneCommand>`  
Represents the command for a single smart glass panel.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/entries.md) | `val entries: EnumEntries<SmartGlassZoneCommand>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/value-of.md) | `fun valueOf(value: String): SmartGlassZoneCommand` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/values.md) | `fun values(): Array<SmartGlassZoneCommand>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/o-f-f/entry.md) | `` |  |  |
| [ON](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/o-n/entry.md) | `` |  |  |
| [SKIP](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/smart-glass-zone-command/s-k-i-p/entry.md) | `` |  |  |

#### SteeringControl
`enum SteeringControl : Enum<SteeringControl>`  
Represents the steering control state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/steering-control/entries.md) | `val entries: EnumEntries<SteeringControl>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/steering-control/value-of.md) | `fun valueOf(value: String): SteeringControl` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/steering-control/values.md) | `fun values(): Array<SteeringControl>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ACTIVE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/steering-control/a-c-t-i-v-e/entry.md) | `` |  |  |
| [OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/steering-control/o-f-f/entry.md) | `` |  |  |
| [READY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/steering-control/r-e-a-d-y/entry.md) | `` |  |  |

#### TripStatisticType
`enum TripStatisticType : Enum<TripStatisticType>`  
Represents the type of trip statistic.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/entries.md) | `val entries: EnumEntries<TripStatisticType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [id](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/id.md) | `val id: Int` |  |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/value-of.md) | `fun valueOf(value: String): TripStatisticType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/values.md) | `fun values(): Array<TripStatisticType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [CUMULATIVE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/c-u-m-u-l-a-t-i-v-e/entry.md) | `` |  |  |
| [CURRENT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/c-u-r-r-e-n-t/entry.md) | `` |  |  |
| [Companion](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/companion/entry.md) | `object Companion` | findById |  |
| [findById](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/companion/find-by-id.md) | `fun findById(id: Int?): TripStatisticType?` |  |  |
| [INSTANTANEOUS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/i-n-s-t-a-n-t-a-n-e-o-u-s/entry.md) | `` |  |  |
| [SINCE_REFILL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/trip-statistic-type/s-i-n-c-e_-r-e-f-i-l-l/entry.md) | `` |  |  |

#### WiperState
`enum WiperState : Enum<WiperState>`  
Represents the wiper state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-state/entries.md) | `val entries: EnumEntries<WiperState>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-state/value-of.md) | `fun valueOf(value: String): WiperState` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-state/values.md) | `fun values(): Array<WiperState>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-state/o-f-f/entry.md) | `` |  |  |
| [ON](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-state/o-n/entry.md) | `` |  |  |
| [OTHER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-state/o-t-h-e-r/entry.md) | `` |  |  |
| [SERVICE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-state/s-e-r-v-i-c-e/entry.md) | `` |  |  |

#### WiperSwitch
`enum WiperSwitch : Enum<WiperSwitch>`  
Represents the wiper switch state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/entries.md) | `val entries: EnumEntries<WiperSwitch>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/value-of.md) | `fun valueOf(value: String): WiperSwitch` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/values.md) | `fun values(): Array<WiperSwitch>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [AUTO](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/a-u-t-o/entry.md) | `` |  |  |
| [CONTINUOUS_LEVEL_1](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/c-o-n-t-i-n-u-o-u-s_-l-e-v-e-l_1/entry.md) | `` |  |  |
| [CONTINUOUS_LEVEL_2](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/c-o-n-t-i-n-u-o-u-s_-l-e-v-e-l_2/entry.md) | `` |  |  |
| [CONTINUOUS_LEVEL_3](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/c-o-n-t-i-n-u-o-u-s_-l-e-v-e-l_3/entry.md) | `` |  |  |
| [CONTINUOUS_LEVEL_4](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/c-o-n-t-i-n-u-o-u-s_-l-e-v-e-l_4/entry.md) | `` |  |  |
| [CONTINUOUS_LEVEL_5](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/c-o-n-t-i-n-u-o-u-s_-l-e-v-e-l_5/entry.md) | `` |  |  |
| [INTERMITTENT_LEVEL_1](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/i-n-t-e-r-m-i-t-t-e-n-t_-l-e-v-e-l_1/entry.md) | `` |  |  |
| [INTERMITTENT_LEVEL_2](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/i-n-t-e-r-m-i-t-t-e-n-t_-l-e-v-e-l_2/entry.md) | `` |  |  |
| [INTERMITTENT_LEVEL_3](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/i-n-t-e-r-m-i-t-t-e-n-t_-l-e-v-e-l_3/entry.md) | `` |  |  |
| [INTERMITTENT_LEVEL_4](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/i-n-t-e-r-m-i-t-t-e-n-t_-l-e-v-e-l_4/entry.md) | `` |  |  |
| [INTERMITTENT_LEVEL_5](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/i-n-t-e-r-m-i-t-t-e-n-t_-l-e-v-e-l_5/entry.md) | `` |  |  |
| [MIST](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/m-i-s-t/entry.md) | `` |  |  |
| [OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/o-f-f/entry.md) | `` |  |  |
| [OTHER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/o-t-h-e-r/entry.md) | `` |  |  |
| [SERVICE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/wiper-switch/s-e-r-v-i-c-e/entry.md) | `` |  |  |

### `ai.pleos.playground.vehicle.constant.telltale`

#### TelltaleColor
`enum TelltaleColor : Enum<TelltaleColor>`  
Represents the telltale color.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/entries.md) | `val entries: EnumEntries<TelltaleColor>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/value-of.md) | `fun valueOf(value: String): TelltaleColor` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/values.md) | `fun values(): Array<TelltaleColor>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [BLUE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/b-l-u-e/entry.md) | `` |  |  |
| [GRAY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/g-r-a-y/entry.md) | `` |  |  |
| [GREEN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/g-r-e-e-n/entry.md) | `` |  |  |
| [RED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/r-e-d/entry.md) | `` |  |  |
| [WHITE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/w-h-i-t-e/entry.md) | `` |  |  |
| [YELLOW](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-color/y-e-l-l-o-w/entry.md) | `` |  |  |

#### TelltaleState
`enum TelltaleState : Enum<TelltaleState>`  
Represents the telltale state.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-state/entries.md) | `val entries: EnumEntries<TelltaleState>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-state/value-of.md) | `fun valueOf(value: String): TelltaleState` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-state/values.md) | `fun values(): Array<TelltaleState>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [BLINK](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-state/b-l-i-n-k/entry.md) | `` |  |  |
| [OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-state/o-f-f/entry.md) | `` |  |  |
| [ON](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-state/o-n/entry.md) | `` |  |  |

#### TelltaleType
`enum TelltaleType : Enum<TelltaleType>`  
Represents the telltale type.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/entries.md) | `val entries: EnumEntries<TelltaleType>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/value-of.md) | `fun valueOf(value: String): TelltaleType` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/values.md) | `fun values(): Array<TelltaleType>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [ABS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/a-b-s/entry.md) | `` |  |  |
| [ACCEL_RESPONSE_ACCEL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/a-c-c-e-l_-r-e-s-p-o-n-s-e_-a-c-c-e-l/entry.md) | `` |  |  |
| [ACCEL_RESPONSE_DASH](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/a-c-c-e-l_-r-e-s-p-o-n-s-e_-d-a-s-h/entry.md) | `` |  |  |
| [AFS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/a-f-s/entry.md) | `` |  |  |
| [AIRBAG](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/a-i-r-b-a-g/entry.md) | `` |  |  |
| [AUTO_HIGH_BEAM](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/a-u-t-o_-h-i-g-h_-b-e-a-m/entry.md) | `` |  |  |
| [AUTO_HOLD](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/a-u-t-o_-h-o-l-d/entry.md) | `` |  |  |
| [AWD_LOCK](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/a-w-d_-l-o-c-k/entry.md) | `` |  |  |
| [CHILD_PRESENCE_DETECTION_OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/c-h-i-l-d_-p-r-e-s-e-n-c-e_-d-e-t-e-c-t-i-o-n_-o-f-f/entry.md) | `` |  |  |
| [CREEP_OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/c-r-e-e-p_-o-f-f/entry.md) | `` |  |  |
| [DBC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-b-c/entry.md) | `` |  |  |
| [DOOR_OPEN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-o-o-r_-o-p-e-n/entry.md) | `` |  |  |
| [DRIFT_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-r-i-f-t_-m-o-d-e/entry.md) | `` |  |  |
| [DRIVER_ATTENTION](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-r-i-v-e-r_-a-t-t-e-n-t-i-o-n/entry.md) | `` |  |  |
| [DRIVE_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-r-i-v-e_-m-o-d-e/entry.md) | `` |  |  |
| [DRIVE_MODE_COMFORT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-r-i-v-e_-m-o-d-e_-c-o-m-f-o-r-t/entry.md) | `` |  |  |
| [DRIVE_MODE_ECO](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-r-i-v-e_-m-o-d-e_-e-c-o/entry.md) | `` |  |  |
| [DRIVE_MODE_SPORT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-r-i-v-e_-m-o-d-e_-s-p-o-r-t/entry.md) | `` |  |  |
| [DRIVE_MODE_TERRAIN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/d-r-i-v-e_-m-o-d-e_-t-e-r-r-a-i-n/entry.md) | `` |  |  |
| [ELECTRONIC_PARKING_BRAKE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-l-e-c-t-r-o-n-i-c_-p-a-r-k-i-n-g_-b-r-a-k-e/entry.md) | `` |  |  |
| [ELK](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-l-k/entry.md) | `` |  |  |
| [EMERGENCY_CALL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-m-e-r-g-e-n-c-y_-c-a-l-l/entry.md) | `` |  |  |
| [ENGINE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-n-g-i-n-e/entry.md) | `` |  |  |
| [ENGINE_OIL_PRESSURE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-n-g-i-n-e_-o-i-l_-p-r-e-s-s-u-r-e/entry.md) | `` |  |  |
| [ENGINE_TEMPERATURE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-n-g-i-n-e_-t-e-m-p-e-r-a-t-u-r-e/entry.md) | `` |  |  |
| [EREV_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-r-e-v_-m-o-d-e/entry.md) | `` |  |  |
| [EREV_SYSTEM](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-r-e-v_-s-y-s-t-e-m/entry.md) | `` |  |  |
| [ESC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-s-c/entry.md) | `` |  |  |
| [ESC_OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-s-c_-o-f-f/entry.md) | `` |  |  |
| [ESC_SPORT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-s-c_-s-p-o-r-t/entry.md) | `` |  |  |
| [EV_BATTERY_CONDITIONING](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-v_-b-a-t-t-e-r-y_-c-o-n-d-i-t-i-o-n-i-n-g/entry.md) | `` |  |  |
| [EV_CHARGING_CONNECTOR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-v_-c-h-a-r-g-i-n-g_-c-o-n-n-e-c-t-o-r/entry.md) | `` |  |  |
| [EV_SYSTEM](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/e-v_-s-y-s-t-e-m/entry.md) | `` |  |  |
| [FCA](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/f-c-a/entry.md) | `` |  |  |
| [FCEV_SYSTEM](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/f-c-e-v_-s-y-s-t-e-m/entry.md) | `` |  |  |
| [FORCED_ALL_WHEEL_DRIVE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/f-o-r-c-e-d_-a-l-l_-w-h-e-e-l_-d-r-i-v-e/entry.md) | `` |  |  |
| [FOUR_WHEEL_DRIVE_MODE_2H](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/f-o-u-r_-w-h-e-e-l_-d-r-i-v-e_-m-o-d-e_2-h/entry.md) | `` |  |  |
| [FOUR_WHEEL_DRIVE_MODE_4A](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/f-o-u-r_-w-h-e-e-l_-d-r-i-v-e_-m-o-d-e_4-a/entry.md) | `` |  |  |
| [FOUR_WHEEL_DRIVE_MODE_4H](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/f-o-u-r_-w-h-e-e-l_-d-r-i-v-e_-m-o-d-e_4-h/entry.md) | `` |  |  |
| [FOUR_WHEEL_DRIVE_MODE_4L](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/f-o-u-r_-w-h-e-e-l_-d-r-i-v-e_-m-o-d-e_4-l/entry.md) | `` |  |  |
| [FRONT_FOG_LAMP](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/f-r-o-n-t_-f-o-g_-l-a-m-p/entry.md) | `` |  |  |
| [GREEN_ZONE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/g-r-e-e-n_-z-o-n-e/entry.md) | `` |  |  |
| [HEADLAMP_HIGH](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/h-e-a-d-l-a-m-p_-h-i-g-h/entry.md) | `` |  |  |
| [HEADLAMP_LOW](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/h-e-a-d-l-a-m-p_-l-o-w/entry.md) | `` |  |  |
| [HEV_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/h-e-v_-m-o-d-e/entry.md) | `` |  |  |
| [HEV_SYSTEM](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/h-e-v_-s-y-s-t-e-m/entry.md) | `` |  |  |
| [H2_LEAK](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/h2_-l-e-a-k/entry.md) | `` |  |  |
| [H2_SENSOR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/h2_-s-e-n-s-o-r/entry.md) | `` |  |  |
| [ICC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/i-c-c/entry.md) | `` |  |  |
| [ICY_ROAD](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/i-c-y_-r-o-a-d/entry.md) | `` |  |  |
| [IMMOBILIZER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/i-m-m-o-b-i-l-i-z-e-r/entry.md) | `` |  |  |
| [ISG](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/i-s-g/entry.md) | `` |  |  |
| [I_PEDAL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/i_-p-e-d-a-l/entry.md) | `` |  |  |
| [LAUNCH_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-a-u-n-c-h_-m-o-d-e/entry.md) | `` |  |  |
| [LCA_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-c-a_-l-e-f-t/entry.md) | `` |  |  |
| [LCA_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-c-a_-r-i-g-h-t/entry.md) | `` |  |  |
| [LED_HEADLAMP](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-e-d_-h-e-a-d-l-a-m-p/entry.md) | `` |  |  |
| [LFA](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-f-a/entry.md) | `` |  |  |
| [LKA](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-k-a/entry.md) | `` |  |  |
| [LOW_EV_BATTERY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-o-w_-e-v_-b-a-t-t-e-r-y/entry.md) | `` |  |  |
| [LOW_FUEL](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-o-w_-f-u-e-l/entry.md) | `` |  |  |
| [LOW_FUEL_FCEV](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-o-w_-f-u-e-l_-f-c-e-v/entry.md) | `` |  |  |
| [LOW_VOLT_BATTERY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-o-w_-v-o-l-t_-b-a-t-t-e-r-y/entry.md) | `` |  |  |
| [LPI](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/l-p-i/entry.md) | `` |  |  |
| [MASTER_WARNING](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/m-a-s-t-e-r_-w-a-r-n-i-n-g/entry.md) | `` |  |  |
| [MDPS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/m-d-p-s/entry.md) | `` |  |  |
| [N_BATTERY_DRAG](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/n_-b-a-t-t-e-r-y_-d-r-a-g/entry.md) | `` |  |  |
| [N_BATTERY_ENDURANCE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/n_-b-a-t-t-e-r-y_-e-n-d-u-r-a-n-c-e/entry.md) | `` |  |  |
| [N_BATTERY_SPRINT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/n_-b-a-t-t-e-r-y_-s-p-r-i-n-t/entry.md) | `` |  |  |
| [OVER_SPEED](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/o-v-e-r_-s-p-e-e-d/entry.md) | `` |  |  |
| [PARKING_BRAKE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-a-r-k-i-n-g_-b-r-a-k-e/entry.md) | `` |  |  |
| [PARTICLE_FILTER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-a-r-t-i-c-l-e_-f-i-l-t-e-r/entry.md) | `` |  |  |
| [PASSENGER_AIRBAG_OFF](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-a-s-s-e-n-g-e-r_-a-i-r-b-a-g_-o-f-f/entry.md) | `` |  |  |
| [PASSENGER_AIRBAG_ON](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-a-s-s-e-n-g-e-r_-a-i-r-b-a-g_-o-n/entry.md) | `` |  |  |
| [PHEV_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-h-e-v_-m-o-d-e/entry.md) | `` |  |  |
| [PHEV_MODE_AUTO](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-h-e-v_-m-o-d-e_-a-u-t-o/entry.md) | `` |  |  |
| [PHEV_MODE_EV_PLUS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-h-e-v_-m-o-d-e_-e-v_-p-l-u-s/entry.md) | `` |  |  |
| [PHEV_MODE_HEV](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-h-e-v_-m-o-d-e_-h-e-v/entry.md) | `` |  |  |
| [POSITION_LAMP](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-o-s-i-t-i-o-n_-l-a-m-p/entry.md) | `` |  |  |
| [POWER_DOWN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-o-w-e-r_-d-o-w-n/entry.md) | `` |  |  |
| [PREHEAT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/p-r-e-h-e-a-t/entry.md) | `` |  |  |
| [RBS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/r-b-s/entry.md) | `` |  |  |
| [REAR_FOG_LAMP](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/r-e-a-r_-f-o-g_-l-a-m-p/entry.md) | `` |  |  |
| [REV](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/r-e-v/entry.md) | `` |  |  |
| [SCR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/s-c-r/entry.md) | `` |  |  |
| [SEAT_BELT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/s-e-a-t_-b-e-l-t/entry.md) | `` |  |  |
| [TOW_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/t-o-w_-m-o-d-e/entry.md) | `` |  |  |
| [TPMS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/t-p-m-s/entry.md) | `` |  |  |
| [TRACK_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/t-r-a-c-k_-m-o-d-e/entry.md) | `` |  |  |
| [TRUNK_OPEN](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/t-r-u-n-k_-o-p-e-n/entry.md) | `` |  |  |
| [TURN_SIGNAL_LEFT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/t-u-r-n_-s-i-g-n-a-l_-l-e-f-t/entry.md) | `` |  |  |
| [TURN_SIGNAL_RIGHT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/t-u-r-n_-s-i-g-n-a-l_-r-i-g-h-t/entry.md) | `` |  |  |
| [UTILITY_MODE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/u-t-i-l-i-t-y_-m-o-d-e/entry.md) | `` |  |  |
| [WATER_SEPARATOR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/w-a-t-e-r_-s-e-p-a-r-a-t-o-r/entry.md) | `` |  |  |
| [XEV_READY](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.telltale/telltale-type/x-e-v_-r-e-a-d-y/entry.md) | `` |  |  |

### `ai.pleos.playground.vehicle.constant.unit`

#### DistanceDisplayUnit
`enum DistanceDisplayUnit : Enum<DistanceDisplayUnit>`  
Represents the distance display unit.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/distance-display-unit/entries.md) | `val entries: EnumEntries<DistanceDisplayUnit>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/distance-display-unit/value-of.md) | `fun valueOf(value: String): DistanceDisplayUnit` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/distance-display-unit/values.md) | `fun values(): Array<DistanceDisplayUnit>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [KILOMETER](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/distance-display-unit/k-i-l-o-m-e-t-e-r/entry.md) | `` |  |  |
| [MILE](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/distance-display-unit/m-i-l-e/entry.md) | `` |  |  |

#### EvBatteryDisplayUnit
`enum EvBatteryDisplayUnit : Enum<EvBatteryDisplayUnit>`  
Represents the EV battery display unit.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/ev-battery-display-unit/entries.md) | `val entries: EnumEntries<EvBatteryDisplayUnit>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/ev-battery-display-unit/value-of.md) | `fun valueOf(value: String): EvBatteryDisplayUnit` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/ev-battery-display-unit/values.md) | `fun values(): Array<EvBatteryDisplayUnit>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [AMPERE_HOURS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/ev-battery-display-unit/a-m-p-e-r-e_-h-o-u-r-s/entry.md) | `` |  |  |
| [KILOWATT_HOUR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/ev-battery-display-unit/k-i-l-o-w-a-t-t_-h-o-u-r/entry.md) | `` |  |  |
| [WATT_HOUR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/ev-battery-display-unit/w-a-t-t_-h-o-u-r/entry.md) | `` |  |  |

#### SpeedDisplayUnit
`enum SpeedDisplayUnit : Enum<SpeedDisplayUnit>`  
Represents the speed display unit.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/speed-display-unit/entries.md) | `val entries: EnumEntries<SpeedDisplayUnit>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/speed-display-unit/value-of.md) | `fun valueOf(value: String): SpeedDisplayUnit` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/speed-display-unit/values.md) | `fun values(): Array<SpeedDisplayUnit>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [KILOMETERS_PER_HOUR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/speed-display-unit/k-i-l-o-m-e-t-e-r-s_-p-e-r_-h-o-u-r/entry.md) | `` |  |  |
| [METER_PER_SEC](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/speed-display-unit/m-e-t-e-r_-p-e-r_-s-e-c/entry.md) | `` |  |  |
| [MILES_PER_HOUR](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/speed-display-unit/m-i-l-e-s_-p-e-r_-h-o-u-r/entry.md) | `` |  |  |

#### TemperatureUnit
`enum TemperatureUnit : Enum<TemperatureUnit>`  
Represents the temperature unit.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [entries](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/temperature-unit/entries.md) | `val entries: EnumEntries<TemperatureUnit>` | Returns a representation of an immutable list of all enum entries, in the order they're declared. |  |
| [valueOf](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/temperature-unit/value-of.md) | `fun valueOf(value: String): TemperatureUnit` | Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |  |
| [values](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/temperature-unit/values.md) | `fun values(): Array<TemperatureUnit>` | Returns an array containing the constants of this enum type, in the order they're declared. |  |
| [CELSIUS](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/temperature-unit/c-e-l-s-i-u-s/entry.md) | `` |  |  |
| [FAHRENHEIT](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.unit/temperature-unit/f-a-h-r-e-n-h-e-i-t/entry.md) | `` |  |  |

### `ai.pleos.playground.vehicle.listener`

#### AmbientColorAutoByDriveModeEnabledListener
`interface AmbientColorAutoByDriveModeEnabledListener : VehicleListener`  
Interface definition for the AmbientColorAutoByDriveModeEnabled updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onAmbientColorAutoByDriveModeEnabledUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ambient-color-auto-by-drive-mode-enabled-listener/on-ambient-color-auto-by-drive-mode-enabled-updated.md) | `abstract fun onAmbientColorAutoByDriveModeEnabledUpdated(enabled: Boolean?)` | Callback invoked when AmbientColorAutoByDriveModeEnabled updates. |  |

#### AmbientLightBrightnessColorListener
`interface AmbientLightBrightnessColorListener : VehicleListener`  
Interface definition for the AmbientLightBrightnessColor updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onBrightnessColorUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ambient-light-brightness-color-listener/on-brightness-color-updated.md) | `abstract fun onBrightnessColorUpdated(argbColor: Int?)` | Callback invoked when AmbientLightBrightnessColor updates. |  |

#### AmbientLightBrightnessListener
`interface AmbientLightBrightnessListener : VehicleListener`  
Interface definition for the AmbientLightBrightness updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onBrightnessUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ambient-light-brightness-listener/on-brightness-updated.md) | `abstract fun onBrightnessUpdated(brightness: Int?)` | Callback invoked when AmbientLightBrightness updates. |  |

#### BrakePedalStatusListener
`interface BrakePedalStatusListener : VehicleListener`  
Interface definition for the BrakePedalStatus updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onBrakePedalStatusUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/brake-pedal-status-listener/on-brake-pedal-status-updated.md) | `abstract fun onBrakePedalStatusUpdated(status: Boolean?)` | Callback invoked when BrakePedalStatus updates. |  |

#### CruiseControlStateListener
`interface CruiseControlStateListener : VehicleListener`  
Interface definition for the CruiseControlState updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onCruiseControlStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-state-listener/on-cruise-control-state-updated.md) | `abstract fun onCruiseControlStateUpdated(state: CruiseControl?)` | Callback invoked when the CruiseControlState updates. |  |

#### CruiseControlTargetSpeedListener
`interface CruiseControlTargetSpeedListener : VehicleListener`  
Listener for cruise control target speed updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onCruiseControlTargetSpeedUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/cruise-control-target-speed-listener/on-cruise-control-target-speed-updated.md) | `abstract fun onCruiseControlTargetSpeedUpdated(speed: Float?)` | Called with the updated cruise control target speed in meters per second (m/s). |  |

#### DistanceDrivenListener
`interface DistanceDrivenListener : VehicleListener`  
Listener for distance updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onDistanceDrivenUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/distance-driven-listener/on-distance-driven-updated.md) | `abstract fun onDistanceDrivenUpdated(value: Float?)` | Called with the updated distance in kilometers (km) or meters (m), depending on the registration API. |  |

#### DoorLockStateListener
`interface DoorLockStateListener : VehicleListener`  
Interface definition for the door lock state updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onDoorLockStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/door-lock-state-listener/on-door-lock-state-updated.md) | `abstract fun onDoorLockStateUpdated(state: Boolean?)` | Callback invoked when the door lock state updates. |  |

#### DoorPositionListener
`interface DoorPositionListener : VehicleListener`  
Interface definition for the door position updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/door-position-listener/on-position-updated.md) | `abstract fun onPositionUpdated(pos: Int?)` | Callback invoked when the door position updates. |  |

#### DrivingModeStateListener
`interface DrivingModeStateListener : VehicleListener`  
Interface definition for the DrivingModeState updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onDrivingModeStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/driving-mode-state-listener/on-driving-mode-state-updated.md) | `abstract fun onDrivingModeStateUpdated(state: DrivingModeType?)` | Callback invoked when the DrivingModeState updates. |  |

#### DrivingTimeListener
`interface DrivingTimeListener : VehicleListener`  
Interface definition for the driving time updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onDrivingTimeUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/driving-time-listener/on-driving-time-updated.md) | `abstract fun onDrivingTimeUpdated(value: Int?)` | Callback invoked when driving time updates. |  |

#### EtcsVoiceGuidanceVolumeListener
`interface EtcsVoiceGuidanceVolumeListener : VehicleListener`  
Interface definition for ETCS voice guidance volume updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onEtcsVoiceGuidanceVolumeUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/etcs-voice-guidance-volume-listener/on-etcs-voice-guidance-volume-updated.md) | `abstract fun onEtcsVoiceGuidanceVolumeUpdated(volume: Int?)` | Callback invoked when ETCS voice guidance volume updates. |  |

#### EvBatteryEfficiencyListener
`interface EvBatteryEfficiencyListener : VehicleListener`  
Interface definition for the EV battery efficiency updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onEvBatteryEfficiencyUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ev-battery-efficiency-listener/on-ev-battery-efficiency-updated.md) | `abstract fun onEvBatteryEfficiencyUpdated(efficiency: Float?)` | Callback invoked when EV battery efficiency updates. |  |

#### EvBatteryStateOfChargeListener
`interface EvBatteryStateOfChargeListener : VehicleListener`  
Interface definition for the EV battery state of charge updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onEvBatteryStateOfChargeUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/ev-battery-state-of-charge-listener/on-ev-battery-state-of-charge-updated.md) | `abstract fun onEvBatteryStateOfChargeUpdated(value: Int?)` | Callback invoked with the updated EV battery state of charge as a percentage (%). |  |

#### FuelEconomyListener
`interface FuelEconomyListener : VehicleListener`  
Interface definition for the fuel economy updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onFuelEconomyUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/fuel-economy-listener/on-fuel-economy-updated.md) | `abstract fun onFuelEconomyUpdated(efficiency: Float?)` | Callback invoked when fuel economy updates. |  |

#### FuelPercentListener
`interface FuelPercentListener : VehicleListener`  
Interface definition for the fuel percentage updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onFuelPercentUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/fuel-percent-listener/on-fuel-percent-updated.md) | `abstract fun onFuelPercentUpdated(value: Int?)` | Callback invoked with the updated fuel level as a percentage (%). |  |

#### GloveBoxLockStateListener
`interface GloveBoxLockStateListener : VehicleListener`  
Interface definition for the GloveBox lock state updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onGloveBoxLockStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/glove-box-lock-state-listener/on-glove-box-lock-state-updated.md) | `abstract fun onGloveBoxLockStateUpdated(state: Boolean?)` | Callback invoked when the GloveBox lock state updates. |  |

#### HvacAcStateListener
`interface HvacAcStateListener : VehicleListener`  
Interface definition for the HvacAcState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacAcStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-ac-state-listener/on-hvac-ac-state-updated.md) | `abstract fun onHvacAcStateUpdated(state: Boolean?)` | Callback invoked when HvacAcState updates. |  |

#### HvacAirCleanerStateListener
`interface HvacAirCleanerStateListener : VehicleListener`  
Interface definition for the HvacAirCleanerState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacAirCleanerStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-air-cleaner-state-listener/on-hvac-air-cleaner-state-updated.md) | `abstract fun onHvacAirCleanerStateUpdated(state: Boolean?)` | Callback invoked when HvacAirCleanerState updates. |  |

#### HvacAutoStateListener
`interface HvacAutoStateListener : VehicleListener`  
Interface definition for the HvacAutoState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacAutoStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-auto-state-listener/on-hvac-auto-state-updated.md) | `abstract fun onHvacAutoStateUpdated(state: Boolean?)` | Callback invoked when HvacAutoState updates. |  |

#### HvacDefrosterStateListener
`interface HvacDefrosterStateListener : VehicleListener`  
Interface definition for the HvacDefrosterState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacDefrosterStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-defroster-state-listener/on-hvac-defroster-state-updated.md) | `abstract fun onHvacDefrosterStateUpdated(state: Boolean?)` | Callback invoked when HvacDefrosterState updates. |  |

#### HvacDualStateListener
`interface HvacDualStateListener : VehicleListener`  
Interface definition for the HvacDualState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacDualStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-dual-state-listener/on-hvac-dual-state-updated.md) | `abstract fun onHvacDualStateUpdated(state: Boolean?)` | Callback invoked when HvacDualState updates. |  |

#### HvacFanSpeedListener
`interface HvacFanSpeedListener : VehicleListener`  
Interface definition for the HvacFanSpeed updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacFanSpeedUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-fan-speed-listener/on-hvac-fan-speed-updated.md) | `abstract fun onHvacFanSpeedUpdated(speed: Int?)` | Callback invoked when HvacFanSpeed updates. |  |

#### HvacMaxAcStateListener
`interface HvacMaxAcStateListener : VehicleListener`  
Interface definition for the HvacMaxAcState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacMaxAcStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-max-ac-state-listener/on-hvac-max-ac-state-updated.md) | `abstract fun onHvacMaxAcStateUpdated(state: Boolean?)` | Callback invoked when HvacMaxAcState updates. |  |

#### HvacMaxDefrostStateListener
`interface HvacMaxDefrostStateListener : VehicleListener`  
Interface definition for the HvacMaxDefrostState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacMaxDefrostStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-max-defrost-state-listener/on-hvac-max-defrost-state-updated.md) | `abstract fun onHvacMaxDefrostStateUpdated(state: Boolean?)` | Callback invoked when HvacMaxDefrostState updates. |  |

#### HvacPowerStateListener
`interface HvacPowerStateListener : VehicleListener`  
Interface definition for the HvacPowerState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacPowerStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-power-state-listener/on-hvac-power-state-updated.md) | `abstract fun onHvacPowerStateUpdated(state: Boolean?)` | Callback invoked when HvacPowerState updates. |  |

#### HvacRecirculationStateListener
`interface HvacRecirculationStateListener : VehicleListener`  
Interface definition for the HvacAutoRecirculationState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacAutoRecirculationStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-recirculation-state-listener/on-hvac-auto-recirculation-state-updated.md) | `abstract fun onHvacAutoRecirculationStateUpdated(state: Boolean?)` | Callback invoked when HvacAutoRecirculationState updates. |  |

#### HvacSeatTemperatureListener
`interface HvacSeatTemperatureListener : VehicleListener`  
Interface definition for the HvacSeatTemperature updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacSeatTemperatureUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-seat-temperature-listener/on-hvac-seat-temperature-updated.md) | `abstract fun onHvacSeatTemperatureUpdated(temperature: Int?)` | Callback invoked when HvacSeatTemperature updates. |  |

#### HvacSeatVentilationListener
`interface HvacSeatVentilationListener : VehicleListener`  
Interface definition for the HvacSeatVentilation updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacSeatVentilationUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-seat-ventilation-listener/on-hvac-seat-ventilation-updated.md) | `abstract fun onHvacSeatVentilationUpdated(ventilation: Int?)` | Callback invoked when HvacSeatVentilation updates. |  |

#### HvacSideMirrorHeatStateListener
`interface HvacSideMirrorHeatStateListener : VehicleListener`  
Interface definition for the HvacSideMirrorHeatState updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacSideMirrorHeatStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-side-mirror-heat-state-listener/on-hvac-side-mirror-heat-state-updated.md) | `abstract fun onHvacSideMirrorHeatStateUpdated(state: Int?)` | Callback invoked when HvacSideMirrorHeatState updates. |  |

#### HvacSteeringWheelHeatStateListener
`interface HvacSteeringWheelHeatStateListener : VehicleListener`  
Interface definition for the HvacSteeringWheelHeatState updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacSteeringWheelHeatStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-steering-wheel-heat-state-listener/on-hvac-steering-wheel-heat-state-updated.md) | `abstract fun onHvacSteeringWheelHeatStateUpdated(state: Int?)` | Callback invoked when the HvacSteeringWheelHeatState updates. |  |

#### HvacTemperatureListener
`interface HvacTemperatureListener : VehicleListener`  
Listener for HVAC temperature updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacTemperatureUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-listener/on-hvac-temperature-updated.md) | `abstract fun onHvacTemperatureUpdated(temperature: Float?)` | Called with the updated HVAC temperature in degrees Celsius (°C). |  |

#### HvacTemperatureValueListener
`interface HvacTemperatureValueListener : VehicleListener`  
Interface definition for HVAC temperature updates in degrees Celsius (°C) and degrees Fahrenheit (°F).

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onHvacTemperatureUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/hvac-temperature-value-listener/on-hvac-temperature-updated.md) | `abstract fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?)` | Callback invoked when HVAC temperature updates. |  |

#### LightStateListener
`interface LightStateListener : VehicleListener`  
Interface definition for the HighBeamLightState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onLightStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/light-state-listener/on-light-state-updated.md) | `abstract fun onLightStateUpdated(state: LightState?)` | Callback invoked when HighBeamLightState updates. |  |

#### LowWasherFluidWaringListener
`interface LowWasherFluidWaringListener : VehicleListener`  
Interface definition for the LowWasherFluidWaring updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onLowWasherFluidWaringUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/low-washer-fluid-waring-listener/on-low-washer-fluid-waring-updated.md) | `abstract fun onLowWasherFluidWaringUpdated(state: Boolean?)` | Callback invoked when the LowWasherFluidWaring updates. |  |

#### OilLevelStateListener
`interface OilLevelStateListener : VehicleListener`  
Interface definition for the OilLevelState updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onOilLevelUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/oil-level-state-listener/on-oil-level-updated.md) | `abstract fun onOilLevelUpdated(level: OilLevelState?)` | Callback invoked when the OilLevelState updates. |  |

#### PowerDownWarningIndicatorListener
`interface PowerDownWarningIndicatorListener : VehicleListener`  
Interface definition for the PowerDownWarningIndicator updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onPowerDownWarningIndicatorUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/power-down-warning-indicator-listener/on-power-down-warning-indicator-updated.md) | `abstract fun onPowerDownWarningIndicatorUpdated(state: Boolean?)` | Callback invoked when the PowerDownWarningIndicator updates. |  |

#### SeatAirbagDeployedListener
`interface SeatAirbagDeployedListener : VehicleListener`  
Interface definition for the SeatAirbagDeployed updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatAirbagDeployUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-airbag-deployed-listener/on-seat-airbag-deploy-updated.md) | `abstract fun onSeatAirbagDeployUpdated(deployed: Boolean?)` | Callback invoked when SeatAirbagDeployed updates. |  |

#### SeatAirbagEnabledListener
`interface SeatAirbagEnabledListener : VehicleListener`  
Interface definition for the SeatAirbagEnabled updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatAirbagEnabledUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-airbag-enabled-listener/on-seat-airbag-enabled-updated.md) | `abstract fun onSeatAirbagEnabledUpdated(enabled: Boolean?)` | Callback invoked when SeatAirbagEnabled updates. |  |

#### SeatBackrestAnglePositionListener
`interface SeatBackrestAnglePositionListener : VehicleListener`  
Interface definition for the SeatBackrestAnglePosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatBackrestAnglePositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-backrest-angle-position-listener/on-seat-backrest-angle-position-updated.md) | `abstract fun onSeatBackrestAnglePositionUpdated(position: Int?)` | Callback invoked when SeatBackrestAnglePosition updates. |  |

#### SeatBeltBuckledStateListener
`interface SeatBeltBuckledStateListener : VehicleListener`  
Interface definition for the SeatBeltBuckledState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatBeltBuckledStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-belt-buckled-state-listener/on-seat-belt-buckled-state-updated.md) | `abstract fun onSeatBeltBuckledStateUpdated(state: Boolean?)` | Callback invoked when SeatBeltBuckledState updates. |  |

#### SeatBeltHeightPositionListener
`interface SeatBeltHeightPositionListener : VehicleListener`  
Interface definition for the SeatBeltHeightPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatBeltHeightPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-belt-height-position-listener/on-seat-belt-height-position-updated.md) | `abstract fun onSeatBeltHeightPositionUpdated(position: Int?)` | Callback invoked when SeatBeltHeightPosition updates. |  |

#### SeatBeltNotBuckledWarningListener
`interface SeatBeltNotBuckledWarningListener : VehicleListener`  
Interface definition for the SeatBeltNotBuckledWarning updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatBeltNotBuckledWarningUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-belt-not-buckled-warning-listener/on-seat-belt-not-buckled-warning-updated.md) | `abstract fun onSeatBeltNotBuckledWarningUpdated(state: Boolean?)` | Callback invoked when SeatBeltNotBuckledWarning updates. |  |

#### SeatDepthPositionListener
`interface SeatDepthPositionListener : VehicleListener`  
Interface definition for the SeatDepthPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatDepthPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-depth-position-listener/on-seat-depth-position-updated.md) | `abstract fun onSeatDepthPositionUpdated(position: Int?)` | Callback invoked when SeatDepthPosition updates. |  |

#### SeatForeAftPositionListener
`interface SeatForeAftPositionListener : VehicleListener`  
Interface definition for the SeatForeAftPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatForeAftPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-fore-aft-position-listener/on-seat-fore-aft-position-updated.md) | `abstract fun onSeatForeAftPositionUpdated(position: Int?)` | Callback invoked when SeatForeAftPosition updates. |  |

#### SeatHeadrestAnglePositionListener
`interface SeatHeadrestAnglePositionListener : VehicleListener`  
Interface definition for the SeatHeadrestAnglePosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatHeadrestAnglePositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-headrest-angle-position-listener/on-seat-headrest-angle-position-updated.md) | `abstract fun onSeatHeadrestAnglePositionUpdated(position: Int?)` | Callback invoked when SeatHeadrestAnglePosition updates. |  |

#### SeatHeadrestForeAftPositionListener
`interface SeatHeadrestForeAftPositionListener : VehicleListener`  
Interface definition for the SeatHeadrestForeAftPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatHeadrestForeAftPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-headrest-fore-aft-position-listener/on-seat-headrest-fore-aft-position-updated.md) | `abstract fun onSeatHeadrestForeAftPositionUpdated(position: Int?)` | Callback invoked when SeatHeadrestForeAftPosition updates. |  |

#### SeatHeadrestHeightAftPositionListener
`interface SeatHeadrestHeightAftPositionListener : VehicleListener`  
Interface definition for the SeatHeadrestHeightAftPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatHeadrestHeightAftPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-headrest-height-aft-position-listener/on-seat-headrest-height-aft-position-updated.md) | `abstract fun onSeatHeadrestHeightAftPositionUpdated(position: Int?)` | Callback invoked when SeatHeadrestHeightAftPosition updates. |  |

#### SeatHeightPositionListener
`interface SeatHeightPositionListener : VehicleListener`  
Interface definition for the SeatHeightPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatHeightPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-height-position-listener/on-seat-height-position-updated.md) | `abstract fun onSeatHeightPositionUpdated(position: Int?)` | Callback invoked when SeatHeightPosition updates. |  |

#### SeatLumbarForeAftPositionListener
`interface SeatLumbarForeAftPositionListener : VehicleListener`  
Interface definition for the SeatLumbarForeAftPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatLumbarForeAftPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-lumbar-fore-aft-position-listener/on-seat-lumbar-fore-aft-position-updated.md) | `abstract fun onSeatLumbarForeAftPositionUpdated(position: Int?)` | Callback invoked when SeatLumbarForeAftPosition updates. |  |

#### SeatLumbarSideSupportPositionListener
`interface SeatLumbarSideSupportPositionListener : VehicleListener`  
Interface definition for the SeatLumbarSideSupportPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatLumbarSideSupportPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-lumbar-side-support-position-listener/on-seat-lumbar-side-support-position-updated.md) | `abstract fun onSeatLumbarSideSupportPositionUpdated(position: Int?)` | Callback invoked when SeatLumbarSideSupportPosition updates. |  |

#### SeatOccupancyListener
`interface SeatOccupancyListener : VehicleListener`  
Interface definition for the SeatOccupancy updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatOccupancyUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-occupancy-listener/on-seat-occupancy-updated.md) | `abstract fun onSeatOccupancyUpdated(occupancy: Boolean?)` | Callback invoked when SeatOccupancy updates. |  |

#### SeatTiltPositionListener
`interface SeatTiltPositionListener : VehicleListener`  
Interface definition for the SeatTiltPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSeatTiltPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/seat-tilt-position-listener/on-seat-tilt-position-updated.md) | `abstract fun onSeatTiltPositionUpdated(position: Int?)` | Callback invoked when SeatTiltPosition updates. |  |

#### SmartGlassLevelListener
`interface SmartGlassLevelListener : VehicleListener`  
Interface definition for the SmartGlass Level updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSmartGlassLevelUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/smart-glass-level-listener/on-smart-glass-level-updated.md) | `abstract fun onSmartGlassLevelUpdated(level: Int?)` | Callback invoked when SmartGlass Level updates. |  |

#### SmartGlassStateListener
`interface SmartGlassStateListener : VehicleListener`  
Interface definition for the SmartGlassState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSmartGlassStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/smart-glass-state-listener/on-smart-glass-state-updated.md) | `abstract fun onSmartGlassStateUpdated(state: Boolean?)` | Callback invoked when SmartGlassState updates. |  |

#### SteeringWheelAngleListener
`interface SteeringWheelAngleListener : VehicleListener`  
Listener for front-wheel steering angle updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onSteeringWheelAngleUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/steering-wheel-angle-listener/on-steering-wheel-angle-updated.md) | `abstract fun onSteeringWheelAngleUpdated(angle: Float?)` | Called when a front-wheel steering angle update is available. |  |

#### TelltaleInfoListener
`interface TelltaleInfoListener : VehicleListener`  
Interface definition for the TelltaleInfo updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onTelltaleInfoUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/telltale-info-listener/on-telltale-info-updated.md) | `abstract fun onTelltaleInfoUpdated(telltales: Telltales?)` | Callback invoked when TelltaleInfo updates. |  |

#### TemperatureDisplayUnitListener
`interface TemperatureDisplayUnitListener : VehicleListener`  
Interface definition for the TemperatureDisplayUnit updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onTemperatureDisplayUnitUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/temperature-display-unit-listener/on-temperature-display-unit-updated.md) | `abstract fun onTemperatureDisplayUnitUpdated(unit: TemperatureUnit?)` | Callback invoked when TemperatureDisplayUnit updates. |  |

#### TripDrivingDistanceListener
`interface TripDrivingDistanceListener : VehicleListener`  
Interface definition for the trip driving distance updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onTripDrivingDistanceUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/trip-driving-distance-listener/on-trip-driving-distance-updated.md) | `abstract fun onTripDrivingDistanceUpdated(statistics: TripStatistics<Float>?)` | Callback invoked when trip driving distance updates. |  |

#### TripDrivingTimeListener
`interface TripDrivingTimeListener : VehicleListener`  
Interface definition for the trip driving time updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onTripDrivingTimeUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/trip-driving-time-listener/on-trip-driving-time-updated.md) | `abstract fun onTripDrivingTimeUpdated(statistics: TripStatistics<Int>?)` | Callback invoked when trip driving time updates. |  |

#### TripEvEfficiencyListener
`interface TripEvEfficiencyListener : VehicleListener`  
Interface definition for the trip EV efficiency updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onTripEvEfficiencyUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/trip-ev-efficiency-listener/on-trip-ev-efficiency-updated.md) | `abstract fun onTripEvEfficiencyUpdated(statistics: TripStatistics<Float>?)` | Callback invoked when trip EV efficiency updates. |  |

#### TripFuelEconomyListener
`interface TripFuelEconomyListener : VehicleListener`  
Interface definition for the trip fuel economy updates.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onTripFuelEconomyUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/trip-fuel-economy-listener/on-trip-fuel-economy-updated.md) | `abstract fun onTripFuelEconomyUpdated(statistics: TripStatistics<Float>?)` | Callback invoked when trip fuel economy updates. |  |

#### TurnSignalStateListener
`interface TurnSignalStateListener : VehicleListener`  
Interface definition for the TurnSignalState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onTurnSignalStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/turn-signal-state-listener/on-turn-signal-state-updated.md) | `abstract fun onTurnSignalStateUpdated(state: TurnSignalValue?)` | Callback invoked when TurnSignalStates updates. |  |

#### VehicleListener
`sealed interface VehicleListener`  
VehicleListener is an interface for constructing standard responses from the vehicle call.

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onFailed](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/vehicle-listener/on-failed.md) | `abstract fun onFailed(e: Exception)` | Callback invoked when a exception occurs. |  |

#### WindowLockStateListener
`interface WindowLockStateListener : VehicleListener`  
Interface definition for the WindowLockState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onWindowLockStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/window-lock-state-listener/on-window-lock-state-updated.md) | `abstract fun onWindowLockStateUpdated(state: Boolean?)` | Callback invoked when WindowLockState updates. |  |

#### WindowPositionListener
`interface WindowPositionListener : VehicleListener`  
Interface definition for the WindowPosition updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onWindowPositionUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/window-position-listener/on-window-position-updated.md) | `abstract fun onWindowPositionUpdated(position: Int?)` | Callback invoked when WindowPosition updates. |  |

#### WiperStateListener
`interface WiperStateListener : VehicleListener`  
Interface definition for the WiperState updates

| 멤버 | 시그니처 | 설명 | 권한 |
|---|---|---|---|
| [onWiperStateUpdated](../refs/pleos-docs/connect-sdk/Vehicle/vehicle/ai.pleos.playground.vehicle.listener/wiper-state-listener/on-wiper-state-updated.md) | `abstract fun onWiperStateUpdated(state: WiperState?)` | Callback invoked when WiperState updates. |  |

