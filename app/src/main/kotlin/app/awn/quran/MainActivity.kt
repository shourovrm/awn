package app.awn.quran

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

/**
 * An AppCompatActivity because AppCompat's per-app language
 * (AppCompatDelegate.setApplicationLocales) only reaches the activity's resources through it on
 * Android 12 and earlier; Compose draws everything, so no AppCompat widget is used.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val services = (application as AwnApplication).services
        setContent { AwnApp(services) }
    }
}
