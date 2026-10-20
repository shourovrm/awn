package app.awn.quran.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class MaddMeasurementTest {

    private val fatha = "َ"
    private val kasra = "ِ"
    private val damma = "ُ"

    // Eight words of a consonant and a short vowel each, then one word with a lengthened alif.
    private val consonants = "بتثجحخدذ"
    private val reference =
        consonants.mapIndexed { index, consonant ->
            ReferenceWord(
                WordKeyLike(1, 1, index + 1),
                listOf(consonant.toString(), fatha, consonant.toString(), kasra),
            )
        } +
            ReferenceWord(
                WordKeyLike(1, 1, consonants.length + 1),
                listOf("س", fatha, "ا", "ا", "ب", fatha),
                madds = listOf(MaddAnnotation("ا", "natural", 2, phonemeIndex = 2)),
            ) +
            ReferenceWord(WordKeyLike(1, 1, consonants.length + 2), listOf("ر", kasra, "ر", damma))

    private fun checkOfVowelHeldWithStrayToken(strayToken: Boolean): DurationCheck {
        val tracker = AlignmentTracker()
        tracker.load(reference)
        var clock = 0f
        val heard = mutableListOf<PhonemeEvent>()
        fun say(symbol: String, step: Float = SHORT_STEP) {
            heard += PhonemeEvent(symbol, clock, -1f)
            clock += step
        }
        for (consonant in consonants) {
            say("$consonant$fatha")
            say("$consonant$kasra")
        }
        say("س$fatha")
        say("اا", step = if (strayToken) 0.04f else HELD_STEP)
        if (strayToken) say("ي", step = HELD_STEP - 0.04f)
        say("ب$fatha")
        say("ر$kasra")
        say("ر$damma")
        val events = tracker.onPhonemes(heard)
        return events.filterIsInstance<TrackerEvent.Duration>().single().check
    }

    @Test
    fun aHeldVowelIsMeasuredToTheNextMatchedTokenWhenAStrayTokenSitsInsideIt() {
        val withStray = checkOfVowelHeldWithStrayToken(strayToken = true)
        val clean = checkOfVowelHeldWithStrayToken(strayToken = false)

        assertEquals(clean.measuredSeconds, withStray.measuredSeconds, 0.001f)
        assertEquals(DurationVerdict.Overheld, withStray.verdict)
    }

    private companion object {
        const val SHORT_STEP = 0.2f
        const val HELD_STEP = 0.5f
    }
}
