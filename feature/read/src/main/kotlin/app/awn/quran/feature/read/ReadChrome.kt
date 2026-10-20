package app.awn.quran.feature.read

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

private const val BANGLA_LANGUAGE = "bn"

/** The surah name in the app language: Bangla when the user reads in Bangla and the pack has it. */
@Composable
fun Chapter.localName(): String {
    val language = LocalConfiguration.current.locales[0].language
    return if (language == BANGLA_LANGUAGE) nameBangla ?: nameSimple else nameSimple
}

/** Surah name on the left; juz and page on the right, both at body size. */
@Composable
internal fun ReadHeader(surahName: String, juz: Int, page: Int, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AwnSpacing.huge)
                .padding(horizontal = AwnSpacing.screenMargin),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = surahName,
            style = AwnTypography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.header_juz_page, juz, page),
            style = AwnTypography.body,
            maxLines = 1,
        )
    }
}

/** One text action on the toolbar row, in the accent colour of the field. */
@Composable
internal fun ToolbarAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = AwnTypography.bodyStrong,
        color = Awn.field.buttonFill,
        maxLines = 1,
        modifier =
            modifier
                .heightIn(min = AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AwnSpacing.small, horizontal = AwnSpacing.small),
    )
}

/** The row under the header with the page view switch and the way into the lists. */
@Composable
internal fun ReadToolbar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    viewSwitchLabel: String,
    onViewSwitch: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = AwnSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarAction(label = primaryLabel, onClick = onPrimary)
        ToolbarAction(label = viewSwitchLabel, onClick = onViewSwitch)
        ToolbarAction(label = stringResource(R.string.action_browse), onClick = onBrowse)
    }
}
