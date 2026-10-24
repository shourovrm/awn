package app.awn.quran.index

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.tabs.TodayTiles

const val HIFZ_TILE_TAG = "tile-hifz"
const val LEARN_TILE_TAG = "tile-learn"
const val BROWSE_DUAS_TAG = "browse-duas"
const val BROWSE_TOPICS_TAG = "browse-topics"

private const val DETAIL_MAX_LINES = 2

/** How many duas and topics the tiles show; null until that collection has been read. */
internal data class BrowseCounts(val duas: Int?, val topics: Int?)

/**
 * One row of four equal tiles under the continue card: Hifz and Learn with today's load, then Duas
 * and Topics with their counts. The tiles are always four; a collection not read yet shows no
 * detail line.
 */
@Composable
internal fun QuickTiles(
    todayTiles: TodayTiles,
    browse: BrowseCounts,
    actions: QuranTabActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        val tileModifier = Modifier.weight(1f).fillMaxHeight()
        QuickTile(
            icon = todayTiles.hifz.icon,
            title = todayTiles.hifz.title,
            detail = todayTiles.hifz.detail,
            onClick = todayTiles.hifz.onOpen,
            modifier = tileModifier.testTag(HIFZ_TILE_TAG),
        )
        QuickTile(
            icon = todayTiles.learn.icon,
            title = todayTiles.learn.title,
            detail = todayTiles.learn.detail,
            onClick = todayTiles.learn.onOpen,
            modifier = tileModifier.testTag(LEARN_TILE_TAG),
        )
        QuickTile(
            icon = R.drawable.ic_app_menu_book,
            title = stringResource(R.string.browse_duas),
            detail = browse.duas?.let { pluralStringResource(R.plurals.duas_entry_count, it, it) },
            onClick = actions.onDuas,
            modifier = tileModifier.testTag(BROWSE_DUAS_TAG),
        )
        QuickTile(
            icon = R.drawable.ic_app_topics,
            title = stringResource(R.string.browse_topics),
            detail =
                browse.topics?.let { pluralStringResource(R.plurals.topics_entry_count, it, it) },
            onClick = actions.onTopics,
            modifier = tileModifier.testTag(BROWSE_TOPICS_TAG),
        )
    }
}

@Composable
private fun QuickTile(
    @DrawableRes icon: Int,
    title: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    Column(
        modifier
            .clip(AwnShapes.control)
            .background(field.softFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(AwnSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small / 2),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = field.buttonFill,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Text(
            text = title,
            style = AwnTypography.bodyStrong,
            color = field.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = AwnTypography.small,
                color = field.content,
                maxLines = DETAIL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
