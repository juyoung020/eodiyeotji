package app.eodiyeotji.place

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.PlaceQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

/**
 * 장소 이름 찾기 — 앱 전체에서 화면과 상관없이 뒤에서 돈다 ([EodiApp][app.eodiyeotji.EodiApp]이 [start]).
 *
 * 흐름: 깨어남 → Rust `pendingPlaces()`로 이름이 필요한 지점을 받음 → 출처 순서대로 묻기 →
 * 찾으면 `setPlaceName`, 못 찾으면 `markPlaceUnresolved(id, 다시 물을 때까지 ms)`.
 * 어느 지점을 물을지·출처 우선순위는 Rust가 정하고, 여기는 네트워크 호출과 결과 전달만 한다.
 *
 * 깨어나는 때: 기록 변경([EodiCore.changes]), 네트워크 연결됨, [TICK_MS]마다(재시도 시각이 지난 지점용).
 * 깨어난 뒤 [SETTLE_MS] 기다려 그 사이 신호를 하나로 합친다 (주행 중 몇 초마다 오는 변경에 매번 돌지 않게).
 *
 * 출처 순서: 카카오(키가 있을 때, 가게 이름) → OSM Nominatim(키 없이). 카카오가 "결과 없음"이거나
 * 키 오류(401·403)일 때만 OSM으로 넘어간다. 네트워크 오류·요청 과다는 넘어가지 않고 나중에 다시 묻는다
 * (낮은 출처 이름이 먼저 박혀 카카오 이름을 못 받는 일을 막는다).
 *
 * 오프라인이면 아무 요청도 하지 않는다 (ConnectivityManager). 네트워크 오류로 앱이 죽지 않는다.
 */
@Singleton
class PlaceResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w(TAG, "장소 이름 작업 오류", e) }
    )
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val online = MutableStateFlow(false)

    /** 실패 횟수 (지점 id별, 네트워크 오류 백오프용). 앱이 다시 켜지면 처음부터. */
    private val failures = HashMap<Long, Int>()

    private val providers: MutableList<PlaceProvider> = buildList {
        val key = BuildConfig.KAKAO_REST_API_KEY
        if (key.isNotBlank()) add(KakaoLocalProvider(key))
        add(NominatimProvider("eodiyeotji/${BuildConfig.VERSION_NAME} (Hyundai in-car app contest prototype)"))
    }.toMutableList()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) {
            Log.w(TAG, "KAKAO_REST_API_KEY 없음 → 실제 가게 이름 대신 OSM 이름만 씀. local.properties에 KAKAO_REST_API_KEY=키 를 넣고 다시 빌드")
        }
        scope.launch { watchNetwork() }
        scope.launch { core.changes.collect { wake.trySend(Unit) } }
        scope.launch { while (true) { wake.trySend(Unit); delay(TICK_MS) } }
        scope.launch { workLoop() }
    }

    private fun watchNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        fun hasInternet(caps: NetworkCapabilities?) = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        setOnline(hasInternet(cm.getNetworkCapabilities(cm.activeNetwork)))
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = setOnline(hasInternet(caps))
            override fun onLost(network: Network) = setOnline(false)
        })
    }

    private fun setOnline(value: Boolean) {
        val was = online.getAndUpdate { value }
        if (value && !was) wake.trySend(Unit)
    }

    private suspend fun workLoop() {
        while (true) {
            wake.receive()
            delay(SETTLE_MS)
            wake.tryReceive() // 기다리는 동안 온 신호는 이번 차례에 합친다
            if (!online.value) continue
            try {
                runPass()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "장소 이름 차례 실패", e)
            }
        }
    }

    /** 한 차례: 이름이 필요한 지점이 없어질 때까지(최대 [MAX_PER_PASS]개) 묻는다. */
    private suspend fun runPass() {
        val seen = HashSet<Long>() // Rust가 같은 지점을 또 주면(저장 실패 등) 이번 차례엔 다시 묻지 않는다
        var done = 0
        while (done < MAX_PER_PASS) {
            val batch = core.pendingPlaces(BATCH).filter { seen.add(it.id) }
            if (batch.isEmpty()) return
            for (q in batch) {
                if (!online.value) return
                done++
                if (!resolveOne(q)) return
            }
        }
        wake.trySend(Unit) // 남은 지점은 다음 차례에
    }

    /** 지점 하나. false면 이번 차례를 멈춘다 (오프라인·요청 과다). */
    private suspend fun resolveOne(q: PlaceQuery): Boolean {
        var anyAnswered = false
        for (p in providers.toList()) {
            try {
                when (val r = p.lookup(q)) {
                    is Lookup.Found -> {
                        core.setPlaceName(q.id, r.label)
                        failures.remove(q.id)
                        return true
                    }
                    Lookup.NotFound -> anyAnswered = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                when {
                    e.isAuthError -> {
                        Log.w(TAG, "${p.name} 키 거부(${e.code}) → 이번 실행 동안 끔")
                        providers.remove(p)
                    }
                    e.isRateLimited -> {
                        Log.w(TAG, "${p.name} 요청 과다(${e.code}) → 잠시 쉼")
                        core.markPlaceUnresolved(q.id, PlaceBackoff.RATE_LIMITED_MS)
                        return false
                    }
                    else -> return networkFailure(q, p, e)
                }
            } catch (e: IOException) {
                return networkFailure(q, p, e)
            } catch (e: Exception) {
                // 응답 형식이 예상과 다름(JSONException 등) → 결과 없음처럼 다음 출처로
                Log.w(TAG, "${p.name} 응답 해석 실패", e)
                anyAnswered = true
            }
        }
        // 모든 출처가 결과 없음(또는 쓸 출처가 없음) → 오래 뒤에 다시
        core.markPlaceUnresolved(q.id, if (anyAnswered) PlaceBackoff.NOT_FOUND_MS else PlaceBackoff.RATE_LIMITED_MS)
        return true
    }

    private suspend fun networkFailure(q: PlaceQuery, p: PlaceProvider, e: IOException): Boolean {
        if (!online.value) return false // 연결이 끊김 → 표시하지 않고 다시 연결되면 이어서
        val n = (failures[q.id] ?: 0) + 1
        failures[q.id] = n
        Log.w(TAG, "${p.name} 네트워크 오류 (${n}번째): ${e.message}")
        core.markPlaceUnresolved(q.id, PlaceBackoff.networkRetryMs(n))
        return true
    }

    companion object {
        private const val TAG = "PlaceResolver"
        private const val SETTLE_MS = 5_000L
        private const val TICK_MS = 10 * 60_000L
        private const val BATCH = 10
        private const val MAX_PER_PASS = 40
    }
}

/** 다시 물을 때까지 기다리는 시간. */
object PlaceBackoff {
    /** 모든 출처가 결과 없음 → 하루 뒤 */
    const val NOT_FOUND_MS = 24 * 60 * 60_000L

    /** 요청 과다(429·503) → 10분 뒤 */
    const val RATE_LIMITED_MS = 10 * 60_000L

    private const val NETWORK_BASE_MS = 60_000L
    private const val NETWORK_MAX_MS = 6 * 60 * 60_000L

    /** 네트워크 오류 [attempt]번째 → 1분, 2분, 4분 … 최대 6시간 */
    fun networkRetryMs(attempt: Int): Long =
        (NETWORK_BASE_MS shl (attempt - 1).coerceIn(0, 20)).coerceAtMost(NETWORK_MAX_MS)
}
