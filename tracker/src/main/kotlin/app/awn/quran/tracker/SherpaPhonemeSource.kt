package app.awn.quran.tracker

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig

/**
 * Runs the streaming Zipformer CTC model through sherpa-onnx.
 *
 * Endpointing is off because the default rule cuts the stream after 20 seconds, which is shorter
 * than a long ayah. sherpa-onnx returns the whole token list for the stream on every call, so the
 * source remembers how many tokens it already reported.
 */
class SherpaPhonemeSource(modelPath: String, tokensPath: String, numThreads: Int = 2) :
    PhonemeSource {

    private val recognizer: OnlineRecognizer
    private val stream: OnlineStream
    private var reportedTokenCount = 0

    init {
        val modelConfig =
            OnlineModelConfig(
                zipformer2Ctc = OnlineZipformer2CtcModelConfig(model = modelPath),
                tokens = tokensPath,
                numThreads = numThreads,
            )
        val config =
            OnlineRecognizerConfig(
                featConfig =
                    FeatureConfig(
                        sampleRate = TrackerInput.SAMPLE_RATE_HZ,
                        featureDim = FEATURE_DIMENSION,
                    ),
                modelConfig = modelConfig,
                decodingMethod = "greedy_search",
                enableEndpoint = false,
            )
        recognizer = OnlineRecognizer(config = config)
        stream = recognizer.createStream()
    }

    override fun accept(block: FloatArray): List<PhonemeEvent> {
        stream.acceptWaveform(block, TrackerInput.SAMPLE_RATE_HZ)
        decodeReadyFrames()
        return newEvents()
    }

    override fun finish(): List<PhonemeEvent> {
        stream.inputFinished()
        decodeReadyFrames()
        return newEvents()
    }

    override fun close() {
        stream.release()
        recognizer.release()
    }

    private fun decodeReadyFrames() {
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }
    }

    private fun newEvents(): List<PhonemeEvent> {
        val result = recognizer.getResult(stream)
        val tokens = result.tokens
        val events = mutableListOf<PhonemeEvent>()
        for (index in reportedTokenCount until tokens.size) {
            events +=
                PhonemeEvent(
                    symbol = tokens[index],
                    onsetSeconds = result.timestamps[index],
                    probability = result.ysProbs.getOrElse(index) { UNKNOWN_PROBABILITY },
                )
        }
        reportedTokenCount = tokens.size
        return events
    }

    private companion object {
        const val FEATURE_DIMENSION = 80

        // sherpa-onnx 1.13.8 leaves ysProbs empty for CTC greedy search, so no probability exists.
        const val UNKNOWN_PROBABILITY = -1f
    }
}
