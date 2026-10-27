package app.awn.quran

import androidx.navigation.NavController
import app.awn.quran.core.data.AyahRef

/**
 * Opens the Quran screen above the Duas screen it was opened from. The Duas entry is not popped, so
 * its open card and view model are still there when Back returns to it.
 */
internal fun NavController.openQuranOverDuas(page: Int?, ayah: AyahRef?) {
    navigate(Routes.quran(page, ayah)) {
        launchSingleTop = true
    }
}

/**
 * Back from the Quran screen. A Quran opened from a Dua card pops back to that Duas entry; any
 * other Quran pops to the tabs, as [openQuran] expects.
 */
internal fun NavController.leaveQuran() {
    val routeBelow = previousBackStackEntry?.destination?.route
    if (routeBelow == Routes.DUAS) {
        popBackStack()
    } else {
        popBackStack(Routes.TABS, inclusive = false)
    }
}
