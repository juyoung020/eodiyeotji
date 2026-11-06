package app.eodiyeotji.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.eodiyeotji.phone.PhoneDriveSignals
import app.eodiyeotji.phone.PhoneLocationSource
import app.eodiyeotji.phone.PhoneNavigator
import app.eodiyeotji.phone.PhoneSpeechInput
import app.eodiyeotji.phone.PhoneSpeechOutput
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.agent.ManualDrivingStatePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 폰 판의 플랫폼 연결: 위치는 Android LocationManager, 주행 신호는 위치로 판단(Rust drivedetect),
 * 길 안내는 설치된 지도 앱, 음성은 Android 표준 인식·합성.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds abstract fun location(impl: PhoneLocationSource): LocationSource
    @Binds abstract fun driveSignals(impl: PhoneDriveSignals): DriveSignalSource
    @Binds abstract fun navigator(impl: PhoneNavigator): Navigator
    @Binds abstract fun speechInput(impl: PhoneSpeechInput): SpeechInput
    @Binds abstract fun speechOutput(impl: PhoneSpeechOutput): SpeechOutput
    @Binds abstract fun drivingState(impl: PhoneDrivingStatePort): DrivingStatePort
    @Binds abstract fun recording(impl: PhoneRecordingPlatform): RecordingPlatform
}

/**
 * 폰 주행 상태 = 위치로 판단한 주행 중 ([PhoneDriveSignals.driving]) 또는 디버그 화면의 수동 값.
 * 화면은 잠그지 않는다 ([locksScreen] false): 기억 에이전트가 음성 + 큰 카드로 짧게 답할 뿐이다.
 *
 * 폰은 동승자가 들고 있을 수도 있다 → 화면의 "동승자예요"를 누르면 이번 주행 동안은 정차 화면(글로 묻기·목록)을 그대로 쓴다
 * ([setPassenger]). 주행이 끝나면 저절로 풀린다.
 */
@Singleton
class PhoneDrivingStatePort @Inject constructor(
    signals: PhoneDriveSignals,
    manual: ManualDrivingStatePort,
) : DrivingStatePort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val passengerFlag = MutableStateFlow(false)

    /** 차가 달리는 중인지 (동승자 여부와 상관없이). 화면 위 "운전 중" 띠가 본다. */
    val moving: StateFlow<Boolean> = combine(signals.driving, manual.isDriving) { s, m -> s || m }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** 이번 주행은 동승자가 쓰는 중. */
    val passenger: StateFlow<Boolean> = passengerFlag.asStateFlow()

    override val isDriving: StateFlow<Boolean> = combine(moving, passengerFlag) { d, p -> d && !p }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val locksScreen: Boolean = false

    init {
        // 주행이 끝나면 동승자 표시를 푼다 (다음 주행은 다시 운전 중 화면부터)
        scope.launch { moving.collect { if (!it) passengerFlag.value = false } }
    }

    fun setPassenger(value: Boolean) {
        passengerFlag.value = value && moving.value
    }
}

/**
 * 폰 첫 실행 권한: ① 위치(정확한 위치) + 알림 → ② 설명을 보여 준 뒤 "항상 허용" 위치(앱을 닫아도·폰을 켠 직후에도 출발 감지).
 * 위치만 허용돼도 기록은 시작한다 (앱이 켜져 있는 동안 시작한 기록 서비스는 계속 위치를 받는다).
 */
@Singleton
class PhoneRecordingPlatform @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecordingPlatform {
    override fun hasRecordingPermission(): Boolean = PhoneLocationSource.hasPermission(context)

    override fun permissionSteps(): List<PermissionStep> = buildList {
        val first = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(PermissionStep(first))
        if (Build.VERSION.SDK_INT >= 29) {
            add(
                PermissionStep(
                    permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    rationale = BACKGROUND_RATIONALE,
                    requires = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                ),
            )
        }
    }

    override val foregroundServiceType: Int =
        if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

    override val checkedIssues: List<RecordingIssue> = RecordingIssue.entries.toList()

    /** 위치 → "항상 허용" → 알림 → 배터리 최적화 순 (머리 상태 표시는 맨 앞 하나만 보인다). */
    override fun issues(): List<RecordingIssue> = buildList {
        if (!hasRecordingPermission()) {
            add(RecordingIssue.LOCATION_OFF)
        } else if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            add(RecordingIssue.BACKGROUND_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) add(RecordingIssue.NOTIFICATIONS_OFF)
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(context.packageName)) add(RecordingIssue.BATTERY_OPTIMIZED)
    }

    /** 앱 안에서 바로 묻는 것: 위치, 알림. "항상 허용"은 첫 실행에 한 번만 묻고 그다음은 설정 화면으로 보낸다. */
    override fun permissionsFor(issue: RecordingIssue): List<String> = when (issue) {
        RecordingIssue.LOCATION_OFF -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        RecordingIssue.NOTIFICATIONS_OFF -> if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        else -> emptyList()
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val BACKGROUND_RATIONALE =
            "앱을 열지 않아도 출발하면 자동으로 기록하려면 위치 권한을 \"항상 허용\"으로 바꿔 주세요.\n" +
                "서 있을 때는 전력을 적게 쓰는 위치만 가끔 확인하고, 기록은 기기 안에만 저장돼요."
    }
}
