package app.awn.quran.feature.hifz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.ui.components.AwnSettingsPage
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.mutedContent
import app.awn.quran.core.ui.theme.softFill
import app.awn.quran.feature.hifz.AnswerChoices
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.gradeLabel
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.Journal
import app.awn.quran.feature.hifz.method.JournalEntry
import app.awn.quran.feature.hifz.method.MistakeRecord
import app.awn.quran.feature.hifz.method.PartKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Which saved parts the journal lists. */
internal enum class JournalFilter {
    All,
    NewLessons,
    Revision,
    Mistakes,
}

/** What the journal shows; [today] dates the day headings and the week strip. */
internal data class JournalContent(
    val entries: List<JournalEntry>,
    val mistakes: List<MistakeRecord>,
    val method: HifzMethodId,
    val today: LocalDate,
)

/**
 * Every part saved in the last 60 days, newest first, under day headings, after a strip of the
 * minutes practised on each of the last seven days. Tapping an entry opens its first ayah.
 */
@Composable
internal fun JournalScreen(
    content: JournalContent,
    surahName: (Int) -> String,
    onOpenAyah: (AyahRef) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(JournalFilter.All) }
    AwnSettingsPage(
        title = stringResource(R.string.journal_title),
        backDescription = stringResource(R.string.hifz_back),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AwnSpacing.screenMargin)) {
            AnswerChoices(
                options = JournalFilter.entries,
                selected = filter,
                label = { filterLabel(it) },
                tagFor = { "journal-filter-${it.name.lowercase()}" },
                onSelect = { filter = it },
            )
            WeekStrip(content)
            when {
                content.entries.isEmpty() -> EmptyJournal()
                filter == JournalFilter.Mistakes ->
                    MistakeList(content.mistakes, surahName, onOpenAyah)
                else -> EntryList(content, filter, surahName, onOpenAyah)
            }
        }
    }
}

@Composable
private fun filterLabel(filter: JournalFilter): String =
    stringResource(
        when (filter) {
            JournalFilter.All -> R.string.journal_filter_all
            JournalFilter.NewLessons -> R.string.journal_filter_lessons
            JournalFilter.Revision -> R.string.journal_filter_revision
            JournalFilter.Mistakes -> R.string.journal_filter_mistakes
        },
    )

private val stripHeight = AwnSpacing.giant

// How tall a day off is drawn: a short bar, so the day is still visible.
private const val DAY_OFF_FRACTION = 0.06f

@Composable
private fun WeekStrip(content: JournalContent) {
    val days = Journal.minutesByDay(content.entries, content.today)
    val longest = days.maxOf { it.second }.coerceAtLeast(1)
    val total = Journal.weekOf(content.entries, content.today).practiceMillis
    Text(
        text = stringResource(R.string.journal_week_minutes, durationText(total)),
        style = AwnTypography.bodyStrong,
        color = Awn.field.content,
        modifier = Modifier.padding(top = AwnSpacing.medium),
    )
    Row(
        Modifier.fillMaxWidth().height(stripHeight).padding(top = AwnSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AwnSpacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { (_, minutes) ->
            // A day off is a short soft bar: no red, nothing broken.
            val fraction = (minutes.toFloat() / longest).coerceAtLeast(DAY_OFF_FRACTION)
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(AwnShapes.wordBlock)
                    .background(if (minutes > 0) Awn.field.buttonFill else Awn.field.softFill),
            )
        }
    }
}

@Composable
private fun EmptyJournal() {
    Text(
        text = stringResource(R.string.journal_empty),
        style = AwnTypography.body,
        color = Awn.field.content,
        modifier = Modifier.padding(top = AwnSpacing.large),
    )
}

@Composable
private fun EntryList(
    content: JournalContent,
    filter: JournalFilter,
    surahName: (Int) -> String,
    onOpenAyah: (AyahRef) -> Unit,
) {
    val shown =
        content.entries.filter { entry ->
            when (filter) {
                JournalFilter.NewLessons -> entry.kind == PartKind.NewLesson
                JournalFilter.Revision -> entry.kind != PartKind.NewLesson
                else -> true
            }
        }
    shown
        .groupBy { it.day }
        .forEach { (day, entries) ->
            Heading(dayText(day, content.today), Modifier.padding(top = AwnSpacing.large))
            entries.forEach { entry ->
                EntryRow(entry, content, surahName, onOpenAyah)
            }
        }
}

@Composable
private fun EntryRow(
    entry: JournalEntry,
    content: JournalContent,
    surahName: (Int) -> String,
    onOpenAyah: (AyahRef) -> Unit,
) {
    val first = entry.firstAyah
    val details = buildList {
        add(entryResultText(entry))
        entry.durationMillis?.let { add(durationText(it)) }
        val mistakes = Journal.mistakesDuring(entry, content.mistakes)
        if (mistakes > 0) add(pluralStringResource(R.plurals.journal_mistakes, mistakes, mistakes))
    }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = AwnSpacing.settingsRowTall)
            .clickable(enabled = first != null, role = Role.Button) { first?.let(onOpenAyah) }
            .padding(vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(4.dp)
                .height(AwnSpacing.extraLarge)
                .clip(AwnShapes.wordBlock)
                .background(
                    if (entry.kind == PartKind.NewLesson) Awn.field.tentativeWord
                    else Awn.field.confirmedWord,
                ),
        )
        Spacer(Modifier.width(AwnSpacing.medium))
        Column {
            Text(
                text = entryTitle(entry, content.method, surahName),
                style = AwnTypography.bodyStrong,
                color = Awn.field.content,
            )
            Text(
                text = details.joinToString(" · "),
                style = AwnTypography.small,
                color = Awn.field.mutedContent,
            )
        }
    }
}

@Composable
private fun MistakeList(
    mistakes: List<MistakeRecord>,
    surahName: (Int) -> String,
    onOpenAyah: (AyahRef) -> Unit,
) {
    val byAyah = mistakes.groupingBy { it.ayah }.eachCount().entries.sortedByDescending { it.value }
    byAyah.forEach { (ayah, count) ->
        ActionRow(
            icon = R.drawable.ic_hifz_speaker,
            title =
                stringResource(
                    R.string.weak_spot_title,
                    surahName(ayah.surah),
                    ayah.surah,
                    ayah.ayah,
                ),
            detail = pluralStringResource(R.plurals.journal_mistakes, count, count),
            trailing = null,
            onClick = { onOpenAyah(ayah) },
            modifier = Modifier.padding(top = AwnSpacing.small),
        )
    }
}

@Composable
private fun dayText(day: LocalDate, today: LocalDate): String =
    when (day) {
        today -> stringResource(R.string.journal_today)
        today.minusDays(1) -> stringResource(R.string.journal_yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

/** "Sabaq · Al-Baqarah ayahs 154 to 160" or "Manzil · pages 582 to 586". */
@Composable
internal fun entryTitle(
    entry: JournalEntry,
    method: HifzMethodId,
    surahName: (Int) -> String,
): String {
    val first = entry.firstAyah
    val last = entry.lastAyah
    val part = partTitle(entry.kind, method)
    return if (first != null && last != null) {
        stringResource(
            R.string.journal_entry_title,
            part,
            surahName(first.surah),
            ayahRangeText(first, last),
        )
    } else {
        stringResource(R.string.end_context, part, pagesText(entry.pages))
    }
}

/** "4 of 7 ayahs memorised" or "3 of 5 pages revised"; an older entry gives its grade. */
@Composable
internal fun entryResultText(entry: JournalEntry): String =
    when {
        entry.kind != PartKind.NewLesson ->
            pluralStringResource(
                R.plurals.journal_revision_result,
                entry.pages.size,
                entry.revisedPages,
                entry.pages.size,
            )
        entry.ayahAnswers.isEmpty() -> gradeLabel(entry.grade)
        else ->
            pluralStringResource(
                R.plurals.journal_lesson_result,
                entry.ayahAnswers.size,
                entry.memorisedAyahs,
                entry.ayahAnswers.size,
            )
    }
