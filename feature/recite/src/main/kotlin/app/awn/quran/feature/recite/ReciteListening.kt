package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.flow.first

/**
 * The ayah the Listen button plays: the one holding the mistake the user is looking at, else the
 * one holding the next word the reciter is expected to say, else the first loaded ayah before
 * anything was recited. Null while nothing is loaded.
 */
fun ReciteController.ayahToListenTo(): AyahRef? {
    val key =
        focusedWord.value ?: session.currentWord.value ?: session.words.value.firstOrNull()?.key
    return key?.let { AyahRef(it.surah, it.ayah) }
}

/**
 * Plays once with the reciter chosen in the settings: the focused word when the reciter has word
 * timings, otherwise [ayahToListenTo].
 */
suspend fun ReciteController.listenToAyah() {
    val ayah = ayahToListenTo() ?: return
    val reciterId = settings.reciterId.first()
    val focused = focusedWord.value
    if (focused != null && hasWordTimings(reciterId, ayah)) {
        playWord(audioPlayer, reciterId, focused)
    } else {
        audioPlayer.play(reciterId, ayah, ayah)
    }
}

/**
 * Stops the microphone without forgetting what was heard, so the recognition never hears the
 * reciter. It is never undone by itself: the user taps Start again, which goes on from the next
 * expected word with every mark kept.
 */
fun ReciteController.pauseMicrophone() {
    session.pauseForListening()
}
