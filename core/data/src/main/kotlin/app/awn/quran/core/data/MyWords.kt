package app.awn.quran.core.data

import kotlinx.coroutines.flow.Flow

/**
 * The user's own word list, as the Quran screen sees it. The Learn course implements it; the Quran
 * screen offers "Add to my words" on a word card only when :app hands one over.
 */
interface MyWords {
    /** Emits whether the form of [word] is in the list, again after each change. */
    fun isAdded(word: WordKey): Flow<Boolean>

    /** Adds the form of [word] to the words taught next. */
    suspend fun add(word: WordKey)

    /**
     * Takes the form of [word] out of the course, also when the user has already met it. The result
     * puts it back exactly as it was.
     */
    suspend fun remove(word: WordKey): RemovedMyWord
}

/** What [MyWords.remove] took out, kept in memory so the user can undo the removal. */
fun interface RemovedMyWord {
    suspend fun restore()
}
