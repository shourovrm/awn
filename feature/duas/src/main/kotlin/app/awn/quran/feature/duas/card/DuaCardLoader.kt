package app.awn.quran.feature.duas.card

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.ui.cardWords

/** What the cards are read for: which translation to show and in which language. */
data class CardRequest(
    /** The first translation ticked in the Quran screen's settings, or null when none is ticked. */
    val translationId: TranslationId?,
    /** The app language, "en" or "bn". */
    val language: String,
)

/** Reads the texts of dua cards from the Quran text and the installed translations. */
class DuaCardLoader(
    private val quranText: QuranTextRepository,
    private val translations: TranslationRepository,
) {

    /** Reads the cards of [duas]; the surah list is read once for all of them. */
    suspend fun load(duas: List<Dua>, request: CardRequest): List<DuaCardData> {
        val chapters = quranText.chapters().associateBy { it.number }
        return duas.map { dua ->
            val chapter = chapters.getValue(dua.start.surah)
            val surahName =
                if (request.language == BANGLA) chapter.nameBangla ?: chapter.nameSimple
                else chapter.nameSimple
            DuaCardData(
                dua = dua,
                words = cardWords(dua.ayahs.flatMap { quranText.wordsOfAyah(it) }, dua),
                reference = referenceOf(dua, surahName),
                translation = translationOf(dua, request.translationId),
            )
        }
    }

    /** The page that holds the dua's first ayah in [script]. */
    suspend fun pageOf(dua: Dua, script: ScriptId): Int =
        quranText.pageOf(dua.ayahs.first(), script)

    /** The tiles of the dua's own words, with meanings in [language] and English where blank. */
    suspend fun wordTiles(card: DuaCardData, language: String): List<DuaWordTile> {
        val duaWords = card.words.filter { it.isDuaWord }.map { it.word }
        val ayahs = card.dua.ayahs
        val meaningsByAyah = ayahs.associateWith { meaningsOf(it, language) }
        val soundsByAyah = ayahs.associateWith { translations.wordTransliteration(it) }
        return duaWords.map { word ->
            val ayah = AyahRef(word.key.surah, word.key.ayah)
            val index = word.key.wordPosition - 1
            DuaWordTile(
                key = word.key,
                arabic = word.textUthmani,
                meaning = meaningsByAyah.getValue(ayah).getOrNull(index).orEmpty(),
                transliteration = soundsByAyah.getValue(ayah).getOrNull(index).orEmpty(),
            )
        }
    }

    private fun referenceOf(dua: Dua, surahName: String): String {
        val ayahNumbers =
            if (dua.start.ayah == dua.end.ayah) {
                "${dua.start.ayah}"
            } else {
                "${dua.start.ayah}$EN_DASH${dua.end.ayah}"
            }
        return "$surahName ${dua.start.surah}:$ayahNumbers"
    }

    // A publisher that merges ayahs returns the same passage for each, so repeats are dropped.
    private suspend fun translationOf(dua: Dua, translationId: TranslationId?): String? {
        if (translationId == null) return null
        return dua.ayahs
            .mapNotNull { ayah -> translations.text(translationId, ayah)?.text }
            .distinct()
            .joinToString(separator = " ")
            .takeIf { it.isNotBlank() }
    }

    // A language with no word meanings on the phone, and a word that the pack leaves blank, show
    // the English meaning instead, as the Quran screen does.
    private suspend fun meaningsOf(ayah: AyahRef, language: String): List<String> {
        val wanted = translations.wordByWord(language, ayah)
        if (language == ENGLISH) return wanted
        val english = translations.wordByWord(ENGLISH, ayah)
        return List(maxOf(wanted.size, english.size)) { index ->
            wanted.getOrNull(index)?.takeIf { it.isNotBlank() } ?: english.getOrNull(index).orEmpty()
        }
    }

    private companion object {
        const val ENGLISH = "en"
        const val BANGLA = "bn"
        const val EN_DASH = "–"
    }
}
