package app.awn.quran.feature.learn.content

/** The two hadith collections the snippets are taken from. */
enum class HadithCollection(
    val key: String,
    val englishName: String,
    val banglaName: String,
) {
    Bukhari("bukhari", "Sahih al-Bukhari", "সহীহ বুখারী"),
    Muslim("muslim", "Sahih Muslim", "সহীহ মুসলিম");

    companion object {
        fun fromKey(key: String): HadithCollection =
            entries.firstOrNull { it.key == key }
                ?: error("Unknown hadith collection '$key' in snippets.json.")
    }
}

/** Where a snippet comes from, kept as data so each language can write the reference its way. */
sealed interface SnippetSource {
    data class Ayah(val surah: Int, val ayah: Int) : SnippetSource

    /** [number] is text because Sahih Muslim numbers carry a letter, as in "804a". */
    data class Hadith(val collection: HadithCollection, val number: String) : SnippetSource
}

/**
 * The reference line of a snippet in [language]: "Quran 73:4" or "Sahih al-Bukhari 5027" in
 * English, "সূরা আল-মুযযাম্মিল, ৭৩:৪" or "সহীহ বুখারী, হাদিস ৫০২৭" in Bangla. [surahName] gives the
 * Bangla name of a surah and is only called for an ayah in Bangla.
 */
fun snippetReference(
    source: SnippetSource,
    language: LearnLanguage,
    surahName: (surah: Int) -> String,
): String =
    when (source) {
        is SnippetSource.Ayah -> ayahReference(source, language, surahName)
        is SnippetSource.Hadith -> hadithReference(source, language)
    }

private fun ayahReference(
    source: SnippetSource.Ayah,
    language: LearnLanguage,
    surahName: (surah: Int) -> String,
): String =
    when (language) {
        LearnLanguage.English -> "Quran ${source.surah}:${source.ayah}"
        LearnLanguage.Bangla ->
            "সূরা ${surahName(source.surah)}, " + toBanglaDigits("${source.surah}:${source.ayah}")
    }

private fun hadithReference(source: SnippetSource.Hadith, language: LearnLanguage): String =
    when (language) {
        LearnLanguage.English -> "${source.collection.englishName} ${source.number}"
        // The letter after a Sahih Muslim number marks a sub-report on sunnah.com; Bangla
        // editions number whole reports, so only the digits are shown.
        LearnLanguage.Bangla ->
            "${source.collection.banglaName}, হাদিস " +
                toBanglaDigits(source.number.filter(Char::isDigit))
    }

private const val BANGLA_ZERO = '০'

private fun toBanglaDigits(text: String): String =
    text
        .map { character ->
            if (character in '0'..'9') BANGLA_ZERO + (character - '0') else character
        }
        .joinToString("")
