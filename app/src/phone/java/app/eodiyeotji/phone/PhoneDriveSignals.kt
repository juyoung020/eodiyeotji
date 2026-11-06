package app.eodiyeotji.phone

import android.util.Log
import app.eodiyeotji.core.DriveDetectResult
import app.eodiyeotji.core.EodiDriveDetect
import app.eodiyeotji.core.MotionSample
import app.eodiyeotji.platform.DriveEdge
import app.eodiyeotji.platform.DriveSignalSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * 폰 주행 신호: 차 신호가 없으니 위치로 판단한다. 판단은 Rust `drivedetect`([EodiDriveDetect]), 여기는 위치를 묶어 넘기고
 * Rust가 돌려준 "고정밀 필요?"에 맞춰 위치 요청([LocationRate])만 바꾼다.
 *
 * - 서 있을 때: 1분 간격 저전력 위치 + passive. 움직임이 보이면 Rust가 고정밀을 켜라고 한다 → 1초 GPS로 확인.
 * - 주행 중: 1초 GPS. 4분 넘게 한자리면 주행 끝 → 다시 저전력.
 * - 위치는 [BATCH_SIZE]개 또는 [FLUSH_MS]마다 묶어서 Rust에 넘긴다 (JNI는 묶음으로). 같은 타이머로 tick도 보낸다
 *   (위치가 끊겨도 주행 끝을 알게). 서 있을 때 타이머는 [IDLE_TICK_MS].
 * 흐름은 기록 서비스가 모으는 동안만 돈다. [driving]은 화면(주행 상태)이 본다.
 */
@Singleton
class PhoneDriveSignals @Inject constructor(
    private val location: PhoneLocationSource,
) : DriveSignalSource {
    private val _driving = MutableStateFlow(false)

    /** 지금 주행 중인지 (Rust 판단, 기록 서비스가 돌고 있을 때만 바뀐다). */
    val driving: StateFlow<Boolean> = _driving.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun edges(resumeDriving: Boolean, lastFixMs: Long): Flow<DriveEdge> = channelFlow {
        val detect = EodiDriveDetect()
        val rate = MutableStateFlow(if (resumeDriving) LocationRate.HIGH else LocationRate.LOW)
        if (resumeDriving) {
            detect.resumeDriving(lastFixMs)
            _driving.value = true
            send(DriveEdge(active = true, tMs = lastFixMs))
        }
        val buffer = ArrayList<MotionSample>(BATCH_SIZE)

        suspend fun apply(r: DriveDetectResult) {
            _driving.value = r.driving
            rate.value = if (r.highRate) LocationRate.HIGH else LocationRate.LOW
            for (e in r.edges) {
                Log.i(TAG, "주행 ${if (e.active) "시작" else "끝"} t=${e.tMs}")
                send(e)
            }
        }

        try {
            val fixes = rate.flatMapLatest { r -> location.motion(r).catch { Log.w(TAG, "위치 수신 실패", it) } }
                .map<MotionSample, Tick> { Tick.Fix(it) }
            merge(fixes, ticker { if (rate.value == LocationRate.HIGH) FLUSH_MS else IDLE_TICK_MS }).collect { t ->
                when (t) {
                    is Tick.Fix -> {
                        buffer += t.sample
                        // 저전력일 때는 점이 드물어 바로 넘긴다
                        if (buffer.size >= BATCH_SIZE || rate.value == LocationRate.LOW) {
                            apply(detect.push(buffer.toList()))
                            buffer.clear()
                        }
                    }
                    Tick.Timer -> {
                        if (buffer.isNotEmpty()) {
                            apply(detect.push(buffer.toList()))
                            buffer.clear()
                        }
                        apply(detect.tick(System.currentTimeMillis()))
                    }
                }
            }
        } finally {
            _driving.value = false
            detect.close()
        }
    }.flowOn(Dispatchers.Default)

    private sealed interface Tick {
        data class Fix(val sample: MotionSample) : Tick
        data object Timer : Tick
    }

    private fun ticker(periodMs: () -> Long): Flow<Tick> = flow {
        while (true) {
            delay(periodMs())
            emit(Tick.Timer)
        }
    }

    private companion object {
        const val TAG = "EodiPhoneDrive"
        const val BATCH_SIZE = 10
        const val FLUSH_MS = 5_000L

        /** 서 있을 때는 드물게 깨어난다 (고정밀 확인 시간이 끝났는지 보는 정도). */
        const val IDLE_TICK_MS = 30_000L
    }
}
