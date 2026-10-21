package app.awn.quran.feature.learn

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.ProgressEvents
import app.awn.quran.feature.learn.content.LessonRepository
import app.awn.quran.feature.learn.content.SnippetRepository
import app.awn.quran.feature.learn.data.LearnDatabase
import app.awn.quran.feature.learn.progress.LearnProgress
import app.awn.quran.feature.learn.progress.LearnSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.learnDataStore by preferencesDataStore(name = LearnSettings.DATASTORE_NAME)

/** Everything the Learn screens need, created once per process. */
class LearnServices private constructor(context: Context) {
    val settings = LearnSettings(context.learnDataStore)
    val progress = LearnProgress(LearnDatabase.open(context), settings)
    val lessons = LessonRepository(context.assets)
    val snippets = SnippetRepository(context.assets)

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
