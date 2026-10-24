package app.awn.quran.feature.learn

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Snippet
import app.awn.quran.feature.learn.content.snippetReference
import app.awn.quran.feature.learn.progress.ProgressCalculator
import app.awn.quran.feature.learn.progress.ProgressSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One lesson as the lists show it. */
data class LessonSummary(
    val id: String,
    val title: String,
    val exampleCount: Int,
    val isDraft: Boolean,
)

/** The day's snippet: Arabic text and where it comes from. */
data class SnippetLine(val arabic: String, val reference: String)

data class LearnUiState(
    val streakDays: Int,
    val pagesThisWeek: Int,
    val ayahsDueToday: Int,
    val lessons: List<LessonSummary>,
    val snippet: SnippetLine?,
)

/** One ayah to revise. [text] is null when the Quran text is not available. */
data class RevisionCard(val ayah: AyahRef, val label: String, val text: String?)

data class RevisionUiState(
    val current: RevisionCard?,
    val remaining: Int,
    val total: Int,
    val isTextShown: Boolean,
)

/** One of the last seven days on the Reading progress screen; [label] is the short weekday. */
data class WeekDay(val label: String, val isActive: Boolean, val isToday: Boolean, val pages: Int)

/** The Reading progress screen. [days] are oldest first and end today. */
data class ProgressUiState(
    val streakDays: Int,
    val days: List<WeekDay>,
    val pagesThisWeek: Int,
    val memorisedCount: Int,
    val wordsKnown: Int,
    val quranWordsPercent: Int,
    val nextRevisionDay: String?,
    val nextRevisionCount: Int,
) {
    /**
     * True until the user has read, memorised or learned anything, which shows the first-use card.
     */
    val isNewUser: Boolean
        get() = streakDays == 0 && pagesThisWeek == 0 && memorisedCount == 0 && wordsKnown == 0
}

/** The ayahs due today from one surah; [firstAyah] to [lastAyah] is the span they cover. */
data class DueSurah(val name: String, val firstAyah: Int, val lastAyah: Int, val dueCount: Int)

/** One day of the strip that shows what is coming; [label] is the short weekday name. */
data class UpcomingDay(val label: String, val dueCount: Int)

/**
 * The Revise screen. [nextRevisionDay] is the weekday name, or a date when the day is more than a
 * week away; it is null when no ayah has been memorised yet.
 */
data class ReviseUiState(
    val dueCount: Int,
    val dueSurahs: List<DueSurah>,
    val upcomingDays: List<UpcomingDay>,
    val nextRevisionDay: String?,
    val nextRevisionCount: Int,
) {
    val isSomethingDue: Boolean
        get() = dueCount > 0

    /** A strip of seven empty days says nothing, so it is drawn only when something is coming. */
    val hasUpcoming: Boolean
        get() = upcomingDays.any { it.dueCount > 0 }
}

object LearnUiMapper {

    /** How many lessons the Learn screen lists; the rest sit behind "All lessons". */
    const val LESSONS_ON_LEARN_SCREEN = 4

    fun learnState(
        snapshot: ProgressSnapshot,
        lessons: List<LessonSummary>,
        snippet: Snippet?,
        language: LearnLanguage,
        surahName: (surah: Int) -> String,
    ): LearnUiState =
        LearnUiState(
            streakDays = snapshot.streakDays,
            pagesThisWeek = snapshot.pagesThisWeek,
            ayahsDueToday = snapshot.dueToday.size,
            lessons = lessons.take(LESSONS_ON_LEARN_SCREEN),
            snippet =
                snippet?.let {
                    SnippetLine(it.arabic, snippetReference(it.source, language, surahName))
                },
        )

    // Each argument is a different figure of the screen: the snapshot, two word figures, the day
    // and how to write it.
    @Suppress("LongParameterList")
    fun progressState(
        snapshot: ProgressSnapshot,
        wordsKnown: Int,
        quranWordsShare: Double,
        today: LocalDate,
        zone: ZoneId,
        locale: Locale,
    ): ProgressUiState {
        val week = ProgressCalculator.lastSevenDays(today)
        val days = week.mapIndexed { index, day ->
            WeekDay(
                label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                isActive = day in snapshot.activeDaysThisWeek,
                isToday = day == today,
                pages = snapshot.pagesEachDayThisWeek[index],
            )
        }
        return ProgressUiState(
            streakDays = snapshot.streakDays,
            days = days,
            pagesThisWeek = snapshot.pagesThisWeek,
            memorisedCount = snapshot.memorisedCount,
            wordsKnown = wordsKnown,
            quranWordsPercent = (quranWordsShare * PERCENT).roundToInt(),
            nextRevisionDay = nextRevisionDay(snapshot, today, zone, locale),
            nextRevisionCount = snapshot.nextDueAyahs.size,
        )
    }

    fun reviseState(
        snapshot: ProgressSnapshot,
        surahName: (Int) -> String,
        today: LocalDate,
        zone: ZoneId,
        locale: Locale,
    ): ReviseUiState {
        val dueSurahs =
            snapshot.dueToday
                .groupBy { it.surah }
                .toSortedMap()
                .map { (surah, ayahs) ->
                    DueSurah(
                        name = surahName(surah),
                        firstAyah = ayahs.minOf { it.ayah },
                        lastAyah = ayahs.maxOf { it.ayah },
                        dueCount = ayahs.size,
                    )
                }
        val upcomingDays =
            snapshot.dueNextSevenDays.mapIndexed { index, dueCount ->
                val day = today.plusDays(index + 1L)
                UpcomingDay(day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), dueCount)
            }
        return ReviseUiState(
            dueCount = snapshot.dueToday.size,
            dueSurahs = dueSurahs,
            upcomingDays = upcomingDays,
            nextRevisionDay = nextRevisionDay(snapshot, today, zone, locale),
            nextRevisionCount = snapshot.nextDueAyahs.size,
        )
    }

    /** The weekday for a day inside the coming week, which the strip also shows; else the date. */
    fun dayName(day: LocalDate, today: LocalDate, locale: Locale): String {
        val daysAhead = day.toEpochDay() - today.toEpochDay()
        return if (daysAhead in 1..WEEK_LENGTH_DAYS) {
            day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        } else {
            day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }

    private fun nextRevisionDay(
        snapshot: ProgressSnapshot,
        today: LocalDate,
        zone: ZoneId,
        locale: Locale,
    ): String? = snapshot.nextDue?.let { dayName(it.atZone(zone).toLocalDate(), today, locale) }

    private const val WEEK_LENGTH_DAYS = 7L
    private const val PERCENT = 100
}
