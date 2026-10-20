package app.awn.quran.feature.listen

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.components.AwnSwitch
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * The reciter list on the Listen field. The chosen reciter sits on an ochre block.
 * [onOpenDownloads] opens [DownloadsScreen].
 */
@Composable
fun RecitersScreen(
    viewModel: RecitersViewModel,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    AwnTheme(field = ListenField) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(text = stringResource(R.string.reciters_title), style = AwnTypography.title)
            state.rows.forEach { row ->
                ReciterRowItem(
                    row = row,
                    onSelect = { viewModel.select(row.id) },
                    onRemove = { viewModel.removeDownloads(row.id) },
                )
            }
            WifiOnlyRow(state.wifiOnly, viewModel::setWifiOnly)
            TextAction(stringResource(R.string.reciters_downloads), onOpenDownloads)
        }
    }
}

@Composable
private fun ReciterRowItem(row: ReciterRow, onSelect: () -> Unit, onRemove: () -> Unit) {
    val field = Awn.field
    val contentColor = if (row.isSelected) field.onButton else field.content
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AwnShapes.control)
                .background(if (row.isSelected) field.buttonFill else field.background)
                .clickable(role = Role.RadioButton, onClick = onSelect)
                .padding(AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = row.name, style = AwnTypography.lead, color = contentColor)
            Text(
                text =
                    stringResource(
                        if (row.wordByWord) R.string.reciter_word_by_word
                        else R.string.reciter_listening_only,
                    ),
                style = AwnTypography.small,
                color = contentColor,
            )
            Text(
                text = downloadedSizeText(row.downloadedBytes),
                style = AwnTypography.small,
                color = contentColor,
            )
        }
        if (row.downloadedBytes > 0L) {
            TextAction(stringResource(R.string.reciter_remove_downloads), onRemove, contentColor)
        }
    }
}

@Composable
private fun downloadedSizeText(bytes: Long): String =
    if (bytes > 0L) {
        Formatter.formatShortFileSize(LocalContext.current, bytes)
    } else {
        stringResource(R.string.reciter_nothing_downloaded)
    }

@Composable
private fun WifiOnlyRow(wifiOnly: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reciters_wifi_only),
            style = AwnTypography.body,
            modifier = Modifier.weight(1f),
        )
        AwnSwitch(checked = wifiOnly, onCheckedChange = onChange)
    }
}

/** A text label that acts as a button: 48 dp tall, no fill. */
@Composable
internal fun TextAction(
    label: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = Awn.field.content,
) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        color = color,
        modifier =
            Modifier.heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AwnSpacing.small, vertical = AwnSpacing.small),
    )
}
