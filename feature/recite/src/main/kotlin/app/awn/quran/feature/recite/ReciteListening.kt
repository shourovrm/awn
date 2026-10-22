package app.awn.quran.feature.recite

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.flow.first

/**
 * The ayah the Listen button plays: the one holding the mistake the user is looking at, else the
 * one holding the next word the reciter is expected to say, else, when every loaded word is done,
 * the ayah after the last loaded one (which may be on the next page). Null while nothing is loaded.
 */
suspend fun ReciteController.ayahToListenTo(): AyahRef? {
    val key = focusedWord.value ?: session.currentWord.value
    val lastLoaded = session.words.value.lastOrNull()?.key
    return when {
        key != null -> AyahRef(key.surah, key.ayah)
        lastLoaded == null -> null
        else -> {
            val lastLoadedAyah = AyahRef(lastLoaded.surah, lastLoaded.ayah)
            // At the end of the Quran there is no next ayah, so the last one is played again.
            nextAyahAfter(lastLoadedAyah) ?: lastLoadedAyah
        }
    }
}

/**
 * Plays once: the focused word with the Word audio reciter when it has word timings for the ayah,
 * otherwise [ayahToListenTo] with the recite reciter.
 */
suspend fun ReciteController.listenToAyah() {
    val ayah = ayahToListenTo() ?: return
    val reciterId = settings.reciterId.first()
    val wordReciter = wordReciterId(reciterId)
    val focused = focusedWord.value
    if (focused != null && hasWordTimings(wordReciter, ayah)) {
        playWord(audioPlayer, wordReciter, focused)
    } else {
        audioPlayer.play(reciterId, ayah, ayah)
    }
}

/**
 * Stops the microphone and keeps every mark, so the next start goes on from the next expected word
 * instead of clearing the page. This is what the microphone button does while listening.
 */
fun ReciteController.stopMicrophone() {
    session.pauseForListening()
}

/**
 * Stops the microphone before the reciter is played, so the recognition never hears the audio. It
 * is never undone by itself: the user taps Start again, which goes on from the next expected word
 * with every mark kept.
 */
fun ReciteController.pauseMicrophone() {
    stopMicrophone()
}
