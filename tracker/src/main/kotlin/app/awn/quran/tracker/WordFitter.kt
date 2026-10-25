package app.awn.quran.tracker

/**
 * Finds where one word, or a few words in a row, sits inside the heard phonemes. Every expected
 * phoneme is used (matched, substituted or left out), while heard phonemes before and after the
 * word are free: the user may have said something else first, and goes on with the next word.
 *
 * It is the dynamic programme of [PhonemeAligner] with the same scores and affine gaps (Gotoh's
 * three matrices), with a free start on the heard side and a free end on the heard side. Among
 * equal scores the earliest end wins.
 */
internal object WordFitter {

    private const val FROM_MATCH: Byte = 0
    private const val FROM_INSERTION: Byte = 1
    private const val FROM_DELETION: Byte = 2

    /**
     * Returns the operations of the best fit of [expected] inside [heard], from the first expected
     * phoneme to the last, with heard indexes into [heard]. Empty when [expected] is empty.
     */
    fun fit(heard: IntArray, expected: IntArray): List<AlignmentOperation> {
        if (expected.isEmpty()) return emptyList()
        val table = FitTable(heard.size, expected.size)
        fill(table, heard, expected)
        var endRow = 0
        for (heardCount in 1..heard.size) {
            if (
                table.best(table.cell(heardCount, expected.size)) >
                    table.best(table.cell(endRow, expected.size))
            ) {
                endRow = heardCount
            }
        }
        return traceBack(table, endRow, expected.size)
    }

    /** The three score matrices of [fit], and for each cell the matrix its best path came from. */
    private class FitTable(val heardLength: Int, val expectedLength: Int) {
        private val width = expectedLength + 1
        private val cellCount = (heardLength + 1) * width
        val match = IntArray(cellCount) { AlignmentScoring.IMPOSSIBLE }
        val insertion = IntArray(cellCount) { AlignmentScoring.IMPOSSIBLE }
        val deletion = IntArray(cellCount) { AlignmentScoring.IMPOSSIBLE }
        val matchOrigin = ByteArray(cellCount)
        val insertionOrigin = ByteArray(cellCount)
        val deletionOrigin = ByteArray(cellCount)

        fun cell(heardCount: Int, expectedCount: Int) = heardCount * width + expectedCount

        fun best(cell: Int) = maxOf(match[cell], insertion[cell], deletion[cell])

        fun bestOrigin(cell: Int): Byte =
            when (best(cell)) {
                match[cell] -> FROM_MATCH
                insertion[cell] -> FROM_INSERTION
                else -> FROM_DELETION
            }
    }

    private fun fill(table: FitTable, heard: IntArray, expected: IntArray) {
        // Any number of heard phonemes may come before the word at no cost.
        for (heardCount in 0..heard.size) table.match[table.cell(heardCount, 0)] = 0
        for (heardCount in 0..heard.size) {
            for (expectedCount in 1..expected.size) {
                val cell = table.cell(heardCount, expectedCount)
                if (heardCount > 0) {
                    val diagonal = table.cell(heardCount - 1, expectedCount - 1)
                    val pairScore =
                        if (heard[heardCount - 1] == expected[expectedCount - 1]) {
                            AlignmentScoring.MATCH
                        } else {
                            AlignmentScoring.SUBSTITUTION
                        }
                    table.match[cell] = table.best(diagonal) + pairScore
                    table.matchOrigin[cell] = table.bestOrigin(diagonal)
                    fillGap(
                        table,
                        cell,
                        table.cell(heardCount - 1, expectedCount),
                        isInsertion = true,
                    )
                }
                fillGap(table, cell, table.cell(heardCount, expectedCount - 1), isInsertion = false)
            }
        }
    }

    // An insertion comes from the cell above (one more heard phoneme), a deletion from the cell to
    // the left (one more expected phoneme).
    private fun fillGap(table: FitTable, cell: Int, previous: Int, isInsertion: Boolean) {
        val scores = if (isInsertion) table.insertion else table.deletion
        val origins = if (isInsertion) table.insertionOrigin else table.deletionOrigin
        val extendCost =
            if (isInsertion) AlignmentScoring.INSERTION_EXTEND else AlignmentScoring.DELETION_EXTEND
        val openCost =
            if (isInsertion) AlignmentScoring.INSERTION_OPEN else AlignmentScoring.DELETION_OPEN
        val opened = table.best(previous) + openCost
        val extended = scores[previous] + extendCost
        if (extended > opened) {
            scores[cell] = extended
            origins[cell] = if (isInsertion) FROM_INSERTION else FROM_DELETION
        } else {
            scores[cell] = opened
            origins[cell] = table.bestOrigin(previous)
        }
    }

    private fun traceBack(table: FitTable, endRow: Int, endColumn: Int): List<AlignmentOperation> {
        val reversed = mutableListOf<AlignmentOperation>()
        var heardCount = endRow
        var expectedCount = endColumn
        var state = table.bestOrigin(table.cell(heardCount, expectedCount))
        while (expectedCount > 0) {
            val cell = table.cell(heardCount, expectedCount)
            when (state) {
                FROM_MATCH -> {
                    val diagonal = table.cell(heardCount - 1, expectedCount - 1)
                    val gain = table.match[cell] - table.best(diagonal)
                    val step =
                        if (gain == AlignmentScoring.MATCH) AlignmentStep.Match
                        else AlignmentStep.Substitution
                    reversed += AlignmentOperation(step, heardCount - 1, expectedCount - 1)
                    state = table.matchOrigin[cell]
                    heardCount--
                    expectedCount--
                }
                FROM_INSERTION -> {
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
}
