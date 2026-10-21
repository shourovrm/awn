package app.awn.quran.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.components.AwnSettingsPage
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnTheme

/**
 * A settings screen in the app's own theme: a top bar with a back arrow above a scrolling list of
 * rows. [targetRow] is the row a search result opened; it scrolls into view. A page whose content
 * fills the screen itself passes [scrolls] false.
 */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrolls: Boolean = true,
    targetRow: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AwnTheme(field = AwnField.Paper, barBehindStatusBar = true) {
        AwnSettingsPage(
            title = title,
            backDescription = stringResource(R.string.navigate_back),
            onBack = onBack,
            modifier = modifier,
            scrolls = scrolls,
            targetRow = targetRow,
            content = content,
        )
    }
}
