package app.awn.quran.feature.duas.content

import app.awn.quran.core.data.WordKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuaSearchTest {

    private fun dua(id: String, surah: Int, ayah: Int) =
        Dua(
            id = id,
            start = WordKey(surah, ayah, 1),
            end = WordKey(surah, ayah, 5),
            groupKey = "group",
            opening = DuaOpening.Rabbi,
            prophetKey = null,
            isStarter = false,
        )

    private val parentsDua = dua("17-24", 17, 24)
    private val forgiveDua = dua("71-28", 71, 28)
    private val worldDua = dua("2-201", 2, 201)

    private val entries =
        listOf(
            DuaSearchEntry(
                parentsDua,
                arabic = "رَّبِّ ٱرْحَمْهُمَا كَمَا رَبَّيَانِى صَغِيرًۭا",
                translation = "My Lord, have mercy on them as they raised me when I was small.",
                groupName = "Family and children",
            ),
            DuaSearchEntry(
                forgiveDua,
                arabic = "رَّبِّ ٱغْفِرْ لِى وَلِوَٰلِدَىَّ",
                translation = "My Lord, forgive me and my parents.",
                groupName = "Forgiveness and mercy",
            ),
            DuaSearchEntry(
                worldDua,
                arabic = "رَبَّنَآ ءَاتِنَا فِى ٱلدُّنْيَا حَسَنَةًۭ",
                translation = null,
                groupName = "দুনিয়া ও আখিরাতের কল্যাণ",
            ),
        )

    private fun ids(query: String) = DuaSearch.search(query, entries).map { it.dua.id }

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(DuaSearch.search("  ", entries).isEmpty())
    }

    @Test
    fun translationWordsMatchWithoutCaseAndByWordStart() {
        assertEquals(listOf("71-28"), ids("Parents"))
        assertEquals(listOf("71-28"), ids("parent"))
    }

    @Test
    fun groupNameMatchesAndIsReportedAsGroup() {
        val hits = DuaSearch.search("family", entries)
        assertEquals(listOf("17-24"), hits.map { it.dua.id })
        assertEquals(DuaMatchField.Group, hits.single().field)
    }

    @Test
    fun arabicMatchesWithoutHarakatAndWithKeyboardSpelling() {
        val hits = DuaSearch.search("ربنا اتنا", entries)
        assertEquals(listOf("2-201"), hits.map { it.dua.id })
        assertEquals(DuaMatchField.Arabic, hits.single().field)
    }

    @Test
    fun banglaGroupNameMatches() {
        assertEquals(listOf("2-201"), ids("আখিরাত"))
    }

    @Test
    fun oneLetterTypoInALongWordStillMatches() {
        assertEquals(listOf("71-28"), ids("forgve"))
    }

    @Test
    fun eachDuaAppearsOnceInCollectionOrder() {
        // "my lord" is in two translations; both appear once, in the order of the collection.
        assertEquals(listOf("17-24", "71-28"), ids("my lord"))
    }
}
