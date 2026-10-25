package app.awn.quran.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Reciting habits on real audio (Husary through the model): saying an ayah twice, restarting an
 * ayah, going back, jumping ahead, a noise between ayahs, and a slip before a phrase that comes
 * again later on the page. None of them may turn a recited word red or colour more than two words
 * red at once, and only the real jumps may move the cursor through the whole text.
 */
class ScenarioFixtureTest {

    private val reference by lazy { JsonReferencePhonemes.fromFile(TestPaths.referenceFile) }

    private fun ayahs(surah: Int, vararg numbers: Int) = numbers.map {
        ScenarioRecordings.ayah(surah, it)
    }

    private fun run(name: String, surah: Int, pieces: List<FloatArray>): ScenarioRun {
        val words = SurahReference(surah, reference).words
        val recording = RecordingDecoder.decode(ScenarioRecordings.build(name, pieces))
        val run = ScenarioRun.replay(words, recording) { AlignmentTracker() }
        println("SCENARIO $name: ${run.summary()}")
        return run
    }

    @Test
    fun anAyahSaidTwiceIsARepeatWithNoRedAndNoJump() {
        assumeTrue(TestPaths.fixturesAvailable())
        val runs =
            listOf(
                run("repeat_s1_a2", 1, ayahs(1, 1, 2, 2, 3, 4, 5, 6, 7)),
                run("repeat_s112_a3", 112, ayahs(112, 1, 2, 3, 3, 4)),
            )
        for (run in runs) {
            assertTrue(run.summary(), run.events.any { it is TrackerEvent.Repeated })
            assertEquals(run.summary(), 0, run.wordsEverRed)
            assertEquals(run.summary(), 0, run.jumps)
        }
    }

    @Test
    fun aRestartedAyahTurnsNothingRed() {
        assumeTrue(TestPaths.fixturesAvailable())
        val runs =
            listOf(
                run(
                    "restart_s1_a2",
                    1,
                    ayahs(1, 1) +
                        ScenarioRecordings.startOfAyah(1, 2, RESTART_SHARE) +
                        ayahs(1, 2, 3, 4, 5, 6, 7),
                ),
                run(
                    "restart_s114_a5",
                    114,
                    ayahs(114, 1, 2, 3, 4) +
                        ScenarioRecordings.startOfAyah(114, 5, RESTART_SHARE) +
                        ayahs(114, 5, 6),
                ),
            )
        for (run in runs) {
            assertEquals(run.summary(), 0, run.wordsEverRed)
            assertEquals(run.summary(), 0, run.jumps)
        }
    }

    @Test
    fun goingBackKeepsEveryMark() {
        assumeTrue(TestPaths.fixturesAvailable())
        val run = run("back_s112_a3_to_1", 112, ayahs(112, 1, 2, 3, 1, 2, 3, 4))

        assertTrue(run.summary(), run.events.any { it is TrackerEvent.Anchored })
        assertEquals(run.summary(), 0, run.wordsEverRed)
        assertEquals(run.summary(), 0, run.finalStates.count { it == WordState.Unread })
        assertEquals(run.summary(), run.finalStates.size, run.greenWords)
    }

    @Test
    fun jumpingAheadLeavesUnreadWordsAndNoRed() {
        assumeTrue(TestPaths.fixturesAvailable())
        val run = run("forward_s1_a1_to_4", 1, ayahs(1, 1, 4, 5, 6, 7))

        assertEquals(run.summary(), 1, run.events.count { it is TrackerEvent.JumpedAhead })
        assertEquals(run.summary(), 0, run.wordsEverRed)
        assertTrue(run.summary(), run.finalStates.any { it == WordState.Unread })
    }

    @Test
    fun aNoiseBetweenAyahsMovesNothing() {
        assumeTrue(TestPaths.fixturesAvailable())
        val cough = NoiseSupport.white(NOISE_SECONDS, NOISE_RMS)
        val clicks = NoiseSupport.clicks(NOISE_SECONDS * 2, CLICK_PEAK)
        val pieces =
            ayahs(1, 1) + cough + ayahs(1, 2) + clicks + ayahs(1, 3) + cough + ayahs(1, 4, 5, 6, 7)
        val run = run("noise_s1", 1, pieces)

        assertEquals(run.summary(), 0, run.wordsEverRed)
        assertEquals(run.summary(), 0, run.jumps)
        assertEquals(run.summary(), run.finalStates.size, run.greenWords)
    }

    @Test
    fun aSlipBeforeAPhraseThatComesAgainDoesNotJump() {
        assumeTrue(TestPaths.fixturesAvailable())
        // 2:12 and 2:13 both begin with "ala innahum hum". The start of 2:12 is replaced by the
        // start of another surah, so the user's 2:12 opens with a slip.
        val slipped =
            ScenarioRecordings.foreignWords(113, 1) +
                ScenarioRecordings.restOfAyah(2, 12, SLIP_SHARE)
        val pieces =
            ayahs(2, *(1..11).toList().toIntArray()) +
                slipped +
                ayahs(2, *(13..20).toList().toIntArray())
        val run = run("trap_s2_a12", 2, pieces)
        val ayah13 = SurahReference(2, reference).wordIndices(13)

        assertEquals(run.summary(), 0, run.jumps)
        assertEquals(run.summary(), 0, run.pageWideRedUpdates)
        assertTrue(run.summary(), ayah13.none { run.finalStates[it] == WordState.Wrong })
    }

    private companion object {
        const val RESTART_SHARE = 0.4f
        const val SLIP_SHARE = 0.25f
        const val NOISE_SECONDS = 0.5f
        const val NOISE_RMS = 0.05f
        const val CLICK_PEAK = 0.8f
    }
}

/** What a tracker did with one recording, update by update. */
class ScenarioRun(val updates: List<List<TrackerEvent>>, val finalStates: List<WordState>) {
    val events: List<TrackerEvent>
        get() = updates.flatten()

    /** Updates that coloured more than two words red at once. */
    val pageWideRedUpdates: Int
        get() = updates.count { update -> update.count { it.isRed() } > 2 }

    /** Words that were red at some moment, even if corrected later. */
    val wordsEverRed: Int
        get() =
            events.filter { it.isRed() }.map { (it as TrackerEvent.WordChanged).index }.toSet().size

    /** Moves of the cursor through the whole text: forward jumps and re-anchoring. */
    val jumps: Int
        get() = events.count { it is TrackerEvent.JumpedAhead || it is TrackerEvent.Anchored }

    val greenWords: Int
        get() = finalStates.count { it == WordState.Confirmed }

    fun summary(): String =
        "green $greenWords of ${finalStates.size}, ever red $wordsEverRed, " +
            "page-wide red updates $pageWideRedUpdates, jumps $jumps, " +
            "repeats ${events.count { it is TrackerEvent.Repeated }}, " +
            "unread ${finalStates.count { it == WordState.Unread }}"

    private fun TrackerEvent.isRed() = this is TrackerEvent.WordChanged && state == WordState.Wrong

    companion object {
        fun replay(
            words: List<ReferenceWord>,
            recording: PhonemeRecording,
            newTracker: () -> Tracker,
        ): ScenarioRun {
            val tracker = newTracker()
            tracker.load(words, 0)
            val updates = recording.eventsPerBlock.map { tracker.onPhonemes(it) }
            return ScenarioRun(updates, words.indices.map { tracker.stateOfWord(it) })
        }
    }
}
