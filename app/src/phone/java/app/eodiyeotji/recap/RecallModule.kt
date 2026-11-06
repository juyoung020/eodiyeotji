package app.eodiyeotji.recap

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.eodiyeotji.R
import app.eodiyeotji.core.OnThisDay
import app.eodiyeotji.core.Recall
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** 폰 판의 회상 내보내기. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecallModule {
    @Binds abstract fun output(impl: PhoneRecallOutput): RecallOutput
}

/**
 * 폰: 알림 한 줄 (폰은 주머니 · 거치대에 있어 갑자기 말하지 않는다). 누르면 앱이 열린다.
 * 알림 권한이 없으면 내보내지 않고 false (횟수에 세지 않는다).
 */
@Singleton
class PhoneRecallOutput @Inject constructor(@ApplicationContext private val context: Context) : RecallOutput {
    override suspend fun showRecall(recall: Recall): Boolean =
        notify(RECALL_ID, context.getString(R.string.recap_recall_notification_title, recall.whenText), recall.line)

    override suspend fun showOnThisDay(otd: OnThisDay): Boolean =
        notify(ON_THIS_DAY_ID, context.getString(R.string.recap_on_this_day_stamp, otd.yearsAgo), otd.line)

    private fun notify(id: Int, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.recap_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(id, n) }.isSuccess
    }

    private companion object {
        const val CHANNEL_ID = "recall"
        const val RECALL_ID = 2001
        const val ON_THIS_DAY_ID = 2002
    }
}
