package app.awn.quran.feature.learn.reminder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.components.SettingsSwitchRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.learn.LearnServices
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.progress.LearnSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * The daily revision reminder for the Settings hub: a switch row, and a time row while it is on.
 * Turning it on asks for the notification permission on Android 13 and later; when the user
 * refuses, the switch stays off.
 */
@Composable
fun ReviseReminderSettingsPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { LearnServices.get(context).settings }
    val scope = rememberCoroutineScope()
    val enabled by settings.reminderEnabled.collectAsState(initial = false)
    val hour by settings.reminderHour.collectAsState(initial = LearnSettings.DEFAULT_REMINDER_HOUR)
    val minute by settings.reminderMinute.collectAsState(initial = 0)
    var timePickerOpen by remember { mutableStateOf(false) }
    var permissionRefused by remember { mutableStateOf(false) }

    fun turnOn() {
        scope.launch {
            settings.setReminderEnabled(true)
            ReviseReminder.schedule(context, hour, minute)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionRefused = !granted
            if (granted) turnOn()
        }

    fun setEnabled(turnedOn: Boolean) {
        if (!turnedOn) {
            scope.launch { settings.setReminderEnabled(false) }
            ReviseReminder.cancel(context)
            return
        }
        permissionRefused = false
        if (ReviseReminder.canPostNotifications(context)) {
            turnOn()
        } else {
            // Only Android 13 and later can lack the permission; earlier versions always have it.
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            label = stringResource(R.string.reminder_setting),
            checked = enabled,
            onCheckedChange = ::setEnabled,
            rowKey = SettingRowKeys.LEARN_REMINDER,
        )
        if (enabled) {
            SettingsLinkRow(
                label = stringResource(R.string.reminder_time),
                value = formatTime(hour, minute),
                onClick = { timePickerOpen = true },
                rowKey = SettingRowKeys.LEARN_REMINDER_TIME,
            )
        }
        if (permissionRefused) {
            Text(
                text = stringResource(R.string.reminder_permission),
                style = AwnTypography.small,
                color = Awn.field.content,
            )
        }
    }

    if (timePickerOpen) {
        TimeDialog(
            hour = hour,
            minute = minute,
            onConfirm = { newHour, newMinute ->
                timePickerOpen = false
                scope.launch { settings.setReminderTime(newHour, newMinute) }
                ReviseReminder.schedule(context, newHour, newMinute)
            },
            onDismiss = { timePickerOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    hour: Int,
    minute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Awn.field.background,
        text = { TimePicker(state = state) },
        confirmButton = {
            DialogAction(stringResource(R.string.reminder_time_set)) {
                onConfirm(state.hour, state.minute)
            }
        },
        dismissButton = {
            DialogAction(stringResource(R.string.reminder_time_cancel), onDismiss)
        },
    )
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.bodyStrong, color = Awn.field.buttonFill)
    }
}

private fun formatTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
