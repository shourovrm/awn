package app.awn.quran.feature.learn.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The lesson reader parses with org.json, which only Robolectric provides on the JVM.
@RunWith(RobolectricTestRunner::class)
class LearnTracksTest {

    private val tajweed =
        listOf(
            TajweedLessonTitle("qalqalah", "Qalqalah"),
            TajweedLessonTitle("makharij", "Where letters are said"),
            TajweedLessonTitle("extra-rule", "Extra"),
        )

    private fun input(done: Set<String> = emptySet(), includeLetters: Boolean = true) =
        TrackInput(
            content = readingContent(),
            languageCode = "en",
            tajweedLessons = tajweed,
            doneLessonIds = done,
            includeLetters = includeLetters,
        )

    @Test
    fun `reading units are numbered in course order and the first not done is next`() {
        val units = LearnTracks.units(LearnTrack.Reading, input(done = setOf("letters-01")))

        assertEquals(listOf("letters-01", "letters-02", "letters-03"), units.map { it.id })
        assertEquals(listOf(1, 2, 3), units.map { it.number })
        assertEquals(
            listOf(PathState.Done, PathState.Next, PathState.Upcoming),
            units.map { it.state },
        )
    }

    @Test
    fun `grammar lists the basics before the grammar lessons`() {
        val units = LearnTracks.units(LearnTrack.Grammar, input())

        assertEquals(listOf("basics-01", "grammar-03"), units.map { it.id })
    }

    @Test
    fun `tajweed follows the fixed order and keeps unknown lessons at the end`() {
        val units = LearnTracks.units(LearnTrack.Tajweed, input())

        assertEquals(listOf("makharij", "qalqalah", "extra-rule"), units.map { it.id })
    }

    @Test
    fun `tajweed waits for the reading units when the user starts with the letters`() {
        val closed = LearnTracks.summary(LearnTrack.Tajweed, input())
        val allReadingDone = setOf("letters-01", "letters-02", "letters-03")
        val open = LearnTracks.summary(LearnTrack.Tajweed, input(done = allReadingDone))

        // The fixture has three reading units, fewer than the rule's unit, so all three open it.
        assertEquals(3, closed.opensAfterReadingUnit)
        assertNull(open.opensAfterReadingUnit)
    }

    @Test
    fun `tajweed is open at once for a user who already reads`() {
        assertTrue(LearnTracks.isOpen(LearnTrack.Tajweed, input(includeLetters = false)))
        assertFalse(LearnTracks.isOpen(LearnTrack.Tajweed, input(includeLetters = true)))
    }

    @Test
    fun `the unit after the last one is null`() {
        assertEquals(
            "letters-02",
            LearnTracks.unitAfter(LearnTrack.Reading, "letters-01", input())?.id,
        )
        assertNull(LearnTracks.unitAfter(LearnTrack.Reading, "letters-03", input()))
    }

    @Test
    fun `a lesson id maps to its track`() {
        val content = readingContent()

        assertEquals(LearnTrack.Reading, LearnTracks.trackOf("letters-02", content))
        assertEquals(LearnTrack.Grammar, LearnTracks.trackOf("basics-01", content))
        assertEquals(LearnTrack.Tajweed, LearnTracks.trackOf("makharij", content))
        assertNull(LearnTracks.trackOf("unknown", content))
    }

    @Test
    fun `a finished track has no next unit`() {
        val summary =
            LearnTracks.summary(
                LearnTrack.Grammar,
                input(done = setOf("basics-01", "grammar-03")),
            )

        assertNull(summary.next)
        assertEquals(2, summary.doneCount)
    }

    private fun readingContent(): CourseContent {
        fun lesson(id: String, course: String, order: Int): String {
            val reading = """{"kind":"screen","title":"T","blocks":[]}"""
            return """{"id":"$id","course":"$course","order":$order,"introducedBy":[],
                "licence":"x","reviewed":false,"languages":{"en":{"title":"$id","screens":[$reading]}}}"""
        }
        val json =
            """{"formatVersion":1,"references":[],"lessons":[
            ${lesson("letters-03", "letters", 3)},
            ${lesson("letters-01", "letters", 1)},
            ${lesson("letters-02", "letters", 2)},
            ${lesson("basics-01", "basics", 1)},
            ${lesson("grammar-03", "grammar", 3)}]}"""
        return CourseContentReader.parse(json)
    }
}
