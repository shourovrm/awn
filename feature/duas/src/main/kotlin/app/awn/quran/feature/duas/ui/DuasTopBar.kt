package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnIconButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.duas.R

/**
 * The bar of the Duas screens: Back, a title with a smaller line under it, and icon buttons at the
 * end. [AwnTopBar] has a title only, and the cards need the position ("2 of 5") under theirs.
 */
@Composable
internal fun DuasTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val field = Awn.field
    Column(
        modifier
            .fillMaxWidth()
            .background(field.barBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(AwnSpacing.topBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AwnIconButton(
                icon = UiR.drawable.ic_ui_arrow_back,
                contentDescription = stringResource(R.string.duas_back),
                onClick = onBack,
                tint = field.onBar,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AwnTypography.bodyStrong,
                    color = field.onBar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = AwnTypography.small,
                        color = field.onBar,
                        maxLines = 1,
                    )
                }
            }
            actions()
        }
    }
}
