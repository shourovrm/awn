package app.awn.quran.feature.duas.content

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.quran.SqliteQuranTextRepository
import app.awn.quran.feature.duas.ui.cardWords
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks the bundled `topics.json` against the real Quran text and the Duas groups. */
@RunWith(RobolectricTestRunner::class)
class BundledTopicCatalogueTest {

    private val topics = TopicCatalogueParser.parse(File("src/main/assets/topics.json").readText())
    private val duas = DuaCatalogueParser.parse(File("src/main/assets/duas.json").readText())
    private val quranText =
        SqliteQuranTextRepository(
            databaseFile = { File("../../core/data/src/main/assets/packs/quran.sqlite") },
        )

    @Test
    fun everyPassageIsAShortRunOfAyahsThatExist() {
        topics.topics
            .flatMap { it.parts }
            .flatMap { it.passages }
            .forEach { passage ->
                assertTrue("$passage is long", passage.lastAyah - passage.firstAyah < 8)
                val last = runBlocking {
                    quranText.wordsOfAyah(AyahRef(passage.surah, passage.lastAyah))
                }
                assertTrue("$passage ends past its surah", last.isNotEmpty())
            }
    }

    @Test
    fun keysAreUniqueAndLinkedDuaGroupsExist() {
        assertEquals(topics.topics.size, topics.topics.map { it.key }.toSet().size)
        val duaGroups = duas.groups.map { it.key }.toSet()
        topics.topics.forEach { topic ->
            assertEquals(topic.parts.size, topic.parts.map { it.key }.toSet().size)
            topic.duasGroupKey?.let { assertTrue("${topic.key} links $it", it in duaGroups) }
            assertTrue("${topic.key} has no parts", topic.parts.isNotEmpty())
        }
    }

    @Test
    fun theCardCollectionHasOneGroupPerPartAndCardsWithWholeAyahs() {
        val cards = topics.asCardCatalogue()

        assertEquals(topics.topics.sumOf { it.parts.size }, cards.groups.size)
        assertEquals(topics.topics.sumOf { it.passageCount }, cards.duas.size)
        assertEquals(cards.duas.size, cards.duas.map { it.id }.toSet().size)
        val card = cards.duas.first()
        val words = card.ayahs.flatMap { runBlocking { quranText.wordsOfAyah(it) } }
        assertTrue(cardWords(words, card).all { it.isDuaWord })
    }

    @Test
    fun everyTopicHasBothNames() {
        val names =
            topics.categories.map { it.name } +
                topics.topics.map { it.name } +
                topics.topics.flatMap { topic -> topic.parts.map { it.name } }
        names.forEach { name ->
            assertTrue(name.english.isNotBlank())
            assertTrue("no Bangla for ${name.english}", name.bangla.isNotBlank())
        }
    }
}
