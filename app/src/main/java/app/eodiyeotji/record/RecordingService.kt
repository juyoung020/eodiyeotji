package app.eodiyeotji.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.eodiyeotji.BuildConfig
import app.eodiyeotji.R
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.rust.ReadingKind
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.core.NaviHint
import app.eodiyeotji.platform.DriveSignalSource
import app.eodiyeotji.platform.LocationSource
import app.eodiyeotji.platform.Navigator
import app.eodiyeotji.platform.RecordingPlatform
import app.eodiyeotji.recap.RecallCenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 자동 기록 서비스. 앱 화면이 없어도(다른 앱을 보거나 앱을 닫아도) 주행을 기록한다.
 *
 * 흐름: 주행 신호([DriveSignalSource]) →
 *   주행 시작: markDriveStart → (폰: 감지 전에 받은 점 먼저 저장) → 위치([LocationSource])를 모아 10초 또는 30개마다 묶어서 [EodiCore.ingest]
 *   주행 끝: 위치 수신 중지 → 남은 묶음 저장 → markDriveEnd
 * 주행 신호는 판마다 다르다 (`platform/PlatformModule`):
 * - 차: 기어·시동 신호(`VehicleDriveSignals`) → 판단(`DriveRule`) → 디바운스. 주차 중에는 위치 SDK를 켜지 않고 차량 신호 콜백 하나만 걸어 둔다.
 * - 폰: 위치로 판단(`PhoneDriveSignals` + Rust `drivedetect`). 서 있을 때는 저전력 위치만 드물게 받는다.
 * 주행 중에는 차량 내비([Navigator])도 듣는다: 목적지·경유지 도착 이벤트와, 주행이 끝날 때 남아 있는 목적지·경유지를
 *   [EodiCore.addNaviHint]로 넘긴다 → Rust가 가까운 도착 지점·머문 곳의 이름으로 쓴다 ([NaviSession]). 내비 이벤트가 없으면(폰) 건너뛴다.
 * 저장한 위치 묶음은 회상([RecallCenter])에도 넘긴다: 예전에 들렀던 곳 근처를 지나면 짧게 알려 준다 (시동 뒤엔 "N년 전 오늘" 한 번).
 * 모든 작업은 Dispatchers.Default/IO에서 한다.
 *
 * ## 포그라운드 서비스 종류 ([RecordingPlatform.foregroundServiceType], 매니페스트는 판마다)
 * 폰은 `location`(Android 위치 권한을 쓰므로 그대로 맞다). 차는 아래 이유로 `specialUse`:
 * targetSdk 34+에서는 포그라운드 서비스마다 종류를 선언하고, 종류마다 요구 권한이 있다.
 * - `location`: 실행 시점에 Android ACCESS_COARSE/FINE_LOCATION이 허용돼 있어야 한다.
 *   우리는 Android 위치(LocationManager)를 쓰지 않고 Pleos FusedLocation(별도 권한)만 쓰므로,
 *   이 종류를 고르면 쓰지도 않는 위치 권한을 한 번 더 묻게 된다 → 앱마켓 "권한 최소화" 심사에도 불리하다.
 *   또 부팅 직후(BOOT_COMPLETED)에 시작한 서비스에는 while-in-use 위치 권한이 주어지지 않는다.
 * - `dataSync`: Android 15부터 하루 6시간 제한, BOOT_COMPLETED에서 시작 금지 → 상시 기록에 맞지 않는다.
 * - `connectedDevice` 등: 요구 권한(블루투스 등)이 실제 용도와 맞지 않는다.
 * - `specialUse`: FOREGROUND_SERVICE_SPECIAL_USE(normal, 묻지 않음)만 필요하고 부팅 직후 시작도 된다.
 *   용도는 매니페스트 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`에 적는다.
 * 알림 권한(POST_NOTIFICATIONS)은 차에서는 묻지 않는다. 없어도 서비스는 돌고, 알림만 알림 목록에 안 보인다.
 * 폰은 기록 중임을 알림으로 보여 줘야 하므로 첫 실행에 같이 묻는다 (`PhoneRecordingPlatform`).
 */
@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var core: EodiCore
    @Inject lateinit var location: LocationSource
    @Inject lateinit var drive: DriveSignalSource
    @Inject lateinit var journal: SessionJournal
    @Inject lateinit var navi: Navigator
    @Inject lateinit var platform: RecordingPlatform
    @Inject lateinit var monitor: RecordingMonitor
    @Inject lateinit var recall: RecallCenter
    @Inject lateinit var handoff: app.eodiyeotji.handoff.HandoffRepository
    @Inject lateinit var cabin: app.eodiyeotji.platform.CabinSource
    @Inject lateinit var energy: app.eodiyeotji.platform.EnergySource
    @Inject lateinit var gauges: app.eodiyeotji.platform.GaugeSource

    private var running = false
    private var foregroundOk = false

    /** 주행 중 탑승 인원을 읽는 작업. */
    private var cabinJob: Job? = null

    /** 주행 중 마지막 위치. 내비 도착 이벤트에 좌표가 없을 때 대신 쓴다. */
    @Volatile private var lastFix: LocationFix? = null

    override fun onCreate() {
        super.onCreate()
        foregroundOk = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= 34) platform.foregroundServiceType else 0,
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
            Log.w(TAG, "위치 권한 없음 또는 위치 미지원 → 기록 서비스 중지")
            monitor.set(RecordingPhase.OFF)
            withContext(Dispatchers.Main) { stopSelf() }
            return
        }
        Log.i(TAG, "기록 대기 시작")
        monitor.set(RecordingPhase.WAITING)

        var recovered = journal.read()
        var session: Job? = null
        var naviSession: NaviSession? = null
        val naviAvailable = navi.eventsAvailable()
        Log.i(TAG, "내비 이벤트 연동: ${if (naviAvailable) "사용" else "내비 이벤트 없음 → 건너뜀"}")

        drive.edges(resumeDriving = recovered != null, lastFixMs = recovered?.lastFixMs ?: 0L).collect { edge ->
            // 직전 프로세스가 기록 중에 죽었으면: 아직 주행 중이면 이어서, 이미 주차면 끝 표시만 한다.
            recovered?.let { open ->
                recovered = null
                if (edge.active) {
                    Log.i(TAG, "주행 기록 이어서 (시작 ${open.startMs})")
                    monitor.set(RecordingPhase.DRIVING)
                    session = lifecycleScope.startCollecting()
                    if (naviAvailable) naviSession = lifecycleScope.startNavi()
                    cabinJob = lifecycleScope.startCabin()
                } else {
                    safely("markDriveEnd(복구)") { core.markDriveEnd(open.lastFixMs) }
                    journal.close()
                    Log.i(TAG, "끊긴 주행 기록 마감 (끝 ${open.lastFixMs})")
                }
                return@collect
            }

            val current = session
            if (edge.active && current == null) {
                Log.i(TAG, "주행 시작 t=${edge.tMs} (감지 전 점 ${edge.backlog.size}개)")
                monitor.set(RecordingPhase.DRIVING)
                safely("markDriveStart") { core.markDriveStart(edge.tMs) }
                journal.open(edge.tMs)
                if (edge.backlog.isNotEmpty()) flush(edge.backlog.toMutableList(), "감지 전")
                session = lifecycleScope.startCollecting()
                if (naviAvailable) naviSession = lifecycleScope.startNavi()
                cabinJob = lifecycleScope.startCabin()
                lifecycleScope.launch(Dispatchers.IO) { recordEnergy(edge.tMs) }
                recall.onDriveStart()
                if (SENDS_PARKING) lifecycleScope.launch(Dispatchers.IO) { handoff.sendMoving(edge.tMs) }
            } else if (!edge.active && current != null) {
                current.cancelAndJoin() // 위치 수신 중지 + 남은 묶음 저장까지 끝난 뒤 돌아온다
                session = null
                cabinJob?.cancel()
                cabinJob = null
                safely("markDriveEnd") { core.markDriveEnd(edge.tMs) }
                journal.close()
                monitor.set(RecordingPhase.WAITING)
                Log.i(TAG, "주행 끝 t=${edge.tMs}")
                naviSession?.let { safely("내비 마감") { it.finish(edge.tMs) } }
                naviSession = null
                lastFix = null
                recall.onDriveEnd()
                lifecycleScope.launch(Dispatchers.IO) { recordEnergy(edge.tMs) }
                // 차: 주차한 자리를 연결된 폰으로 ("내 차 어디였지" · 차에서 내려 가게까지 걸어서)
                if (SENDS_PARKING) lifecycleScope.launch(Dispatchers.IO) {
                    safely("parkedCar") { core.parkedCar() }?.let { handoff.sendParked(it) }
                }
            }
        }
    }

    override fun onDestroy() {
        monitor.set(RecordingPhase.OFF)
        super.onDestroy()
    }

    /**
     * 주행 중 실내 · 날씨: 출발하고 다들 앉은 뒤(20초)부터 2분마다 와이퍼(비)를 읽고, 탑승 인원은 10분마다
     * (휴게소에서 타고 내림). 센서가 없으면(폰) 아무것도 남기지 않는다. 인원은 몇 명인지만 남는다.
     */
    private fun CoroutineScope.startCabin(): Job = launch(Dispatchers.Default) {
        delay(CABIN_FIRST_MS)
        var tick = 0
        var seats = true
        var wiper = true
        while (seats || wiper) {
            val now = System.currentTimeMillis()
            if (wiper) {
                val w = gauges.wiping()
                if (BuildConfig.DEBUG) Log.d(TAG, "와이퍼 ${when (w) { true -> "움직임(비)"; false -> "멈춤"; null -> "읽을 수 없음" }}")
                if (w == null) wiper = false else safely("setDriveReading(비)") { core.setDriveReading(now, ReadingKind.RAIN, if (w) 1.0 else 0.0) }
            }
            if (seats && tick % CABIN_PEOPLE_EVERY == 0) {
                val n = cabin.occupants()
                if (n == null) seats = false else {
                    if (BuildConfig.DEBUG) Log.d(TAG, "탑승 인원 $n")
                    safely("setDrivePeople") { core.setDrivePeople(now, n) }
                }
            }
            tick++
            delay(CABIN_EVERY_MS)
        }
    }

    /** 출발 · 도착 때 배터리 · 연료 잔량과 주행거리계 (모르면(폰) 건너뛴다). */
    private suspend fun recordEnergy(tMs: Long) {
        energy.level()?.let { e ->
            if (BuildConfig.DEBUG) Log.d(TAG, "잔량 ${e.pct}% (${if (e.ev) "배터리" else "연료"})")
            safely("setDriveEnergy") { core.setDriveEnergy(tMs, e.pct, e.ev) }
        }
        gauges.odometerKm()?.let { km ->
            if (BuildConfig.DEBUG) Log.d(TAG, "주행거리계 ${km}km")
            safely("setDriveReading(주행거리계)") { core.setDriveReading(tMs, ReadingKind.ODOMETER, km) }
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
        // 지나가는 곳 근처에 기억할 만한 곳이 있으면 짧게 회상 (규칙 · 횟수 제한은 Rust recap.rs)
        if (reason != "주행 끝") recall.onBatch(batch)
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
        private const val CABIN_FIRST_MS = 20_000L
        // 와이퍼는 2분마다, 인원은 그 5번에 한 번(10분)
        private const val CABIN_EVERY_MS = 2 * 60_000L
        private const val CABIN_PEOPLE_EVERY = 5
        /** 주차 위치를 폰으로 보내는 쪽은 차 판뿐 (폰 판은 자기 기록으로 바로 안다). */
        private val SENDS_PARKING = BuildConfig.FLAVOR == "car"
    }
}
