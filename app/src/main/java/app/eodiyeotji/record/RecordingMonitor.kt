package app.eodiyeotji.record

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 기록 서비스가 지금 하는 일. */
enum class RecordingPhase {
    /** 기록 서비스가 돌지 않는다 (아직 안 켜졌거나, 위치 권한·위치 서비스가 없어 멈춤) */
    OFF,
    /** 켜져서 출발을 기다린다 (주차 · 서 있음) */
    WAITING,
    /** 주행을 기록하는 중 */
    DRIVING,
}

/**
 * 기록 서비스 상태의 단일 출처. [RecordingService]만 바꾸고, 화면(머리의 상태 표시)은 [phase]를 본다.
 * 같은 프로세스 안 싱글턴이라 서비스와 화면이 따로 떠도 같은 값을 본다. 값만 옮기므로 UI 스레드를 막지 않는다.
 */
@Singleton
class RecordingMonitor @Inject constructor() {
    private val _phase = MutableStateFlow(RecordingPhase.OFF)
    val phase: StateFlow<RecordingPhase> = _phase.asStateFlow()

    internal fun set(phase: RecordingPhase) {
        _phase.value = phase
    }
}
