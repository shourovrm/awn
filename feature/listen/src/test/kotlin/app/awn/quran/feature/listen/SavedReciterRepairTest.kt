package app.awn.quran.feature.listen

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Reciter
import app.awn.quran.core.data.ReciterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedReciterRepairTest {

    private fun reciter(id: String) =
        Reciter(
            id = id,
            name = id,
            surahUrlPattern = "",
            ayahUrlPattern = "",
            wordByWord = false,
            timingPack = null,
        )

    private val listedReciters =
        object : ReciterRepository {
            override suspend fun reciters() = listOf(reciter("alafasy"), reciter("minshawi"))

            override suspend fun ayahAudio(reciterId: String, ayah: AyahRef): AyahAudio? = null
        }

    @Test
    fun aSavedReciterThatIsNoLongerListedBecomesTheDefault() = runTest {
        val settings = ListenSettings(InMemoryPreferences())
        settings.selectReciter("raad_al_kurdi")

        settings.replaceUnknownReciter(listedReciters)

        assertEquals("minshawi", settings.selectedReciterId.first())
    }

    @Test
    fun aSavedReciterThatIsListedStaysSelected() = runTest {
        val settings = ListenSettings(InMemoryPreferences())
        settings.selectReciter("alafasy")

        settings.replaceUnknownReciter(listedReciters)

        assertEquals("alafasy", settings.selectedReciterId.first())
    }
}
