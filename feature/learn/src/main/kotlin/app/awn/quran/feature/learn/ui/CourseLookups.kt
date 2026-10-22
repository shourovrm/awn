package app.awn.quran.feature.learn.ui

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.course.CourseChoices
import app.awn.quran.feature.learn.course.CourseContent
import app.awn.quran.feature.learn.course.CoursePath
import app.awn.quran.feature.learn.course.LearnTrack
import app.awn.quran.feature.learn.course.LearnTracks
import app.awn.quran.feature.learn.course.PathInput
import app.awn.quran.feature.learn.course.PathRow
import app.awn.quran.feature.learn.course.TajweedLessonTitle
import app.awn.quran.feature.learn.course.TrackInput
import app.awn.quran.feature.learn.session.TodayPlan
import app.awn.quran.feature.learn.session.WordInfoSource
import app.awn.quran.feature.learn.words.CourseSnapshot
import app.awn.quran.feature.learn.words.WordFrequency
import app.awn.quran.feature.learn.words.WordProgress
import app.awn.quran.feature.learn.words.WordStatus
import java.time.Duration

/** The generated course data and the figures every course screen builds its state from. */
internal class CourseLookups(
    val content: CourseContent,
    val frequency: WordFrequency,
    val infoSource: WordInfoSource,
    val languageCode: String,
)

// The hub reads one value from each source; a parameter object would only rename them.
@Suppress("LongParameterList")
internal suspend fun buildHubState(
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
    plan: TodayPlan,
    choices: CourseChoices,
    dueAyahs: Int,
    meanings: MeaningsState,
    tajweedLessons: List<TajweedLessonTitle>,
): HubState {
    val path =
        CoursePath.build(
            PathInput(
                content = lookups.content,
                frequency = lookups.frequency,
                metRanks = snapshot.wordCards.keys,
                knownRanks = snapshot.knownRanks.toSet(),
                doneLessonIds = snapshot.doneLessonIds,
                includeLetters = choices.includeLetters,
                plan = plan,
            ),
        )
    return HubState(
        plan = plan,
        sessionMinutes = choices.sessionMinutes,
        path = path,
        lessonTitles = lessonTitles(lookups),
        triggerMeanings = triggerMeanings(lookups, path),
        wordsMet = snapshot.wordsMet,
        wordsKnown = snapshot.wordsKnown,
        coverage = WordProgress.coverage(snapshot.knownRanks, lookups.frequency),
        dueAyahs = dueAyahs,
        meanings = meanings,
        tracks =
            listOf(LearnTrack.Reading, LearnTrack.Tajweed, LearnTrack.Grammar).map {
                LearnTracks.summary(it, trackInput(lookups, snapshot, choices, tajweedLessons))
            },
    )
}

internal fun trackInput(
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
    choices: CourseChoices,
    tajweedLessons: List<TajweedLessonTitle>,
): TrackInput =
    TrackInput(
        content = lookups.content,
        languageCode = lookups.languageCode,
        tajweedLessons = tajweedLessons,
        doneLessonIds = snapshot.doneLessonIds,
        includeLetters = choices.includeLetters,
    )

private fun lessonTitles(lookups: CourseLookups): Map<String, String> =
    lookups.content.lessons.associate { it.id to it.text(lookups.languageCode).title }

// A grammar lesson sits after the set that brought it; the row names the word that did.
private suspend fun triggerMeanings(
    lookups: CourseLookups,
    path: List<PathRow>,
): Map<String, Pair<WordKey, String>> =
    path
        .filterIsInstance<PathRow.LessonStep>()
        .mapNotNull { row -> triggerMeaning(lookups, row) }
        .toMap()

private suspend fun triggerMeaning(
    lookups: CourseLookups,
    row: PathRow.LessonStep,
): Pair<String, Pair<WordKey, String>>? {
    val rank = row.triggerRank ?: return null
    return lookups.infoSource.info(rank)?.let { info ->
        row.lessonId to (lookups.frequency.representative(rank) to info.meaning)
    }
}

/** One row per form, most frequent first; the meaning is empty when the phone has none. */
internal suspend fun buildWordListRows(
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
): List<WordListRow> =
    (1..lookups.frequency.formCount).map { rank ->
        WordListRow(
            rank = rank,
            key = lookups.frequency.representative(rank),
            meaning = lookups.infoSource.info(rank)?.meaning.orEmpty(),
            status = WordProgress.statusOf(snapshot.wordCards[rank]),
        )
    }

private const val SHOWN_AYAH_COUNT = 3

internal suspend fun buildWordDetails(
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
    rank: Int,
    nowMillis: Long,
): WordDetails {
    val card = snapshot.wordCards[rank]
    val status = WordProgress.statusOf(card)
    val ayahs =
        lookups.frequency
            .occurrences(rank)
            .map { AyahRef(it.surah, it.ayah) }
            .distinct()
            .take(SHOWN_AYAH_COUNT)
    return WordDetails(
        rank = rank,
        key = lookups.frequency.representative(rank),
        meaning = lookups.infoSource.info(rank)?.meaning.orEmpty(),
        status = status,
        occurrences = lookups.frequency.occurrenceCount(rank),
        backInDays =
            card?.takeIf { status == WordStatus.Known }?.let { daysUntil(it.due, nowMillis) },
        ayahs = ayahs,
        isQueued = rank in snapshot.queuedRanks,
    )
}

private fun daysUntil(dueMillis: Long, nowMillis: Long): Int {
    val days = Duration.ofMillis(dueMillis - nowMillis).toDays().toInt()
    return days.coerceAtLeast(1)
}
