package app.eodiyeotji.recap

import android.util.Log
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.LatLon
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.core.OnThisDay
import app.eodiyeotji.core.Recall
import app.eodiyeotji.core.markOnThisDaySpoken
import app.eodiyeotji.core.markRecallShown
import app.eodiyeotji.core.onThisDay
import app.eodiyeotji.core.recallNear
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 회상을 사용자에게 내보내는 곳 (판마다 다르다, 각 판의 `recap/RecallModule`).
 * - 차: 짧게 읽기 (Pleos TTS, 내비 안내 음성과 겹치지 않게 오디오 포커스를 지킨다). 카드는 주행 화면이 [RecallCenter.current]를 보고 그린다.
 * - 폰: 알림 한 줄.
 * 반환: 실제로 내보냈으면 true → 횟수에 센다. 다른 말을 하거나 듣는 중이면 false (다음 묶음 때 다시 본다).
 */
interface RecallOutput {
    suspend fun showRecall(recall: Recall): Boolean

    suspend fun showOnThisDay(otd: OnThisDay): Boolean
}

/**
 * 운전 중 회상 (README "지나가면 떠오르는 기억" · "N년 전 오늘"). 고르기 · 횟수 제한 · 문장은 Rust `recap.rs`가 하고,
 * 여기서는 언제 물을지와 내보내기만 한다.
 *
 * - 기록 서비스가 위치 묶음을 저장할 때마다 [onBatch] → Rust `recall_near`(300m 안, 최근 30일 안 간 곳 제외,
 *   하루 3번 · 같은 곳 1주일 · 15분 간격) → [RecallOutput.showRecall] → 내보냈으면 `mark_recall_shown`으로 센다.
 * - 주행이 시작되면 [ON_THIS_DAY_DELAY_MS] 뒤(출발 직후 내비 안내와 겹치지 않게) 아직 달리는 중이면 "N년 전 오늘"을 한 번
 *   내보낸다 (오늘 이미 읽었거나 카드를 닫았으면 건너뜀).
 * - 차 주행 화면의 작은 카드는 [current]를 그린다 ([CARD_MS] 뒤, 또는 주행이 끝나면 사라진다).
 */
@Singleton
class RecallCenter @Inject constructor(
    private val core: EodiCore,
    private val output: RecallOutput,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w(TAG, "회상 오류", e) },
    )
    private val _current = MutableStateFlow<Recall?>(null)

    /** 주행 화면에 보일 회상 (없으면 null). */
    val current: StateFlow<Recall?> = _current.asStateFlow()

    private var onThisDayJob: Job? = null
    private var clearJob: Job? = null

    /** 주행 시작: 잠시 뒤 "N년 전 오늘"을 한 번. */
    fun onDriveStart() {
        onThisDayJob?.cancel()
        onThisDayJob = scope.launch {
            delay(ON_THIS_DAY_DELAY_MS)
            val otd = safely("onThisDay") { core.onThisDay() } ?: return@launch
            if (otd.spoken || otd.dismissed) return@launch
            if (output.showOnThisDay(otd)) {
                Log.i(TAG, "N년 전 오늘 내보냄 (${otd.yearsAgo}년 전)")
                safely("markOnThisDaySpoken") { core.markOnThisDaySpoken() }
            }
        }
    }

    /** 주행 끝: 기다리던 "N년 전 오늘"을 버리고 카드를 내린다. */
    fun onDriveEnd() {
        onThisDayJob?.cancel()
        onThisDayJob = null
        dismissCard()
    }

    /** 저장한 위치 묶음 (기록 서비스가 부른다, 10초 또는 30개마다). */
    suspend fun onBatch(fixes: List<LocationFix>) {
        if (fixes.isEmpty()) return
        val recall = safely("recallNear") { core.recallNear(fixes.map { LatLon(it.lat, it.lon) }) } ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "회상 후보 ${recall.place.name} ${recall.distanceM.toInt()}m: ${recall.line}") else Log.i(TAG, "회상 후보")
        if (!output.showRecall(recall)) {
            Log.i(TAG, "지금은 회상을 내보내지 않음 (다른 음성 중) → 다음 묶음에 다시")
            return
        }
        safely("markRecallShown") { core.markRecallShown(recall.key) }
        _current.value = recall
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CARD_MS)
            if (_current.value?.key == recall.key) _current.value = null
        }
    }

    /** 카드 닫기 (× 또는 주행 끝). */
    fun dismissCard() {
        clearJob?.cancel()
        _current.value = null
    }

    private suspend fun <T> safely(what: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what 실패", e)
            null
        }

    private companion object {
        const val TAG = "EodiRecall"
        /** 출발 뒤 이만큼 지나 "N년 전 오늘"을 말한다 (내비 첫 안내 · 출발 직후 조작과 겹치지 않게). */
        const val ON_THIS_DAY_DELAY_MS = 45_000L
        /** 주행 화면 카드가 떠 있는 시간. */
        const val CARD_MS = 3 * 60_000L
    }
}
