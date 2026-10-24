package app.awn.quran.feature.hifz

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.PartGrade
import app.awn.quran.feature.hifz.method.PartKind
import app.awn.quran.feature.hifz.ui.pagesText
import app.awn.quran.feature.hifz.ui.partTitle

const val END_PART_SHEET_TAG = "end-part-sheet"
const val END_PART_SAVE_TAG = "end-part-save"
const val END_PART_KEEP_TAG = "end-part-keep"

/** A choice button's tag is this prefix, the row key, a dash and the choice name in lower case. */
const val END_PART_CHOICE_TAG_PREFIX = "end-part-choice-"

/**
 * The sheet End part opens over the page: one row per ayah of a lesson or per page of a revision,
 * each with its answer, then Save and Keep practising pinned under the list. [context] names the
 * part and its range; [ayahText] gives the first words of an ayah when the page has them loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EndPartSheet(
    answers: EndPartAnswers,
    context: String,
    ayahText: (AyahRef) -> String?,
    onChange: (EndPartAnswers) -> Unit,
    onSave: () -> Unit,
    onKeepPractising: () -> Unit,
) {
    val field = Awn.field
    ModalBottomSheet(
        onDismissRequest = onKeepPractising,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = field.background,
        contentColor = field.content,
        modifier = Modifier.testTag(END_PART_SHEET_TAG),
    ) {
        Column(Modifier.padding(horizontal = AwnSpacing.screenMargin)) {
            Text(text = context, style = AwnTypography.small)
            Text(text = summaryText(answers), style = AwnTypography.lead)
        }
        LazyColumn(
            Modifier.weight(1f, fill = false).padding(horizontal = AwnSpacing.screenMargin),
        ) {
            when (answers) {
                is EndPartAnswers.Lesson ->
                    items(answers.ayahs) { ayah ->
                        AyahAnswerRow(ayah, answers, ayahText(ayah)) {
                            onChange(answers.with(ayah, it))
                        }
                    }
                is EndPartAnswers.Revision ->
                    items(answers.pages) { page ->
                        PageAnswerRow(page, answers) { onChange(answers.with(page, it)) }
                    }
            }
        }
        PinnedActions(answers, onSave, onKeepPractising)
    }
}

@Composable
private fun summaryText(answers: EndPartAnswers): String =
    when (answers) {
        is EndPartAnswers.Lesson ->
            pluralStringResource(
                R.plurals.end_lesson_summary,
                answers.ayahs.size,
                answers.memorisedCount,
                answers.ayahs.size,
            )
        is EndPartAnswers.Revision ->
            pluralStringResource(
                R.plurals.end_revision_summary,
                answers.pages.size,
                answers.revisedCount,
                answers.pages.size,
            )
    }

@Composable
private fun AyahAnswerRow(
    ayah: AyahRef,
    answers: EndPartAnswers.Lesson,
    arabic: String?,
    onSelect: (AyahFirmness) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = ayahRangeText(ayah, ayah), style = AwnTypography.bodyStrong)
            Spacer(Modifier.width(AwnSpacing.medium))
            arabic?.let {
                Text(
                    text = it,
                    style = AwnTypography.searchResultArabic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AnswerChoices(
            options = AyahFirmness.entries,
            selected = answers.answers[ayah] ?: AyahFirmness.NotYet,
            label = { firmnessLabel(it) },
            tagFor = { "${END_PART_CHOICE_TAG_PREFIX}${ayah.surah}-${ayah.ayah}-${it.storedName}" },
            onSelect = onSelect,
        )
        answers.notes[ayah]?.let { RecitationNoteText(it) }
    }
}

@Composable
private fun PageAnswerRow(
    page: Int,
    answers: EndPartAnswers.Revision,
    onSelect: (PartGrade?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = AwnSpacing.small)) {
        Text(text = stringResource(R.string.bar_page, page), style = AwnTypography.bodyStrong)
        AnswerChoices(
            options = listOf(null) + PartGrade.entries,
            selected = answers.answers[page],
            label = { grade ->
                grade?.let { gradeLabel(it) } ?: stringResource(R.string.firmness_not_yet)
            },
            tagFor = {
                "${END_PART_CHOICE_TAG_PREFIX}page$page-${it?.name?.lowercase() ?: "not_yet"}"
            },
            onSelect = onSelect,
        )
        answers.notes[page]?.let { RecitationNoteText(it) }
    }
}

@Composable
private fun RecitationNoteText(note: RecitationNote) {
    val text =
        when (note) {
            RecitationNote.NotRecited -> stringResource(R.string.end_not_recited)
            is RecitationNote.WordsWrong ->
                if (note.count == 0) stringResource(R.string.end_no_mistakes)
                else pluralStringResource(R.plurals.end_words_wrong, note.count, note.count)
        }
    Text(text = text, style = AwnTypography.caption)
}

@Composable
private fun PinnedActions(
    answers: EndPartAnswers,
    onSave: () -> Unit,
    onKeepPractising: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
    ) {
        if (answers is EndPartAnswers.Lesson) {
            val nextStart = answers.nextStart
            Text(
                text =
                    if (nextStart != null)
                        stringResource(R.string.end_next_start, ayahRangeText(nextStart, nextStart))
                    else stringResource(R.string.end_lesson_complete),
                style = AwnTypography.small,
                modifier = Modifier.padding(bottom = AwnSpacing.small),
            )
        }
        AwnButton(
            label = stringResource(R.string.end_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().testTag(END_PART_SAVE_TAG),
        )
        TextButton(
            onClick = onKeepPractising,
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = AwnSpacing.touchTarget)
                    .testTag(END_PART_KEEP_TAG),
        ) {
            Text(
                text = stringResource(R.string.end_keep_practising),
                style = AwnTypography.bodyStrong,
                color = Awn.field.buttonFill,
            )
        }
    }
}

@Composable
internal fun firmnessLabel(firmness: AyahFirmness): String =
    stringResource(
        when (firmness) {
            AyahFirmness.NotYet -> R.string.firmness_not_yet
            AyahFirmness.Shaky -> R.string.firmness_shaky
            AyahFirmness.Firm -> R.string.firmness_firm
            AyahFirmness.VeryFirm -> R.string.firmness_very_firm
        },
    )

@Composable
internal fun endPartContext(request: PracticeRequest): String {
    val range =
        if (request.kind == PartKind.NewLesson) ayahRangeText(request.firstAyah, request.lastAyah)
        else pagesText(request.pages)
    return stringResource(R.string.end_context, partTitle(request.kind, request.method), range)
}
