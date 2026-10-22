package app.awn.quran

import androidx.navigation.NavController
import app.awn.quran.core.data.AyahRef
import app.awn.quran.tabs.AwnTab

/** Moves between the tabs and the screens that open above them. */
internal class TabNavigation(
    private val navController: NavController,
    val selectTab: (AwnTab) -> Unit,
) {
    fun openQuran(page: Int?, ayah: AyahRef?) = navController.openQuran(page, ayah)

    fun openSettings() = navController.navigate(Routes.SETTINGS)

    fun openSearch() = navController.navigate(Routes.SEARCH)

    fun openBookmarks() = navController.navigate(Routes.BOOKMARKS)

    /** Closes everything above the tabs and shows [tab]. */
    fun showTab(tab: AwnTab) {
        selectTab(tab)
        returnToTabs()
    }

    /** Closes everything above the tabs and leaves the selected tab as it is. */
    fun returnToTabs() {
        navController.popBackStack(Routes.TABS, inclusive = false)
    }
}
