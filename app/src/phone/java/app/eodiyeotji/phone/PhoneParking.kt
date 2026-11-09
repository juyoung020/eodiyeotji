package app.eodiyeotji.phone

import android.content.Context
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.rust.ParkedCar
import app.eodiyeotji.core.rust.parkedFromJson
import app.eodiyeotji.core.rust.parkedLatest
import app.eodiyeotji.core.rust.parkedToJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 폰: "내 차" 주차 위치.
 * 두 곳에서 온다 — 연결된 차가 P에 들어가며 보낸 것(받아 둔다), 폰이 직접 기록한 주행의 끝(Rust `parked_car`).
 * 둘 중 늦은 것을 쓴다 (Rust `parked_latest`). 차가 다시 출발했다고 알리거나, "찾았어요"를 누르면 감춘다.
 */
@Singleton
class PhoneParking @Inject constructor(
    @ApplicationContext context: Context,
    private val core: EodiCore,
) {
    private val prefs = context.getSharedPreferences("parking", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _car = MutableStateFlow<ParkedCar?>(null)

    /** 지금 보여 줄 주차 위치 (없으면 null). */
    val car: StateFlow<ParkedCar?> = _car.asStateFlow()

    init {
        scope.launch { core.changes.collect { refresh() } }
        scope.launch { refresh() }
    }

    /** 차가 보낸 주차 위치를 받았을 때. */
    fun onReceived(car: ParkedCar) {
        prefs.edit().putString(K_RECEIVED, parkedToJson(car)).apply()
        scope.launch { refresh() }
    }

    /** 차가 다시 출발했을 때: 그 전에 받은 주차 위치는 지운다. */
    fun onMoving(tMs: Long) {
        val got = received()
        if (got != null && got.tMs <= tMs) prefs.edit().remove(K_RECEIVED).apply()
        scope.launch { refresh() }
    }

    /** "찾았어요": 지금 주차 위치를 감춘다 (다음 주차부터 다시 보인다). */
    fun dismiss() {
        _car.value?.let { prefs.edit().putLong(K_DISMISSED, it.tMs).apply() }
        _car.value = null
    }

    suspend fun refresh() {
        val own = runCatching { core.parkedCar() }.getOrNull()
        val latest = parkedLatest(received(), own)
        _car.value = latest?.takeIf { it.tMs > prefs.getLong(K_DISMISSED, 0) }
    }

    private fun received(): ParkedCar? = prefs.getString(K_RECEIVED, null)?.let { parkedFromJson(it) }

    private companion object {
        const val K_RECEIVED = "received"
        const val K_DISMISSED = "dismissed"
    }
}
