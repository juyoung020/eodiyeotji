package app.eodiyeotji.record

import android.car.Car
import android.car.VehicleGear
import android.car.VehicleIgnitionState
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import app.eodiyeotji.platform.DriveEdge
import app.eodiyeotji.platform.DriveSignalSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transformLatest

/** 차에서 받은 원시 신호. 값은 AAOS 상수(VehicleGear / VehicleIgnitionState) 그대로. */
sealed interface VehicleSignal {
    val atMs: Long

    data class Gear(val gear: Int, override val atMs: Long) : VehicleSignal
    data class Ignition(val state: Int, override val atMs: Long) : VehicleSignal
}

/**
 * AAOS 표준 Car API(CarPropertyManager)로 기어·시동 상태를 듣는다. 권한: android.car.permission.CAR_POWERTRAIN(normal).
 *
 * - 기어: GEAR_SELECTION(변속 레버)을 먼저, 없으면 CURRENT_GEAR. 에뮬레이터는 둘 다 있다.
 * - 시동: IGNITION_STATE는 차마다 없을 수 있다(에뮬레이터에 없음) → 없으면 기어만으로 판단한다.
 * - 이벤트는 값이 바뀔 때만 오고(ON_CHANGE), 등록 직후 현재 값이 한 번 온다. 주차 중에는 아무 일도 하지 않는다.
 * - 콜백은 전용 HandlerThread에서 받는다 → UI 스레드를 쓰지 않는다.
 * - 주행 시작·끝([edges])은 [DriveRule]로 판단하고 [debounced]로 깜빡임을 거른다.
 *   다시 뜬 프로세스의 이어 기록 여부도 등록 직후 오는 현재 기어 값으로 정해지므로 resumeDriving은 쓰지 않는다.
 */
@Singleton
class VehicleDriveSignals @Inject constructor(
    @ApplicationContext private val context: Context,
) : DriveSignalSource {
    override fun edges(resumeDriving: Boolean, lastFixMs: Long): Flow<DriveEdge> = signals().toDriveEdges().debounced()

    // status·unregisterCallback은 Car API 35에서 deprecated지만, 기기(Pleos 2.1)는 API 34라서 대체 API가 없다.
    @Suppress("DEPRECATION")
    fun signals(): Flow<VehicleSignal> = callbackFlow {
        val thread = HandlerThread("eodi-car").apply { start() }
        var manager: CarPropertyManager? = null

        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
                val v = value.value as? Int ?: return
                val now = System.currentTimeMillis()
                when (value.propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.CURRENT_GEAR -> trySend(VehicleSignal.Gear(v, now))
                    VehiclePropertyIds.IGNITION_STATE -> trySend(VehicleSignal.Ignition(v, now))
                }
            }

            override fun onErrorEvent(propId: Int, zone: Int) {
                Log.w(TAG, "속성 오류 0x${propId.toString(16)} zone=$zone")
            }
        }

        // DO_NOT_WAIT: 바로 돌아오고, 연결되면 ready=true로 부른다. 차량 서비스가 죽었다 살아나면 다시 ready=true가 온다.
        val car = Car.createCar(context, Handler(thread.looper), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
            if (!ready) {
                Log.w(TAG, "차량 서비스 연결 끊김 — 다시 연결되기를 기다린다")
                manager = null
                return@createCar
            }
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = m
            subscribe(m, callback)
        }

        awaitClose {
            runCatching { manager?.unregisterCallback(callback) }
            runCatching { car.disconnect() }
            thread.quitSafely()
        }
    }

    @Suppress("DEPRECATION") // subscribePropertyEvents는 Car API 35부터. 기기(Pleos 2.1)는 API 34라서 registerCallback을 쓴다.
    private fun subscribe(m: CarPropertyManager, callback: CarPropertyManager.CarPropertyEventCallback) {
        val gearProp = listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.CURRENT_GEAR)
            .firstOrNull { m.getCarPropertyConfig(it) != null }
        if (gearProp != null) {
            m.registerCallback(callback, gearProp, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }
        val hasIgnition = m.getCarPropertyConfig(VehiclePropertyIds.IGNITION_STATE) != null
        if (hasIgnition) {
            m.registerCallback(callback, VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }
        Log.i(TAG, "차량 신호 구독: 기어=${gearProp?.let { VehiclePropertyIds.toString(it) } ?: "없음"}, 시동=${if (hasIgnition) "있음" else "없음"}")
    }

    companion object {
        private const val TAG = "EodiVehicle"
    }
}

/**
 * 기어·시동 신호 → "지금 주행 기록 중이어야 하나". 신호를 받을 때마다 판단 값을 내놓는 작은 상태 기계.
 *
 * - 시동이 꺼짐(OFF/LOCK/ACC) → 기록 안 함.
 * - 기어가 P가 아님 → 기록함.
 * - 시동이 켜지는 순간(ON/START) → P여도 기록 시작 ("시동을 걸면 기록이 시작된다"). 출발 지점을 놓치지 않는다.
 *   이렇게 시작한 기록은 D 등으로 갔다가 다시 P로 돌아오는 순간 끝난다.
 * - 에뮬레이터처럼 시동 신호가 없으면 기어 P↔비P만으로 시작·끝.
 *
 * 등록 직후 현재 값이 오는 순서(시동 먼저/기어 먼저)와 상관없이 같은 결과가 나오게 "P로 돌아오는 순간"만 끝으로 본다.
 */
internal class DriveRule {
    private var ignitionOn: Boolean? = null
    private var parked: Boolean? = null
    private var armedByIgnition = false

    /** 반환: 기록해야 하면 true, 아니면 false, 아직 판단할 신호가 없으면 null. */
    fun onSignal(s: VehicleSignal): Boolean? {
        when (s) {
            is VehicleSignal.Gear -> if (s.gear != VehicleGear.GEAR_UNKNOWN) {
                val p = s.gear == VehicleGear.GEAR_PARK
                if (p && parked == false) armedByIgnition = false
                parked = p
            }
            is VehicleSignal.Ignition -> when (s.state) {
                VehicleIgnitionState.ON, VehicleIgnitionState.START -> {
                    if (ignitionOn != true) armedByIgnition = true
                    ignitionOn = true
                }
                VehicleIgnitionState.OFF, VehicleIgnitionState.LOCK, VehicleIgnitionState.ACC -> {
                    armedByIgnition = false
                    ignitionOn = false
                }
            }
        }
        return when {
            ignitionOn == false -> false
            parked == false -> true
            parked == null && ignitionOn == null -> null
            else -> armedByIgnition
        }
    }
}

/** 신호 흐름 → 판단이 바뀔 때만 [DriveEdge] (디바운스 전). */
internal fun Flow<VehicleSignal>.toDriveEdges(): Flow<DriveEdge> {
    val rule = DriveRule()
    return mapNotNull { s -> rule.onSignal(s)?.let { DriveEdge(it, s.atMs) } }
        .distinctUntilChanged { a, b -> a.active == b.active }
}

/** 기어가 P에서 빠져 이만큼 유지돼야 시작 (P→R→D 조작 중 깜빡임 무시). */
internal const val START_DEBOUNCE_MS = 2_000L

/** P로 돌아와 이만큼 유지돼야 끝 (잠깐 P에 넣었다 빼는 경우 기록을 쪼개지 않음). */
internal const val END_DEBOUNCE_MS = 5_000L

/** 판단이 바뀐 뒤 일정 시간 그대로일 때만 내보낸다. 내보내는 시각(tMs)은 처음 바뀐 순간 그대로다. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<DriveEdge>.debounced(): Flow<DriveEdge> = transformLatest { edge ->
    delay(if (edge.active) START_DEBOUNCE_MS else END_DEBOUNCE_MS)
    emit(edge)
}
