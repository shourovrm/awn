package app.awn.quran.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.notice.NoticeContent

/** The notice again, opened from the Notice row of About. */
@Composable
fun NoticeAgainScreen(onBack: () -> Unit) {
    SettingsPage(title = stringResource(R.string.about_notice), onBack = onBack) {
        NoticeContent()
    }
}
