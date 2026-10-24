package app.awn.quran.feature.learn.course

import app.awn.quran.feature.learn.words.WordLevel
import app.awn.quran.feature.learn.words.WordLevels

/** Where the user stands in a level: the one open level is [Current]. */
enum class LevelState {
    Finished,
    Current,
    Later,
}

/** The rows of one word level of the Quran words page. */
data class LevelSection(val level: WordLevel, val rows: List<PathRow>, val state: LevelState)

object LevelSections {

    /**
     * Groups the sets of [rows], and each grammar line after the set that brings it, under their
     * level. The level that holds [currentSet] is current; levels before it are finished. Levels
     * with no row in [rows] are left out.
     */
    fun group(rows: List<PathRow>, currentSet: Int): List<LevelSection> =
        WordLevels.all.mapNotNull { level ->
            val levelRows = rows.filter { setNumberOf(it) in level.sets }
            if (levelRows.isEmpty()) null
            else LevelSection(level, levelRows, stateOf(level, currentSet))
        }

    private fun stateOf(level: WordLevel, currentSet: Int): LevelState =
        when {
            currentSet in level.sets -> LevelState.Current
            currentSet > level.sets.last -> LevelState.Finished
            else -> LevelState.Later
        }

    private fun setNumberOf(row: PathRow): Int? =
        when (row) {
            is PathRow.SetStep -> row.number
            is PathRow.LessonStep -> row.triggerSet
            else -> null
        }
}
