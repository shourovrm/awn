package app.awn.quran.feature.read

import app.awn.quran.feature.read.quran.starterPacksFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StarterOfferTest {

    @Test
    fun englishGetsSaheehAndEnglishWords() {
        val packs = starterPacksFor("en")!!

        assertEquals(listOf("en_saheeh", "wbw_en"), packs.ids)
    }

    @Test
    fun banglaGetsZakariaAndBanglaWords() {
        val packs = starterPacksFor("bn")!!

        assertEquals(listOf("bn_zakaria", "wbw_bn"), packs.ids)
    }

    @Test
    fun otherLanguagesGetNoStarterDownload() {
        assertNull(starterPacksFor("ar"))
    }
}
