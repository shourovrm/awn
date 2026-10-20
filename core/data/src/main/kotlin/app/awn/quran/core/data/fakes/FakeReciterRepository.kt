package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository

/** In-memory reciters for tests. Audio is looked up by reciter id and ayah. */
class FakeReciterRepository(
    val reciterList: MutableList<Reciter> = mutableListOf(),
    val audio: MutableMap<Pair<String, AyahRef>, AyahAudio> = mutableMapOf(),
) : ReciterRepository {

    override suspend fun reciters(): List<Reciter> = reciterList.toList()

    override suspend fun ayahAudio(reciterId: String, ayah: AyahRef): AyahAudio? =
        audio[reciterId to ayah]
}
