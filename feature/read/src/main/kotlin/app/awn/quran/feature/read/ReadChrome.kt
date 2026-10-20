package app.awn.quran.feature.read

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import app.awn.quran.core.data.Chapter

private const val BANGLA_LANGUAGE = "bn"

/** The surah name in the app language: Bangla when the user reads in Bangla and the pack has it. */
@Composable
fun Chapter.localName(): String {
    val language = LocalConfiguration.current.locales[0].language
    return if (language == BANGLA_LANGUAGE) nameBangla ?: nameSimple else nameSimple
}
