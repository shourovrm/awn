package app.awn.quran.core.data.search

import app.awn.quran.core.data.AyahRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typed Arabic (the keyboard spelling, no vowel signs) against the Uthmani spelling of the Quran
 * text.
 *
 * Every Uthmani string below is copied from `quran.sqlite` `word.text_uthmani` (surah:ayah:position
 * in the comment), so the test uses the text the app searches and not a hand-typed imitation of it.
 */
class UthmaniSpellingSearchTest {

    private class TypedWord(val typed: String, val uthmani: String)

    private val typedWords =
        listOf(
            TypedWord("الصلاة", "ٱلصَّلَوٰةَ"), // 2:3:5
            TypedWord("الصلوة", "ٱلصَّلَوٰةَ"), // 2:3:5
            TypedWord("ابراهيم", "إِبْرَٰهِـۧمَ"), // 2:124:3
            TypedWord("ابراهيم", "إِبْرَٰهِيمَ"), // 3:33:7
            TypedWord("إبراهيم", "إِبْرَٰهِيمَ"), // 3:33:7
            TypedWord("إبرٰهيم", "إِبْرَٰهِيمَ"), // 3:33:7
            TypedWord("الزكاة", "ٱلزَّكَوٰةَ"), // 2:43:4
            TypedWord("الحياة", "ٱلْحَيَوٰةِ"), // 2:85:37
            TypedWord("الرحمن", "ٱلرَّحْمَـٰنِ"), // 1:1:3
            TypedWord("الرحمان", "ٱلرَّحْمَـٰنِ"), // 1:1:3
            TypedWord("العالمين", "ٱلْعَـٰلَمِينَ"), // 1:2:4
            TypedWord("اسماعيل", "إِسْمَـٰعِيلَ"), // 14:39:8
            TypedWord("اسحاق", "إِسْحَـٰقَ"), // 6:84:3
            TypedWord("موسى", "مُوسَىٰٓ"), // 2:51:3
            TypedWord("موسي", "مُوسَىٰ"), // 2:54:3
            TypedWord("عيسى", "عِيسَىٰ"), // 3:52:3
            TypedWord("هذا", "هَـٰذَا"), // 2:25:20
            TypedWord("هاذا", "هَـٰذَا"), // 2:25:20
            TypedWord("ذلك", "ذَٰلِكَ"), // 2:2:1
            TypedWord("الكتاب", "ٱلْكِتَـٰبُ"), // 2:2:2
            TypedWord("امنوا", "ءَامَنُوا۟"), // 2:9:4
            TypedWord("آمنوا", "ءَامَنُوا۟"), // 2:9:4
            TypedWord("السماء", "ٱلسَّمَآءِ"), // 2:19:4
            TypedWord("السماوات", "ٱلسَّمَـٰوَٰتِ"), // 2:33:15
            TypedWord("الانسان", "ٱلْإِنسَـٰنُ"), // 4:28:7
            TypedWord("القرآن", "ٱلْقُرْءَانُ"), // 2:185:6
            TypedWord("القران", "ٱلْقُرْءَانُ"), // 2:185:6
            TypedWord("يسألونك", "يَسْـَٔلُونَكَ"), // 2:215:1
            TypedWord("النبيين", "ٱلنَّبِيِّـۧنَ"), // 2:61:52
            TypedWord("رؤوف", "رَءُوفٌۢ"), // 2:207:10
            TypedWord("رءوف", "رَءُوفٌۢ"), // 2:207:10
            TypedWord("شيء", "شَىْءٍۢ"), // 2:20:24
            TypedWord("التوراة", "ٱلتَّوْرَىٰةَ"), // 3:3:10
            TypedWord("داود", "دَاوُۥدُ"), // 2:251:5
            TypedWord("سليمان", "سُلَيْمَـٰنُ"), // 2:102:10
            TypedWord("لكن", "لَـٰكِنِ"), // 3:198:1
            TypedWord("اولئك", "أُو۟لَـٰٓئِكَ"), // 2:5:1
            TypedWord("القيامة", "ٱلْقِيَـٰمَةِ"), // 2:85:40
            TypedWord("الاخرة", "ٱلْـَٔاخِرَةُ"), // 2:94:6
            TypedWord("الملائكة", "ٱلْمَلَـٰٓئِكَةُ"), // 2:248:22
            TypedWord("آدم", "ءَادَمَ"), // 2:31:2
            TypedWord("الله", "ٱللَّهِ"), // 1:1:2
            TypedWord("مشكاة", "كَمِشْكَوٰةٍۢ"), // 24:35:7
            TypedWord("الربا", "ٱلرِّبَوٰا۟"), // 2:275:3
        )

    private class TypedPhrase(val typed: String, val uthmaniAyah: String)

    private val typedPhrases =
        listOf(
            TypedPhrase(
                "يا ايها الذين امنوا",
                "يَـٰٓأَيُّهَا ٱلَّذِينَ ءَامَنُوا۟ لَا تَقُولُوا۟ رَٰعِنَا وَقُولُوا۟ ٱنظُرْنَا وَٱسْمَعُوا۟ ۗ " +
                    "وَلِلْكَـٰفِرِينَ عَذَابٌ أَلِيمٌ",
            ), // 2:104
            TypedPhrase(
                "يأيها الذين آمنوا",
                "يَـٰٓأَيُّهَا ٱلَّذِينَ ءَامَنُوا۟ لَا تَقُولُوا۟ رَٰعِنَا وَقُولُوا۟ ٱنظُرْنَا وَٱسْمَعُوا۟ ۗ " +
                    "وَلِلْكَـٰفِرِينَ عَذَابٌ أَلِيمٌ",
            ), // 2:104
            TypedPhrase(
                "يا بني اسرائيل",
                "يَـٰبَنِىٓ إِسْرَٰٓءِيلَ ٱذْكُرُوا۟ نِعْمَتِىَ ٱلَّتِىٓ أَنْعَمْتُ عَلَيْكُمْ " +
                    "وَأَوْفُوا۟ بِعَهْدِىٓ أُوفِ بِعَهْدِكُمْ وَإِيَّـٰىَ فَٱرْهَبُونِ",
            ), // 2:40
            TypedPhrase(
                "يا قوم",
                "وَإِذْ قَالَ مُوسَىٰ لِقَوْمِهِۦ يَـٰقَوْمِ إِنَّكُمْ ظَلَمْتُمْ أَنفُسَكُم بِٱتِّخَاذِكُمُ ٱلْعِجْلَ",
            ), // 2:54
            TypedPhrase(
                "لا اله الا هو",
                "ٱللَّهُ لَآ إِلَـٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ ۚ لَا تَأْخُذُهُۥ سِنَةٌ وَلَا نَوْمٌ ۚ",
            ), // 2:255
        )

    private fun searchOver(vararg arabicTexts: String): TextSearch {
        val ayahs = arabicTexts.indices.map { AyahRef(1, it + 1) }
        val texts = SearchableTexts(SearchSource.Arabic, ayahs, arabicTexts.toList())
        return TextSearch(loadTexts = { listOf(texts) })
    }

    @Test
    fun atLeastTwentyTypedWordsAreChecked() {
        assertTrue(typedWords.map { it.typed }.toSet().size >= 20)
    }

    @Test
    fun everyTypedWordFindsItsUthmaniSpelling() = runBlocking {
        for (word in typedWords) {
            val hits = searchOver(word.uthmani).search(word.typed).hits

            assertEquals("'${word.typed}' should find '${word.uthmani}'", 1, hits.size)
        }
    }

    @Test
    fun theMatchedRangeLiesInsideTheUthmaniWordAndIsNotEmpty() = runBlocking {
        for (word in typedWords) {
            val hit = searchOver(word.uthmani).search(word.typed).hits.single()

            val marked = hit.text.substring(hit.matches.single())
            // The range may stop before a silent trailing letter (the alef after waw in الربا) or
            // start after an attached prefix letter (the kaf of 24:35:7), but it stays in the word.
            assertTrue(
                "'${word.typed}' marked '$marked' in '${word.uthmani}'",
                word.uthmani.contains(marked),
            )
            assertTrue("'${word.typed}' marked only '$marked'", marked.length >= word.typed.length)
        }
    }

    @Test
    fun typedPhrasesWithSpacesFindUthmaniAyahs() = runBlocking {
        for (phrase in typedPhrases) {
            val hits = searchOver(phrase.uthmaniAyah).search(phrase.typed).hits

            assertEquals("'${phrase.typed}' should find its ayah", 1, hits.size)
        }
    }

    @Test
    fun theTypedWordFindsBothSpellingsInOneSearch() = runBlocking {
        val search = searchOver("إبراهيم", "إبرٰهيم", "إِبْرَٰهِـۧمَ", "اسماعيل")

        val ayahs = search.search("ابراهيم").hits.map { it.ayah.ayah }

        assertEquals(listOf(1, 2, 3), ayahs)
    }

    @Test
    fun theTypedSalatFindsTheUthmaniSpellingWithAndWithoutItsMarks() = runBlocking {
        val search = searchOver("الصلوة", "الصلاة", "ٱلصَّلَوٰةَ")

        assertEquals(listOf(1, 2, 3), search.search("الصلاة").hits.map { it.ayah.ayah })
        assertEquals(listOf(1, 3), search.search("الصلوة").hits.map { it.ayah.ayah })
        assertEquals(listOf(1, 3), search.search("ٱلصَّلَوٰةَ").hits.map { it.ayah.ayah })
    }

    @Test
    fun wordsThatDifferByARealAlefStayApart() = runBlocking {
        // قال (said) and قل (say) must not find each other, and neither must كان and كن.
        val search = searchOver("قَالَ", "قُلْ", "كَانَ", "كُن")

        assertEquals(listOf(1), search.search("قال").hits.map { it.ayah.ayah })
        assertEquals(listOf(2), search.search("قل").hits.map { it.ayah.ayah })
        assertEquals(listOf(3), search.search("كان").hits.map { it.ayah.ayah })
        assertEquals(listOf(4), search.search("كن").hits.map { it.ayah.ayah })
    }

    @Test
    fun aDaggerAlefWordDoesNotMatchAnUnrelatedWord() = runBlocking {
        val search = searchOver("ذَٰلِكَ")

        assertTrue(search.search("ذكر").hits.isEmpty())
        assertTrue(search.search("دلك").hits.isEmpty())
    }
}
