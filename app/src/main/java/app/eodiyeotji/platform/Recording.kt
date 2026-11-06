package app.eodiyeotji.platform

import android.content.Context
import app.eodiyeotji.core.LocationFix
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow

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
