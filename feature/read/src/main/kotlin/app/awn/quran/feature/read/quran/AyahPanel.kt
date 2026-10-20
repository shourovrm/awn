package app.awn.quran.feature.read.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.ReadIconButton
import app.awn.quran.feature.read.localName
import app.awn.quran.feature.read.settings.ReadSettings

private const val TRANSLATION_TAB = 0
private const val PLAY_TAB = 1
private const val UNSELECTED_TAB_ALPHA = 0.5f

/**
 * The panel that rises over the lower part of the screen for the selected [ayah]. The Translation
 * tab shows the enabled translations with previous and next ayah buttons; the Play tab is
 * [playTab], supplied by :feature:listen through the slots.
 */
@Composable
internal fun AyahPanel(
    ayah: AyahRef,
    chapter: Chapter?,
    block: AyahBlock?,
    settings: ReadSettings,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    playTab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(TRANSLATION_TAB) }
    val field = Awn.field
    Column(
        modifier
            .shadow(AwnSpacing.small, AwnShapes.control)
            .background(field.background, AwnShapes.control)
            .padding(horizontal = AwnSpacing.screenMargin),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TabButton(
                iconRes = R.drawable.ic_read_translate,
                description = stringResource(R.string.panel_translation),
                isSelected = tab == TRANSLATION_TAB,
                onClick = { tab = TRANSLATION_TAB },
            )
            TabButton(
                iconRes = R.drawable.ic_read_tab_play,
                description = stringResource(R.string.panel_play),
                isSelected = tab == PLAY_TAB,
                onClick = { tab = PLAY_TAB },
            )
            Box(Modifier.weight(1f))
            ReadIconButton(
                iconRes = R.drawable.ic_read_close,
                description = stringResource(R.string.panel_close),
                onClick = onClose,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == PLAY_TAB) {
                playTab()
            } else {
                TranslationTab(ayah, chapter, block, settings, onPrevious, onNext)
            }
        }
    }
}

@Composable
private fun TabButton(iconRes: Int, description: String, isSelected: Boolean, onClick: () -> Unit) {
    ReadIconButton(
        iconRes = iconRes,
        description = description,
        onClick = onClick,
        modifier = Modifier.semantics { selected = isSelected },
        tint =
            if (isSelected) Awn.field.buttonFill
            else Awn.field.content.copy(alpha = UNSELECTED_TAB_ALPHA),
    )
}

@Composable
private fun TranslationTab(
    ayah: AyahRef,
    chapter: Chapter?,
    block: AyahBlock?,
    settings: ReadSettings,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    stringResource(
                        R.string.ayah_title,
                        chapter?.localName().orEmpty(),
                        ayah.surah,
                        ayah.ayah,
                    ),
                style = AwnTypography.bodyStrong,
                modifier = Modifier.weight(1f),
            )
            ReadIconButton(
                iconRes = R.drawable.ic_read_ayah_previous,
                description = stringResource(R.string.panel_previous_ayah),
                onClick = onPrevious,
            )
            ReadIconButton(
                iconRes = R.drawable.ic_read_ayah_next,
                description = stringResource(R.string.panel_next_ayah),
                onClick = onNext,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            val bodyStyle =
                AwnTypography.body.copy(
                    fontSize = AwnTypography.body.fontSize * settings.translationScale,
                )
            block?.translations?.forEach { translation ->
                Text(text = translation.text, style = bodyStyle)
                Text(
                    text = translation.name,
                    style = AwnTypography.caption,
                    color = Awn.field.buttonFill,
                )
            }
        }
    }
}
