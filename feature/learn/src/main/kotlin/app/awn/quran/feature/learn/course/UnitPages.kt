package app.awn.quran.feature.learn.course

/** One page of a unit opened from a track: teaching, a check, the practice or the closing page. */
sealed interface UnitPage {
    /**
     * A reading screen with the grids that follow it, or grids alone when no reading screen comes
     * before them. The first screen gives the page its title.
     */
    data class Teaching(val screens: List<LessonScreenData>) : UnitPage

    /** [index] counts the checks of the unit from 0. */
    data class Check(val index: Int, val check: CheckWithAnswer) : UnitPage

    /** The practice set: ten items drawn from the unit's pool, one screen each. */
    data object Practice : UnitPage

    data object Done : UnitPage
}

/**
 * The pages of a unit in swipe order: the teaching pages, every check, the practice when the unit
 * has any practice item, then the closing page.
 */
fun unitPages(text: LessonText): List<UnitPage> =
    teachingPages(text) +
        text.checks.mapIndexed { index, check -> UnitPage.Check(index, check) } +
        listOfNotNull(UnitPage.Practice.takeIf { text.practiceItems.isNotEmpty() }) +
        UnitPage.Done

private fun teachingPages(text: LessonText): List<UnitPage.Teaching> {
    val pages = mutableListOf<MutableList<LessonScreenData>>()
    for (screen in text.teachingScreens) {
        if (screen.kind == ScreenKind.Grid && pages.isNotEmpty()) {
            pages.last().add(screen)
        } else {
            pages.add(mutableListOf(screen))
        }
    }
    return pages.map { UnitPage.Teaching(it) }
}

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
