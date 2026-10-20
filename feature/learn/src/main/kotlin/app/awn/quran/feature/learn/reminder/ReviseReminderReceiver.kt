package app.awn.quran.feature.learn.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the daily alarm, which shows the reminder and sets tomorrow's alarm, and the boot and
 * app-update broadcasts, after which the alarm is set again because the system forgets it.
 */
class ReviseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ReviseReminder.ALARM_ACTION) {
                    ReviseReminder.notifyIfDue(appContext)
                }
                ReviseReminder.rescheduleIfEnabled(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
