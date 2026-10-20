package app.awn.quran.core.data.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.awn.quran.core.data.R

/** The notification that stays visible while a pack downloads in the background. */
internal object PackNotifications {

    private const val CHANNEL_ID = "pack-downloads"
    private const val NOTIFICATION_ID_BASE = 4_200
    private const val ID_RANGE = 1_000
    private const val MAX_PERCENT = 100

    /** Returns a notification id that is the same for the same pack and differs between packs. */
    fun idOf(packId: String): Int =
        NOTIFICATION_ID_BASE + (packId.hashCode() and Int.MAX_VALUE) % ID_RANGE

    fun build(
        context: Context,
        title: String,
        percent: Int,
        cancelIntent: PendingIntent,
    ): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_data_download)
            .setContentTitle(context.getString(R.string.download_notification_title, title))
            .setProgress(MAX_PERCENT, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_data_download,
                context.getString(R.string.download_notification_cancel),
                cancelIntent,
            )
            .build()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
