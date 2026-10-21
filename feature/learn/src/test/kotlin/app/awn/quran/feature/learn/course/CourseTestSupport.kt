package app.awn.quran.feature.learn.course

import app.awn.quran.feature.learn.data.WordCardEntity
import app.awn.quran.feature.learn.words.CourseSnapshot

/** A course with basics 1 and 2, letters 2 and a grammar lesson brought by form 25 (set 3). */
fun fixtureContent(): CourseContent {
    fun lesson(id: String, course: String, order: Int, introducedBy: String, check: Boolean): String {
        val reading = """{"kind":"screen","title":"T","blocks":[{"type":"paragraph","text":"x"}]}"""
        val checkScreens =
            """{"kind":"check","title":"C","check":{"question":"q","options":["a","b"],"answer":0}},""" +
                """{"kind":"answer","title":"A","blocks":[{"type":"paragraph","text":"y"}]}"""
        val screens = if (check) "$reading,$checkScreens" else reading
        return """{"id":"$id","course":"$course","order":$order,"introducedBy":[$introducedBy],
            "licence":"x","reviewed":false,"languages":{"en":{"title":"$id","screens":[$screens]}}}"""
    }
    val json =
        """{"formatVersion":1,"references":[],"lessons":[
        ${lesson("basics-01", "basics", 1, "", false)},
        ${lesson("basics-02", "basics", 2, "", false)},
        ${lesson("letters-02", "letters", 2, "", false)},
        ${lesson("grammar-03", "grammar", 3, "\"2:1:25\"", true)}]}"""
    return CourseContentReader.parse(json)
}

fun wordCard(rank: Int, due: Long = 0, stability: Double? = null, state: Int = 1, lapses: Int = 0) =
    WordCardEntity(
        rank = rank,
        stability = stability,
        difficulty = stability?.let { 5.0 },
        due = due,
        lastReview = null,
        reps = 0,
        lapses = lapses,
        state = state,
        step = 0,
        metAt = 0,
        lastQuestionType = null,
    )

fun snapshotOf(
    cards: List<WordCardEntity> = emptyList(),
    queued: List<Int> = emptyList(),
    doneLessons: Set<String> = emptySet(),
) =
    CourseSnapshot(
        wordCards = cards.associateBy { it.rank },
        queuedRanks = queued,
        grammarCards = emptyMap(),
        doneLessonIds = doneLessons,
        sets = emptyMap(),
    )
