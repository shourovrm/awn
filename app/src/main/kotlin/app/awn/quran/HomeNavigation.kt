package app.awn.quran

import androidx.navigation.NavController

/**
 * Back on the Quran screen with nothing open goes to the index, the app's home page. The app opens
 * straight on the Quran screen, so the index is usually not in the back stack; then the Quran
 * screen is replaced by the index, which makes the index the bottom of the stack and lets the next
 * Back leave the app. When the index is already below (opened from it), it is popped back to.
 */
internal fun NavController.goToHomeFromQuran() {
    if (previousBackStackEntry?.destination?.route == Routes.INDEX) {
        popBackStack()
        return
    }
    navigate(Routes.INDEX) {
        popUpTo(Routes.QURAN) { inclusive = true }
        launchSingleTop = true
    }
}
