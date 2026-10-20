package app.awn.quran.feature.read

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

/** The field colour that tells the user they are in Read mode. */
val ReadField = AwnField.Paper

/** Placeholder until the read milestone; it only shows the mode name on its field. */
@Composable
fun ReadScreen(modifier: Modifier = Modifier) {
    AwnTheme(field = ReadField) {
        Box(modifier.fillMaxSize().padding(AwnSpacing.screenMargin)) {
            Text(text = stringResource(R.string.read_title), style = AwnTypography.title)
        }
    }
}
