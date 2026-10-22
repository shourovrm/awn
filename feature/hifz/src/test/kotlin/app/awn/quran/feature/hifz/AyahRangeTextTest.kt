package app.awn.quran.feature.hifz

import app.awn.quran.core.data.AyahRef
import org.junit.Assert.assertEquals
import org.junit.Test

/** The muted line of the part menu names what is practised by surah and ayah numbers. */
class AyahRangeTextTest {
    @Test
    fun oneAyahIsWrittenOnce() {
        assertEquals("2:255", ayahRangeText(AyahRef(2, 255), AyahRef(2, 255)))
    }

    @Test
    fun aRangeInOneSurahNamesTheSurahOnce() {
        assertEquals("2:255-257", ayahRangeText(AyahRef(2, 255), AyahRef(2, 257)))
    }

    @Test
    fun aRangeAcrossTwoSurahsNamesBoth() {
        assertEquals("2:285-3:2", ayahRangeText(AyahRef(2, 285), AyahRef(3, 2)))
    }
}
