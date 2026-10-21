package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.read.ReadDependencies
import kotlinx.coroutines.flow.first

/** Single words of the selected reciter, which exist only when the reciter has word timings. */
internal class WordAudio(private val dependencies: ReadDependencies) {
    suspend fun hasTimings(ayah: AyahRef): Boolean {
        val reciterId = dependencies.selectedReciterId.first()
        return dependencies.reciters?.ayahAudio(reciterId, ayah)?.words.orEmpty().isNotEmpty()
    }

    suspend fun play(ayah: AyahRef, position: Int) {
        val reciterId = dependencies.selectedReciterId.first()
        dependencies.audioPlayer.playWord(reciterId, ayah, position)
    }
}
