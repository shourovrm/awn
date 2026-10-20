package app.awn.quran.feature.learn.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.awn.quran.feature.learn.LearnServices
import app.awn.quran.feature.learn.R
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * The optional daily notification "N ayahs to revise". It is off until the user turns it on. An
 * inexact alarm fires once a day near the chosen time; the receiver shows the notification only
 * when ayahs are due and then sets the next alarm.
 */
object ReviseReminder {

    const val CHANNEL_ID = "revise_reminder"
    internal const val ALARM_ACTION = "app.awn.quran.feature.learn.REVISE_REMINDER"
    private const val NOTIFICATION_ID = 4101
    private const val ALARM_REQUEST_CODE = 4102
    private const val LAUNCH_REQUEST_CODE = 4103

    /** The first moment after [now] at which the wall clock reads [hour]:[minute]. */
    fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val today = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** A reminder with nothing to revise would only teach the user to ignore it. */
    fun shouldNotify(dueCount: Int): Boolean = dueCount > 0

    /** Sets the alarm when the user turned reminders on, and cancels it otherwise. */
    suspend fun rescheduleIfEnabled(context: Context) {
        val settings = LearnServices.get(context).settings
        if (settings.reminderEnabled.first()) {
            schedule(context, settings.reminderHour.first(), settings.reminderMinute.first())
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val trigger = nextTrigger(ZonedDateTime.now(ZoneId.systemDefault()), hour, minute)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // Inexact on purpose: the reminder needs no exact-alarm permission and a few minutes of
        // drift do not matter.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC,
            trigger.toInstant().toEpochMilli(),
            alarmIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReviseReminderReceiver::class.java).setAction(ALARM_ACTION)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Shows the notification when ayahs are due and the user allowed notifications. */
    // canPostNotifications() checks the permission, which lint cannot see through the helper.
    @SuppressLint("MissingPermission")
    suspend fun notifyIfDue(context: Context) {
        val dueCount = LearnServices.get(context).progress.dueAyahs().size
        if (!shouldNotify(dueCount) || !canPostNotifications(context)) return
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        createChannel(context)
        val openApp =
            PendingIntent.getActivity(
                context,
                LAUNCH_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val text = context.resources.getQuantityString(R.plurals.reminder_text, dueCount, dueCount)
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_learn_reminder)
                .setContentTitle(context.getString(R.string.revision_title))
                .setContentText(text)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
