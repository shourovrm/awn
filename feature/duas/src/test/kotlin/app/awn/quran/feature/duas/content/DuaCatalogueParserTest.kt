package app.awn.quran.feature.duas.content

import app.awn.quran.core.data.WordKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The shape of `duas.json`; the bundled file and the parser must both follow it. */
@RunWith(RobolectricTestRunner::class)
class DuaCatalogueParserTest {

    private val sample =
        """
        {
          "note": "Word positions follow the bundled quran.sqlite.",
          "groups": [
            { "key": "both-worlds", "name_en": "Good in this world and the next", "name_bn": "দুনিয়া ও আখিরাতের কল্যাণ" },
            { "key": "speech", "name_en": "Ease and clear speech", "name_bn": "সহজ কাজ ও স্পষ্ট কথা" }
          ],
          "prophets": { "musa": { "name_en": "Musa", "name_bn": "মূসা" } },
          "duas": [
            { "id": "2-201", "surah": 2,
              "start": { "ayah": 201, "word": 4, "text": "رَبَّنَآ" },
              "end": { "ayah": 201, "word": 14, "text": "ٱلنَّارِ" },
              "group": "both-worlds", "opening": "rabbana", "prophet": null, "starter": true },
            { "id": "20-25", "surah": 20,
              "start": { "ayah": 25, "word": 2, "text": "رَبِّ" },
              "end": { "ayah": 28, "word": 2, "text": "قَوْلِى" },
              "group": "speech", "opening": "rabbi", "prophet": "musa", "starter": false }
          ]
        }
        """

    @Test
    fun readsGroupsProphetsAndDuas() {
        val catalogue = DuaCatalogueParser.parse(sample)

        assertEquals(listOf("both-worlds", "speech"), catalogue.groups.map { it.key })
        assertEquals("Good in this world and the next", catalogue.groups[0].name.english)
        assertEquals("মূসা", catalogue.prophets.getValue("musa").bangla)

        val first = catalogue.duas[0]
        assertEquals("2-201", first.id)
        assertEquals(WordKey(2, 201, 4), first.start)
        assertEquals(WordKey(2, 201, 14), first.end)
        assertEquals(DuaOpening.Rabbana, first.opening)
        assertNull(first.prophetKey)
        assertTrue(first.isStarter)

        val second = catalogue.duas[1]
        assertEquals(DuaOpening.Rabbi, second.opening)
        assertEquals("musa", second.prophetKey)
        assertEquals(4, second.ayahs.size)
    }

    @Test
    fun unknownGroupFailsAndNamesTheDua() {
        val broken = sample.replace("\"group\": \"speech\"", "\"group\": \"missing\"")

        val error =
            assertThrows(IllegalArgumentException::class.java) { DuaCatalogueParser.parse(broken) }

        assertTrue(error.message.orEmpty().contains("20-25"))
    }

    @Test
    fun unknownProphetFailsAndNamesTheDua() {
        val broken = sample.replace("\"prophet\": \"musa\"", "\"prophet\": \"harun\"")

        val error =
            assertThrows(IllegalArgumentException::class.java) { DuaCatalogueParser.parse(broken) }

        assertTrue(error.message.orEmpty().contains("20-25"))
    }

    @Test
    fun unknownOpeningFails() {
        val broken = sample.replace("\"opening\": \"rabbi\"", "\"opening\": \"ya-rabb\"")

        assertThrows(IllegalArgumentException::class.java) { DuaCatalogueParser.parse(broken) }
    }
}
