package app.awn.quran.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill

/** The tick box at the start of a [AwnPackRow]. */
enum class PackTick {
    /** The list has no tick boxes at all, such as the audio downloads, so no space is kept. */
    Absent,
    /** The entry is not on the phone yet, so there is nothing to show or hide. */
    None,
    Off,
    On,
}

/** One button at the end of a [AwnPackRow]: what it does for a screen reader, and what it runs. */
class PackRowButton(val description: String, val onClick: () -> Unit)

private const val TICK_RING_ALPHA = 0.45f

/**
 * One downloadable entry of a list. At the start a tick box shows or hides the entry (only when it
 * is on the phone). In the middle are the [title] and a [detail] line, and while [progress] is not
 * null a thin bar under them. At the end is one button: [download] when the entry is not on the
 * phone, [cancel] while it downloads, or [remove] when it is on the phone and can be removed.
 */
@Composable
fun AwnPackRow(
    title: String,
    detail: String,
    tick: PackTick,
    onTickChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isDetailError: Boolean = false,
    progress: Float? = null,
    download: PackRowButton? = null,
    cancel: PackRowButton? = null,
    remove: PackRowButton? = null,
) {
    val field = Awn.field
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowTall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tick != PackTick.Absent) TickBox(title, tick, onTickChange)
        Column(Modifier.weight(1f).padding(vertical = AwnSpacing.small)) {
            Text(text = title, style = AwnTypography.body, color = field.content)
            Text(
                text = detail,
                style = AwnTypography.small,
                color = if (isDetailError) field.wrongWord else field.mutedContent,
            )
            if (progress != null) ProgressBar(progress)
        }
        val button = cancel ?: download ?: remove
        if (button != null) {
            AwnIconButton(
                icon = iconOf(isCancel = cancel != null, isDownload = download != null),
                contentDescription = button.description,
                onClick = button.onClick,
                tint = field.buttonFill,
            )
        } else {
            Box(Modifier.size(AwnSpacing.touchTarget))
        }
    }
}

private fun iconOf(isCancel: Boolean, isDownload: Boolean): Int =
    when {
        isCancel -> R.drawable.ic_ui_close
        isDownload -> R.drawable.ic_ui_download
        else -> R.drawable.ic_ui_delete
    }

@Composable
private fun TickBox(title: String, tick: PackTick, onTickChange: (Boolean) -> Unit) {
    val field = Awn.field
    if (tick == PackTick.None) {
        Box(Modifier.size(AwnSpacing.touchTarget))
        return
    }
    val isOn = tick == PackTick.On
    Box(
        modifier =
            Modifier.requiredSize(AwnSpacing.touchTarget)
                .toggleable(value = isOn, role = Role.Checkbox, onValueChange = onTickChange)
                .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        val boxModifier =
            if (isOn) {
                Modifier.background(field.buttonFill, AwnShapes.tickBox)
            } else {
                Modifier.border(
                    AwnSpacing.switchKnobRing,
                    field.content.copy(alpha = TICK_RING_ALPHA),
                    AwnShapes.tickBox,
                )
            }
        Box(
            modifier = Modifier.size(AwnSpacing.tickBoxSize).then(boxModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (isOn) {
                Icon(
                    painter = painterResource(R.drawable.ic_ui_check),
                    contentDescription = null,
                    tint = field.onButton,
                    modifier = Modifier.size(AwnSpacing.smallIconSize),
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val field = Awn.field
    Box(
        modifier =
            Modifier.padding(top = 4.dp)
                .fillMaxWidth()
                .height(AwnSpacing.packProgressHeight)
                .clip(CircleShape)
                .background(field.softFill)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
    ) {
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(field.buttonFill),
        )
    }
}
