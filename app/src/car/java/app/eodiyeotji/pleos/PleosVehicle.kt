package app.eodiyeotji.pleos

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Pleos Vehicle SDK 하나를 앱 전체가 같이 쓴다 (좌석 · 배터리 · 연료). 처음 부를 때 한 번 초기화. */
@Singleton
class PleosVehicle @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile private var vehicle: Vehicle? = null

    /** 초기화된 Vehicle (실패하면 null, 다음에 다시 시도). IO 스레드에서 부른다. */
    @Synchronized
    fun get(): Vehicle? = vehicle ?: runCatching { Vehicle(context.applicationContext).also { it.initialize() } }
        .onFailure { Log.w("EodiVehicle", "Vehicle SDK 시작 실패", it) }
        .getOrNull()
        ?.also { vehicle = it }
}
