package app.awn.quran.feature.hifz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.AwnSettingsPage
import app.awn.quran.core.ui.components.SettingsStepperRow
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.PageTable

private enum class MarkBy {
    Surah,
    Juz,
    Pages,
}

private const val SURAH_COUNT = 114
private const val JUZ_COUNT = 30

/**
 * "Mark what I know": pick surahs, juz or a run of pages and say how well they are known. It is the
 * way in for people with no method and for people who memorised before using the app.
 */
// One page of rows and one button; splitting it would only pass its state around.
@Suppress("LongMethod")
@Composable
internal fun MarkKnownScreen(
    table: PageTable,
    surahName: (Int) -> String,
    onSave: (IntRange, PageStatus) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var by by remember { mutableStateOf(MarkBy.Surah) }
    var surah by remember { mutableIntStateOf(1) }
    var juz by remember { mutableIntStateOf(1) }
    var fromPage by remember { mutableIntStateOf(1) }
    var toPage by remember { mutableIntStateOf(1) }
    var status by remember { mutableStateOf(PageStatus.Memorised) }

    AwnSettingsPage(
        title = stringResource(R.string.mark_title),
        backDescription = stringResource(R.string.hifz_back),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        ) {
            AwnChoiceRow(
                label = stringResource(R.string.mark_by),
                options = MarkBy.entries,
                selected = by,
                optionLabel = { markByText(it) },
                onSelect = { by = it },
            )
            when (by) {
                MarkBy.Surah ->
                    AwnChoiceRow(
                        label = stringResource(R.string.mark_surah),
                        options = (1..SURAH_COUNT).toList(),
                        selected = surah,
                        optionLabel = { "$it. ${surahName(it)}" },
                        onSelect = { surah = it },
                    )
                MarkBy.Juz ->
                    AwnChoiceRow(
                        label = stringResource(R.string.mark_juz),
                        options = (1..JUZ_COUNT).toList(),
                        selected = juz,
                        optionLabel = { stringResource(R.string.juz_label, it) },
                        onSelect = { juz = it },
                    )
                MarkBy.Pages -> {
                    PageStepper(
                        label = stringResource(R.string.mark_from_page),
                        page = fromPage,
                        pageCount = table.pageCount,
                        onChange = {
                            fromPage = it
                            if (toPage < it) toPage = it
                        },
                    )
                    PageStepper(
                        label = stringResource(R.string.mark_to_page),
                        page = toPage,
                        pageCount = table.pageCount,
                        onChange = {
                            toPage = it
                            if (fromPage > it) fromPage = it
                        },
                    )
                }
            }
            AwnChoiceRow(
                label = stringResource(R.string.mark_state),
                options = PageStatus.entries,
                selected = status,
                optionLabel = { statusName(it) },
                onSelect = { status = it },
            )
            AwnButton(
                label = stringResource(R.string.mark_save),
                onClick = {
                    val range =
                        when (by) {
                            MarkBy.Surah -> table.pagesOfSurah(surah)
                            MarkBy.Juz -> table.pagesOfJuz(juz)
                            MarkBy.Pages -> fromPage..toPage
                        }
                    onSave(range, status)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.medium),
            )
        }
    }
}

@Composable
private fun markByText(by: MarkBy): String =
    stringResource(
        when (by) {
            MarkBy.Surah -> R.string.mark_surah
            MarkBy.Juz -> R.string.mark_juz
            MarkBy.Pages -> R.string.mark_pages
        },
    )

@Composable
private fun PageStepper(label: String, page: Int, pageCount: Int, onChange: (Int) -> Unit) {
    SettingsStepperRow(
        label = label,
        valueText = page.toString(),
        decreaseDescription = stringResource(R.string.stepper_decrease),
        increaseDescription = stringResource(R.string.stepper_increase),
        onDecrease = { onChange(page - 1) },
        onIncrease = { onChange(page + 1) },
        canDecrease = page > 1,
        canIncrease = page < pageCount,
    )
}
