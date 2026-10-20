package app.awn.quran.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.notice.NoticeContent

/** About stub: the notice again. Credits and version arrive with their features. */
@Composable
fun AboutScreen() {
    AwnTheme(field = AwnField.Paper) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.large),
        ) {
            Text(text = stringResource(R.string.about_title), style = AwnTypography.title)
            NoticeContent()
        }
    }
}
