package app.awn.quran.tracker

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig

/**
 * Loads the streaming Zipformer CTC model through sherpa-onnx once, so that every recitation can
 * open a [PhonemeSource] without paying the load again (about 5 s on the phone, which was the cost
 * of every Recite start). Close it to free the model.
 *
 * Endpointing is off because the default rule cuts the stream after 20 seconds, which is shorter
 * than a long ayah.
 */
class SherpaRecognizer(modelPath: String, tokensPath: String, numThreads: Int = 2) : AutoCloseable {

    internal val recognizer: OnlineRecognizer

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
    }

    /** Opens one recitation. Closing the source leaves this recognizer loaded for the next one. */
    fun newSource(): PhonemeSource = SherpaPhonemeSource(recognizer, ownsRecognizer = false)

    override fun close() = recognizer.release()

    internal companion object {
        const val FEATURE_DIMENSION = 80
    }
}

/**
 * Runs the streaming Zipformer CTC model through sherpa-onnx for one recitation. sherpa-onnx
 * returns the whole token list for the stream on every call, so the source remembers how many
 * tokens it already reported.
 *
 * This constructor loads the model and frees it on [close]; [SherpaRecognizer.newSource] shares a
 * loaded model instead.
 */
class SherpaPhonemeSource
internal constructor(
    private val recognizer: OnlineRecognizer,
    private val ownsRecognizer: Boolean,
) : PhonemeSource {

    constructor(
        modelPath: String,
        tokensPath: String,
        numThreads: Int = 2,
    ) : this(SherpaRecognizer(modelPath, tokensPath, numThreads).recognizer, ownsRecognizer = true)

    private val stream: OnlineStream = recognizer.createStream()
    private var reportedTokenCount = 0

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
        if (ownsRecognizer) recognizer.release()
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
        // sherpa-onnx 1.13.8 leaves ysProbs empty for CTC greedy search, so no probability exists.
        const val UNKNOWN_PROBABILITY = -1f
    }
}
