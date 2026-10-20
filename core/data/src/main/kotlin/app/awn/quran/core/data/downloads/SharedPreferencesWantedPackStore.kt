package app.awn.quran.core.data.downloads

import android.content.Context
import androidx.core.content.edit

/** Keeps the wanted list in a private preferences file, which survives process death. */
class SharedPreferencesWantedPackStore(context: Context) : WantedPackStore {

    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized override fun ids(): Set<String> = readIds()

    @Synchronized
    override fun add(id: String) {
        preferences.edit { putStringSet(KEY_IDS, readIds() + id) }
    }

    @Synchronized
    override fun remove(id: String) {
        preferences.edit { putStringSet(KEY_IDS, readIds() - id) }
    }

    /** The stored set must not be changed in place, so every read returns a copy. */
    private fun readIds(): Set<String> =
        preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()

    private companion object {
        const val FILE_NAME = "wanted_downloads"
        const val KEY_IDS = "ids"
    }
}
