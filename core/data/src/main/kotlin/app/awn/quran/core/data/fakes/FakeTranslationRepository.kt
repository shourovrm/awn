package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.TranslationInfo
import app.awn.quran.core.data.TranslationRepository
import app.awn.quran.core.data.TranslationText

/** In-memory translations for tests. A translation is installed when it has an entry in [texts]. */
class FakeTranslationRepository(
    val infos: MutableList<TranslationInfo> = mutableListOf(),
    val texts: MutableMap<TranslationId, MutableMap<AyahRef, TranslationText>> = mutableMapOf(),
    val wordMeanings: MutableMap<Pair<String, AyahRef>, List<String>> = mutableMapOf(),
    val wordTransliterations: MutableMap<AyahRef, List<String>> = mutableMapOf(),
) : TranslationRepository {

    override suspend fun available(): List<TranslationInfo> = infos.toList()

    override suspend fun text(id: TranslationId, ayah: AyahRef): TranslationText? =
        texts[id]?.get(ayah)

    override suspend fun wordByWord(language: String, ayah: AyahRef): List<String> =
        wordMeanings[language to ayah].orEmpty()

    override suspend fun wordTransliteration(ayah: AyahRef): List<String> =
        wordTransliterations[ayah].orEmpty()
}
