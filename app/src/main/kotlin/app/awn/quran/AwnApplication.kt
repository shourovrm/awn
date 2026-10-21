package app.awn.quran

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import androidx.work.Configuration
import app.awn.quran.feature.learn.reminder.ReviseReminder
import app.awn.quran.player.AudioDownloadWork
import app.awn.quran.services.AwnServices
import java.io.File
import kotlinx.coroutines.launch

/**
 * Creates the one [AwnServices] holder before any screen exists, except in the `:speech` process,
 * which holds only the speech model. It also tells WorkManager how to build the download worker,
 * which is why WorkManager's own start-up is switched off in the manifest: it must wait until
 * [services] exists.
 */
class AwnApplication : Application(), Configuration.Provider {

    lateinit var services: AwnServices
        private set

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder().setWorkerFactory(services.coreData.packWorkerFactory).build()

    // The speech model that only waits out its grace period after recite closed is the largest
    // thing this app can give back, so any sign of memory pressure, or the app leaving the screen,
    // ends the wait.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::services.isInitialized) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) services.recite.onLowMemory()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::services.isInitialized) services.recite.onLowMemory()
    }

    override fun onCreate() {
        super.onCreate()
        // The speech process runs only SpeechService: no screens, databases or downloads.
        if (isSpeechProcess()) return
        services = AwnServices(this)
        // Before WorkManager restarts an audio download that died with the process.
        AudioDownloadWork.install(
            services.audioDownloads,
            services.audioFreeSpace,
            services.networkStatus,
        )
        // A download that Android stopped, or that died with the process, starts again.
        services.applicationScope.launch {
            services.coreData.packDownloads.resumeWanted(services.coreData.packs)
        }
        services.applicationScope.launch { ReviseReminder.rescheduleIfEnabled(this@AwnApplication) }
    }

    private fun isSpeechProcess(): Boolean {
        val processName =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getProcessName()
            } else {
                // Before Android 9 the name is only in the command line, ended by NUL bytes.
                File("/proc/self/cmdline").readText().trimEnd('\u0000')
            }
        return processName.endsWith(SPEECH_PROCESS_SUFFIX)
    }

    private companion object {
        /** android:process of SpeechService in the manifest. */
        const val SPEECH_PROCESS_SUFFIX = ":speech"
    }
}
