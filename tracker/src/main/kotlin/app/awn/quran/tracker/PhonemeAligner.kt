package app.awn.quran.tracker

/** What happened at one step of an alignment between heard and expected phonemes. */
internal enum class AlignmentStep {
    /** The heard phoneme equals the expected one. */
    Match,

    /** The heard phoneme differs from the expected one at the same place. */
    Substitution,

    /** A heard phoneme has no expected counterpart (extra sound). */
    Insertion,

    /** An expected phoneme has no heard counterpart (left out). */
    Deletion,
}

/**
 * One step of an alignment. [heardIndex] is the position in the heard sequence and [expectedIndex]
 * the position in the expected sequence; the one that does not take part in the step is -1.
 */
internal data class AlignmentOperation(
    val step: AlignmentStep,
    val heardIndex: Int,
    val expectedIndex: Int,
)

/** Scores for the alignment. Gaps cost an opening price plus an extension price per phoneme. */
internal object AlignmentScoring {
    const val MATCH = 4
    const val SUBSTITUTION = -3

    // A heard phoneme with no expected counterpart (extra sound).
    const val INSERTION_OPEN = -5
    const val INSERTION_EXTEND = -1

    // An expected phoneme with no heard counterpart. Extending is cheap so that a whole omitted
    // ayah costs little more than an omitted word.
    const val DELETION_OPEN = -5
    const val DELETION_EXTEND = 0

    const val IMPOSSIBLE = Int.MIN_VALUE / 2
}

/**
 * Aligns heard phonemes to expected phonemes with the Needleman-Wunsch dynamic programme and affine
 * gap costs (Gotoh's three-matrix form). The alignment is anchored at the start of the expected
 * sequence and must consume every heard phoneme, but it may stop anywhere in the expected sequence,
 * because the user has not recited the rest yet.
 */
internal object PhonemeAligner {

    // Which of the three matrices an optimal path was in before the current cell.
    private const val FROM_MATCH_STATE: Byte = 0
    private const val FROM_INSERTION_STATE: Byte = 1
    private const val FROM_DELETION_STATE: Byte = 2

    fun align(heard: IntArray, expected: IntArray): List<AlignmentOperation> {
        if (heard.isEmpty()) return emptyList()
        val table = ScoreTable(heard.size, expected.size)
        fillTable(table, heard, expected)
        return traceBack(table, bestEndColumn(table))
    }

    private class ScoreTable(val heardLength: Int, val expectedLength: Int) {
        private val width = expectedLength + 1
        private val cellCount = (heardLength + 1) * width

        // Best score of a path that ends with a (mis)match, an insertion or a deletion.
        val matchScore = IntArray(cellCount) { AlignmentScoring.IMPOSSIBLE }
        val insertionScore = IntArray(cellCount) { AlignmentScoring.IMPOSSIBLE }
        val deletionScore = IntArray(cellCount) { AlignmentScoring.IMPOSSIBLE }

        // For every cell and matrix, the matrix the path came from.
        val matchOrigin = ByteArray(cellCount)
        val insertionOrigin = ByteArray(cellCount)
        val deletionOrigin = ByteArray(cellCount)

        fun cell(heardCount: Int, expectedCount: Int) = heardCount * width + expectedCount

        fun best(cell: Int) = maxOf(matchScore[cell], insertionScore[cell], deletionScore[cell])

        fun bestOrigin(cell: Int): Byte {
            val best = best(cell)
            return when (best) {
                matchScore[cell] -> FROM_MATCH_STATE
                insertionScore[cell] -> FROM_INSERTION_STATE
                else -> FROM_DELETION_STATE
            }
        }
    }

    private fun fillTable(table: ScoreTable, heard: IntArray, expected: IntArray) {
        table.matchScore[table.cell(0, 0)] = 0
        for (heardCount in 0..heard.size) {
            for (expectedCount in 0..expected.size) {
                if (heardCount == 0 && expectedCount == 0) continue
                val cell = table.cell(heardCount, expectedCount)
                if (heardCount > 0 && expectedCount > 0) {
                    fillMatchState(table, cell, heard[heardCount - 1], expected[expectedCount - 1])
                }
                if (heardCount > 0) {
                    fillInsertionState(table, cell, table.cell(heardCount - 1, expectedCount))
                }
                if (expectedCount > 0) {
                    fillDeletionState(table, cell, table.cell(heardCount, expectedCount - 1))
                }
            }
        }
    }

    private fun fillMatchState(table: ScoreTable, cell: Int, heard: Int, expected: Int) {
        val diagonalCell = cell - table.expectedLength - 2
        val pairScore =
            if (heard == expected) AlignmentScoring.MATCH else AlignmentScoring.SUBSTITUTION
        table.matchScore[cell] = table.best(diagonalCell) + pairScore
        table.matchOrigin[cell] = table.bestOrigin(diagonalCell)
    }

    private fun fillInsertionState(table: ScoreTable, cell: Int, cellAbove: Int) {
        val opened = table.best(cellAbove) + AlignmentScoring.INSERTION_OPEN
        val extended = table.insertionScore[cellAbove] + AlignmentScoring.INSERTION_EXTEND
        if (extended > opened) {
            table.insertionScore[cell] = extended
            table.insertionOrigin[cell] = FROM_INSERTION_STATE
        } else {
            table.insertionScore[cell] = opened
            table.insertionOrigin[cell] = table.bestOrigin(cellAbove)
        }
    }

    private fun fillDeletionState(table: ScoreTable, cell: Int, cellToLeft: Int) {
        val opened = table.best(cellToLeft) + AlignmentScoring.DELETION_OPEN
        val extended = table.deletionScore[cellToLeft] + AlignmentScoring.DELETION_EXTEND
        if (extended > opened) {
            table.deletionScore[cell] = extended
            table.deletionOrigin[cell] = FROM_DELETION_STATE
        } else {
            table.deletionScore[cell] = opened
            table.deletionOrigin[cell] = table.bestOrigin(cellToLeft)
        }
    }

    // Among equal scores the shortest stretch of the expected text wins, so the tracker never
    // credits more of the reference than the evidence needs.
    private fun bestEndColumn(table: ScoreTable): Int {
        var bestColumn = 0
        var bestScore = AlignmentScoring.IMPOSSIBLE
        for (expectedCount in 0..table.expectedLength) {
            val score = table.best(table.cell(table.heardLength, expectedCount))
            if (score > bestScore) {
                bestScore = score
                bestColumn = expectedCount
            }
        }
        return bestColumn
    }

    private fun traceBack(table: ScoreTable, endColumn: Int): List<AlignmentOperation> {
        val reversed = mutableListOf<AlignmentOperation>()
        var heardCount = table.heardLength
        var expectedCount = endColumn
        var state = table.bestOrigin(table.cell(heardCount, expectedCount))
        while (heardCount > 0 || expectedCount > 0) {
            val cell = table.cell(heardCount, expectedCount)
            when (state) {
                FROM_MATCH_STATE -> {
                    val step = matchOrSubstitution(table, cell)
                    reversed += AlignmentOperation(step, heardCount - 1, expectedCount - 1)
                    state = table.matchOrigin[cell]
                    heardCount--
                    expectedCount--
                }
                FROM_INSERTION_STATE -> {
                    reversed += AlignmentOperation(AlignmentStep.Insertion, heardCount - 1, -1)
                    state = table.insertionOrigin[cell]
                    heardCount--
                }
                else -> {
                    reversed += AlignmentOperation(AlignmentStep.Deletion, -1, expectedCount - 1)
                    state = table.deletionOrigin[cell]
                    expectedCount--
                }
            }
        }
        return reversed.asReversed()
    }

    // A matched pair raises the score by MATCH, a substituted one lowers it; the table does not
    // store which happened, so read it back from the score difference.
    private fun matchOrSubstitution(table: ScoreTable, cell: Int): AlignmentStep {
        val diagonalCell = cell - table.expectedLength - 2
        val gain = table.matchScore[cell] - table.best(diagonalCell)
        return if (gain == AlignmentScoring.MATCH) AlignmentStep.Match
        else AlignmentStep.Substitution
    }

    /** The stretch of the expected sequence that best explains a run of heard phonemes. */
    data class LocalMatch(
        val score: Int,
        val heardStart: Int,
        val expectedStart: Int,
        val expectedEnd: Int,
    )

    /**
     * Finds where [heard] fits best anywhere inside [expected] (Smith-Waterman local alignment with
     * the same scores), used to find the place again after the tracker lost it. Returns null when
     * nothing scores above zero.
     */
    // Smith-Waterman is one loop nest with one branch per recurrence case.
    @Suppress("CyclomaticComplexMethod")
    fun findBestPlace(heard: IntArray, expected: IntArray): LocalMatch? {
        if (heard.isEmpty() || expected.isEmpty()) return null
        val width = expected.size + 1
        val scores = IntArray((heard.size + 1) * width)
        val starts = IntArray((heard.size + 1) * width)
        val heardStarts = IntArray((heard.size + 1) * width)
        var bestScore = 0
        var bestEnd = -1
        var bestStart = -1
        var bestHeardStart = -1
        for (heardCount in 1..heard.size) {
            for (expectedCount in 1..expected.size) {
                val cell = heardCount * width + expectedCount
                val diagonal = cell - width - 1
                val pairScore =
                    if (heard[heardCount - 1] == expected[expectedCount - 1]) {
                        AlignmentScoring.MATCH
                    } else {
                        AlignmentScoring.SUBSTITUTION
                    }
                val fromDiagonal = scores[diagonal] + pairScore
                val fromAbove = scores[cell - width] + AlignmentScoring.INSERTION_OPEN
                val fromLeft = scores[cell - 1] + AlignmentScoring.DELETION_OPEN
                val best = maxOf(0, fromDiagonal, fromAbove, fromLeft)
                scores[cell] = best
                val origin =
                    when {
                        best == 0 -> cell
                        best == fromDiagonal && scores[diagonal] == 0 -> -1
                        best == fromDiagonal -> diagonal
                        best == fromAbove -> cell - width
                        else -> cell - 1
                    }
                when {
                    best == 0 -> {
                        starts[cell] = expectedCount
                        heardStarts[cell] = heardCount
                    }
                    origin == -1 -> {
                        starts[cell] = expectedCount - 1
                        heardStarts[cell] = heardCount - 1
                    }
                    else -> {
                        starts[cell] = starts[origin]
                        heardStarts[cell] = heardStarts[origin]
                    }
                }
                if (best > bestScore) {
                    bestScore = best
                    bestEnd = expectedCount
                    bestStart = starts[cell]
                    bestHeardStart = heardStarts[cell]
                }
            }
        }
        return if (bestEnd < 0) null else LocalMatch(bestScore, bestHeardStart, bestStart, bestEnd)
    }
}
