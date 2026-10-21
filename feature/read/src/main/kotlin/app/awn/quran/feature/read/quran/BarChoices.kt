package app.awn.quran.feature.read.quran

import androidx.compose.ui.unit.Dp
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.core.ui.theme.AwnSpacing

/**
 * The installed packs the layers menu lists as translations, ordered by language and then name. The
 * transliteration pack is left out: it has its own layer, and listing it twice made it look like
 * two choices. The pack is recognised by its id, as the catalogue does.
 */
internal fun layerTranslations(installed: List<TranslationInfo>): List<TranslationInfo> =
    installed
        .filterNot { info -> info.id.id.contains(TRANSLITERATION_ID_PART, ignoreCase = true) }
        .sortedWith(compareBy<TranslationInfo> { it.language }.thenBy { it.name })

private const val TRANSLITERATION_ID_PART = "transliteration"

/**
 * True when the top bar has no room for the settings button next to the others: the title keeps at
 * least [MIN_TITLE_WIDTH] so the page and juz numbers stay readable, and the button moves into the
 * overflow menu. [buttonCount] counts the buttons on the right, the index button is added here.
 */
internal fun shouldMoveSettingsToOverflow(availableWidth: Dp, buttonCount: Int): Boolean =
    availableWidth - AwnSpacing.touchTarget * (buttonCount + 1) < MIN_TITLE_WIDTH

private val MIN_TITLE_WIDTH = AwnSpacing.giant + AwnSpacing.large * 2
