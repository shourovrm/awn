package app.awn.quran.core.ui.theme

import android.content.res.AssetManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

private const val HAFS_ASSET_PATH = "packs/UthmanicHafs1Ver18.ttf"

// A resource font only stores its id, so it can be built without Android; file and asset fonts
// load a typeface as soon as they are created.
private const val NOT_YET_INSTALLED_RESOURCE = 0

/**
 * The KFGQPC Uthmanic Hafs font that ships in the APK for the Mushaf fallback, used for Arabic text
 * outside the page as well.
 *
 * Compose can only load an asset through an [AssetManager], which does not exist when
 * [AwnTypography] is built (a plain JVM test builds it too). Until [install] runs the family holds
 * a resource font with no real resource that is never drawn; the theme calls [install] before any
 * text is composed, so every text style reads the real family.
 */
internal object HafsFont {
    private val notYetInstalled = FontFamily(Font(resId = NOT_YET_INSTALLED_RESOURCE))

    @Volatile private var installed: FontFamily? = null

    val family: FontFamily
        get() = installed ?: notYetInstalled

    /** Builds the family from the app's assets once; later calls keep the first one. */
    fun install(assetManager: AssetManager) {
        if (installed != null) return
        synchronized(this) {
            if (installed == null) installed = FontFamily(Font(HAFS_ASSET_PATH, assetManager))
        }
    }
}
