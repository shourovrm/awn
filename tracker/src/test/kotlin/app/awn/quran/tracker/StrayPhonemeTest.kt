package app.awn.quran.tracker

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Room sound makes the model emit a few unrelated phonemes now and then. Before the entry check,
 * two random phonemes coloured a word in 55 % of 300 seeded runs on Al-Fatihah and eight turned a
 * word green in 1 % (measured 2026-10-21); the tracker now judges nothing until the heard phonemes
 * match the start of the text.
 */
class StrayPhonemeTest {

    private val reference by lazy { JsonReferencePhonemes.fromFile(TestPaths.referenceFile) }

    private val alphabet by lazy {
        (1..7).flatMap { reference.forAyah(1, it) }.flatMap { it.phonemes }.distinct()
    }

    private fun colouredWordCount(surah: Int, phonemeCount: Int, seed: Int): Int {
        val words = SurahReference(surah, reference).words
        val random = Random(seed)
        val tracker = AlignmentTracker(TrackerConfig())
        tracker.load(words, 0)
        repeat(phonemeCount) { position ->
            val symbol = alphabet[random.nextInt(alphabet.size)]
            tracker.onPhonemes(listOf(PhonemeEvent(symbol, position * 0.2f, -1f)))
        }
        return words.indices.count { tracker.stateOfWord(it) != WordState.Neutral }
    }

    @Test
    fun aShortBurstOfRandomPhonemesColoursNoWord() {
        assumeTrue(TestPaths.fixturesAvailable())
        for (surah in listOf(1, 112)) {
            for (burstLength in listOf(2, 4, 8, 12)) {
                val colouredRuns =
                    (0 until RUNS).count { colouredWordCount(surah, burstLength, it) > 0 }
                println("STRAY surah $surah burst $burstLength: coloured $colouredRuns/$RUNS")
                assertEquals("surah $surah burst $burstLength", 0, colouredRuns)
            }
        }
    }

    @Test
    fun aLongStreamOfRandomPhonemesColoursNoWord() {
        assumeTrue(TestPaths.fixturesAvailable())
        for (surah in listOf(1, 112, 2)) {
            val colouredRuns =
                (0 until RUNS).count { colouredWordCount(surah, STREAM_LENGTH, 1000 + it) > 0 }
            println("STREAM surah $surah length $STREAM_LENGTH: coloured $colouredRuns/$RUNS")
            assertEquals("surah $surah", 0, colouredRuns)
        }
    }

    private companion object {
        const val RUNS = 300
        const val STREAM_LENGTH = 300
    }
}
