package app.awn.quran.feature.learn.course

/** One page of a unit opened from a track: a reading screen, a check, or the closing page. */
sealed interface UnitPage {
    data class Reading(val screen: LessonScreenData) : UnitPage

    /** [index] counts the checks of the unit from 0. */
    data class Check(val index: Int, val check: CheckWithAnswer) : UnitPage

    data object Done : UnitPage
}

/** The pages of a unit in swipe order: every reading screen, every check, then the closing page. */
fun unitPages(text: LessonText): List<UnitPage> =
    text.readingScreens.map { UnitPage.Reading(it) } +
        text.checks.mapIndexed { index, check -> UnitPage.Check(index, check) } +
        UnitPage.Done

/**
 * The answers given in one unit. [chosen] maps a check index to the option picked; the first pick
 * counts, so a user who swipes back cannot change a wrong answer into a right one.
 */
data class UnitAnswers(val chosen: Map<Int, Int> = emptyMap()) {
    fun choose(checkIndex: Int, optionIndex: Int): UnitAnswers =
        if (checkIndex in chosen) this else UnitAnswers(chosen + (checkIndex to optionIndex))

    fun rightCount(checks: List<CheckWithAnswer>): Int =
        checks.indices.count { index -> chosen[index] == checks[index].check.correctIndex }
}
