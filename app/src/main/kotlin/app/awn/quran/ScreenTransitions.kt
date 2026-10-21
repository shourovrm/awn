package app.awn.quran

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

private typealias EnterSpec =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition

private typealias ExitSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/**
 * How one screen replaces another: a short slide. Going forward the new screen slides in from the
 * trailing edge (right in left-to-right text, left in right-to-left) while the old one moves a
 * third of the way off the other side; Back plays the same movement in reverse, so the screen being
 * left slides out to the trailing edge and the previous one slides in from the leading edge.
 * [SlideDirection.Start] and [SlideDirection.End] follow the layout direction, which mirrors the
 * slide in Arabic.
 *
 * NavTransitionsTest checks that every NavHost uses these four and that the slide stays quick.
 */
internal object ScreenTransitions {

    /** Long enough to read as movement, short enough that Back never feels like waiting. */
    const val SLIDE_MILLIS = 200

    // The old screen moves less than the new one, so the two read as layers and not as a tear.
    private const val BACKGROUND_SHARE = 3

    private val slide = tween<IntOffset>(durationMillis = SLIDE_MILLIS)

    val enter: EnterSpec = { slideIntoContainer(SlideDirection.Start, animationSpec = slide) }

    val exit: ExitSpec = {
        slideOutOfContainer(
            SlideDirection.Start,
            animationSpec = slide,
            targetOffset = { fullWidth -> fullWidth / BACKGROUND_SHARE },
        )
    }

    val popEnter: EnterSpec = {
        slideIntoContainer(
            SlideDirection.End,
            animationSpec = slide,
            initialOffset = { fullWidth -> fullWidth / BACKGROUND_SHARE },
        )
    }

    val popExit: ExitSpec = { slideOutOfContainer(SlideDirection.End, animationSpec = slide) }
}
