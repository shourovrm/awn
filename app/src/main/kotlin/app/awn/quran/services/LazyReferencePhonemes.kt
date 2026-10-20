package app.awn.quran.services

import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.ReferenceWord

/**
 * Defers reading the reference phonemes until the first ayah is asked for. The file is 12.8 MB, so
 * the app must not parse it at startup.
 */
class LazyReferencePhonemes(loader: () -> ReferencePhonemes) : ReferencePhonemes {

    private val loaded by lazy(loader)

    override fun forAyah(surah: Int, ayah: Int): List<ReferenceWord> = loaded.forAyah(surah, ayah)
}
