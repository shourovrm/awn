package app.awn.quran.feature.learn.course

import app.awn.quran.feature.learn.session.DueItem
import app.awn.quran.feature.learn.session.ItemRef
import app.awn.quran.feature.learn.session.LessonSlot
import app.awn.quran.feature.learn.session.Pace
import app.awn.quran.feature.learn.session.PlannerInput
import app.awn.quran.feature.learn.session.SessionPlanner
import app.awn.quran.feature.learn.session.TodayPlan
import app.awn.quran.feature.learn.words.CourseSnapshot
import app.awn.quran.feature.learn.words.WordFrequency
import java.time.Instant
import java.time.ZoneId

/** The user's choices that shape a day. */
data class CourseChoices(val sessionMinutes: Int, val includeLetters: Boolean)

/**
 * Connects what the user has done ([CourseSnapshot]) to the planner: what is due today, which words
 * come next, which lessons are waiting and which lesson a set of new words brings up.
 */
class CoursePlanner(private val content: CourseContent, private val frequency: WordFrequency) {

    fun plan(
        snapshot: CourseSnapshot,
        pace: Pace,
        choices: CourseChoices,
        now: Instant,
        zone: ZoneId,
    ): TodayPlan {
        val startLessons = startLessons(snapshot, choices)
        val metRanks = snapshot.wordCards.keys
        val pendingGrammar =
            grammarLessons(snapshot).filter { lesson ->
                lesson.introducedBy.any { frequency.rankOf(it) in metRanks }
            }
        return SessionPlanner.plan(
            PlannerInput(
                budgetSeconds = choices.sessionMinutes * SECONDS_PER_MINUTE,
                pace = pace,
                dueItems = dueItems(snapshot, now, zone),
                newWordRanks = newWordRanks(snapshot),
                maximumNewWords = choices.sessionMinutes,
                startLessons = startLessons.map(::slotOf),
                hasFinishedStartLesson = hasFinishedStartLesson(snapshot, choices),
                pendingGrammar = pendingGrammar.map(::slotOf),
                grammarBroughtBy = { ranks ->
                    grammarLessons(snapshot)
                        .filter { it !in pendingGrammar }
                        .filter { lesson ->
                            lesson.introducedBy.any { frequency.rankOf(it) in ranks }
                        }
                        .map(::slotOf)
                },
            ),
        )
    }

    /** The start lessons not done, in order: letters first when chosen, then basics. */
    fun startLessons(snapshot: CourseSnapshot, choices: CourseChoices): List<CourseLesson> {
        val letters =
            if (choices.includeLetters) content.inCourse(LessonCourse.Letters) else emptyList()
        return (letters + content.inCourse(LessonCourse.Basics)).filter {
            it.id !in snapshot.doneLessonIds
        }
    }

    private fun hasFinishedStartLesson(snapshot: CourseSnapshot, choices: CourseChoices): Boolean {
        val all =
            content.inCourse(LessonCourse.Basics) +
                if (choices.includeLetters) content.inCourse(LessonCourse.Letters) else emptyList()
        return all.any { it.id in snapshot.doneLessonIds }
    }

    private fun grammarLessons(snapshot: CourseSnapshot): List<CourseLesson> =
        content.inCourse(LessonCourse.Grammar).filter { it.id !in snapshot.doneLessonIds }

    private fun dueItems(snapshot: CourseSnapshot, now: Instant, zone: ZoneId): List<DueItem> {
        val today = now.atZone(zone).toLocalDate()

        fun isDue(dueMillis: Long) =
            !Instant.ofEpochMilli(dueMillis).atZone(zone).toLocalDate().isAfter(today)
        val words =
            snapshot.wordCards.values
                .filter { isDue(it.due) }
                .map { DueItem(ItemRef.Word(it.rank), it.due) }
        val grammar =
            snapshot.grammarCards.values
                .filter { isDue(it.due) }
                .map { DueItem(ItemRef.Grammar(it.lessonId), it.due) }
        return words + grammar
    }

    /** Words the user added first, then the most frequent words not yet met. */
    fun newWordRanks(snapshot: CourseSnapshot, limit: Int = NEW_WORD_CANDIDATES): List<Int> {
        val queued = snapshot.queuedRanks.filter { it !in snapshot.wordCards }
        val following =
            generateSequence(1) { it + 1 }
                .takeWhile { it <= frequency.formCount }
                .filter { it !in snapshot.wordCards && it !in queued }
        return (queued.asSequence() + following).take(limit).toList()
    }

    private fun slotOf(lesson: CourseLesson): LessonSlot {
        val screens = lesson.text(CourseLesson.ENGLISH).screens.size
        return LessonSlot(lesson.id, (screens / FULL_LESSON_SCREENS).coerceIn(MIN_WEIGHT, 1.0))
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val NEW_WORD_CANDIDATES = 40

        /** A grammar lesson of two reading screens, a check and an answer is a full lesson. */
        const val FULL_LESSON_SCREENS = 4.0

        /** The shortest a lesson counts, so one short screen still costs a minute. */
        const val MIN_WEIGHT = 0.4
    }
}
