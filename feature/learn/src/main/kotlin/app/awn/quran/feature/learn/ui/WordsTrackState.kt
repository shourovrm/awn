package app.awn.quran.feature.learn.ui

import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.course.CourseChoices
import app.awn.quran.feature.learn.course.CoursePath
import app.awn.quran.feature.learn.course.PageWords
import app.awn.quran.feature.learn.course.PathInput
import app.awn.quran.feature.learn.course.PathRow
import app.awn.quran.feature.learn.session.PlanStep
import app.awn.quran.feature.learn.words.CourseSnapshot
import app.awn.quran.feature.learn.words.LearnSets
import app.awn.quran.feature.learn.words.PageWordsSource
import app.awn.quran.feature.learn.words.WordStatus

/** The pages the Quran words screen links to. */
internal enum class PageLinkKind {
    LastRead,
    HifzToday,
}

/** A page with how many different words it holds and how many of them the user knows. */
internal data class PageLinkRow(
    val kind: PageLinkKind,
    val page: Int,
    val wordCount: Int,
    val knownCount: Int,
)

/** One word of today's set; [meaning] is null while the meanings are not on the phone. */
internal data class SetWordCell(val rank: Int, val key: WordKey, val meaning: String?)

/** Everything the Quran words screen shows, already worked out. */
internal data class WordsTrackState(
    val hub: HubState,
    val todaySet: Int,
    val todayWords: List<SetWordCell>,
    val pageLinks: List<PageLinkRow>,
    /** The sets and grammar lessons of the path; the Reading and Basics lessons are left out. */
    val path: List<PathRow>,
    /** The first words of each set in [path], for the one-line preview. */
    val previewKeys: Map<Int, List<WordKey>>,
) {
    /** Every word the screen draws in Arabic, so the texts load in one go. */
    val allKeys: Set<WordKey>
        get() = todayWords.map { it.key }.toSet() + previewKeys.values.flatten()
}

private const val PREVIEW_WORD_COUNT = 5

internal suspend fun buildWordsTrackState(
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
    choices: CourseChoices,
    hub: HubState,
    links: LearnPageLinks,
    quranText: QuranTextRepository?,
): WordsTrackState {
    val path =
        CoursePath.build(
            PathInput(
                content = lookups.content,
                frequency = lookups.frequency,
                metRanks = snapshot.wordCards.keys,
                knownRanks = snapshot.knownRanks.toSet(),
                doneLessonIds = snapshot.doneLessonIds,
                includeLetters = choices.includeLetters,
                plan = hub.plan,
                includeStartLessons = false,
            ),
        )
    val todaySet = todaySetNumber(hub, snapshot, lookups)
    return WordsTrackState(
        hub = hub,
        todaySet = todaySet,
        todayWords = setWordCells(lookups, todaySet, hub.meanings == MeaningsState.Ready),
        pageLinks = pageLinkRows(lookups, snapshot, links, quranText),
        path = path,
        previewKeys = previewKeysOf(lookups, path),
    )
}

/** The set of the first new word in today's plan, else the first set not yet fully met. */
private fun todaySetNumber(hub: HubState, snapshot: CourseSnapshot, lookups: CourseLookups): Int {
    val firstNewRank = hub.plan.steps.filterIsInstance<PlanStep.NewWord>().firstOrNull()?.rank
    if (firstNewRank != null) return LearnSets.numberOf(firstNewRank)
    var number = 1
    while (isSetMet(number, snapshot, lookups)) number++
    return number
}

private fun isSetMet(number: Int, snapshot: CourseSnapshot, lookups: CourseLookups): Boolean {
    val ranks = LearnSets.ranksOf(number)
    val formCount = lookups.frequency.formCount
    return ranks.first <= formCount && ranks.all { it in snapshot.wordCards || it > formCount }
}

private suspend fun setWordCells(
    lookups: CourseLookups,
    setNumber: Int,
    withMeanings: Boolean,
): List<SetWordCell> =
    LearnSets.ranksOf(setNumber)
        .filter { it <= lookups.frequency.formCount }
        .map { rank ->
            SetWordCell(
                rank = rank,
                key = lookups.frequency.representative(rank),
                meaning = if (withMeanings) lookups.infoSource.info(rank)?.meaning else null,
            )
        }

private fun previewKeysOf(lookups: CourseLookups, path: List<PathRow>): Map<Int, List<WordKey>> {
    val numbers = path.flatMap { row ->
        when (row) {
            is PathRow.SetStep -> listOf(row.number)
            is PathRow.Later -> listOf(row.fromSet)
            else -> emptyList()
        }
    }
    return numbers.associateWith { number ->
        LearnSets.ranksOf(number)
            .filter { it <= lookups.frequency.formCount }
            .take(PREVIEW_WORD_COUNT)
            .map { lookups.frequency.representative(it) }
    }
}

private suspend fun pageLinkRows(
    lookups: CourseLookups,
    snapshot: CourseSnapshot,
    links: LearnPageLinks,
    quranText: QuranTextRepository?,
): List<PageLinkRow> {
    if (quranText == null) return emptyList()
    val source = PageWordsSource(quranText)
    return listOfNotNull(
            links.lastReadPage?.let { PageLinkKind.LastRead to it },
            links.hifzTodayPage?.let { PageLinkKind.HifzToday to it },
        )
        .map { (kind, page) ->
            val keys = source.wordsOnPage(page, links.script).keys
            val list = PageWords.build(keys, lookups.frequency, snapshot)
            val known = list.rows.count { it.status == WordStatus.Known }
            PageLinkRow(kind, page, list.rows.size, known)
        }
}
