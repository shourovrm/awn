package app.awn.quran.feature.hifz.method

import app.awn.quran.core.data.AyahRef
import java.time.LocalDate

/**
 * One saved part as the journal shows it. [ayahAnswers] holds what the user said of each ayah of a
 * new lesson and [pageGrades] the grade of each page reached in a revision; both are empty for
 * parts saved before the app kept them, which were always whole parts.
 */
data class JournalEntry(
    val day: LocalDate,
    val atMillis: Long,
    val kind: PartKind,
    val pages: List<Int>,
    val firstAyah: AyahRef?,
    val lastAyah: AyahRef?,
    val grade: PartGrade,
    val isComplete: Boolean,
    val durationMillis: Long?,
    val ayahAnswers: Map<AyahRef, AyahFirmness>,
    val pageGrades: Map<Int, PartGrade>,
) {
    val memorisedAyahs: Int
        get() = ayahAnswers.values.count { it.isMemorised }

    /** The pages graded; an older revision without page grades covered all of its pages. */
    val revisedPages: Int
        get() =
            when {
                kind == PartKind.NewLesson -> 0
                pageGrades.isEmpty() -> pages.size
                else -> pageGrades.size
            }
}

/**
 * The plain numbers of the last seven days on the Hifz tab; no streak, so a day off costs nothing.
 */
data class WeekSummary(
    val newAyahs: Int,
    val pagesRevised: Int,
    val practiceMillis: Long,
    val practiceDays: Int,
) {
    val isEmpty: Boolean
        get() = practiceDays == 0

    companion object {
        val EMPTY = WeekSummary(0, 0, 0, 0)
    }
}

object Journal {
    const val WEEK_DAYS = 7L

    /** The summary of the seven days that end [today]. */
    fun weekOf(entries: List<JournalEntry>, today: LocalDate): WeekSummary {
        val firstDay = today.minusDays(WEEK_DAYS - 1)
        val week = entries.filter { it.day in firstDay..today }
        return WeekSummary(
            newAyahs = week.sumOf { it.memorisedAyahs },
            pagesRevised = week.sumOf { it.revisedPages },
            practiceMillis = week.sumOf { it.durationMillis ?: 0L },
            practiceDays = week.map { it.day }.distinct().size,
        )
    }

    /** The minutes practised on each of the seven days that end [today], oldest first. */
    fun minutesByDay(entries: List<JournalEntry>, today: LocalDate): List<Pair<LocalDate, Long>> =
        (WEEK_DAYS - 1 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo)
            val millis = entries.filter { it.day == day }.sumOf { it.durationMillis ?: 0L }
            day to millis / MILLIS_PER_MINUTE
        }

    /** The mistakes flagged while [entry] was practised, from its start to its save. */
    fun mistakesDuring(entry: JournalEntry, mistakes: List<MistakeRecord>): Int {
        val start = entry.atMillis - (entry.durationMillis ?: 0L)
        return mistakes.count { it.atMillis in start..entry.atMillis }
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}
