package app.awn.quran.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNormaliserTest {

    @Test
    fun vowelSignsShaddaAndDaggerAlefAreDropped() {
        assertEquals("الرحمن", SearchNormaliser.normalise("ٱلرَّحْمَٰنِ"))
    }

    @Test
    fun theTatweelIsDropped() {
        assertEquals("الله", SearchNormaliser.normalise("اللـّـه"))
    }

    @Test
    fun everyAlefFormBecomesAPlainAlef() {
        val forms = listOf("أحمد", "إحمد", "آحمد", "ٱحمد", "احمد")

        assertEquals(setOf("احمد"), forms.map(SearchNormaliser::normalise).toSet())
    }

    @Test
    fun alefMaqsuraBecomesYa() {
        assertEquals("موسي", SearchNormaliser.normalise("مُوسَىٰ"))
        assertEquals("موسي", SearchNormaliser.normalise("موسي"))
    }

    @Test
    fun quranicAnnotationMarksAreDropped() {
        // U+06D6 and U+06DA are pause signs, U+06E1 is the small dotless head of khah.
        assertEquals("لا ريب", SearchNormaliser.normalise("لَا رَيْبَۖ"))
        assertEquals("بسم", SearchNormaliser.normalise("بِسْمِۚ"))
        assertEquals("بسم", SearchNormaliser.normalise("بِسۡمِ"))
    }

    @Test
    fun latinTextIsLowerCasedAndLosesAccents() {
        assertEquals("rahman", SearchNormaliser.normalise("Raḥmān"))
        assertEquals("rahman", SearchNormaliser.normalise("RAHMAN"))
    }

    @Test
    fun hyphensBecomeSpacesAndApostrophesVanish() {
        assertEquals("ar rahman", SearchNormaliser.normalise("Ar-Raḥmān"))
        assertEquals("allahs", SearchNormaliser.normalise("Allah's"))
    }

    @Test
    fun runsOfSpacesCollapseAndTheEndsAreTrimmed() {
        assertEquals("in the name", SearchNormaliser.normalise("  In   the\tname "))
    }

    @Test
    fun banglaVowelSignsSurvive() {
        assertEquals("কুরআন", SearchNormaliser.normalise("কুরআন"))
        assertEquals("পরম করুণাময়", SearchNormaliser.normalise("পরম করুণাময়"))
    }

    @Test
    fun aMatchMapsBackToTheOriginalRangeAndKeepsTrailingMarks() {
        val original = "ٱلرَّحْمَٰنِ"
        val positioned = SearchNormaliser.normaliseWithPositions(original)

        // "الرحم" is the first five normalised letters; the fatha and dagger alef after the meem
        // are kept.
        assertEquals(0..9, positioned.originalRange(original, 0, 5))
    }

    @Test
    fun aMatchInTheMiddleMapsToTheRightLetters() {
        val original = "بِسْمِ ٱللَّهِ"
        val positioned = SearchNormaliser.normaliseWithPositions(original)
        val start = positioned.text.indexOf("الله")

        val range = positioned.originalRange(original, start, start + 4)

        assertEquals("ٱللَّهِ", original.substring(range))
    }
}
