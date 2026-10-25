package app.awn.quran.feature.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.ui.LearnRoute

/** True while the page words that the host was asked to open are on screen. */
internal fun isRequestedPageWordsOpen(route: LearnRoute?, requestedPage: Int?): Boolean =
    requestedPage != null && route == LearnRoute.PageWords(requestedPage)

/**
 * True when [next] is the words track, the place Back from the requested page words leads to.
 * Opening a word from the page leads elsewhere and keeps the request.
 */
internal fun isLeavingRequestedPageWords(
    route: LearnRoute?,
    next: LearnRoute,
    requestedPage: Int?,
): Boolean =
    isRequestedPageWordsOpen(route, requestedPage) && next == LearnRoute.Track(LearnTrack.Words)

/** Forgets the request, then tells :app to show the screen the user came from. */
@Composable
internal fun rememberLeaveRequestedWords(
    requestedPage: MutableState<Int?>,
    onPageWordsBack: (() -> Unit)?,
): () -> Unit =
    remember(requestedPage, onPageWordsBack) {
        {
            requestedPage.value = null
            onPageWordsBack?.invoke()
        }
    }
