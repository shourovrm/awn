package app.awn.quran

import androidx.navigation.NavController
import app.awn.quran.core.data.AyahRef
import app.awn.quran.index.QuranTabActions
import app.awn.quran.tabs.AwnTab

/** Moves between the tabs and the screens that open above them. */
internal class TabNavigation(
    private val navController: NavController,
    val selectTab: (AwnTab) -> Unit,
    private val requestPageWords: (Int) -> Unit = {},
) {
    fun openQuran(page: Int?, ayah: AyahRef?) = navController.openQuran(page, ayah)

    fun openSettings() = navController.navigate(Routes.SETTINGS)

    fun openSearch() = navController.navigate(Routes.SEARCH)

    fun openBookmarks() = navController.navigate(Routes.BOOKMARKS)

    fun openDuas() = navController.navigate(Routes.DUAS)

    /** Closes everything above the tabs and shows [tab]. */
    fun showTab(tab: AwnTab) {
        selectTab(tab)
        returnToTabs()
    }

    /** Closes everything above the tabs and shows the Learn tab at the words of [page]. */
    fun showPageWords(page: Int) {
        requestPageWords(page)
        showTab(AwnTab.Learn)
    }

    /** Closes everything above the tabs and leaves the selected tab as it is. */
    fun returnToTabs() {
        navController.popBackStack(Routes.TABS, inclusive = false)
    }
}

/** What the Quran tab's buttons and rows open. */
internal fun TabNavigation.quranTabActions() =
    QuranTabActions(
        onSearch = ::openSearch,
        onBookmarks = ::openBookmarks,
        onSettings = ::openSettings,
        onDuas = ::openDuas,
        onOpen = ::openQuran,
    )
