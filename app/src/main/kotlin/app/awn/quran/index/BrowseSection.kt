package app.awn.quran.index

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.awn.quran.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill

const val BROWSE_DUAS_TAG = "browse-duas"
const val BROWSE_TOPICS_TAG = "browse-topics"

/**
 * The Browse section: two tiles side by side, Duas and Topics, each with its count; ways into the
 * Quran beside the surah list. A collection whose count is not read yet has no tile.
 */
@Composable
internal fun BrowseSection(
    duaCount: Int?,
    topicCount: Int?,
    onDuas: () -> Unit,
    onTopics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.browse_title),
            style = AwnTypography.bodyStrong,
            color = Awn.field.buttonFill,
            modifier =
                Modifier.padding(top = AwnSpacing.large, bottom = AwnSpacing.small).semantics {
                    heading()
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
            if (duaCount != null) {
                BrowseTile(
                    icon = R.drawable.ic_app_menu_book,
                    title = stringResource(R.string.browse_duas),
                    detail = pluralStringResource(R.plurals.duas_entry_count, duaCount, duaCount),
                    onClick = onDuas,
                    modifier = Modifier.weight(1f).testTag(BROWSE_DUAS_TAG),
                )
            }
            if (topicCount != null) {
                BrowseTile(
                    icon = R.drawable.ic_app_topics,
                    title = stringResource(R.string.browse_topics),
                    detail =
                        pluralStringResource(R.plurals.topics_entry_count, topicCount, topicCount),
                    onClick = onTopics,
                    modifier = Modifier.weight(1f).testTag(BROWSE_TOPICS_TAG),
                )
            }
        }
    }
}

@Composable
private fun BrowseTile(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Column(
        modifier
            .heightIn(min = AwnSpacing.cardRowHeight)
            .clip(AwnShapes.control)
            .background(field.softFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(AwnSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small / 2),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = field.buttonFill,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Text(text = title, style = AwnTypography.bodyStrong, color = field.content)
        Text(text = detail, style = AwnTypography.small, color = field.content)
    }
}
