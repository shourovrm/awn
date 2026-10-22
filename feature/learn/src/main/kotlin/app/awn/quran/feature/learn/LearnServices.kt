package app.awn.quran.feature.learn

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.feature.learn.content.LessonRepository
import app.awn.quran.feature.learn.content.SnippetRepository
import app.awn.quran.feature.learn.course.CourseChoices
import app.awn.quran.feature.learn.course.CourseContent
import app.awn.quran.feature.learn.course.CourseContentReader
import app.awn.quran.feature.learn.course.CoursePlanner
import app.awn.quran.feature.learn.data.LearnDatabase
import app.awn.quran.feature.learn.progress.LearnProgress
import app.awn.quran.feature.learn.progress.LearnSettings
import app.awn.quran.feature.learn.session.TodayPlan
import app.awn.quran.feature.learn.words.CourseSnapshot
import app.awn.quran.feature.learn.words.WordCourseRepository
import app.awn.quran.feature.learn.words.WordForms
import app.awn.quran.feature.learn.words.WordFormsAsset
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.learnDataStore by preferencesDataStore(name = LearnSettings.DATASTORE_NAME)

/** The generated course data: lessons, the word ranking and the planner built on both. */
class CourseAssets(val content: CourseContent, val forms: WordForms, val planner: CoursePlanner)

/** Everything the Learn screens need, created once per process. */
class LearnServices private constructor(context: Context) {
    private val database = LearnDatabase.open(context)
    val settings = LearnSettings(context.learnDataStore)
    val progress = LearnProgress(database, settings)
    val lessons = LessonRepository(context.assets)
    val snippets = SnippetRepository(context.assets)
    val courseRepository = WordCourseRepository(database)

    // Reading the lesson file and the word ranking takes tens of milliseconds, so it happens once,
    // off the main thread, when a screen first asks for it.
    private val loadingCourseAssets: Deferred<CourseAssets> =
        CoroutineScope(SupervisorJob() + Dispatchers.IO).async(start = CoroutineStart.LAZY) {
            val content = CourseContentReader.read(context.assets)
            val forms = WordFormsAsset.read(context.assets)
            CourseAssets(content, forms, CoursePlanner(content, forms))
        }

    suspend fun courseAssets(): CourseAssets = loadingCourseAssets.await()

    /** The plan for today from the current state, at the user's measured pace. */
    suspend fun planToday(snapshot: CourseSnapshot, choices: CourseChoices): TodayPlan =
        courseAssets()
            .planner
            .plan(snapshot, courseRepository.pace(), choices, Instant.now(), ZoneId.systemDefault())

    companion object {
        @Volatile private var instance: LearnServices? = null

        fun get(context: Context): LearnServices =
            instance
                ?: synchronized(this) {
                    instance ?: LearnServices(context.applicationContext).also { instance = it }
                }
    }
}

/**
 * The [ProgressEvents] that :app hands to :feature:read and :feature:recite, so that page views and
 * hifz completions reach the Learn database.
 */
fun learnProgressEvents(context: Context): ProgressEvents = LearnServices.get(context).progress

/** How many ayahs are due for revision today; it changes as the user rates them. */
fun learnDueAyahCount(context: Context): Flow<Int> =
    LearnServices.get(context)
        .progress
        .observeSnapshot()
        .map { snapshot -> snapshot.dueToday.size }
        .distinctUntilChanged()

/** The first ayah due today in reading order, or null when nothing is due. */
suspend fun learnFirstDueAyah(context: Context): AyahRef? =
    LearnServices.get(context).progress.dueAyahs().minOrNull()
