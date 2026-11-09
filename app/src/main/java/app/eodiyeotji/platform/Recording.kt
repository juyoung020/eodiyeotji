package app.eodiyeotji.platform

import android.content.Context
import app.eodiyeotji.core.LocationFix
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 중 위치를 받는 곳. 위치를 [LocationFix] 흐름으로 바꿔 줄 뿐, 가공은 하지 않는다.
 * 차는 Pleos FusedLocation, 폰은 Android LocationManager(고정밀 1초 간격).
 */
interface LocationSource {
    /** 이 기기에서 위치를 받을 수 있는지. 패키지 조회라서 백그라운드 스레드에서 부른다. */
    fun isSupported(): Boolean

    fun hasPermission(): Boolean

    /** 위치 업데이트 흐름. 모으는 동안만 켜져 있다. 권한이 없으면 SecurityException으로 닫힌다. */
    fun updates(): Flow<LocationFix>
}

/**
 * 주행 시작·끝 한 번. [tMs]는 실제로 바뀐 시각(디바운스·감지가 끝난 시각이 아니다).
 * [backlog]: 시작일 때 감지 전에 이미 받은 점 (폰: 출발해서 주행으로 판정되기까지의 위치). 차는 비어 있다.
 */
data class DriveEdge(val active: Boolean, val tMs: Long, val backlog: List<LocationFix> = emptyList())

/**
 * "지금 주행 중인가"를 알려 주는 곳 (흔들림 거르기까지 끝난 값).
 * - 차: 기어·시동 신호 (AAOS CarPropertyManager). 등록 직후 현재 상태가 한 번 온다.
 * - 폰: 위치로 판단 (Rust `drivedetect`: 시속 15km 이상이 이어지면 시작, 4분 넘게 머물면 끝).
 */
interface DriveSignalSource {
    /**
     * 주행 시작·끝 흐름. [resumeDriving]이 true면 직전 프로세스가 주행 기록 중에 죽었다는 뜻이다
     * ([lastFixMs] = 마지막으로 저장한 점 시각) — 폰은 주행 중 상태로 이어서 판단한다. 차는 차량 신호가 알려 주므로 무시한다.
     */
    fun edges(resumeDriving: Boolean = false, lastFixMs: Long = 0L): Flow<DriveEdge>

    /**
     * 차량 신호(기어·시동)를 받고 있는지 — 상태 표시의 "차량 신호 연결됨". 차량 신호가 없는 판(폰)은 null.
     * 기록 서비스가 [edges]를 모으는 동안만 true가 될 수 있다.
     */
    val linked: StateFlow<Boolean>? get() = null
}

/**
 * 기록을 막거나 나중에 멈추게 할 수 있는 설정. 화면 머리의 상태 표시와 설명 시트가 쓴다 (record/RecordingStatus).
 * 순서 = 중요한 순서 (앞의 것이 표시를 차지한다).
 */
enum class RecordingIssue {
    /** 위치 권한 꺼짐 → 기록하지 않는다 */
    LOCATION_OFF,
    /** (폰) "항상 허용"이 아님 → 앱을 닫거나 폰을 켠 직후에는 출발을 못 알아챈다 */
    BACKGROUND_OFF,
    /** (폰) 알림 꺼짐 → 기록 중 알림이 안 보인다 (기록은 한다) */
    NOTIFICATIONS_OFF,
    /** (폰) 배터리 최적화 대상 → 시스템이 오래 쉰 앱을 멈출 수 있다 */
    BATTERY_OPTIMIZED,
}

/**
 * 첫 실행 권한 요청 한 단계 (시스템 대화상자 한 번). [rationale]이 있으면 먼저 그 설명을 보여 주고 묻는다.
 * [requires]가 하나라도 허용되지 않았으면 이 단계는 건너뛴다 (예: 백그라운드 위치는 위치 권한이 먼저 있어야 한다).
 */
data class PermissionStep(
    val permissions: List<String>,
    val rationale: String? = null,
    val requires: List<String> = emptyList(),
)

/** 기록 서비스가 기기마다 달리 하는 것. */
interface RecordingPlatform {
    /** 기록에 꼭 필요한 권한이 있는지 (없으면 기록 서비스를 켜지 않는다). */
    fun hasRecordingPermission(): Boolean

    /** 첫 실행에 물을 권한, 순서대로. 이미 허용된 단계는 건너뛴다. 패키지 조회가 있어 백그라운드 스레드에서 부른다. */
    fun permissionSteps(): List<PermissionStep>

    /** 이 판에서 확인하는 설정 (상태 시트의 줄, 순서대로). */
    val checkedIssues: List<RecordingIssue> get() = listOf(RecordingIssue.LOCATION_OFF)

    /** 지금 걸린 설정 ([checkedIssues] 중, 중요한 순서). 권한·전원 설정 조회뿐이라 가볍지만 백그라운드에서 부른다. */
    fun issues(): List<RecordingIssue> = if (hasRecordingPermission()) emptyList() else listOf(RecordingIssue.LOCATION_OFF)

    /** [issue]를 앱 안에서 바로 물어 고칠 수 있는 권한 (시스템 권한 창). 비면 설정 화면으로 보낸다. */
    fun permissionsFor(issue: RecordingIssue): List<String> = emptyList()

    /** 기록 서비스의 포그라운드 서비스 종류 (`ServiceInfo.FOREGROUND_SERVICE_TYPE_*`, 매니페스트와 같은 값). */
    val foregroundServiceType: Int
}

/** Hilt 주입이 안 되는 곳(부팅 수신기·Compose 함수)에서 [RecordingPlatform]을 꺼낸다. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlatformEntryPoint {
    fun recordingPlatform(): RecordingPlatform
}

fun Context.recordingPlatform(): RecordingPlatform =
    EntryPointAccessors.fromApplication(applicationContext, PlatformEntryPoint::class.java).recordingPlatform()

/**
 * 차 안에 탄 인원 (좌석 센서). 차는 Pleos Vehicle SDK `Seat.getCurrentSeatOccupancy`, 폰은 알 수 없어 null.
 * 누구인지는 모른다 — 몇 명이 앉았는지만 센다.
 */
interface CabinSource {
    /** 지금 앉아 있는 좌석 수. 센서가 없거나 읽지 못하면 null. */
    suspend fun occupants(): Int?
}
