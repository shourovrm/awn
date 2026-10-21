package app.awn.quran.feature.read.quran

import kotlin.math.abs

/**
 * What the page does when the recitation moves to a new word: [turnToPage] is the page to show, or
 * null to stay; [isPaused] is the follow switch to keep for the next word.
 */
internal data class ReciteFollowDecision(val turnToPage: Int?, val isPaused: Boolean)

/**
 * Decides whether the page follows the recitation. The page of the next expected word ([wordPage])
 * wins over the page shown ([shownPage]), with one exception: a reader who swiped away by hand
 * ([isPaused]) is left alone until a word on the page they are looking at is recited, which proves
 * they are reciting from it again. Reciting a word on the shown page always clears the pause.
 */
internal fun decideReciteFollow(
    wordPage: Int,
    shownPage: Int,
    isPaused: Boolean,
): ReciteFollowDecision =
    when {
        wordPage == shownPage -> ReciteFollowDecision(turnToPage = null, isPaused = false)
        isPaused -> ReciteFollowDecision(turnToPage = null, isPaused = true)
        else -> ReciteFollowDecision(turnToPage = wordPage, isPaused = false)
    }

/**
 * Whether the turn to [targetPage] is one ordinary page turn, which the pager animates. A longer
 * way (recite started from another page, a revision jump) would scroll through every page between,
 * so it jumps instead.
 */
internal fun isSinglePageTurn(shownPage: Int, targetPage: Int): Boolean =
    abs(targetPage - shownPage) == 1
