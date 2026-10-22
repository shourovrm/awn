package app.awn.quran.feature.learn

import android.content.Context
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.course.CourseChoices
import app.awn.quran.feature.learn.session.TodayPlan
import app.awn.quran.feature.learn.words.CourseSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** What the Today row on the Quran tab says about the word course. */
data class CourseTodayLine(val minutes: Int, val newWords: Int, val reviews: Int)

private const val SECONDS_PER_MINUTE = 60

/**
 * The course's line for the Today row, or null when the course has not started or has nothing
 * planned. Minutes round up so a short day never reads "0 min".
 */
internal fun courseTodayLine(plan: TodayPlan, courseStarted: Boolean): CourseTodayLine? {
    if (!courseStarted || plan.isEmpty) return null
    val minutes = (plan.plannedSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    return CourseTodayLine(
        minutes = minutes.coerceAtLeast(1),
        newWords = plan.newWordCount,
        reviews = plan.reviewCount,
    )
}

/** Today's course line; it changes as the user finishes words. Null while there is none. */
fun learnCourseTodayLine(context: Context): Flow<CourseTodayLine?> {
    val services = LearnServices.get(context)
    return combine(
            services.courseRepository.observeSnapshot(),
            services.settings.courseStarted,
            services.settings.sessionMinutes,
            services.settings.includeLetters,
        ) { snapshot, courseStarted, sessionMinutes, includeLetters ->
            CourseTodayInputs(snapshot, courseStarted, sessionMinutes, includeLetters)
        }
        .map { inputs ->
            if (!inputs.courseStarted) {
                null
            } else {
                val choices = CourseChoices(inputs.sessionMinutes, inputs.includeLetters)
                courseTodayLine(services.planToday(inputs.snapshot, choices), inputs.courseStarted)
            }
        }
        .distinctUntilChanged()
}

private class CourseTodayInputs(
    val snapshot: CourseSnapshot,
    val courseStarted: Boolean,
    val sessionMinutes: Int,
    val includeLetters: Boolean,
)

/** Whether the course already holds [word]'s form, met or queued; changes when the user adds it. */
fun learnWordIsAdded(context: Context, word: WordKey): Flow<Boolean> {
    val services = LearnServices.get(context)
    return flow {
        val rank = services.courseAssets().forms.rankOf(word)
        emitAll(
            services.courseRepository.observeSnapshot().map { snapshot ->
                rank in snapshot.wordCards || rank in snapshot.queuedRanks
            },
        )
    }
}

/** Takes the form of [word] back out of the words taught next (a word already met stays). */
suspend fun learnRemoveWord(context: Context, word: WordKey) {
    val services = LearnServices.get(context)
    services.courseRepository.unqueueWord(services.courseAssets().forms.rankOf(word))
}

/** Queues the form of [word] among the words taught next. */
suspend fun learnAddWord(context: Context, word: WordKey) {
    val services = LearnServices.get(context)
    services.courseRepository.queueWord(services.courseAssets().forms.rankOf(word))
}
