package app.eodiyeotji.ui.agent

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 중인지 (true면 음성 + 큰 카드 1장 화면, false면 정차 중 전체 화면).
 * 실제 값은 AAOS `CarUxRestrictionsManager`(제약 있음 = 주행)로 채운다.
 */
interface DrivingStatePort {
    val isDriving: StateFlow<Boolean>
}

/**
 * 손으로 바꾸는 임시 구현 (기본: 정차).
 * TODO(통합): CarUxRestrictions를 읽는 구현으로 교체. 디버그 화면은 이걸로 주행 화면을 확인한다.
 */
@Singleton
class ManualDrivingStatePort @Inject constructor() : DrivingStatePort {
    private val driving = MutableStateFlow(false)
    override val isDriving: StateFlow<Boolean> = driving.asStateFlow()

    fun setDriving(value: Boolean) {
        driving.value = value
    }
}
