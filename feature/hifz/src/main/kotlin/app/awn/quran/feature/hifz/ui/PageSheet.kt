package app.awn.quran.feature.hifz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.hifz.AnswerChoices
import app.awn.quran.feature.hifz.HifzState
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.AyahFirmness
import app.awn.quran.feature.hifz.method.DueRevision
import app.awn.quran.feature.hifz.method.PageRecord
import app.awn.quran.feature.hifz.method.PageStatus
import app.awn.quran.feature.hifz.method.RevisionSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val PAGE_SHEET_TAG = "hifz-page-sheet"

/** Everything the page sheet of the map says about one page. */
internal data class PageFacts(
    val page: Int,
    val status: PageStatus,
    val firstAyah: AyahRef,
    val lastAyah: AyahRef,
    val ayahCount: Int,
    val memorisedAyahs: Int,
    val learnedOn: LocalDate?,
    val lastRevisedOn: LocalDate?,
    val nextRevision: LocalDate?,
    /** True for a known page the Manzil cycle brings back, which has no fixed day. */
    val isInCycle: Boolean,
    val mistakesThisWeek: Int,
) {
    companion object {
        /**
         * The facts of [page]. A known page counts every ayah as memorised; another counts the
         * ayahs whose last saved answer was memorised.
         */
        fun of(state: HifzState, page: Int, latestAnswers: Map<AyahRef, AyahFirmness>): PageFacts {
            val record = state.records[page] ?: PageRecord(page, PageStatus.NotStarted)
            val own = state.table.ayahsStartingOn(page).ifEmpty { state.table.ayahsOn(page) }
            val onPage = state.table.ayahsOn(page)
            val spacing = state.plan.settings.revisionSpacing
            val memorised =
                if (record.status.isKnown) own.size
                else own.count { latestAnswers[it]?.isMemorised == true }
            return PageFacts(
                page = page,
                status = record.status,
                firstAyah = onPage.first(),
                lastAyah = onPage.last(),
                ayahCount = own.size,
                memorisedAyahs = memorised,
                learnedOn = record.learnedOn,
                lastRevisedOn = record.lastRevisedOn,
                nextRevision = DueRevision(spacing, state.today).nextDayOf(record),
                isInCycle = spacing == RevisionSpacing.ManzilCycle && record.status.isKnown,
                mistakesThisWeek = state.recentMistakes.count { it.ayah in onPage },
            )
        }
    }
}

/**
 * The sheet a tap on the map opens: the page's state, how much of it is memorised, its dates and
 * mistakes, a choice to change its state, and a button that opens the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageSheet(
    facts: PageFacts,
    surahName: (Int) -> String,
    onChangeState: (PageStatus) -> Unit,
    onOpenPage: (AyahRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val field = Awn.field
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = field.background,
        contentColor = field.content,
        modifier = Modifier.testTag(PAGE_SHEET_TAG),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AwnSpacing.screenMargin, vertical = AwnSpacing.small),
        ) {
            Text(
                text =
                    stringResource(R.string.page_sheet_title, facts.page, statusName(facts.status)),
                style = AwnTypography.lead,
            )
            Text(
                text =
                    stringResource(
                        R.string.page_sheet_range,
                        surahName(facts.firstAyah.surah),
                        ayahRangeText(facts.firstAyah, facts.lastAyah),
                    ),
                style = AwnTypography.small,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.page_sheet_ayahs,
                        facts.ayahCount,
                        facts.memorisedAyahs,
                        facts.ayahCount,
                    ),
                style = AwnTypography.body,
                modifier = Modifier.padding(top = AwnSpacing.small),
            )
            FactLines(facts)
            Heading(
                stringResource(R.string.page_sheet_change_state),
                Modifier.padding(top = AwnSpacing.medium),
            )
            AnswerChoices(
                options = PageStatus.entries,
                selected = facts.status,
                label = { statusName(it) },
                tagFor = { "hifz-page-state-${it.name.lowercase()}" },
                onSelect = onChangeState,
            )
            AwnButton(
                label = stringResource(R.string.page_sheet_open, facts.page),
                onClick = { onOpenPage(facts.firstAyah) },
                modifier = Modifier.fillMaxWidth().padding(top = AwnSpacing.medium),
            )
        }
    }
}

@Composable
private fun FactLines(facts: PageFacts) {
    FactLine(stringResource(R.string.page_sheet_started), dateText(facts.learnedOn))
    FactLine(
        stringResource(R.string.page_sheet_last_revised),
        dateText(facts.lastRevisedOn),
    )
    FactLine(stringResource(R.string.page_sheet_next_revision), nextRevisionText(facts))
    FactLine(
        stringResource(R.string.page_sheet_mistakes),
        facts.mistakesThisWeek.toString(),
    )
}

@Composable
private fun FactLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AwnSpacing.settingsRowShort),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body)
        Text(text = value, style = AwnTypography.bodyStrong)
    }
}

@Composable
private fun nextRevisionText(facts: PageFacts): String =
    when {
        facts.isInCycle -> stringResource(R.string.page_sheet_in_cycle)
        else -> dateText(facts.nextRevision)
    }

@Composable
private fun dateText(day: LocalDate?): String =
    day?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        ?: stringResource(R.string.page_sheet_none)
