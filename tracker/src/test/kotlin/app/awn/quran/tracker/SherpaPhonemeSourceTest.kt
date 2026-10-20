package app.awn.quran.tracker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SherpaPhonemeSourceTest {

    private val modelsDirectory = File(System.getProperty("awn.models.directory"))
    private val modelFile = File(modelsDirectory, "zipformer_p_arabic_v3.int8.onnx")
    private val tokensFile = File(modelsDirectory, "tokens.txt")

    @Test
    fun recognizerInitialisesAndHearsNothingInSilence() {
        assumeTrue(
            "Skipped: model files not found in $modelsDirectory. Run ./gradlew fetchModels.",
            modelFile.isFile && tokensFile.isFile,
        )
        val twoSecondsOfSilence = FloatArray(2 * TrackerInput.SAMPLE_RATE_HZ)
        val heard = mutableListOf<PhonemeEvent>()

        SherpaPhonemeSource(modelFile.path, tokensFile.path).use { source ->
            twoSecondsOfSilence.toList().chunked(BLOCK_SAMPLES).forEach { block ->
                heard += source.accept(block.toFloatArray())
            }
            heard += source.finish()
        }

        val nonBlank = heard.filter { it.symbol.isNotBlank() && it.symbol != BLANK_SYMBOL }
        assertTrue("Silence produced phonemes: $nonBlank", nonBlank.isEmpty())
    }

    private companion object {
        // 100 ms blocks, the size the microphone input will use.
        const val BLOCK_SAMPLES = TrackerInput.SAMPLE_RATE_HZ / 10
        const val BLANK_SYMBOL = "<blank>"
    }
}
