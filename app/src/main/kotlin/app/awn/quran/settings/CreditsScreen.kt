package app.awn.quran.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.R
import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography

/** Data sources from the manifests, the recitation checker and the open-source libraries. */
@Composable
fun CreditsScreen(credits: List<CreditLine>) {
    AwnTheme(field = AwnField.Paper) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            Text(text = stringResource(R.string.credits_title), style = AwnTypography.title)
            GroupTitle(stringResource(R.string.credits_data))
            credits.forEach { credit ->
                Text(
                    text =
                        stringResource(
                            R.string.credits_source_line,
                            credit.sourceName,
                            credit.sourceUrl,
                        ),
                    style = AwnTypography.body,
                )
            }
            GroupTitle(stringResource(R.string.credits_recogniser))
            Text(
                text = stringResource(R.string.credit_recogniser_entry),
                style = AwnTypography.body,
            )
            GroupTitle(stringResource(R.string.credits_libraries))
            stringArrayResource(R.array.credit_libraries).forEach { library ->
                Text(text = library, style = AwnTypography.body)
            }
        }
    }
}
