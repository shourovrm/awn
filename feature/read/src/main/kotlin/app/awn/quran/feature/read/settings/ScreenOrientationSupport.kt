package app.awn.quran.feature.read.settings

import android.content.pm.ActivityInfo

/**
 * The value for `Activity.requestedOrientation`. Auto leaves the choice to the phone, so the
 * rotation lock in the system settings still applies. :app uses this to apply
 * [ReadSettings.appOrientation] to every screen except the Quran pages.
 */
fun ScreenOrientation.requestedOrientation(): Int =
    when (this) {
        ScreenOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
