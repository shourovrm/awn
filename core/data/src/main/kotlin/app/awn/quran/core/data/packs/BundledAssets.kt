package app.awn.quran.core.data.packs

import android.content.res.AssetManager
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Read access to the files that ship inside the APK, addressed by their flat file name. */
interface BundledAssets {

    /** Returns true when a bundled file called [fileName] exists. */
    fun exists(fileName: String): Boolean

    /** Opens the bundled file; throws [FileNotFoundException] naming it when it is missing. */
    fun open(fileName: String): InputStream
}

/** The `assets/packs/` folder that `:core:data:fetchBundledPacks` fills at build time. */
class AndroidBundledAssets(private val assetManager: AssetManager) : BundledAssets {

    @Suppress("SwallowedException") // A missing asset is the answer "false", not an error.
    override fun exists(fileName: String): Boolean =
        try {
            assetManager.open("$FOLDER/$fileName").close()
            true
        } catch (_: FileNotFoundException) {
            false
        }

    override fun open(fileName: String): InputStream =
        try {
            assetManager.open("$FOLDER/$fileName")
        } catch (error: IOException) {
            throw FileNotFoundException(
                    "The bundled file $fileName is missing from assets/$FOLDER; run " +
                        ":core:data:fetchBundledPacks. (${error.message})",
                )
                .apply { initCause(error) }
        }

    private companion object {
        const val FOLDER = "packs"
    }
}
