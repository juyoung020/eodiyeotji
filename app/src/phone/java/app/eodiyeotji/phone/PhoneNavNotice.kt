package app.eodiyeotji.phone

import android.Manifest
import app.eodiyeotji.ui.nav.NavUi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.eodiyeotji.MainActivity
import app.eodiyeotji.R

/**
 * 길 안내 알림: 화면이 꺼지거나 다른 앱을 보는 동안에도 알림창 · 잠금 화면에서 다음 안내를 본다
 * ("300m 앞 우회전 · 경포해변까지 2.6km"). 안내 중인 동안 하나만 띄우고 내용만 바꾼다 (소리 · 진동 없이).
 * 위치 · 음성은 폰 기록 서비스(포그라운드, 위치 종류)가 떠 있어 앱이 뒤로 가도 계속된다.
 */
internal class PhoneNavNotice(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private var lastKey: String? = null

    // 알림 채널 · 앱 여는 동작은 한 번만 만든다 (글이 바뀔 때마다 다시 만들지 않는다)
    private val open: PendingIntent by lazy {
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, "길 안내", NotificationManager.IMPORTANCE_LOW))
        PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun show(ui: NavUi?) {
        if (ui == null) {
            if (lastKey != null) nm?.cancel(ID)
            lastKey = null
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val (title, body) = texts(ui)
        val key = "$title|$body"
        if (key == lastKey) return
        lastKey = key
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setOngoing(ui.arrivedAt == null)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        nm?.notify(ID, n)
    }

    private fun texts(ui: NavUi): Pair<String, String> {
        val p = ui.progress
        return when {
            ui.error != null -> ui.error to ui.title
            ui.arrivedAt != null -> "${ui.arrivedAt} 도착" to (ui.next?.let { "다음 · $it (앱에서 다음 곳 안내)" } ?: "안내를 마쳤어요")
            ui.loading || ui.route == null || p == null -> "경로를 찾는 중" to ui.title
            ui.rerouting -> "경로를 다시 찾는 중" to ui.title
            else -> {
                val step = p.nextStep?.toInt()?.let { ui.route.steps.getOrNull(it) }
                // 남은 거리는 50m 단위로만 바꿔 알림을 너무 자주 고치지 않는다
                val head = step?.let { "${meters(p.toNextM)} 앞 ${it.text}" } ?: "경로를 따라 이동"
                head to "${ui.title}까지 ${meters(p.remainingM)}"
            }
        }
    }

    private fun meters(m: Double): String =
        if (m >= 1000) String.format("%.1fkm", m / 1000) else "${((m / 50).toInt() * 50).coerceAtLeast(0)}m"

    private companion object {
        const val CHANNEL = "nav"
        const val ID = 7302
    }
}
