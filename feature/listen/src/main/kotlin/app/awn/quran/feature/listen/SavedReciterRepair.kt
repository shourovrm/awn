package app.awn.quran.feature.listen

import app.awn.quran.core.data.ReciterRepository
import kotlinx.coroutines.flow.first

/**
 * Moves a saved reciter that the catalogue no longer lists back to
 * [ListenSettings.DEFAULT_RECITER_ID]. Without it the audio bar shows no reciter name and Play does
 * nothing, because every screen looks the saved id up in the catalogue on its own.
 */
suspend fun ListenSettings.replaceUnknownReciter(reciters: ReciterRepository) {
    val savedReciterId = selectedReciterId.first()
    val isStillListed = reciters.reciters().any { it.id == savedReciterId }
    if (!isStillListed) selectReciter(ListenSettings.DEFAULT_RECITER_ID)
}
