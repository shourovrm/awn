package app.awn.quran

import android.app.Application
import app.awn.quran.services.AwnServices

/** Creates the one [AwnServices] holder before any screen exists. */
class AwnApplication : Application() {

    lateinit var services: AwnServices
        private set

    override fun onCreate() {
        super.onCreate()
        services = AwnServices(this)
    }
}
