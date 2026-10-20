package app.awn.quran.feature.listen

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.player.DownloadProblem
import app.awn.quran.player.PlaybackOptions

private const val TRACK_ALPHA = 0.3f

/** A 24 dp icon from this module's `ic_listen_*` drawables. */
@Composable
internal fun ListenIcon(
    @DrawableRes icon: Int,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = Awn.field.content,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        modifier = modifier.size(AwnSpacing.large),
        tint = tint,
    )
}

/**
 * An icon button at least 48 dp square whose [description] is read by screen readers. A [badge] is
 * drawn small over the top right corner of the icon.
 */
@Composable
internal fun IconAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Awn.field.content,
    badge: String? = null,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(AwnSpacing.touchTarget, AwnSpacing.touchTarget)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ListenIcon(icon, description, tint = tint)
        if (badge != null) {
            Text(
                text = badge,
                style = AwnTypography.caption,
                color = tint,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = AwnSpacing.small),
            )
        }
    }
}

/** A thin bar filled to [fraction] (0 to 1); an unknown fraction shows an empty track. */
@Composable
internal fun ProgressTrack(fraction: Float?, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(AwnSpacing.sliderTrackHeight)
            .clip(AwnShapes.control)
            .background(color.copy(alpha = TRACK_ALPHA)),
    ) {
        if (fraction != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color))
        }
    }
}

/**
 * Returns the two sizes of "2.1 of 5.4 MB": the done size loses its unit when it is the total's
 * unit, so the unit is written once.
 */
internal fun progressSizes(
    context: Context,
    doneBytes: Long,
    totalBytes: Long,
): Pair<String, String> {
    val total = Formatter.formatShortFileSize(context, totalBytes)
    val done = Formatter.formatShortFileSize(context, doneBytes)
    val unit = total.substringAfterLast(' ', missingDelimiterValue = "")
    val doneWithoutUnit =
        if (unit.isNotEmpty() && done.endsWith(unit)) done.removeSuffix(unit).trim() else done
    return doneWithoutUnit to total
}

/** The label of a repeat count: its number, or the infinity sign for "forever". */
@Composable
internal fun repeatLabel(plays: Int): String =
    if (plays == PlaybackOptions.INFINITE_REPEAT) {
        stringResource(R.string.repeat_infinite)
    } else {
        plays.toString()
    }

/** The line that names why a download failed, with sizes for the space problem. */
@Composable
internal fun problemText(problem: DownloadProblem): String {
    val context = LocalContext.current
    return when (problem) {
        is DownloadProblem.NotEnoughSpace ->
            stringResource(
                R.string.download_problem_space,
                Formatter.formatShortFileSize(context, problem.neededBytes),
                Formatter.formatShortFileSize(context, problem.freeBytes),
            )
        DownloadProblem.NoInternet -> stringResource(R.string.download_problem_internet)
        DownloadProblem.ServerNotReachable -> stringResource(R.string.download_problem_server)
        DownloadProblem.WifiOnly -> stringResource(R.string.downloads_wifi_needed)
    }
}
