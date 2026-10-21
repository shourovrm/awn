package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.flow.first

/**
 * The ayah the Listen button plays: the one holding the next word the reciter is expected to say,
 * or the first loaded ayah before anything was recited. Null while nothing is loaded.
 */
fun ReciteController.ayahToListenTo(): AyahRef? {
    val key = session.currentWord.value ?: session.words.value.firstOrNull()?.key
    return key?.let { AyahRef(it.surah, it.ayah) }
}

/** Plays [ayahToListenTo] once with the reciter chosen in the settings. */
suspend fun ReciteController.listenToAyah() {
    val ayah = ayahToListenTo() ?: return
    audioPlayer.play(settings.reciterId.first(), ayah, ayah)
}

/**
 * Stops the microphone without forgetting what was heard, so the recognition never hears the
 * reciter. It is never undone by itself: the user taps Start again, which goes on from the next
 * expected word with every mark kept.
 */
fun ReciteController.pauseMicrophone() {
    session.pauseForListening()
}
