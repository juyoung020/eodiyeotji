package app.eodiyeotji.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.eodiyeotji.platform.PermissionStep
import app.eodiyeotji.platform.recordingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 자동 기록을 켜는 입구. 시작 버튼은 없다 — 권한이 있으면 그냥 켠다.
 *
 * - 앱 화면: [RecordingPermissionGate]로 감싸면 첫 실행에 권한을 묻고, 허용되면 기록 서비스를 켠다.
 * - 부팅·앱 업데이트: [RecordingBootReceiver]가 앱을 열지 않아도 켠다.
 * 여러 번 불러도 서비스는 하나만 돈다. 어떤 권한이 필요한지는 판마다 다르다 ([app.eodiyeotji.platform.RecordingPlatform]).
 */
object RecordingController {
    private const val TAG = "EodiRecord"

    fun hasPermission(context: Context): Boolean = context.recordingPlatform().hasRecordingPermission()

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
 *
 * 판이 정한 단계([PermissionStep])를 차례로 묻는다. 설명이 있는 단계(폰의 "항상 허용" 위치)는 설명 창을 먼저 띄운다.
 * 한 단계가 끝날 때마다 기록 서비스를 켜 본다 (위치만 허용돼도 바로 기록을 시작한다).
 */
@Composable
fun RecordingPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    /** 남은 단계. null = 아직 계산 전. */
    var pending by remember { mutableStateOf<List<PermissionStep>?>(null) }
    var explaining by remember { mutableStateOf<PermissionStep?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        RecordingController.ensureStarted(context)
        pending = pending?.drop(1)
    }
    LaunchedEffect(Unit) {
        // 패키지 조회(Pleos 위치 서비스 유무)가 있어 백그라운드에서 계산한다
        pending = withContext(Dispatchers.IO) { runCatching { context.recordingPlatform().permissionSteps() }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(pending) {
        val steps = pending ?: return@LaunchedEffect
        val step = steps.firstOrNull()
        when {
            step == null -> RecordingController.ensureStarted(context)
            // 앞 단계(예: 위치)를 거절했거나 이미 다 허용됨 → 건너뛴다
            !step.requires.all { context.granted(it) } || step.permissions.all { context.granted(it) } -> pending = steps.drop(1)
            step.rationale != null -> explaining = step
            else -> launcher.launch(step.permissions.toTypedArray())
        }
    }
    content()
    explaining?.let { step ->
        AlertDialog(
            onDismissRequest = { explaining = null; pending = pending?.drop(1) },
            text = { Text(step.rationale.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { explaining = null; launcher.launch(step.permissions.toTypedArray()) }) { Text("계속") }
            },
            dismissButton = {
                TextButton(onClick = { explaining = null; pending = pending?.drop(1) }) { Text("나중에") }
            },
        )
    }
}

private fun Context.granted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** 기기가 켜져 부팅이 끝나거나 앱이 업데이트되면, 앱을 열지 않아도 기록 대기를 시작한다. */
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
