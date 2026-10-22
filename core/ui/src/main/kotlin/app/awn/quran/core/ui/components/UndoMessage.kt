package app.awn.quran.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.AwnColors
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import kotlinx.coroutines.delay

/** The test tag of the Undo action of [UndoMessage]. */
const val UNDO_ACTION_TAG = "undo-action"

/** How long [UndoMessage] stays before it calls `onTimeout`. */
const val UNDO_MESSAGE_MILLIS = 5_000L

/**
 * A short message with one action, in the ink and ochre of the app so it reads in every colour
 * mode. After [UNDO_MESSAGE_MILLIS] it calls [onTimeout], which is when the caller lets go of what
 * it kept for the undo.
 */
@Composable
fun UndoMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeoutNow = rememberUpdatedState(onTimeout)
    LaunchedEffect(message) {
        delay(UNDO_MESSAGE_MILLIS)
        timeoutNow.value()
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(AwnColors.ink, AwnShapes.control)
            .padding(start = AwnSpacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = AwnTypography.body,
            color = AwnColors.paper,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = actionLabel,
            style = AwnTypography.bodyStrong,
            color = AwnColors.ochre,
            modifier =
                Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = AwnSpacing.medium)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .testTag(UNDO_ACTION_TAG),
        )
    }
}
