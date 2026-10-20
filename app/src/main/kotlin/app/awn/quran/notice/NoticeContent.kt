package app.awn.quran.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.R
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

/**
 * The AI-advisory notice in English, Bangla and Arabic. All three are always shown, whatever the
 * interface language, because the licence of the speech model requires it.
 */
@Composable
fun NoticeContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        Text(text = stringResource(R.string.notice_english), style = AwnTypography.body)
        Text(text = stringResource(R.string.notice_bangla), style = AwnTypography.body)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                text = stringResource(R.string.notice_arabic),
                style = AwnTypography.lead,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
