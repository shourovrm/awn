package app.awn.quran.tracker

import java.io.File

/** Where the fixtures, the model and the reference live, as set by tracker/build.gradle.kts. */
object TestPaths {
    val modelsDirectory = File(System.getProperty("awn.models.directory"))
    val fixturesDirectory = File(System.getProperty("awn.fixtures.directory"))
    val modelFile = File(modelsDirectory, "zipformer_p_arabic_v3.int8.onnx")
    val tokensFile = File(modelsDirectory, "tokens.txt")
    val referenceFile = File(modelsDirectory, "ordered_quran_phonemes.json")

    fun fixturesAvailable() =
        modelFile.isFile && tokensFile.isFile && File(fixturesDirectory, "full_s1.wav").isFile
}

enum class FixtureKind {
    Full,
    Cut,
    Splice,
    Stretch,
}

/**
 * One test recording, described by its file name as written by the fetchFixtures task: `full_s112`,
 * `cut_s1_a3` (ayah 3 left out), `splice_s112_after1_from1-2` (a foreign word after ayah 1) and
 * `stretch_s1_t1.2` (the whole surah at 1.2 times the speed).
 */
data class Fixture(
    val file: File,
    val kind: FixtureKind,
    val surah: Int,
    val cutAyah: Int?,
    val spliceAfterAyah: Int?,
    val tempo: Float,
) {
    val name: String
        get() = file.nameWithoutExtension

    // splice_s112_after1_from1-2 spliced in the start of ayah 1:2, stored as word_001002.wav.
    fun foreignSegmentFile(): File {
        val donor = Regex("from(\\d+)-(\\d+)").find(name)!!.groupValues
        return File(
            TestPaths.fixturesDirectory,
            "word_%03d%03d.wav".format(donor[1].toInt(), donor[2].toInt()),
        )
    }

    companion object {
        private val fullPattern = Regex("full_s(\\d+)")
        private val cutPattern = Regex("cut_s(\\d+)_a(\\d+)")
        private val splicePattern = Regex("splice_s(\\d+)_after(\\d+)_from.*")
        private val stretchPattern = Regex("stretch_s(\\d+)_t([0-9.]+)")

        fun listAll(): List<Fixture> =
            TestPaths.fixturesDirectory
                .listFiles { file -> file.extension == "wav" }
                .orEmpty()
                .sortedBy { it.name }
                .mapNotNull { parse(it) }

        @Suppress("ReturnCount")
        fun parse(file: File): Fixture? {
            val stem = file.nameWithoutExtension
            fullPattern.matchEntire(stem)?.let {
                return Fixture(file, FixtureKind.Full, it.int(1), null, null, 1f)
            }
            cutPattern.matchEntire(stem)?.let {
                return Fixture(file, FixtureKind.Cut, it.int(1), it.int(2), null, 1f)
            }
            splicePattern.matchEntire(stem)?.let {
                return Fixture(file, FixtureKind.Splice, it.int(1), null, it.int(2), 1f)
            }
            stretchPattern.matchEntire(stem)?.let {
                val tempo = it.groupValues[2].toFloat()
                return Fixture(file, FixtureKind.Stretch, it.int(1), null, null, tempo)
            }
            return null
        }

        private fun MatchResult.int(group: Int) = groupValues[group].toInt()
    }
}

/** The surah's words and where each ayah starts in them. */
class SurahReference(val surah: Int, reference: ReferencePhonemes) {
    // Surahs 2 and 36 are recorded for their first 20 ayahs only.
    private val ayahLimit = if (surah == 2 || surah == 36) 20 else Int.MAX_VALUE

    val words: List<ReferenceWord>
    val ayahCount: Int

    init {
        val collected = mutableListOf<ReferenceWord>()
        var ayah = 1
        while (true) {
            val ayahWords = reference.forAyah(surah, ayah)
            if (ayahWords.isEmpty() || ayah > ayahLimit) break
            collected += ayahWords
            ayah++
        }
        words = collected
        ayahCount = ayah - 1
    }

    fun wordIndices(ayah: Int): IntRange {
        val indices = words.indices.filter { words[it].key.ayah == ayah }
        return indices.first()..indices.last()
    }

    fun ayahOfWord(index: Int): Int = words[index].key.ayah
}

/** The ayah being recited between two moments of a recording, from the lengths of the pieces. */
data class TruthSegment(val ayah: Int, val startSeconds: Float, val endSeconds: Float)

object TruthTimeline {
    private const val PAUSE_SECONDS = 0.4f
    const val FOREIGN_WORD_AYAH = -1

    fun of(fixture: Fixture, reference: SurahReference): List<TruthSegment> {
        val segments = mutableListOf<TruthSegment>()
        var clock = 0f
        for (ayah in 1..reference.ayahCount) {
            if (ayah == fixture.cutAyah) continue
            val length = ayahLength(fixture.surah, ayah) / fixture.tempo
            segments += TruthSegment(ayah, clock, clock + length)
            clock += length + PAUSE_SECONDS
            if (ayah == fixture.spliceAfterAyah) {
                val foreignLength = WavTrackerInput(fixture.foreignSegmentFile()).durationSeconds
                segments += TruthSegment(FOREIGN_WORD_AYAH, clock, clock + foreignLength)
                clock += foreignLength + PAUSE_SECONDS
            }
        }
        return segments
    }

    private fun ayahLength(surah: Int, ayah: Int): Float {
        val file = File(TestPaths.fixturesDirectory, "ayah/%03d%03d.wav".format(surah, ayah))
        return WavTrackerInput(file).durationSeconds
    }
}

/** Everything the model produced for one recording, one list of phonemes per 100 ms block. */
class PhonemeRecording(val eventsPerBlock: List<List<PhonemeEvent>>, val durationSeconds: Float) {
    val allEvents: List<PhonemeEvent>
        get() = eventsPerBlock.flatten()
}

/** Decodes recordings with the real model, and remembers the result on disk between runs. */
object RecordingDecoder {
    private const val BLOCK_SECONDS = 0.1f
    private const val TRAILING_SILENCE_SECONDS = 1.0f
    private const val FIELD_SEPARATOR = '\t'

    fun decode(file: File): PhonemeRecording {
        val cacheFile = cacheFileFor(file)
        if (cacheFile.isFile) return readCache(cacheFile)
        val recording = runModel(file)
        cacheFile.parentFile.mkdirs()
        writeCache(cacheFile, recording)
        return recording
    }

    // The cache name carries the model and recording sizes, so a changed input is decoded again.
    private fun cacheFileFor(file: File): File {
        val key = "${file.nameWithoutExtension}-${file.length()}-${TestPaths.modelFile.length()}"
        return File(TestPaths.fixturesDirectory, "events/$key.tsv")
    }

    fun runModel(file: File): PhonemeRecording {
        val input = WavTrackerInput(file)
        val eventsPerBlock = mutableListOf<List<PhonemeEvent>>()
        SherpaPhonemeSource(TestPaths.modelFile.path, TestPaths.tokensFile.path).use { source ->
            while (true) {
                val block = input.nextBlock() ?: break
                eventsPerBlock += source.accept(block)
            }
            // A live microphone never stops; silence lets the last 0.48 s chunk be decoded.
            val silenceBlockCount = (TRAILING_SILENCE_SECONDS / BLOCK_SECONDS).toInt()
            repeat(silenceBlockCount) {
                eventsPerBlock += source.accept(FloatArray(TrackerInput.SAMPLE_RATE_HZ / 10))
            }
            eventsPerBlock += source.finish()
        }
        return PhonemeRecording(eventsPerBlock, input.durationSeconds)
    }

    private fun writeCache(cacheFile: File, recording: PhonemeRecording) {
        val lines = mutableListOf("duration${FIELD_SEPARATOR}${recording.durationSeconds}")
        for ((blockIndex, events) in recording.eventsPerBlock.withIndex()) {
            for (event in events) {
                lines +=
                    listOf(blockIndex, event.symbol, event.onsetSeconds, event.probability)
                        .joinToString(FIELD_SEPARATOR.toString())
            }
            if (events.isEmpty()) lines += "$blockIndex"
        }
        cacheFile.writeText(lines.joinToString("\n"))
    }

    private fun readCache(cacheFile: File): PhonemeRecording {
        val lines = cacheFile.readLines()
        val duration = lines.first().split(FIELD_SEPARATOR)[1].toFloat()
        val blocks = sortedMapOf<Int, MutableList<PhonemeEvent>>()
        for (line in lines.drop(1)) {
            val fields = line.split(FIELD_SEPARATOR)
            val events = blocks.getOrPut(fields[0].toInt()) { mutableListOf() }
            if (fields.size >= 4) {
                events += PhonemeEvent(fields[1], fields[2].toFloat(), fields[3].toFloat())
            }
        }
        return PhonemeRecording(blocks.values.toList(), duration)
    }
}
