package app.awn.quran.feature.duas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.duas.DuasDependencies
import app.awn.quran.feature.duas.PronunciationState
import app.awn.quran.feature.duas.R

internal const val PRONUNCIATION_DOWNLOAD_TAG = "dua-pronunciation-download"

/**
 * Under the word tiles while the pronunciation of each word is not on the phone: one line and a
 * Download button, or the line alone while it downloads. Nothing once it is there.
 */
@Composable
internal fun PronunciationOffer(dependencies: DuasDependencies, modifier: Modifier = Modifier) {
    val state by
        dependencies.pronunciation.collectAsStateWithLifecycle(PronunciationState.Installed)
    if (state == PronunciationState.Installed) return
    val field = Awn.field
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = AwnSpacing.touchTarget)
            .clip(AwnShapes.control)
            .background(field.softFill)
            .padding(horizontal = AwnSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    if (state == PronunciationState.Downloading)
                        R.string.duas_pronunciation_downloading
                    else R.string.duas_pronunciation_offer,
                ),
            style = AwnTypography.small,
            color = field.content,
            modifier = Modifier.weight(1f),
        )
        if (state == PronunciationState.Missing) {
            TextButton(
                onClick = dependencies.requestPronunciation,
                modifier = Modifier.testTag(PRONUNCIATION_DOWNLOAD_TAG),
            ) {
                Text(
                    text = stringResource(R.string.duas_pronunciation_download),
                    style = AwnTypography.bodyStrong,
                    color = field.buttonFill,
                )
            }
        }
    }
}
