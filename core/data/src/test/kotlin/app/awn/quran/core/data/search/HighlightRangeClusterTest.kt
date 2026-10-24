package app.awn.quran.core.data.search

import java.text.BreakIterator
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A highlight that starts or ends inside a letter-and-marks cluster leaves the marks on their own
 * span; the text renderer then shapes them without their base letter and draws each on a dotted
 * circle. These tests run every possible match range over Uthmani words and require the range to
 * start and end on grapheme cluster boundaries.
 */
class HighlightRangeClusterTest {

    // Copied from quran.sqlite word.text_uthmani, like UthmaniSpellingSearchTest.
    private val uthmaniWords =
        listOf(
            "ٱلرَّحْمَـٰنِ", // 1:1:3, tatweel with a dagger alef
            "ءَامَنُوا۟", // 2:9:4, hamza before alef, small zero after the last alef
            "ٱلصَّلَوٰةَ", // 2:3:5, dagger alef straight on a waw
            "يَسْـَٔلُونَكَ", // 2:215:1, hamza sign on a tatweel
            "ٱلنَّبِيِّـۧنَ", // 2:61:52, small high yeh on a tatweel
            "ٱلْقُرْءَانُ", // 2:185:6
            "رَءُوفٌۢ", // 2:207:10
            "إِبْرَٰهِـۧمَ", // 2:124:3
            "مُوسَىٰٓ", // 2:51:3, dagger alef and maddah on alef maqsura
        )

    @Test
    fun everyMatchRangeStartsAndEndsOnAGraphemeClusterBoundary() {
        for (word in uthmaniWords) {
            val boundaries = clusterBoundaries(word)
            for (reading in DaggerAlefReading.entries) {
                val positioned = SearchNormaliser.normaliseWithPositions(word, reading)
                for (start in positioned.text.indices) {
                    for (end in start + 1..positioned.text.length) {
                        val range = positioned.originalRange(word, start, end)

                        assertTrue(
                            "$word ($reading, $start until $end): range $range starts inside a cluster",
                            range.first in boundaries,
                        )
                        assertTrue(
                            "$word ($reading, $start until $end): range $range ends inside a cluster",
                            range.last + 1 in boundaries,
                        )
                    }
                }
            }
        }
    }

    private fun clusterBoundaries(text: String): Set<Int> {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val boundaries = mutableSetOf<Int>()
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            boundaries += boundary
            boundary = iterator.next()
        }
        return boundaries
    }
}
