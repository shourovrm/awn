package app.awn.quran.feature.learn

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
private fun rememberLeaveRequestedWords(
    requestedPage: MutableState<Int?>,
    onPageWordsBack: (() -> Unit)?,
): () -> Unit =
    remember(requestedPage, onPageWordsBack) {
        {
            requestedPage.value = null
            onPageWordsBack?.invoke()
        }
    }

/** The page words that :app asked the tab to open, and the Back that returns to the Quran page. */
internal class RequestedPageWords(
    private val routeState: MutableState<LearnRoute?>,
    private val requestedPage: MutableState<Int?>,
    private val leave: () -> Unit,
) {
    /**
     * Changes the route, except that Back from the requested page words returns to the Quran page.
     */
    fun changeRoute(next: LearnRoute) {
        if (isLeavingRequestedPageWords(routeState.value, next, requestedPage.value)) {
            leave()
        } else {
            routeState.value = next
        }
    }
}

/**
 * Opens the words of [pageWordsRequest] when :app asks, and remembers the page while
 * [onPageWordsBack] exists, so that Back can return to the Quran page instead of the words track.
 */
@Composable
internal fun rememberRequestedPageWords(
    pageWordsRequest: Int?,
    routeState: MutableState<LearnRoute?>,
    onHandled: () -> Unit,
    onPageWordsBack: (() -> Unit)?,
): RequestedPageWords {
    val requestedPage = rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(pageWordsRequest) {
        if (pageWordsRequest != null) {
            if (onPageWordsBack != null) requestedPage.value = pageWordsRequest
            routeState.value = LearnRoute.PageWords(pageWordsRequest)
            onHandled()
        }
    }
    val leave = rememberLeaveRequestedWords(requestedPage, onPageWordsBack)
    // Registered after the route's own handler, so it wins while the requested page is shown.
    BackHandler(enabled = isRequestedPageWordsOpen(routeState.value, requestedPage.value)) {
        leave()
    }
    return remember(routeState, requestedPage, leave) {
        RequestedPageWords(routeState, requestedPage, leave)
    }
}
