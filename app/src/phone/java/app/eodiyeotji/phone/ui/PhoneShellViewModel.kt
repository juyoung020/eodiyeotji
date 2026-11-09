package app.eodiyeotji.phone.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.phone.PhoneNavigator
import app.eodiyeotji.phone.PhonePrefs
import app.eodiyeotji.phone.PhoneSettings
import app.eodiyeotji.platform.PhoneDrivingStatePort
import app.eodiyeotji.record.RecordingController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 기록에 쓰는 권한이 지금 허용돼 있는지 (설정 화면 · 기록 화면 경고 줄). */
@Immutable
data class PhonePermissions(
    val location: Boolean = true,
    val background: Boolean = true,
    val notifications: Boolean = true,
)

/**
 * 폰 화면 뼈대(아래 탭 · 운전 중 띠 · 설정 · 지도 앱 없음 시트)가 보는 것을 한곳에 모은다.
 * 계산은 없다: 설정 값, 주행·동승자 상태, 길 안내 상태를 그대로 넘기고 버튼을 이어 줄 뿐이다.
 */
@HiltViewModel
class PhoneShellViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: PhoneSettings,
    private val navigator: PhoneNavigator,
    private val driving: PhoneDrivingStatePort,
    private val parking: app.eodiyeotji.phone.PhoneParking,
) : ViewModel() {
    /** 내 차 주차 위치 (없으면 null). */
    val parked: StateFlow<app.eodiyeotji.core.rust.ParkedCar?> = parking.car

    /** "찾았어요": 주차 카드를 감춘다. */
    fun dismissParked() = parking.dismiss()

    val prefs: StateFlow<PhonePrefs> = settings.prefs
    val moving: StateFlow<Boolean> = driving.moving
    val passenger: StateFlow<Boolean> = driving.passenger
    /** 앱 안 길 안내 (안내 중이 아니면 null). */
    val nav: StateFlow<app.eodiyeotji.phone.NavUi?> = navigator.ui

    /** 길 안내 화면이 떠 있는지 (읽는 말 자막 자리를 아래로 옮긴다). */
    val navigating: StateFlow<Boolean> = navigator.ui
        .map { it != null }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    fun endNav() = navigator.stop()
    fun nextStop() = navigator.next()

    private val _permissions = MutableStateFlow(PhonePermissions())
    val permissions: StateFlow<PhonePermissions> = _permissions.asStateFlow()


    fun setPassenger(value: Boolean) = driving.setPassenger(value)

    fun markBackgroundAsked() = settings.markBackgroundAsked()

    fun finishOnboarding() {
        settings.markOnboarded()
        startRecording()
    }

    /** 권한이 있으면 기록 서비스를 켠다 (여러 번 불러도 하나만 돈다). */
    fun startRecording() {
        viewModelScope.launch(Dispatchers.IO) { RecordingController.ensureStarted(context) }
    }

    /** 화면으로 돌아올 때마다 권한을 다시 읽는다 (설정 앱에서 바꾸고 돌아올 수 있다). */
    fun refreshPermissions() {
        _permissions.value = PhonePermissions(
            location = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            background = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    /** 이 앱의 시스템 설정 화면 (권한을 다시 켤 때). "항상 허용"은 다시 묻지 않고 여기로 보낸다. */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}
