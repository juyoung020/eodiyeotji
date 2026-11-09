package app.eodiyeotji.platform

import android.content.Context
import android.content.pm.ServiceInfo
import app.eodiyeotji.pleos.PleosLocationSource
import app.eodiyeotji.pleos.PleosNavi
import app.eodiyeotji.pleos.PleosSpeech
import app.eodiyeotji.pleos.PleosTts
import app.eodiyeotji.record.VehicleDriveSignals
import app.eodiyeotji.ui.agent.CarDrivingStatePort
import app.eodiyeotji.ui.agent.DrivingStatePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 차(Pleos Connect) 판의 플랫폼 연결: 위치·내비·음성은 Pleos SDK, 주행 신호는 AAOS 기어·시동, 주행 상태는 CarUxRestrictions.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds abstract fun location(impl: PleosLocationSource): LocationSource
    @Binds abstract fun driveSignals(impl: VehicleDriveSignals): DriveSignalSource
    @Binds abstract fun navigator(impl: PleosNavi): Navigator
    @Binds abstract fun speechInput(impl: PleosSpeech): SpeechInput
    @Binds abstract fun speechOutput(impl: PleosTts): SpeechOutput
    @Binds abstract fun drivingState(impl: CarDrivingStatePort): DrivingStatePort
    @Binds abstract fun recording(impl: CarRecordingPlatform): RecordingPlatform
    @Binds abstract fun cabin(impl: app.eodiyeotji.pleos.PleosCabin): CabinSource
    @Binds abstract fun energy(impl: app.eodiyeotji.pleos.PleosEnergy): EnergySource
}

/** 차: Pleos 차량 위치 권한 하나만 묻는다 (Pleos 위치 서비스가 없는 환경에서는 묻지 않는다). 서비스 종류는 specialUse. */
@Singleton
class CarRecordingPlatform @Inject constructor(
    @ApplicationContext private val context: Context,
    private val location: PleosLocationSource,
) : RecordingPlatform {
    override fun hasRecordingPermission(): Boolean = PleosLocationSource.hasPermission(context)

    override fun permissionSteps(): List<PermissionStep> =
        if (!hasRecordingPermission() && location.isSupported()) listOf(PermissionStep(listOf(PleosLocationSource.PERMISSION))) else emptyList()

    override val foregroundServiceType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

    override fun permissionsFor(issue: RecordingIssue): List<String> =
        if (issue == RecordingIssue.LOCATION_OFF) listOf(PleosLocationSource.PERMISSION) else emptyList()
}
