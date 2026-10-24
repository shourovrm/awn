package app.awn.quran.services

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.downloads.CatalogueModel
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.wordReciterIdFor
import app.awn.quran.feature.learn.sound.TimingPackState
import app.awn.quran.feature.learn.sound.WordSound
import app.awn.quran.feature.learn.sound.WordSoundStatus
import app.awn.quran.feature.learn.sound.wordSoundStatusOf
import app.awn.quran.player.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Plays a Quran word of a Learn page in the voice of the word reciter chosen in Settings, else the
 * default word reciter. When that reciter's word timings are not on the phone it reports the
 * download instead of playing, and downloads only when asked.
 */
class LearnWordSound(
    private val reciters: ReciterRepository,
    private val audioPlayer: AudioPlayer,
    private val listeningReciterId: Flow<String>,
    private val chosenWordReciterId: Flow<String?>,
    packs: PackRepository,
    downloads: PackDownloads,
    scope: CoroutineScope,
) : WordSound {

    private val catalogue = CatalogueModel(packs, downloads, scope)

    override suspend fun play(word: WordKey) {
        audioPlayer.playWordToEnd(
            wordReciterIdNow(),
            AyahRef(word.surah, word.ayah),
            word.wordPosition,
        )
    }

    override suspend fun status(word: WordKey): WordSoundStatus {
        val reciterId = wordReciterIdNow()
        val ayah = AyahRef(word.surah, word.ayah)
        val hasTimings = reciters.ayahAudio(reciterId, ayah)?.words.orEmpty().isNotEmpty()
        return wordSoundStatusOf(hasTimings, timingPackOf(reciterId))
    }

    override fun download(packId: String) = catalogue.install(packId)

    private suspend fun timingPackOf(reciterId: String): TimingPackState? {
        val packId =
            reciters.reciters().firstOrNull { it.id == reciterId }?.timingPack ?: return null
        val row = catalogue.state.first { it.isLoaded }.rowFor(packId)
        return row?.let {
            TimingPackState(packId, it.item.isInstalled, it.item.downloadBytes, it.transfer)
        }
    }

    // With no word reciter at all the listening reciter is kept, so the lookup finds no timings.
    private suspend fun wordReciterIdNow(): String {
        val listening = listeningReciterId.first()
        val withTimings = reciters.reciters().filter(Reciter::hasWordTimings).map { it.id }
        return wordReciterIdFor(chosenWordReciterId.first(), listening, withTimings) ?: listening
    }
}

private val Reciter.hasWordTimings: Boolean
    get() = wordByWord && timingPack != null
