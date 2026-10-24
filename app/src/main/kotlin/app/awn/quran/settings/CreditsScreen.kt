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
        DetailedCreditRow(
            name = stringResource(R.string.credit_surah_names_name),
            detail = stringResource(R.string.credit_surah_names_detail),
            linkLabel = CreditText.linkLabel(SURAH_NAMES_URL),
            onLinkClick = { openLink(context, SURAH_NAMES_URL) },
        )
        GroupTitle(stringResource(R.string.credits_lessons))
        DetailedCreditRow(
            name = stringResource(R.string.credit_qaida_name),
            detail = stringResource(R.string.credit_qaida_detail),
        )
        DetailedCreditRow(
            name = stringResource(R.string.credit_academy_name),
            detail = stringResource(R.string.credit_academy_detail),
            linkLabel = CreditText.linkLabel(ACADEMY_URL),
            onLinkClick = { openLink(context, ACADEMY_URL) },
        )
        DetailedCreditRow(
            name = stringResource(R.string.credit_tajweed_book_name),
            detail = stringResource(R.string.credit_tajweed_book_detail),
        )
        DetailedCreditRow(
            name = stringResource(R.string.credit_mouth_drawing_name),
            detail = stringResource(R.string.credit_mouth_drawing_detail),
            linkLabel = CreditText.linkLabel(MOUTH_DRAWING_URL),
            onLinkClick = { openLink(context, MOUTH_DRAWING_URL) },
        )
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

/** A source with a line on what Awn takes from it; the address is shown when there is one. */
@Composable
private fun DetailedCreditRow(
    name: String,
    detail: String,
    linkLabel: String? = null,
    onLinkClick: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = AwnSpacing.touchTarget)
            .then(
                if (onLinkClick != null)
                    Modifier.clickable(role = Role.Button, onClick = onLinkClick)
                else Modifier,
            )
            .padding(vertical = AwnSpacing.small),
    ) {
        Text(text = name, style = AwnTypography.body, color = Awn.field.content)
        Text(text = detail, style = AwnTypography.small, color = Awn.field.content)
        if (linkLabel != null) {
            Text(text = linkLabel, style = AwnTypography.small, color = Awn.field.buttonFill)
        }
    }
}

private const val MOUTH_DRAWING_URL =
    "https://commons.wikimedia.org/wiki/File:Neutral_Sagittal_Section.svg"
private const val SURAH_NAMES_URL =
    "https://bn.wikipedia.org/wiki/%E0%A6%95%E0%A7%81%E0%A6%B0%E0%A6%86%E0%A6%A8%E0%A7%87%E0%A6%B0_%E0%A6%B8%E0%A7%82%E0%A6%B0%E0%A6%BE%E0%A6%B0_%E0%A6%A4%E0%A6%BE%E0%A6%B2%E0%A6%BF%E0%A6%95%E0%A6%BE"
private const val ACADEMY_URL = "https://www.youtube.com/channel/UCX766Oi2anGGIkIgMJXu9ng"

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // A device with no browser has nothing to show the address in; the label stays readable.
    }
}
