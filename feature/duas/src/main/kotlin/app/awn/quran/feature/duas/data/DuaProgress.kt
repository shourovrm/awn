package app.awn.quran.feature.duas.data

import kotlinx.coroutines.flow.Flow

/** How many clean recitations mark a dua as learned. */
const val CLEAN_RECITATIONS_TO_LEARN = 3

/** What the user has done with the duas on this phone. */
data class DuaProgress(
    val savedIds: Set<String> = emptySet(),
    val cleanRecitations: Map<String, Int> = emptyMap(),
) {
    fun isSaved(duaId: String): Boolean = duaId in savedIds

    fun cleanCount(duaId: String): Int = cleanRecitations[duaId] ?: 0

    fun isLearned(duaId: String): Boolean = cleanCount(duaId) >= CLEAN_RECITATIONS_TO_LEARN
}

/** Keeps the saved duas and the clean recitations between runs of the app. */
interface DuaProgressStore {
    val progress: Flow<DuaProgress>

    /** Saves the dua, or takes it out of the saved list when it is already there. */
    suspend fun toggleSaved(duaId: String)

    /** Counts one recitation in which every word of the dua was confirmed. */
    suspend fun recordCleanRecitation(duaId: String)
}
