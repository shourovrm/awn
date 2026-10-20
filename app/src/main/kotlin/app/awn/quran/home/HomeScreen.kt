package app.awn.quran.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.R
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.awnFieldColors

/** Wordmark, Settings, and one poster band per mode; the bands are the navigation. */
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onReadClick: () -> Unit,
    onListenClick: () -> Unit,
    onReciteClick: () -> Unit,
    onLearnClick: () -> Unit,
) {
    AwnTheme(field = AwnField.Paper) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.app_name), style = AwnTypography.lead)
                Box(
                    modifier =
                        Modifier.heightIn(min = AwnSpacing.touchTarget)
                            .clickable(role = Role.Button, onClick = onSettingsClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.home_settings), style = AwnTypography.body)
                }
            }
            ModeBand(AwnField.Paper, R.string.mode_read, onReadClick)
            ModeBand(AwnField.Green, R.string.mode_listen, onListenClick)
            ModeBand(AwnField.Night, R.string.mode_recite, onReciteClick)
            ModeBand(AwnField.Ochre, R.string.mode_learn, onLearnClick)
        }
    }
}

@Composable
private fun ModeBand(field: AwnField, nameResource: Int, onClick: () -> Unit) {
    val colors = awnFieldColors(field)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(AwnSpacing.posterBandHeight)
                .background(colors.background)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.screenMargin),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(nameResource),
            style = AwnTypography.title,
            color = colors.content,
        )
    }
}
