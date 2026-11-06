package app.eodiyeotji.platform

import android.Manifest
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
 */
@Singleton
class PhoneDrivingStatePort @Inject constructor(
    signals: PhoneDriveSignals,
    manual: ManualDrivingStatePort,
) : DrivingStatePort {
    override val isDriving: StateFlow<Boolean> = combine(signals.driving, manual.isDriving) { s, m -> s || m }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, false)

    override val locksScreen: Boolean = false
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

    private companion object {
        const val BACKGROUND_RATIONALE =
            "앱을 열지 않아도 출발하면 자동으로 기록하려면 위치 권한을 \"항상 허용\"으로 바꿔 주세요.\n" +
                "서 있을 때는 전력을 적게 쓰는 위치만 가끔 확인하고, 기록은 기기 안에만 저장돼요."
    }
}
