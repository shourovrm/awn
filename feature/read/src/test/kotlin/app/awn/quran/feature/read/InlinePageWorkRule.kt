package app.awn.quran.feature.read

import app.awn.quran.feature.read.page.PageWork
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Runs the off-thread measuring of a Mushaf page inline, so a page is on screen as soon as the
 * compose test is idle. Add it to every test class that composes a page.
 */
class InlinePageWorkRule : TestWatcher() {
    override fun starting(description: Description) {
        PageWork.dispatcher = Dispatchers.Unconfined
    }

    override fun finished(description: Description) {
        PageWork.dispatcher = Dispatchers.Default
    }
}
