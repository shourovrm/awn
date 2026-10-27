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

    /**
     * Measures [AyahFinder.findAll] on every clip at 5 s, at 3 s and as a fragment that starts
     * mid-ayah, then prints one summary. It asserts only that clips were measured, so accuracy
     * never fails the build; the printed numbers are the measurement.
     */
    @Test
    fun findAllListsAreMeasuredOnClipsAndFragments() {
        assumeTrue(TestPaths.fixturesAvailable())
        val recordings = fixtureRecordings()
        val measurements = FIND_ALL_VARIANTS.flatMap { variant ->
            recordings.map { (file, recording) -> measureClip(file, variant, recording) }
        }
        printFindAllSummary(recordings.size, measurements)
        assertTrue("no clips were measured", measurements.isNotEmpty())
    }

    private fun fixtureRecordings(): List<Pair<File, PhonemeRecording>> =
        FIXTURE_DIRECTORIES.flatMap { directoryName ->
                val directory = File(TestPaths.fixturesDirectory, directoryName)
                directory.listFiles { file -> file.extension == "wav" }.orEmpty().sorted()
            }
            .map { file -> file to RecordingDecoder.decode(file) }

    private fun measureClip(
        file: File,
        variant: ClipVariant,
        recording: PhonemeRecording,
    ): FindAllMeasurement {
        val expected = expectedAyahOf(file)
        val heard =
            recording.allEvents
                .filter { event -> isInsideVariant(event.onsetSeconds, variant) }
                .map { event -> event.symbol }
        val matches = finder.findAll(heard)
        val rankIndex = matches.indexOfFirst { match -> (match.surah to match.ayah) == expected }
        return FindAllMeasurement(
            clip = "${file.parentFile.name}/${file.name}",
            variant = variant.name,
            rank = if (rankIndex < 0) -1 else rankIndex + 1,
            bestScore = matches.firstOrNull()?.score ?: 0,
            rightScore = matches.getOrNull(rankIndex)?.score ?: -1,
            heardPhonemes = heard.size,
            listLength = matches.size,
        )
    }

    private fun isInsideVariant(onsetSeconds: Float, variant: ClipVariant): Boolean =
        onsetSeconds >= variant.startSeconds && onsetSeconds < variant.endSeconds

    private fun printFindAllSummary(clipCount: Int, measurements: List<FindAllMeasurement>) {
        println("findAll summary: $clipCount clips, ${measurements.size} measurements")
        for (variant in FIND_ALL_VARIANTS) {
            val rows = measurements.filter { it.variant == variant.name }
            val hitCount = rows.count { it.rank > 0 }
            val listLengths = rows.map { it.listLength }
            println(
                "  %s: hit rate %.2f (%d of %d), mean list %.1f, max list %d"
                    .format(
                        variant.name,
                        hitCount.toDouble() / rows.size,
                        hitCount,
                        rows.size,
                        listLengths.average(),
                        listLengths.maxOrNull() ?: 0,
                    ),
            )
        }
        println(
            "clip,variant,rank_of_right_ayah_or_-1,best_score,right_score," +
                "heard_phonemes,list_length",
        )
        for (row in measurements) {
            println(
                "${row.clip},${row.variant},${row.rank},${row.bestScore}," +
                    "${row.rightScore},${row.heardPhonemes},${row.listLength}",
            )
        }
    }

    // A heard phoneme counts when its onset falls in [startSeconds, endSeconds).
    private data class ClipVariant(val name: String, val startSeconds: Float, val endSeconds: Float)

    // rank is 1-based within the findAll list, -1 when the right ayah is not listed. rightScore is
    // -1 in the same case, because findAll does not return scores of dropped ayahs.
    private data class FindAllMeasurement(
        val clip: String,
        val variant: String,
        val rank: Int,
        val bestScore: Int,
        val rightScore: Int,
        val heardPhonemes: Int,
        val listLength: Int,
    )

    private fun expectedAyahOf(file: File): Pair<Int, Int> {
        val name = file.nameWithoutExtension
        return name.take(SURAH_DIGITS).toInt() to name.drop(SURAH_DIGITS).take(SURAH_DIGITS).toInt()
    }

    private companion object {
        val CLIP_SECONDS = listOf(3f, 5f, 8f)
        const val SURAH_DIGITS = 3
        const val MISSES_SHOWN = 10
        const val NANOS_PER_MILLI = 1_000_000L
        val FIXTURE_DIRECTORIES = listOf("ayah", "search")
        val FIND_ALL_VARIANTS =
            listOf(
                ClipVariant("5s", 0f, 5f),
                ClipVariant("3s", 0f, 3f),
                ClipVariant("fragment", 1.5f, 4.5f),
            )
    }
}
