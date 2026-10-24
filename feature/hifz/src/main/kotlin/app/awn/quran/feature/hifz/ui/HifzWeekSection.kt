package app.awn.quran.feature.hifz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.hifz.R
import app.awn.quran.feature.hifz.method.JournalEntry
import app.awn.quran.feature.hifz.method.WeekSummary

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** Three plain numbers for the last seven days; absent until something was saved this week. */
@Composable
internal fun WeekSection(week: WeekSummary) {
    if (week.isEmpty) return
    Heading(stringResource(R.string.week_title), Modifier.padding(top = AwnSpacing.medium))
    WeekLine(stringResource(R.string.week_new_ayahs), week.newAyahs.toString())
    WeekLine(stringResource(R.string.week_pages_revised), week.pagesRevised.toString())
    WeekLine(
        stringResource(R.string.week_practice),
        pluralStringResource(
            R.plurals.week_practice_days,
            week.practiceDays,
            durationText(week.practiceMillis),
            week.practiceDays,
        ),
    )
}

@Composable
private fun WeekLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AwnSpacing.touchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AwnTypography.body, color = Awn.field.content)
        Text(text = value, style = AwnTypography.bodyStrong, color = Awn.field.content)
    }
}

/** The Journal row; its second line names the newest saved part. */
@Composable
internal fun JournalRow(latest: JournalEntry?, onOpen: () -> Unit) {
    Column(Modifier.padding(top = AwnSpacing.small)) {
        ActionRow(
            icon = R.drawable.ic_hifz_journal,
            title = stringResource(R.string.journal_title),
            detail = latest?.let { entryResultText(it) },
            trailing = null,
            onClick = onOpen,
        )
    }
}

/** "1 h 5 min", or "25 min" under an hour. */
@Composable
internal fun durationText(millis: Long): String {
    val minutes = millis / MILLIS_PER_MINUTE
    return if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.minutes_short, minutes.toInt())
    } else {
        stringResource(
            R.string.duration_hours_minutes,
            (minutes / MINUTES_PER_HOUR).toInt(),
            (minutes % MINUTES_PER_HOUR).toInt(),
        )
    }
}
