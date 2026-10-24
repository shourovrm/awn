package app.awn.quran.feature.duas.content

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.quran.SqliteQuranTextRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks the bundled `duas.json` against the real Quran text, so a wrong word position fails the
 * build instead of showing the wrong words on a card. The file stores the Uthmani text of each
 * start and end word for exactly this check.
 */
@RunWith(RobolectricTestRunner::class)
class BundledDuaCatalogueTest {

    private val json = File("src/main/assets/duas.json").readText()
    private val catalogue = DuaCatalogueParser.parse(json)
    private val quranText =
        SqliteQuranTextRepository(
            databaseFile = { File("../../core/data/src/main/assets/packs/quran.sqlite") },
        )

    private fun words(ayah: AyahRef): List<Word> = runBlocking { quranText.wordsOfAyah(ayah) }

    private fun wordText(key: WordKey): String? =
        words(AyahRef(key.surah, key.ayah)).firstOrNull { it.key == key }?.textUthmani

    @Test
    fun holdsAtLeastTheSeventySevenDuasOfTheFirstListInGroupsOfTwoOrMore() {
        assertTrue("only ${catalogue.duas.size} duas", catalogue.duas.size >= 77)
        catalogue.groups.forEach { group ->
            assertTrue("group ${group.key} is too small", catalogue.duasIn(group.key).size >= 2)
        }
    }

    @Test
    fun idsAndGroupKeysAreUnique() {
        assertEquals(catalogue.duas.size, catalogue.duas.map { it.id }.toSet().size)
        assertEquals(catalogue.groups.size, catalogue.groups.map { it.key }.toSet().size)
    }

    @Test
    fun everyGroupAndProphetHasBothNames() {
        val names = catalogue.groups.map { it.name } + catalogue.prophets.values
        names.forEach { name ->
            assertTrue("missing English name", name.english.isNotBlank())
            assertTrue("missing Bangla name for ${name.english}", name.bangla.isNotBlank())
        }
    }

    @Test
    fun startAndEndWordsMatchTheQuranText() {
        val duas = JSONObject(json).getJSONArray("duas")
        for (index in 0 until duas.length()) {
            val entry = duas.getJSONObject(index)
            val dua = catalogue.duas[index]
            assertEquals(dua.id, entry.getString("id"))
            assertEquals(
                "start of ${dua.id}",
                entry.getJSONObject("start").getString("text"),
                wordText(dua.start),
            )
            assertEquals(
                "end of ${dua.id}",
                entry.getJSONObject("end").getString("text"),
                wordText(dua.end),
            )
        }
    }

    @Test
    fun noTwoDuasShareAWord() {
        val seen = mutableMapOf<WordKey, String>()
        catalogue.duas.forEach { dua ->
            duaWords(dua).forEach { key ->
                val earlier = seen.put(key, dua.id)
                assertTrue("${dua.id} overlaps $earlier at $key", earlier == null)
            }
        }
    }

    @Test
    fun tenStartersOfFifteenWordsOrFewer() {
        val starters = catalogue.starters
        assertEquals(10, starters.size)
        starters.forEach { dua ->
            val count = duaWords(dua).size
            assertTrue("${dua.id} has $count words", count <= 15)
        }
    }

    private fun duaWords(dua: Dua): List<WordKey> =
        dua.ayahs
            .flatMap { ayah -> words(ayah).map { it.key } }
            .filter { key -> isWithin(key, dua) }

    private fun isWithin(key: WordKey, dua: Dua): Boolean {
        val afterStart =
            key.ayah > dua.start.ayah ||
                (key.ayah == dua.start.ayah && key.wordPosition >= dua.start.wordPosition)
        val beforeEnd =
            key.ayah < dua.end.ayah ||
                (key.ayah == dua.end.ayah && key.wordPosition <= dua.end.wordPosition)
        return afterStart && beforeEnd
    }
}
