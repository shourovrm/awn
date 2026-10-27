package app.awn.quran.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AyahFinderTest {

    // Four made-up ayahs; each string is the phonemes of one word.
    private val reference =
        FakeReference(
            mapOf(
                (1 to 1) to listOf("abcdef", "ghijkl"),
                (1 to 2) to listOf("mnopqr", "stuvwx"),
                (2 to 1) to listOf("zyxwvu", "tsrqpo"),
                (2 to 2) to listOf("abcxyz", "mnoghi"),
            ),
        )

    private val finder = AyahFinder.from(reference, surahCount = 2)

    private fun heard(text: String): List<String> = text.map { it.toString() }

    @Test
    fun anExactRecitationFindsItsAyahFirst() {
        val matches = finder.find(heard("mnopqrstuvwx"))

        assertEquals(1 to 2, matches.first().surah to matches.first().ayah)
    }

    @Test
    fun aRecitationWithModelErrorsStillFindsItsAyah() {
        // Two phonemes heard wrong and one missed.
        val matches = finder.find(heard("zyQwvutsrRpo".replace("v", "")))

        assertEquals(2 to 1, matches.first().surah to matches.first().ayah)
    }

    @Test
    fun aRecitationThatStartsMidAyahAndRunsOnFindsTheAyahItStartedIn() {
        val matches = finder.find(heard("ghijklmnop"))

        assertEquals(1 to 1, matches.first().surah to matches.first().ayah)
    }

    @Test
    fun aWholeAyahCoversAllItsWords() {
        val match = finder.find(heard("mnopqrstuvwx")).first()

        assertEquals(0, match.firstWord)
        assertEquals(1, match.lastWord)
    }

    @Test
    fun aRecitationFromTheMiddleOfAnAyahCoversOnlyThoseWords() {
        val reference =
            FakeReference(mapOf((1 to 1) to listOf("abcdef", "ghijkl", "mnopqr", "stuvwx")))

        val match = AyahFinder.from(reference, surahCount = 1).find(heard("ghijklmnopqr")).first()

        assertEquals(1, match.firstWord)
        assertEquals(2, match.lastWord)
    }

    @Test
    fun aRecitationThatRunsOnIntoTheNextAyahEndsAtTheLastWordOfTheFirst() {
        val match = finder.find(heard("ghijklmnop")).first { it.surah == 1 && it.ayah == 1 }

        assertEquals(1, match.firstWord)
        assertEquals(1, match.lastWord)
    }

    @Test
    fun aRepeatedPhonemeCountsOnce() {
        // The model reports a held sound as the same symbol several times in a row.
        val matches = finder.find(heard("mmnnooppqqrrsstt"))

        assertEquals(1 to 2, matches.first().surah to matches.first().ayah)
    }

    @Test
    fun matchesAreDistinctAyahsBestFirst() {
        val matches = finder.find(heard("abcdefghi"), limit = 3)

        val ayahs = matches.map { it.surah to it.ayah }
        assertEquals(ayahs.distinct(), ayahs)
        assertEquals(1 to 1, ayahs.first())
        assertTrue(matches.zipWithNext().all { (first, second) -> first.score >= second.score })
    }

    @Test
    fun aMatchFromTheStartOfAnAyahRanksAboveTheSamePhrasesMidAyah() {
        // The same two words are the middle of 1:1 and open 1:2.
        val reference =
            FakeReference(
                mapOf(
                    (1 to 1) to listOf("xyzxyz", "abcdef", "ghijkl"),
                    (1 to 2) to listOf("abcdef", "ghijkl"),
                ),
            )
        val matches = AyahFinder.from(reference, surahCount = 1).find(heard("abcdefghijkl"))

        assertEquals(1 to 2, matches.first().surah to matches.first().ayah)
    }

    @Test
    fun noiseFindsNothing() {
        assertTrue(finder.find(heard("QRSTQRSTQRST")).isEmpty())
    }

    @Test
    fun tooFewPhonemesFindNothing() {
        assertTrue(finder.find(heard("abc")).isEmpty())
    }

    @Test
    fun theBlankSymbolIsIgnored() {
        val withBlanks = listOf("<blank>") + heard("mnopqr") + "<blank>" + heard("stuvwx")

        val matches = finder.find(withBlanks)

        assertEquals(1 to 2, matches.first().surah to matches.first().ayah)
    }

    @Test
    fun findAllReturnsOneMatchForAClearRecitation() {
        val matches = finder.findAll(heard("mnopqrstuvwx"))

        assertEquals(listOf(1 to 2), matches.map { it.surah to it.ayah })
    }

    @Test
    fun findAllKeepsEveryAyahThatTiesForTheBestInQuranOrder() {
        // Three ayahs open with the same phrase, so a recitation of only that phrase ties them.
        val reference =
            FakeReference(
                mapOf(
                    (1 to 1) to listOf("abcdef", "ghijkl"),
                    (1 to 2) to listOf("abcdef", "ghijkl"),
                    (1 to 3) to listOf("abcdef", "ghijkl"),
                    (1 to 4) to listOf("mnopqr", "stuvwx"),
                ),
            )

        val matches = AyahFinder.from(reference, surahCount = 1).findAll(heard("abcdefghijkl"))

        assertEquals(listOf(1 to 1, 1 to 2, 1 to 3), matches.map { it.surah to it.ayah })
    }

    @Test
    fun findAllLeavesOutAnAyahBelowTheCutoffShareOfTheBest() {
        // 1:2 shares only its first word with the recitation, so it scores well under 85% of 1:1.
        val reference =
            FakeReference(
                mapOf(
                    (1 to 1) to listOf("abcdef", "ghijkl"),
                    (1 to 2) to listOf("abcdef", "xyzxyz"),
                ),
            )

        val matches = AyahFinder.from(reference, surahCount = 1).findAll(heard("abcdefghijkl"))

        assertEquals(listOf(1 to 1), matches.map { it.surah to it.ayah })
    }

    @Test
    fun findAllNeverReturnsMoreThanTheMaximum() {
        // One more identical ayah than the cap, so every ayah ties for the best.
        val identicalAyahs =
            (1..AyahFinder.MAXIMUM_RESULTS + 1).associate { ayah ->
                (1 to ayah) to listOf("abcdef", "ghijkl")
            }

        val matches =
            AyahFinder.from(FakeReference(identicalAyahs), surahCount = 1)
                .findAll(heard("abcdefghijkl"))

        assertEquals(AyahFinder.MAXIMUM_RESULTS, matches.size)
    }

    @Test
    fun findAllFindsNothingWhenTooFewPhonemesWereHeard() {
        assertTrue(finder.findAll(heard("abc")).isEmpty())
    }

    private class FakeReference(private val words: Map<Pair<Int, Int>, List<String>>) :
        ReferencePhonemes {
        override fun forAyah(surah: Int, ayah: Int): List<ReferenceWord> =
            words[surah to ayah].orEmpty().mapIndexed { index, word ->
                ReferenceWord(WordKeyLike(surah, ayah, index + 1), word.map { it.toString() })
            }
    }
}
