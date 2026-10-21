package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.TranslationId
import app.awn.quran.feature.read.page.AyahRange

/** The copy and share text of ayahs: "Arabic — translation (2:255)", one ayah per line. */
internal class AyahShareTexts(private val contentLoader: PageContentLoader) {
    /** [translation] holds the one translation to add, or nothing. */
    suspend fun of(
        range: AyahRange,
        translation: List<TranslationId>,
        chapters: List<Chapter>,
    ): String {
        val lines = mutableListOf<String>()
        var ayah: AyahRef? = range.first
        while (ayah != null && ayah in range) {
            val block = contentLoader.loadBlock(ayah, translation)
            lines += ayahShareText(block.arabic, block.translations.firstOrNull()?.text, ayah)
            ayah = adjacentAyah(ayah, 1, chapters)
        }
        return lines.joinToString("\n")
    }
}
