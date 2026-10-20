package app.awn.quran.feature.recite

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Where the RECORD_AUDIO permission stands, from the point of view of what the screen offers. */
enum class MicrophoneAccess {
    Granted,

    /** Not granted and the system dialog may still be shown. */
    CanAsk,

    /** The user chose "don't ask again"; only the app settings can turn the microphone on. */
    DeniedPermanently,
}

/** Holds the microphone permission for one screen. */
@Stable
class MicrophonePermissionState
internal constructor(
    private val context: Context,
    private val launchRequest: () -> Unit,
    private val hasAsked: Boolean,
) {
    /**
     * Computed on every read; a new instance is created whenever the permission may have changed.
     */
    val access: MicrophoneAccess
        get() {
            val granted =
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            if (granted) return MicrophoneAccess.Granted
            val activity = context.findActivity()
            val mayAskAgain =
                !hasAsked ||
                    activity?.shouldShowRequestPermissionRationale(
                        Manifest.permission.RECORD_AUDIO,
                    ) == true
            return if (mayAskAgain) MicrophoneAccess.CanAsk else MicrophoneAccess.DeniedPermanently
        }

    /** Shows the system dialog. Does nothing useful when [access] is `DeniedPermanently`. */
    fun requestAccess() = launchRequest()

    /** Opens the Awn page in the system settings, where the user can turn the microphone on. */
    fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** Remembers the permission state and refreshes it when the user comes back from the settings. */
@Composable
fun rememberMicrophonePermission(): MicrophonePermissionState {
    val context = LocalContext.current
    var hasAsked by rememberSaveable { mutableStateOf(false) }
    // Changing this count recomposes the caller, which then receives a new state instance.
    var refreshCount by remember { mutableIntStateOf(0) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasAsked = true
            refreshCount++
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshCount++ }
    return remember(refreshCount, hasAsked) {
        MicrophonePermissionState(
            context = context,
            launchRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
            hasAsked = hasAsked,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
