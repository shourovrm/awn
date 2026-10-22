package app.awn.quran.feature.hifz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.AwnSettingsPage
import app.awn.quran.core.ui.components.SettingsStepperRow
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.PageTable

private const val SURAH_COUNT = 114

/**
 * Picks the last ayah the user has memorised: a surah from a list and an ayah number with a
 * stepper. The plan's next lesson then starts after it.
 */
@Composable
internal fun KnownUpToScreen(
    table: PageTable,
    surahName: (Int) -> String,
    onSave: (AyahRef) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var surah by remember { mutableIntStateOf(1) }
    var ayahNumber by remember { mutableIntStateOf(1) }
    val ayahCount = table.allAyahs.count { it.surah == surah }

    AwnSettingsPage(
        title = stringResource(R.string.known_up_to_title),
        backDescription = stringResource(R.string.hifz_back),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            AwnChoiceRow(
                label = stringResource(R.string.mark_surah),
                options = (1..SURAH_COUNT).toList(),
                selected = surah,
                optionLabel = { "$it. ${surahName(it)}" },
                onSelect = {
                    surah = it
                    ayahNumber = 1
                },
            )
            SettingsStepperRow(
                label = stringResource(R.string.known_up_to_ayah),
                valueText = ayahNumber.toString(),
                decreaseDescription = stringResource(R.string.ayah_stepper_decrease),
                increaseDescription = stringResource(R.string.ayah_stepper_increase),
                onDecrease = { ayahNumber -= 1 },
                onIncrease = { ayahNumber += 1 },
                canDecrease = ayahNumber > 1,
                canIncrease = ayahNumber < ayahCount,
            )
            AwnButton(
                label = stringResource(R.string.mark_save),
                onClick = {
                    onSave(AyahRef(surah, ayahNumber))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.medium),
            )
        }
    }
}
