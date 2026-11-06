package app.eodiyeotji.ui.agent

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 중인지 (true면 기억 에이전트가 음성 + 큰 카드 1장으로 답한다).
 * 차는 AAOS `CarUxRestrictionsManager`(제약 있음 = 주행, `CarDrivingStatePort`), 폰은 위치로 판단한 주행 감지(`PhoneDrivingStatePort`).
 * 어느 쪽을 쓸지는 판마다 `platform/PlatformModule`이 정한다.
 */
interface DrivingStatePort {
    val isDriving: StateFlow<Boolean>

    /** 주행 중에 다른 화면을 감추고 주행 화면만 보일지. 차는 규제상 true, 폰은 false(음성·큰 카드를 먼저 보여 줄 뿐 막지 않는다). */
    val locksScreen: Boolean get() = true
}

/**
 * 손으로 바꾸는 값 (기본: 정차). 디버그 화면이 이걸로 주행 화면을 확인하고, 차·폰 구현이 이 값도 주행으로 본다.
 */
@Singleton
class ManualDrivingStatePort @Inject constructor() : DrivingStatePort {
    private val driving = MutableStateFlow(false)
    override val isDriving: StateFlow<Boolean> = driving.asStateFlow()

    fun setDriving(value: Boolean) {
        driving.value = value
    }
}
