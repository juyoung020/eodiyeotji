package app.eodiyeotji.ui.agent

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 주행 중인지 = AAOS 운전자 주의 분산 제약(`CarUxRestrictions.isRequiresDistractionOptimization`)이 걸렸는지.
 * 앱 내내 하나만 연결해 둔다. 차량 서비스가 없으면(테스트) 정차로 본다. 주행 중에는 화면을 주행 화면으로 잠근다([locksScreen]).
 * [ManualDrivingStatePort] 값이 켜지면(디버그 화면) 그것도 주행으로 본다.
 */
@Singleton
class CarDrivingStatePort @Inject constructor(
    @ApplicationContext context: Context,
    private val manual: ManualDrivingStatePort,
) : DrivingStatePort {
    private val restricted = MutableStateFlow(false)
    override val isDriving: StateFlow<Boolean> = combine(restricted, manual.isDriving) { r, m -> r || m }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, false)
    override val locksScreen: Boolean = true

    init {
        runCatching { connect(context) }.onFailure { Log.w(TAG, "차량 서비스 없음 → 정차로 본다", it) }
    }

    private fun connect(context: Context) {
        val thread = HandlerThread("eodi-ux").apply { start() }
        Car.createCar(context, Handler(thread.looper), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
            if (!ready) return@createCar
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return@createCar
            val update = { r: CarUxRestrictions? -> restricted.value = r?.isRequiresDistractionOptimization == true }
            update(m.currentCarUxRestrictions)
            m.registerListener { update(it) }
        }
    }

    private companion object {
        const val TAG = "EodiUx"
    }
}
