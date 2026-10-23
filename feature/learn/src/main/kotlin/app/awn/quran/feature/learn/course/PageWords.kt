package app.awn.quran.feature.learn.course

import app.awn.quran.core.data.MyWordMark
import app.awn.quran.core.data.WordKey
import app.awn.quran.feature.learn.words.CourseSnapshot
import app.awn.quran.feature.learn.words.WordCourseRepository
import app.awn.quran.feature.learn.words.WordFrequency
import app.awn.quran.feature.learn.words.WordProgress
import app.awn.quran.feature.learn.words.WordStatus

/** The chips of the words-on-a-page screen. */
enum class PageWordFilter {
    All,
    New,
    Learning,
    Known,
}

/** One distinct word form on a page; [key] is its first place on that page. */
data class PageWordRow(
    val rank: Int,
    val key: WordKey,
    val occurrences: Int,
    val status: WordStatus,
    val isQueued: Boolean,
)

/** Every distinct word form of one page, in the order the page first uses them. */
data class PageWordList(val rows: List<PageWordRow>) {

    fun countOf(filter: PageWordFilter): Int = rowsFor(filter).size

    fun rowsFor(filter: PageWordFilter): List<PageWordRow> =
        when (filter) {
            PageWordFilter.All -> rows
            PageWordFilter.New -> rows.filter { it.status == WordStatus.NotMet }
            PageWordFilter.Learning -> rows.filter { it.status == WordStatus.Learning }
            PageWordFilter.Known -> rows.filter { it.status == WordStatus.Known }
        }

    /** The new forms that "Learn new" still has to queue: not met and not queued yet. */
    val ranksToQueue: List<Int>
        get() = rows.filter { it.status == WordStatus.NotMet && !it.isQueued }.map { it.rank }
}

object PageWords {

    /**
     * Groups [keys], the words of a page in reading order, by form. A form that occurs twice on the
     * page is listed once, at its first place.
     */
    fun build(
        keys: List<WordKey>,
        frequency: WordFrequency,
        snapshot: CourseSnapshot,
    ): PageWordList {
        val firstKeyOfRank = LinkedHashMap<Int, WordKey>()
        for (key in keys) firstKeyOfRank.getOrPut(frequency.rankOf(key)) { key }
        val rows = firstKeyOfRank.map { (rank, key) ->
            PageWordRow(
                rank = rank,
                key = key,
                occurrences = frequency.occurrenceCount(rank),
                status = WordProgress.statusOf(snapshot.wordCards[rank]),
                isQueued = rank in snapshot.queuedRanks,
            )
        }
        return PageWordList(rows)
    }

    /**
     * What the Quran page marks: [MyWordMark.Known] for a known form and [MyWordMark.Learning] for
     * a met one. A form the user has not met has no mark, also when it is queued.
     */
    fun marks(
        keys: Collection<WordKey>,
        frequency: WordFrequency,
        snapshot: CourseSnapshot,
    ): Map<WordKey, MyWordMark> {
        val marks = HashMap<WordKey, MyWordMark>()
        for (key in keys) {
            val mark = markOf(WordProgress.statusOf(snapshot.wordCards[frequency.rankOf(key)]))
            if (mark != null) marks[key] = mark
        }
        return marks
    }

    private fun markOf(status: WordStatus): MyWordMark? =
        when (status) {
            WordStatus.Known -> MyWordMark.Known
            WordStatus.Learning -> MyWordMark.Learning
            WordStatus.NotMet -> null
        }
}

/** Queues the new forms of [list], so they come first in the next sets; returns how many. */
suspend fun WordCourseRepository.queueNewWords(list: PageWordList): Int {
    val ranks = list.ranksToQueue
    ranks.forEach { queueWord(it) }
    return ranks.size
}
