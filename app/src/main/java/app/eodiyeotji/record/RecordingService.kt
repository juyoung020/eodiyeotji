package app.eodiyeotji.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.R
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.core.NaviHint
import app.eodiyeotji.pleos.PleosLocationSource
import app.eodiyeotji.pleos.PleosNavi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 자동 기록 서비스. 앱 화면이 없어도(다른 앱을 보거나 앱을 닫아도) 주행을 기록한다.
 *
 * 흐름: 기어·시동 신호([VehicleDriveSignals]) → 판단([DriveRule]) → 디바운스 →
 *   주행 시작: markDriveStart → Pleos 위치를 모아 10초 또는 30개마다 묶어서 [EodiCore.ingest]
 *   주행 끝: 위치 수신 중지 → 남은 묶음 저장 → markDriveEnd
 * 주행 중에는 차량 내비([PleosNavi])도 듣는다: 목적지·경유지 도착 이벤트와, 주행이 끝날 때 남아 있는 목적지·경유지를
 *   [EodiCore.addNaviHint]로 넘긴다 → Rust가 가까운 도착 지점·머문 곳의 이름으로 쓴다 ([NaviSession]). 내비가 없으면 건너뛴다.
 * 주차 중에는 위치 SDK를 켜지 않는다. 차량 신호 콜백 하나만 걸어 두고 기다린다 (CPU 0에 가깝다).
 * 모든 작업은 Dispatchers.Default/IO에서 한다.
 *
 * ## 포그라운드 서비스 종류: `specialUse`
 * targetSdk 34+에서는 포그라운드 서비스마다 종류를 선언하고, 종류마다 요구 권한이 있다.
 * - `location`: 실행 시점에 Android ACCESS_COARSE/FINE_LOCATION이 허용돼 있어야 한다.
 *   우리는 Android 위치(LocationManager)를 쓰지 않고 Pleos FusedLocation(별도 권한)만 쓰므로,
 *   이 종류를 고르면 쓰지도 않는 위치 권한을 한 번 더 묻게 된다 → 앱마켓 "권한 최소화" 심사에도 불리하다.
 *   또 부팅 직후(BOOT_COMPLETED)에 시작한 서비스에는 while-in-use 위치 권한이 주어지지 않는다.
 * - `dataSync`: Android 15부터 하루 6시간 제한, BOOT_COMPLETED에서 시작 금지 → 상시 기록에 맞지 않는다.
 * - `connectedDevice` 등: 요구 권한(블루투스 등)이 실제 용도와 맞지 않는다.
 * - `specialUse`: FOREGROUND_SERVICE_SPECIAL_USE(normal, 묻지 않음)만 필요하고 부팅 직후 시작도 된다.
 *   용도는 매니페스트 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`에 적는다.
 * 알림 권한(POST_NOTIFICATIONS)은 요청하지 않는다. 없어도 서비스는 돌고, 알림만 알림 목록에 안 보인다.
 */
@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var core: EodiCore
    @Inject lateinit var location: PleosLocationSource
    @Inject lateinit var vehicle: VehicleDriveSignals
    @Inject lateinit var journal: SessionJournal
    @Inject lateinit var navi: PleosNavi

    private var running = false
    private var foregroundOk = false

    /** 주행 중 마지막 위치. 내비 도착 이벤트에 좌표가 없을 때 대신 쓴다. */
    @Volatile private var lastFix: LocationFix? = null

    override fun onCreate() {
        super.onCreate()
        foregroundOk = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        }.onFailure { Log.e(TAG, "포그라운드 시작 실패", it) }.isSuccess
        if (!foregroundOk) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (foregroundOk && !running) {
            running = true
            lifecycleScope.launch(Dispatchers.Default) { runRecorder() }
        }
        // 시스템이 프로세스를 정리해도 다시 띄운다. 다시 뜨면 SessionJournal로 이어서 기록한다.
        return START_STICKY
    }

    private suspend fun runRecorder() {
        if (!location.hasPermission() || !location.isSupported()) {
            Log.w(TAG, "위치 권한 없음 또는 Pleos 위치 미지원 → 기록 서비스 중지")
            withContext(Dispatchers.Main) { stopSelf() }
            return
        }
        Log.i(TAG, "기록 대기 시작 (주차 중에는 차량 신호만 듣는다)")

        var recovered = journal.read()
        var session: Job? = null
        var naviSession: NaviSession? = null
        val naviAvailable = navi.isAvailable()
        Log.i(TAG, "차량 내비 연동: ${if (naviAvailable) "사용" else "내비 서비스 없음 → 건너뜀"}")

        vehicle.signals().toDriveEdges().debounced().collect { edge ->
            // 직전 프로세스가 기록 중에 죽었으면: 아직 주행 중이면 이어서, 이미 주차면 끝 표시만 한다.
            recovered?.let { open ->
                recovered = null
                if (edge.active) {
                    Log.i(TAG, "주행 기록 이어서 (시작 ${open.startMs})")
                    session = lifecycleScope.startCollecting()
                    if (naviAvailable) naviSession = lifecycleScope.startNavi()
                } else {
                    safely("markDriveEnd(복구)") { core.markDriveEnd(open.lastFixMs) }
                    journal.close()
                    Log.i(TAG, "끊긴 주행 기록 마감 (끝 ${open.lastFixMs})")
                }
                return@collect
            }

            val current = session
            if (edge.active && current == null) {
                Log.i(TAG, "주행 시작 t=${edge.tMs}")
                safely("markDriveStart") { core.markDriveStart(edge.tMs) }
                journal.open(edge.tMs)
                session = lifecycleScope.startCollecting()
                if (naviAvailable) naviSession = lifecycleScope.startNavi()
            } else if (!edge.active && current != null) {
                current.cancelAndJoin() // 위치 수신 중지 + 남은 묶음 저장까지 끝난 뒤 돌아온다
                session = null
                safely("markDriveEnd") { core.markDriveEnd(edge.tMs) }
                journal.close()
                Log.i(TAG, "주행 끝 t=${edge.tMs}")
                naviSession?.let { safely("내비 마감") { it.finish(edge.tMs) } }
                naviSession = null
                lastFix = null
            }
        }
    }

    /** 주행 중에만 도는 내비 듣기. 힌트는 받는 즉시 Rust에 넘긴다 (드물게 오므로 묶지 않는다). */
    private fun CoroutineScope.startNavi(): NaviSession = NaviSession(navi, this, { lastFix }) { hint -> sendNaviHint(hint) }

    private suspend fun sendNaviHint(hint: NaviHint) {
        // 장소 이름은 개인 기록이라 릴리즈 로그에는 남기지 않는다
        if (BuildConfig.DEBUG) Log.d(TAG, "addNaviHint $hint") else Log.i(TAG, "addNaviHint")
        safely("addNaviHint") { core.addNaviHint(hint) }
    }

    /** 주행 중에만 도는 수집 작업. 위치와 10초 타이머를 한 줄로 받아 순서대로 처리한다 (잠금 없이 순서 보장). */
    private fun CoroutineScope.startCollecting(): Job = launch(Dispatchers.Default) {
        val buffer = ArrayList<LocationFix>(BATCH_SIZE)
        try {
            val fixes = location.updates()
                .map<LocationFix, Tick> { Tick.Fix(it) }
                .catch { Log.w(TAG, "위치 수신 실패", it) }
            merge(fixes, ticker(FLUSH_INTERVAL_MS)).collect { item ->
                when (item) {
                    is Tick.Fix -> {
                        buffer += item.fix
                        lastFix = item.fix
                        if (buffer.size >= BATCH_SIZE) flush(buffer, "${BATCH_SIZE}개")
                    }
                    Tick.Timer -> flush(buffer, "10초")
                }
            }
        } finally {
            withContext(NonCancellable) { flush(buffer, "주행 끝") }
        }
    }

    private suspend fun flush(buffer: MutableList<LocationFix>, reason: String) {
        if (buffer.isEmpty()) return
        val batch = buffer.toList()
        buffer.clear()
        val stored = safely("ingest") { core.ingest(batch) } ?: return
        journal.touch(batch.last().tMs)
        if (BuildConfig.DEBUG) {
            val total = safely("fixCount") { core.fixCount() }
            val last = batch.last()
            Log.d(TAG, "ingest($reason) ${batch.size}개 → 저장 $stored, 누적 fixCount=$total · 마지막 ${last.lat},${last.lon} acc=${last.accuracyM}")
        }
    }

    /** Rust 쪽 오류가 서비스를 죽이지 않게 한다. */
    private suspend fun <T> safely(what: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$what 실패", e)
            null
        }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.record_channel_name), NotificationManager.IMPORTANCE_MIN),
            )
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.record_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private sealed interface Tick {
        data class Fix(val fix: LocationFix) : Tick
        data object Timer : Tick
    }

    private fun ticker(periodMs: Long): Flow<Tick> = flow {
        while (true) {
            delay(periodMs)
            emit(Tick.Timer)
        }
    }

    companion object {
        private const val TAG = "EodiRecord"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
        private const val BATCH_SIZE = 30
        private const val FLUSH_INTERVAL_MS = 10_000L

        /** 기어가 P에서 빠져 이만큼 유지돼야 시작 (P→R→D 조작 중 깜빡임 무시). */
        internal const val START_DEBOUNCE_MS = 2_000L

        /** P로 돌아와 이만큼 유지돼야 끝 (잠깐 P에 넣었다 빼는 경우 기록을 쪼개지 않음). */
        internal const val END_DEBOUNCE_MS = 5_000L
    }
}

/** 판단이 바뀐 뒤 일정 시간 그대로일 때만 내보낸다. 내보내는 시각(tMs)은 처음 바뀐 순간 그대로다. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<DriveEdge>.debounced(): Flow<DriveEdge> = transformLatest { edge ->
    delay(if (edge.active) RecordingService.START_DEBOUNCE_MS else RecordingService.END_DEBOUNCE_MS)
    emit(edge)
}
