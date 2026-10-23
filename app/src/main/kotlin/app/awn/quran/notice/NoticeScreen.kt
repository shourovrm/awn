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

/**
 * The short notice in the app's language, shown in place of Learn until the user taps the button.
 * The full three-language text stays in About, NOTICE and the README.
 */
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
                Text(
                    text = stringResource(R.string.notice_learn_title),
                    style = AwnTypography.title,
                )
                NoticeParagraph(R.string.notice_learn_teacher)
                NoticeParagraph(R.string.notice_learn_practice)
                NoticeParagraph(R.string.notice_learn_listening)
            }
            AwnButton(
                label = stringResource(R.string.notice_accept),
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NoticeParagraph(textResource: Int) {
    Text(text = stringResource(textResource), style = AwnTypography.lead)
}
