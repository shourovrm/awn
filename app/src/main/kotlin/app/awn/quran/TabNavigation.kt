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
    private val currentTab: AwnTab = AwnTab.Quran,
    private val pageWordsReturn: PageWordsReturn? = null,
    private val rememberPageWordsReturn: (PageWordsReturn?) -> Unit = {},
) {
    fun openQuran(page: Int?, ayah: AyahRef?) = navController.openQuran(page, ayah)

    fun openSettings() = navController.navigate(Routes.SETTINGS)

    fun openSearch() = navController.navigate(Routes.SEARCH)

    fun openBookmarks() = navController.navigate(Routes.BOOKMARKS)

    fun openDuas() = navController.navigate(Routes.duas())

    fun openTopics() = navController.navigate(Routes.TOPICS)

    /** Closes everything above the tabs and shows [tab]. */
    fun showTab(tab: AwnTab) {
        selectTab(tab)
        returnToTabs()
    }

    /**
     * Closes everything above the tabs and shows the Learn tab at the words of [page], the page of
     * the Quran screen that asked. [leavePageWords] brings the user back to that page.
     */
    fun showPageWords(page: Int) {
        rememberPageWordsReturn(PageWordsReturn(quranPage = page, tab = currentTab))
        requestPageWords(page)
        showTab(AwnTab.Learn)
    }

    /** Back from the page words: the tab selected before, then the Quran page they came from. */
    fun leavePageWords() {
        val destination = pageWordsReturn
        rememberPageWordsReturn(null)
        if (destination != null) {
            selectTab(destination.tab)
            openQuran(destination.quranPage, null)
        }
    }

    /** Closes everything above the tabs and leaves the selected tab as it is. */
    fun returnToTabs() {
        navController.popBackStack(Routes.TABS, inclusive = false)
    }
}

/** Where Back from the page words leads: [quranPage] on the Quran screen, over [tab]. */
internal data class PageWordsReturn(val quranPage: Int, val tab: AwnTab)

/** What the Quran tab's buttons and rows open. */
internal fun TabNavigation.quranTabActions() =
    QuranTabActions(
        onSearch = ::openSearch,
        onBookmarks = ::openBookmarks,
        onSettings = ::openSettings,
        onDuas = ::openDuas,
        onTopics = ::openTopics,
        onOpen = ::openQuran,
    )
