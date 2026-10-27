package app.awn.quran.feature.duas.card

import app.awn.quran.core.data.TranslationId

/**
 * Picks the translation a dua card shows: the first ticked one in the app language, and when no
 * ticked translation is in that language, the first ticked one. Returns null when none is ticked.
 */
fun chooseCardTranslation(
    enabledTranslations: List<TranslationId>,
    appLanguage: String,
): TranslationId? =
    enabledTranslations.firstOrNull { languageOfTranslation(it) == appLanguage }
        ?: enabledTranslations.firstOrNull()

// Full-ayah translation ids start with the language code, such as `bn_zakaria` for Bangla. Word and
// transliteration packs (`wbw_bn`, `en_transliteration`) are not offered as card translations. The
// manifest's language field is not read here because reading it needs a suspending call.
private fun languageOfTranslation(translationId: TranslationId): String =
    translationId.id.substringBefore('_')
