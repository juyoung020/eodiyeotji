package app.eodiyeotji.platform

import android.content.Context
import android.content.pm.ServiceInfo
import app.eodiyeotji.pleos.PleosLocationSource
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
    // 위치 · 주행 신호: 차가 아닌 기기(태블릿)에서는 가짜 차 (TabletVehicle.kt)
    @Binds abstract fun location(impl: CarLocationSource): LocationSource
    @Binds abstract fun driveSignals(impl: CarDriveSignals): DriveSignalSource
    // 차가 아닌 기기(태블릿)에서는 그 기기의 지도 앱으로 넘긴다 (CarNavigator)
    @Binds abstract fun navigator(impl: CarNavigator): Navigator
    // 말로 묻기 · 읽어 주기: 차가 아닌 기기(태블릿)에는 Pleos 음성이 없어 Android 표준 음성으로 (TabletSpeech.kt)
    @Binds abstract fun speechInput(impl: CarSpeechInput): SpeechInput
    @Binds abstract fun speechOutput(impl: CarSpeechOutput): SpeechOutput
    @Binds abstract fun drivingState(impl: CarDrivingStatePort): DrivingStatePort
    @Binds abstract fun recording(impl: CarRecordingPlatform): RecordingPlatform
    @Binds abstract fun cabin(impl: app.eodiyeotji.pleos.PleosCabin): CabinSource
    @Binds abstract fun energy(impl: app.eodiyeotji.pleos.PleosEnergy): EnergySource
    @Binds abstract fun moodLight(impl: app.eodiyeotji.pleos.PleosMoodLight): MoodLight
    @Binds abstract fun gauges(impl: app.eodiyeotji.pleos.PleosGauges): GaugeSource
}

/** 차: Pleos 차량 위치 권한 하나만 묻는다 (Pleos 위치 서비스가 없는 환경에서는 묻지 않는다). 서비스 종류는 specialUse. */
@Singleton
class CarRecordingPlatform @Inject constructor(
    @ApplicationContext private val context: Context,
    private val location: PleosLocationSource,
    private val vehicle: TabletVehicle,
) : RecordingPlatform {
    // 태블릿(가짜 차)은 넣어 둔 시연 주행을 쓰므로 물을 권한이 없다
    override fun hasRecordingPermission(): Boolean = vehicle.isTablet || PleosLocationSource.hasPermission(context)

    override fun permissionSteps(): List<PermissionStep> =
        if (!hasRecordingPermission() && location.isSupported()) {
            listOf(PermissionStep(listOf(PleosLocationSource.PERMISSION), rationale = LOCATION_NOTICE))
        } else {
            emptyList()
        }

    override val foregroundServiceType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

    override fun permissionsFor(issue: RecordingIssue): List<String> =
        if (issue == RecordingIssue.LOCATION_OFF) listOf(PleosLocationSource.PERMISSION) else emptyList()

    private companion object {
        /**
         * 위치 권한을 묻기 전 알림 (Pleos 앱마켓 심사 "사용자 개인정보 및 보안": 수집 항목 · 목적 · 보유 기간 · 거부할 권리를 알리고,
         * "계속"을 누른 경우에만 묻는다. 바깥을 눌러 닫으면 동의로 보지 않고 건너뛴다 — RecordingPermissionGate).
         */
        const val LOCATION_NOTICE =
            "어디였지는 차의 위치와 시각을 모아 다녀온 길과 머문 곳을 자동으로 기록해요.\n" +
                "· 모으는 것: 차량 위치 · 시각 (주행 중에만)\n" +
                "· 쓰는 곳: 하루 기록 · 말로 찾기 · 회상 안내\n" +
                "· 보관: 이 차 안에만, 직접 지울 때까지 (날짜 · 장소별로 언제든 지울 수 있어요)\n" +
                "거절해도 돼요. 그러면 자동 기록만 꺼지고 나머지는 그대로 쓸 수 있어요."
    }
}
