package app.eodiyeotji.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.eodiyeotji.pleos.PleosLocationSource
import ai.pleos.playground.location.fused.FusedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 자동 기록을 켜는 입구. 시작 버튼은 없다 — 권한이 있으면 그냥 켠다.
 *
 * - 앱 화면: [RecordingPermissionGate]로 감싸면 첫 실행에 권한을 한 번 묻고, 허용되면 기록 서비스를 켠다.
 * - 부팅·앱 업데이트: [RecordingBootReceiver]가 앱을 열지 않아도 켠다.
 * 여러 번 불러도 서비스는 하나만 돈다.
 */
object RecordingController {
    private const val TAG = "EodiRecord"

    fun hasPermission(context: Context): Boolean = PleosLocationSource.hasPermission(context)

    /** 권한이 있으면 기록 서비스를 켠다. 반환: 켜기를 요청했으면 true. */
    fun ensureStarted(context: Context): Boolean {
        if (!hasPermission(context)) {
            Log.i(TAG, "위치 권한이 아직 없어 기록 서비스를 켜지 않음")
            return false
        }
        return runCatching {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        }.onFailure { Log.e(TAG, "기록 서비스 시작 실패", it) }.isSuccess
    }
}

/**
 * 첫 실행 권한 요청 + 기록 시작. 화면은 막지 않고 [content]를 그대로 그린다.
 * MainActivity의 setContent { EodiTheme { RecordingPermissionGate { HomeRoute() } } } 처럼 감싼다.
 */
@Composable
fun RecordingPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) RecordingController.ensureStarted(context)
    }
    LaunchedEffect(Unit) {
        if (RecordingController.hasPermission(context)) {
            RecordingController.ensureStarted(context)
        } else {
            // Pleos 위치 서비스가 없는 환경에서는 묻지 않는다 (패키지 조회는 백그라운드에서).
            val supported = withContext(Dispatchers.IO) {
                runCatching { FusedLocation(context.applicationContext).isSupported() }.getOrDefault(false)
            }
            if (supported) launcher.launch(PleosLocationSource.PERMISSION)
        }
    }
    content()
}

/** 차량이 켜져 부팅이 끝나거나 앱이 업데이트되면, 앱을 열지 않아도 기록 대기를 시작한다. */
class RecordingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i("EodiRecord", "${intent.action} → 기록 서비스 켜기")
                RecordingController.ensureStarted(context)
            }
        }
    }
}
