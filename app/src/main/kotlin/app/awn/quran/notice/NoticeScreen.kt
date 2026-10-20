package app.awn.quran.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

/** Shown before anything else until the user taps the button. */
@Composable
fun NoticeScreen(onAccept: () -> Unit) {
    AwnTheme(field = AwnField.Paper) {
        Column(
            modifier = Modifier.fillMaxSize().padding(AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.large),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.large),
            ) {
                Text(text = stringResource(R.string.notice_title), style = AwnTypography.title)
                NoticeContent()
            }
            AwnButton(
                label = stringResource(R.string.notice_accept),
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
