package app.awn.quran.feature.learn

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Word
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Lesson
import app.awn.quran.feature.learn.content.LessonExample
import app.awn.quran.feature.learn.content.Snippet
import app.awn.quran.feature.learn.progress.ProgressSnapshot
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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

data class SurahProgress(val surah: Int, val name: String, val memorisedCount: Int)

data class ProgressUiState(
    val streakDays: Int,
    val pagesThisWeek: Int,
    val memorisedCount: Int,
    val memorisationThreshold: Int,
    val bySurah: List<SurahProgress>,
    val nextRevisionDate: String?,
)

/** One word of an example ayah; [isHighlighted] marks the words that carry the rule. */
data class ExampleWord(val text: String, val isHighlighted: Boolean)

/** One lesson example with its ayah text, which is empty when the Quran text is not available. */
data class ExampleLine(val reference: String, val words: List<ExampleWord>, val note: String)

object LearnUiMapper {

    /** How many lessons the Learn screen lists; the rest sit behind "All lessons". */
    const val LESSONS_ON_LEARN_SCREEN = 4

    fun lessonSummaries(lessons: List<Lesson>, language: LearnLanguage): List<LessonSummary> =
        lessons.map { lesson ->
            LessonSummary(
                id = lesson.id,
                title = lesson.title(language),
                exampleCount = lesson.examples.size,
                isDraft = lesson.isDraft,
            )
        }

    fun learnState(
        snapshot: ProgressSnapshot,
        lessons: List<LessonSummary>,
        snippet: Snippet?,
    ): LearnUiState =
        LearnUiState(
            pagesThisWeek = snapshot.pagesThisWeek,
            ayahsDueToday = snapshot.dueToday.size,
            lessons = lessons.take(LESSONS_ON_LEARN_SCREEN),
            snippet = snippet?.let { SnippetLine(it.arabic, it.reference) },
        )

    fun progressState(
        snapshot: ProgressSnapshot,
        surahName: (Int) -> String,
        zone: ZoneId,
        locale: Locale,
    ): ProgressUiState {
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return ProgressUiState(
            streakDays = snapshot.streakDays,
            pagesThisWeek = snapshot.pagesThisWeek,
            memorisedCount = snapshot.memorisedCount,
            memorisationThreshold = snapshot.memorisationThreshold,
            bySurah =
                snapshot.memorisedBySurah.map { (surah, count) ->
                    SurahProgress(surah, surahName(surah), count)
                },
            nextRevisionDate = snapshot.nextDue?.atZone(zone)?.toLocalDate()?.format(dateFormat),
        )
    }

    /**
     * Builds the example block of a lesson. [ayahWords] is empty when the Quran text is not
     * available; the reference and the note are shown without the text then.
     */
    fun exampleLine(
        example: LessonExample,
        language: LearnLanguage,
        surahName: String,
        ayahWords: List<Word>,
    ): ExampleLine =
        ExampleLine(
            reference = "$surahName ${example.surah}:${example.ayah}",
            words =
                ayahWords.map { word ->
                    ExampleWord(
                        text = word.textUthmani,
                        isHighlighted = word.key.wordPosition in example.wordPositions,
                    )
                },
            note = example.note(language),
        )
}
