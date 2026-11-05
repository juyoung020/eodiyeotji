package app.eodiyeotji.agent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.EodiCore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 하루 라벨링 깨우미 — 라벨링 자체(어느 날·프롬프트·검증·백오프)는 Rust `core-rs/src/agent/label.rs`가 하고,
 * 여기는 가끔 깨어나 `labelPendingDays`를 부르기만 한다 ([EodiApp][app.eodiyeotji.EodiApp]이 [start], PlaceResolver와 같은 방식).
 *
 * 깨어나는 때: 기록 변경([EodiCore.changes], 30초 모아서), [TICK_MS]마다. 네트워크가 없으면 건너뛴다.
 * 시작할 때 LLM 서버에 고정 프롬프트를 한 번 미리 계산시켜 첫 질문을 빠르게 한다.
 */
@Singleton
class DayLabeler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
    private val bridge: AgentBridge,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w(TAG, "하루 라벨링 작업 오류", e) }
    )
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (BuildConfig.LLM_BASE_URL.isBlank()) {
            Log.w(TAG, "LLM_BASE_URL 없음 → 하루 라벨링 끔. local.properties에 LLM_BASE_URL=http://10.0.2.2:8181/v1 을 넣고 다시 빌드")
            return
        }
        scope.launch { if (online()) bridge.warmUp() }
        scope.launch { core.changes.collect { wake.trySend(Unit) } }
        scope.launch { while (true) { wake.trySend(Unit); delay(TICK_MS) } }
        scope.launch {
            while (true) {
                wake.receive()
                delay(SETTLE_MS)
                wake.tryReceive() // 기다리는 동안 온 신호는 이번 차례에 합친다
                if (!online()) continue
                try {
                    val r = bridge.labelPendingDays(BATCH)
                    if (r.labeled > 0u || r.badOutput > 0u || r.serverDown) Log.i(TAG, "하루 라벨링: ${r.labeled}일, 형식 오류 ${r.badOutput}일, 서버 꺼짐=${r.serverDown}")
                    if (r.labeled.toInt() == BATCH) wake.trySend(Unit) // 남은 날은 바로 다음 차례에
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "하루 라벨링 실패", e)
                }
            }
        }
    }

    private fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private companion object {
        const val TAG = "EodiDayLabeler"
        const val SETTLE_MS = 30_000L
        const val TICK_MS = 15 * 60_000L
        const val BATCH = 5
    }
}
