package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.AwnSpacing

/** A scrolling body with one primary button pinned under it. */
@Composable
internal fun BodyWithButton(
    buttonLabel: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BodyWithBottom(
        bottom = {
            AwnButton(
                label = buttonLabel,
                onClick = onButtonClick,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = modifier,
        content = content,
    )
}

/** A scrolling body with [bottom] pinned under it, at the screen margin. */
@Composable
internal fun BodyWithBottom(
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        ) {
            content()
        }
        Box(Modifier.fillMaxWidth().padding(AwnSpacing.screenMargin)) { bottom() }
    }
}
