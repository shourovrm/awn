package app.awn.quran.services

import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/** The files the speech recogniser reads from disk. */
data class ModelPaths(val model: File, val tokens: File, val referencePhonemes: File)

/**
 * Copies the speech model, its token list and the reference phonemes from the APK assets to
 * [targetDirectory]. sherpa-onnx opens its model by file path, so the files cannot stay inside the
 * APK. A file that already exists is kept: the model file name carries its version, so a new model
 * arrives under a new name.
 */
class ModelFiles(private val assets: AssetManager, private val targetDirectory: File) {

    private val lock = Any()

    /** Returns the paths, copying any file that is missing. Blocks while a copy runs. */
    fun ensureInstalled(): ModelPaths =
        synchronized(lock) {
            try {
                ModelPaths(
                    model = install(MODEL_FILE_NAME),
                    tokens = install(TOKENS_FILE_NAME),
                    referencePhonemes = install(REFERENCE_PHONEMES_FILE_NAME),
                )
            } catch (problem: IOException) {
                // The recitation session handles IllegalStateException by showing its error line.
                throw IllegalStateException(
                    "Could not copy the speech files out of the APK.",
                    problem,
                )
            }
        }

    private fun install(fileName: String): File {
        val target = File(targetDirectory, fileName)
        if (target.isFile) return target
        targetDirectory.mkdirs()
        val partial = File(targetDirectory, "$fileName.part")
        assets.open("$ASSET_FOLDER/$fileName").use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        if (!partial.renameTo(target)) {
            throw IOException("Could not rename ${partial.path} to ${target.path}.")
        }
        return target
    }

    companion object {
        const val ASSET_FOLDER = "models"
        const val MODEL_FILE_NAME = "zipformer_p_arabic_v3.int8.onnx"
        const val TOKENS_FILE_NAME = "tokens.txt"
        const val REFERENCE_PHONEMES_FILE_NAME = "ordered_quran_phonemes.json"
    }
}
