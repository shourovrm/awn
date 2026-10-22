package app.awn.quran.feature.learn.course

/**
 * One visit to the practice of a unit: the drawn [items], the item on screen ([position], from 0)
 * and the option picked for each item. The first pick of an item counts, as in a check.
 */
data class PracticeRun(
    val items: List<PracticeItem>,
    val position: Int = 0,
    val picks: Map<Int, Int> = emptyMap(),
) {
    val isFinished: Boolean
        get() = position >= items.size

    val current: PracticeItem?
        get() = items.getOrNull(position)

    /** The option picked for the item on screen, or null while it is unanswered. */
    val currentPick: Int?
        get() = picks[position]

    /** The questions of the run; a read-aloud item is not one, nothing in it is right or wrong. */
    val questionCount: Int
        get() = items.count { it is PracticeItem.Question }

    val rightCount: Int
        get() = picks.count { (index, pick) ->
            (items[index] as? PracticeItem.Question)?.check?.correctIndex == pick
        }

    fun pick(optionIndex: Int): PracticeRun =
        if (isFinished || position in picks) this
        else copy(picks = picks + (position to optionIndex))

    /** Marks the read-aloud item on screen as done. */
    fun finishReading(): PracticeRun = pick(READ_DONE)

    /** Moves to the next item; an unanswered item stays where it is. */
    fun next(): PracticeRun = if (currentPick == null) this else copy(position = position + 1)
}

private const val READ_DONE = 0
