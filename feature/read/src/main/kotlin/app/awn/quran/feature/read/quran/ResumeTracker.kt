package app.awn.quran.feature.read.quran

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the reader must stay on a place before it becomes the resume point. */
const val RESUME_DEBOUNCE_MS = 1_500L

/**
 * Decides when the page the reader is on is saved as the resume point.
 *
 * Reaching a page by swiping is reading: the page is saved [debounceMs] after the pager settles,
 * and at once when the screen stops, so a kill loses nothing. Reaching a page by a lookup (search,
 * a bookmark, Go to page, the index, a notification) is only a visit: it is saved only when the
 * reader stays [debounceMs], so looking something up never moves the place the reader will come
 * back to. A visit still pending when the screen stops is dropped, and starts its wait again when
 * the screen comes back.
 *
 * This is separate from the 20 second "page read" rule, which only feeds Learn.
 */
internal class ResumeTracker(
    private val scope: CoroutineScope,
    private val save: suspend (page: Int) -> Unit,
    private val debounceMs: Long = RESUME_DEBOUNCE_MS,
) {
    private var waitJob: Job? = null
    private var pendingPage: Int? = null
    private var isPendingReading = false

    /** The reader swiped to [page]. */
    fun onReading(page: Int) = startWaiting(page, isReading = true)

    /** [page] was opened by a lookup. */
    fun onLookup(page: Int) = startWaiting(page, isReading = false)

    /** The screen left the foreground. */
    fun onStop() {
        val page = pendingPage ?: return
        waitJob?.cancel()
        if (isPendingReading) {
            pendingPage = null
            scope.launch { save(page) }
        }
    }

    /** The screen came back; a visit that was waiting starts its wait again. */
    fun onStart() {
        val page = pendingPage ?: return
        startWaiting(page, isReading = isPendingReading)
    }

    private fun startWaiting(page: Int, isReading: Boolean) {
        waitJob?.cancel()
        pendingPage = page
        isPendingReading = isReading
        waitJob = scope.launch {
            delay(debounceMs)
            pendingPage = null
            save(page)
        }
    }
}
