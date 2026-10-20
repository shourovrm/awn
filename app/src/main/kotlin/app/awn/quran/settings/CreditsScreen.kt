package app.awn.quran.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.net.toUri
import app.awn.quran.R
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

/** Data sources from the manifests, the recitation checker, the icons and the libraries. */
@Composable
fun CreditsScreen(credits: List<CreditLine>, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.credits_title), onBack = onBack) {
        GroupTitle(stringResource(R.string.credits_data))
        credits.forEach { credit ->
            CreditRow(
                name = credit.sourceName,
                linkLabel = credit.linkLabel,
                onLinkClick = { openLink(context, credit.sourceUrl) },
            )
        }
        GroupTitle(stringResource(R.string.credits_recogniser))
        PlainLine(stringResource(R.string.credit_recogniser_entry))
        GroupTitle(stringResource(R.string.credits_libraries))
        stringArrayResource(R.array.credit_libraries).forEach { PlainLine(it) }
    }
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        text = title,
        style = AwnTypography.lead,
        color = Awn.field.buttonFill,
        modifier = Modifier.padding(top = AwnSpacing.large, bottom = AwnSpacing.small),
    )
}

@Composable
private fun PlainLine(text: String) {
    Text(
        text = text,
        style = AwnTypography.body,
        color = Awn.field.content,
        modifier = Modifier.padding(vertical = AwnSpacing.small),
    )
}

/** The source name with its short link below; the whole row opens the address. */
@Composable
private fun CreditRow(name: String, linkLabel: String, onLinkClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = AwnSpacing.touchTarget)
            .clickable(role = Role.Button, onClick = onLinkClick)
            .padding(vertical = AwnSpacing.small),
    ) {
        Text(text = name, style = AwnTypography.body, color = Awn.field.content)
        Text(text = linkLabel, style = AwnTypography.small, color = Awn.field.buttonFill)
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // A device with no browser has nothing to show the address in; the label stays readable.
    }
}
