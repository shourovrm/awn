package app.awn.quran.notice

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.quran.FloatingCard

/**
 * The short notice in a floating card over the page, shown before the first recite. It has the same
 * text and button as [NoticeScreen]; the card is the form that fits over the Quran page.
 */
@Composable
fun NoticeCard(onAccept: () -> Unit, modifier: Modifier = Modifier) {
    val field = Awn.field
    FloatingCard(onDismiss = {}, modifier = modifier) {
        Text(
            text = stringResource(R.string.notice_learn_title),
            style = AwnTypography.lead,
            color = field.content,
        )
        Text(
            text = stringResource(R.string.notice_learn_teacher),
            style = AwnTypography.body,
            color = field.content,
        )
        Text(
            text = stringResource(R.string.notice_learn_practice),
            style = AwnTypography.body,
            color = field.content,
        )
        Text(
            text = stringResource(R.string.notice_learn_listening),
            style = AwnTypography.body,
            color = field.content,
        )
        AwnButton(
            label = stringResource(R.string.notice_accept),
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.small),
        )
    }
}
