package app.awn.quran.feature.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.ayah.AyahListScreen
import app.awn.quran.feature.read.ayah.AyahListSource
import app.awn.quran.feature.read.browse.BrowseScreen
import app.awn.quran.feature.read.page.MushafPageScreen
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.rememberReadSettingsViewModel
import kotlinx.coroutines.flow.first

private const val FIRST_PAGE = 1

/**
 * The whole Read mode on the paper field: the Mushaf or ayah-by-ayah view chosen in the read
 * settings, with the lists one tap away. :app calls this for its Read route.
 *
 * [startPage] opens a given page (for "Continue reading" it is the last page); null opens the page
 * read last. [onExpandPlayer] is called when the user expands the collapsed player dock.
 */
@Composable
fun ReadHost(
    dependencies: ReadDependencies,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    startPage: Int? = null,
    reciterName: (String) -> String = { it },
) {
    val settingsViewModel = rememberReadSettingsViewModel(dependencies)
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var isBrowsing by rememberSaveable { mutableStateOf(false) }
    var requestedPage by rememberSaveable { mutableStateOf(startPage) }
    val pageViewSwitch = {
        // Both views record the page they show, so the other view opens where this one stopped.
        requestedPage = null
        settingsViewModel.setPageView(
            if (settings.pageView == PageView.Mushaf) PageView.AyahList else PageView.Mushaf,
        )
    }

    AwnTheme(field = ReadField) {
        if (isBrowsing) {
            BrowseScreen(
                dependencies = dependencies,
                onOpenPage = { page ->
                    requestedPage = page
                    isBrowsing = false
                },
                onClose = {
                    // The reader resumes on the page last shown, not on the page it was opened at.
                    requestedPage = null
                    isBrowsing = false
                },
                modifier = modifier,
            )
        } else if (settings.pageView == PageView.Mushaf) {
            MushafPageScreen(
                dependencies = dependencies,
                startPage = requestedPage,
                onTogglePageView = pageViewSwitch,
                onBrowse = { isBrowsing = true },
                onExpandPlayer = onExpandPlayer,
                modifier = modifier,
                reciterName = reciterName,
            )
        } else {
            AyahListHost(
                dependencies = dependencies,
                requestedPage = requestedPage,
                onTogglePageView = pageViewSwitch,
                onBrowse = { isBrowsing = true },
                onExpandPlayer = onExpandPlayer,
                modifier = modifier,
                reciterName = reciterName,
            )
        }
    }
}

/** Opens the ayah list on [requestedPage], or on the page read last when it is null. */
@Composable
private fun AyahListHost(
    dependencies: ReadDependencies,
    requestedPage: Int?,
    onTogglePageView: () -> Unit,
    onBrowse: () -> Unit,
    onExpandPlayer: () -> Unit,
    modifier: Modifier,
    reciterName: (String) -> String,
) {
    val pageToList by
        produceState<Int?>(initialValue = null, requestedPage) {
            value =
                requestedPage
                    ?: dependencies.bookmarks.recentPages().first().firstOrNull()?.page
                    ?: FIRST_PAGE
        }
    pageToList?.let { page ->
        AyahListScreen(
            dependencies = dependencies,
            source = AyahListSource.Page(page),
            onTogglePageView = onTogglePageView,
            onBrowse = onBrowse,
            onExpandPlayer = onExpandPlayer,
            modifier = modifier,
            reciterName = reciterName,
        )
    }
}
