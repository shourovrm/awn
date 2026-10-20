package app.awn.quran.tracker

import java.io.File

/** One lengthened vowel measured in an ayah recording, with where it sits in the audio. */
data class MeasuredMadd(
    val wordIndex: Int,
    val rule: String,
    val check: DurationCheck,
    val startSeconds: Float,
    val endSeconds: Float,
)

/**
 * Builds recordings in which a single lengthened vowel is slowed down, because slowing a whole
 * recitation changes the reciter's short vowels by the same factor and so cannot change the verdict
 * of a check that compares the two.
 */
object MaddStretcher {
    private const val ONSET_TOLERANCE_SECONDS = 0.005f
    private const val MINIMUM_SINGLE_TEMPO = 0.5f

    // A token's onset lies about 0.12 s after the sound in the file: fitting the onsets of ayah 1:1
    // at tempo 1.0 and 0.8 to onset = lag + (original - lag) / tempo gives a lag of 0.12 s.
    private const val ONSET_LAG_SECONDS = 0.12f

    /** Runs one ayah recording through the model and the tracker and measures its madds. */
    fun measure(
        ayahFile: File,
        surah: Int,
        ayah: Int,
        reference: ReferencePhonemes,
    ): List<MeasuredMadd> {
        val recording = RecordingDecoder.decode(ayahFile)
        val tracker = AlignmentTracker()
        tracker.load(reference.forAyah(surah, ayah))
        val checks = mutableListOf<DurationCheck>()
        for (events in recording.eventsPerBlock) {
            for (event in tracker.onPhonemes(events)) {
                if (event is TrackerEvent.Duration) checks += event.check
            }
        }
        val tokens = recording.allEvents
        return checks.mapNotNull { check -> locate(check, tokens) }
    }

    // The tracker reports no onset, so find the token that starts the measured gap.
    private fun locate(check: DurationCheck, tokens: List<PhonemeEvent>): MeasuredMadd? {
        for (index in 0 until tokens.size - 1) {
            val token = tokens[index]
            val gap = tokens[index + 1].onsetSeconds - token.onsetSeconds
            val isSameGap = kotlin.math.abs(gap - check.measuredSeconds) < ONSET_TOLERANCE_SECONDS
            if (isSameGap && token.symbol.contains(check.letter)) {
                return MeasuredMadd(
                    check.wordIndex,
                    check.rule,
                    check,
                    token.onsetSeconds,
                    tokens[index + 1].onsetSeconds,
                )
            }
        }
        return null
    }

    // ffmpeg's atempo accepts 0.5 to 100 per instance, so slower speeds are chained.
    private fun tempoFilter(slowdown: Float): String {
        val tempo = 1f / slowdown
        if (tempo >= MINIMUM_SINGLE_TEMPO) return "atempo=$tempo"
        return "atempo=$MINIMUM_SINGLE_TEMPO,atempo=${tempo / MINIMUM_SINGLE_TEMPO}"
    }

    /** Writes [source] with the stretch of [madd] played [slowdown] times slower. */
    fun slowDown(source: File, madd: MeasuredMadd, slowdown: Float, target: File) {
        if (target.exists()) return
        target.parentFile.mkdirs()
        val start = madd.startSeconds - ONSET_LAG_SECONDS
        val end = madd.endSeconds - ONSET_LAG_SECONDS
        val filter =
            "[0:a]atrim=0:$start,asetpts=PTS-STARTPTS[head];" +
                "[0:a]atrim=$start:$end,asetpts=PTS-STARTPTS,${tempoFilter(slowdown)}[held];" +
                "[0:a]atrim=$end,asetpts=PTS-STARTPTS[tail];" +
                "[head][held][tail]concat=n=3:v=0:a=1"
        val command =
            listOf(
                "/usr/bin/ffmpeg",
                "-y",
                "-i",
                source.path,
                "-filter_complex",
                filter,
                "-ac",
                "1",
                "-ar",
                "16000",
                target.path,
            )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "ffmpeg failed: $output" }
    }
}
