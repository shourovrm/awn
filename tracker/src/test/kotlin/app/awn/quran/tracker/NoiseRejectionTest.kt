package app.awn.quran.tracker

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Silence and room sounds go through the real model and the tracker; no word of Al-Fatihah may take
 * a state. Measured 2026-10-21 over 26 sounds (white, pink, brown, hum and syllable-rate noise at
 * four levels, clicks, English speech): the model emitted no phoneme except one for loud clicks, so
 * the model is quiet on these sounds and the stray phonemes of real rooms are covered by
 * [StrayPhonemeTest].
 */
class NoiseRejectionTest {

    private fun colouredWordsAfter(samples: FloatArray): Int {
        val surah = SurahReference(1, JsonReferencePhonemes.fromFile(TestPaths.referenceFile))
        val tracker = AlignmentTracker(TrackerConfig())
        tracker.load(surah.words, 0)
        val trailingSilence = List(TRAILING_BLOCKS) { FloatArray(BLOCK_SAMPLES) }
        SherpaPhonemeSource(TestPaths.modelFile.path, TestPaths.tokensFile.path).use { source ->
            for (block in NoiseSupport.toBlocks(samples) + trailingSilence) {
                tracker.onPhonemes(source.accept(block))
            }
            tracker.onPhonemes(source.finish())
        }
        return surah.words.indices.count { tracker.stateOfWord(it) != WordState.Neutral }
    }

    @Test
    fun silenceColoursNothing() {
        assumeTrue(TestPaths.fixturesAvailable())
        assertEquals(0, colouredWordsAfter(NoiseSupport.silence(SECONDS)))
    }

    @Test
    fun steadyRoomNoiseColoursNothing() {
        assumeTrue(TestPaths.fixturesAvailable())
        val sounds =
            mapOf(
                "white" to NoiseSupport.white(SECONDS, 0.03f),
                "pink" to NoiseSupport.pink(SECONDS, 0.03f),
                "brown" to NoiseSupport.brown(SECONDS, 0.1f),
                "hum" to NoiseSupport.hum(SECONDS, 0.1f),
                "syllable" to NoiseSupport.syllableNoise(SECONDS, 0.1f),
                "clicks" to NoiseSupport.clicks(SECONDS, 0.8f),
            )
        for ((name, samples) in sounds) assertEquals(name, 0, colouredWordsAfter(samples))
    }

    @Test
    fun recordedSpeechOfAnotherLanguageColoursNothing() {
        val speech = NoiseSupport.recorded("talk16k.wav")
        assumeTrue(TestPaths.fixturesAvailable() && speech != null)
        assertEquals(0, colouredWordsAfter(checkNotNull(speech)))
    }

    private companion object {
        const val SECONDS = 20f
        const val TRAILING_BLOCKS = 10
        const val BLOCK_SAMPLES = TrackerInput.SAMPLE_RATE_HZ / 10
    }
}
