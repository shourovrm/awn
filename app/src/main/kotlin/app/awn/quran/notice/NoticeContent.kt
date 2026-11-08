package app.awn.quran.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill

/**
 * The full AI-advisory notice under a small heading, shown on the Learn with a teacher page.
 * Condition 4 of the LICENSE asks for the notice in the interface language, so only that language
 * is shown; `NOTICE` and the README keep all three.
 */
@Composable
fun NoticeContent(modifier: Modifier = Modifier) {
    val field = Awn.field
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.small)) {
        Text(
            text = stringResource(R.string.notice_heading),
            style = AwnTypography.bodyStrong,
            color = field.mutedContent,
        )
        Text(
            text = stringResource(R.string.notice_full),
            style = AwnTypography.body,
            color = field.content,
            modifier =
                Modifier.fillMaxWidth()
                    .clip(AwnShapes.control)
                    .background(field.softFill)
                    .padding(AwnSpacing.medium),
        )
    }
}
