package app.awn.quran

import android.app.Application
import androidx.work.Configuration
import app.awn.quran.services.AwnServices
import kotlinx.coroutines.launch

/**
 * Creates the one [AwnServices] holder before any screen exists. It also tells WorkManager how to
 * build the download worker, which is why WorkManager's own start-up is switched off in the
 * manifest: it must wait until [services] exists.
 */
class AwnApplication : Application(), Configuration.Provider {

    lateinit var services: AwnServices
        private set

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder().setWorkerFactory(services.coreData.packWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        services = AwnServices(this)
        // Copies and loads the speech files on a low-priority thread, so the first recite is quick.
        services.recite.prepareSpeech()
        // A download that Android stopped, or that died with the process, starts again.
        services.applicationScope.launch {
            services.coreData.packDownloads.resumeWanted(services.coreData.packs)
        }
    }
}
