package app.awn.quran.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.components.SettingsLinkRow
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.notice.NoticeContent

/** Learn with a teacher: why a teacher comes first, the full notice, and a link to the credits. */
@Composable
fun NoticeAgainScreen(onOpenCredits: () -> Unit, onBack: () -> Unit) {
    SettingsPage(title = stringResource(R.string.notice_learn_title), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
            TeacherHeadline()
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(AwnShapes.control)
                        .background(Awn.field.softFill)
                        .padding(AwnSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
            ) {
                NoticePoint(R.drawable.ic_app_chat, R.string.notice_learn_practice)
                NoticePoint(R.drawable.ic_app_mic, R.string.notice_learn_listening)
            }
            NoticeContent()
        }
        SettingsLinkRow(label = stringResource(R.string.about_credits), onClick = onOpenCredits)
    }
}

@Composable
private fun TeacherHeadline() {
    val field = Awn.field
    Row(
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(AwnSpacing.giant).clip(CircleShape).background(field.softFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_app_school),
                contentDescription = null,
                tint = field.buttonFill,
                modifier = Modifier.size(AwnSpacing.large),
            )
        }
        Text(
            text = stringResource(R.string.notice_learn_teacher),
            style = AwnTypography.bodyStrong,
            color = field.content,
        )
    }
}

@Composable
private fun NoticePoint(@DrawableRes icon: Int, text: Int) {
    val field = Awn.field
    Row(horizontalArrangement = Arrangement.spacedBy(AwnSpacing.medium)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = field.buttonFill,
            modifier = Modifier.size(AwnSpacing.iconSize),
        )
        Text(text = stringResource(text), style = AwnTypography.body, color = field.content)
    }
}
