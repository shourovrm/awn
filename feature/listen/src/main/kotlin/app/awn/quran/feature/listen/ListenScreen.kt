package app.awn.quran.feature.listen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

/** The field colour that tells the user they are in Listen mode. */
val ListenField = AwnField.Green

/** Placeholder until the listen milestone; it only shows the mode name on its field. */
@Composable
fun ListenScreen(modifier: Modifier = Modifier) {
    AwnTheme(field = ListenField) {
        Box(modifier.fillMaxSize().padding(AwnSpacing.screenMargin)) {
            Text(text = stringResource(R.string.listen_title), style = AwnTypography.title)
        }
    }
}
