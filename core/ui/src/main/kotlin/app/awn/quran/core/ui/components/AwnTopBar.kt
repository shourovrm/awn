package app.awn.quran.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awn.quran.core.ui.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * A 56 dp bar in the bar colour of the active mode. It extends behind the status bar, so the screen
 * must use `AwnTheme(barBehindStatusBar = true)`.
 *
 * When [onNavigateBack] is set a back arrow described by [backDescription] leads the bar; [leading]
 * is a mark before the title (the app logo on the Quran tab); [actions] are icon buttons at the
 * end.
 */
@Composable
fun AwnTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    backDescription: String = "",
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val field = Awn.field
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(field.barBackground)
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(AwnSpacing.topBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onNavigateBack != null) {
                AwnIconButton(
                    icon = R.drawable.ic_ui_arrow_back,
                    contentDescription = backDescription,
                    onClick = onNavigateBack,
                    tint = field.onBar,
                )
            }
            if (leading != null) {
                Box(Modifier.padding(start = AwnSpacing.screenMargin)) { leading() }
            }
            Text(
                text = title,
                style = AwnTypography.lead,
                color = field.onBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.weight(1f)
                        .padding(
                            start =
                                when {
                                    leading != null -> AwnSpacing.small
                                    onNavigateBack == null -> AwnSpacing.screenMargin
                                    else -> 0.dp
                                },
                        ),
            )
            actions()
        }
    }
}
