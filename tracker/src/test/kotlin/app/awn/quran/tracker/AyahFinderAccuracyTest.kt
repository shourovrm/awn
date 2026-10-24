package app.awn.quran.tracker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Voice search accuracy on real recordings: each ayah file is cut to its first few seconds, decoded
 * by the model and searched for in the whole Quran. `ayah/` holds Husary (the tracker's reciter);
 * `search/` holds other reciters across the Quran (see tools/fetch_search_fixtures.sh). Files are
 * named SSSAAA.wav.
 */
class AyahFinderAccuracyTest {

    private val finder by lazy {
        AyahFinder.from(JsonReferencePhonemes.fromFile(TestPaths.referenceFile))
    }

    @Test
    fun husaryAyahsAreFoundFromTheirOpening() {
        assumeTrue(TestPaths.fixturesAvailable())
        val shares = measure(File(TestPaths.fixturesDirectory, "ayah"))
        assertTrue("top-3 share at 5 s was ${shares.getValue(5f)}", shares.getValue(5f) >= 0.9)
    }

    @Test
    fun otherRecitersAreFoundFromTheirOpening() {
        val directory = File(TestPaths.fixturesDirectory, "search")
        assumeTrue(TestPaths.fixturesAvailable() && directory.isDirectory)
        val shares = measure(directory)
        assertTrue("top-3 share at 5 s was ${shares.getValue(5f)}", shares.getValue(5f) >= 0.8)
    }

    // Prints top-1 and top-3 shares per clip length and returns the top-3 share per clip length.
    private fun measure(directory: File): Map<Float, Double> {
        val files = directory.listFiles { file -> file.extension == "wav" }.orEmpty().sorted()
        val recordings = files.associateWith { RecordingDecoder.decode(it) }
        val topThreeShares = mutableMapOf<Float, Double>()
        for (clipSeconds in CLIP_SECONDS) {
            var firstCount = 0
            var topThreeCount = 0
            var slowestMillis = 0L
            val misses = mutableListOf<String>()
            for ((file, recording) in recordings) {
                val expected = expectedAyahOf(file)
                val heard =
                    recording.allEvents.filter { it.onsetSeconds < clipSeconds }.map { it.symbol }
                val started = System.nanoTime()
                val matches = finder.find(heard).map { it.surah to it.ayah }
                slowestMillis =
                    maxOf(slowestMillis, (System.nanoTime() - started) / NANOS_PER_MILLI)
                if (matches.firstOrNull() == expected) firstCount++
                if (expected in matches) topThreeCount++ else misses += "${file.name}->$matches"
            }
            val total = recordings.size.toDouble()
            topThreeShares[clipSeconds] = topThreeCount / total
            println(
                "${directory.name} ${clipSeconds}s: top-1 %.2f, top-3 %.2f of %d, slowest %d ms"
                    .format(
                        firstCount / total,
                        topThreeCount / total,
                        recordings.size,
                        slowestMillis,
                    ),
            )
            println("  missed: ${misses.take(MISSES_SHOWN)}")
        }
        return topThreeShares
    }

    private fun expectedAyahOf(file: File): Pair<Int, Int> {
        val name = file.nameWithoutExtension
        return name.take(SURAH_DIGITS).toInt() to name.drop(SURAH_DIGITS).take(SURAH_DIGITS).toInt()
    }

    private companion object {
        val CLIP_SECONDS = listOf(3f, 5f, 8f)
        const val SURAH_DIGITS = 3
        const val MISSES_SHOWN = 10
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
