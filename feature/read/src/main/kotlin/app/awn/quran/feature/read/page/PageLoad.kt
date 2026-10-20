package app.awn.quran.feature.read.page

import app.awn.quran.core.data.PageLayout

/** The state of one page slot of the pager. */
sealed interface PageLoad {
    data object Loading : PageLoad

    data class Loaded(val layout: PageLayout) : PageLoad

    data object Failed : PageLoad
}
