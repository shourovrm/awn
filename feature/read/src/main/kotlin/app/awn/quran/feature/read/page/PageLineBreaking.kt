package app.awn.quran.feature.read.page

/**
 * The tokens that must stay on one line: a word with the end sign that closes its ayah, or a lone
 * token. Keeping the sign with its word means a line never starts with an ayah number.
 */
private typealias UnbreakableChunk = List<LineToken>

/**
 * Returns [rows] unchanged when every printed text line fits in [maxLineWidthEm], with the gaps
 * between its tokens squeezed to [PageGeometry.MIN_GAP_EM] (the narrowest gap the page draws).
 * Otherwise the words of each run of text lines (the lines between two banners) are poured again,
 * in reading order, into new lines of at most [maxLineWidthEm] with the normal gap of
 * [PageGeometry.GAP_EM]: a word goes on the current line while it fits, else it starts the next
 * one. A word never splits, an end sign stays on the line of its ayah's last word, and a banner or
 * Basmalah row keeps its place between the runs. New rows are numbered one after the other from the
 * slot of the first row.
 */
internal fun breakRowsToWidth(
    rows: List<PageRow>,
    tokenWidthEm: (LineToken) -> Float,
    maxLineWidthEm: Float,
): List<PageRow> {
    val textRows = rows.filterIsInstance<PageRow.TextRow>()
    val allFit = textRows.all { row ->
        widthOfTokensEm(row.tokens, tokenWidthEm, PageGeometry.MIN_GAP_EM) <= maxLineWidthEm
    }
    if (allFit || rows.isEmpty()) return rows

    val result = mutableListOf<PageRow>()
    var run = mutableListOf<PageRow.TextRow>()
    var nextSlot = rows.first().slot
    for (row in rows) {
        if (row is PageRow.TextRow) {
            run.add(row)
            continue
        }
        nextSlot = appendBrokenRun(result, run, tokenWidthEm, maxLineWidthEm, nextSlot)
        run = mutableListOf()
        result.add(renumbered(row, nextSlot))
        nextSlot += 1
    }
    appendBrokenRun(result, run, tokenWidthEm, maxLineWidthEm, nextSlot)
    return result
}

/** Adds the lines of [run] poured into [maxLineWidthEm] to [result]; returns the next free slot. */
private fun appendBrokenRun(
    result: MutableList<PageRow>,
    run: List<PageRow.TextRow>,
    tokenWidthEm: (LineToken) -> Float,
    maxLineWidthEm: Float,
    firstSlot: Int,
): Int {
    val chunks = chunksOf(run.flatMap { it.tokens })
    var slot = firstSlot
    var currentLine = mutableListOf<LineToken>()
    for (chunk in chunks) {
        val candidate = currentLine + chunk
        val fits = widthOfTokensEm(candidate, tokenWidthEm, PageGeometry.GAP_EM) <= maxLineWidthEm
        if (!fits && currentLine.isNotEmpty()) {
            result.add(PageRow.TextRow(slot, currentLine))
            slot += 1
            currentLine = mutableListOf()
        }
        currentLine.addAll(chunk)
    }
    if (currentLine.isNotEmpty()) {
        result.add(PageRow.TextRow(slot, currentLine))
        slot += 1
    }
    return slot
}

/** Groups [tokens] so that each end sign joins the token before it. */
private fun chunksOf(tokens: List<LineToken>): List<UnbreakableChunk> {
    val chunks = mutableListOf<MutableList<LineToken>>()
    for (token in tokens) {
        val canJoinPrevious = token is LineToken.RosetteToken && chunks.isNotEmpty()
        if (canJoinPrevious) {
            chunks.last().add(token)
        } else {
            chunks.add(mutableListOf(token))
        }
    }
    return chunks
}

private fun widthOfTokensEm(
    tokens: List<LineToken>,
    tokenWidthEm: (LineToken) -> Float,
    gapEm: Float,
): Float {
    val gapCount = (tokens.size - 1).coerceAtLeast(0)
    return tokens.sumOf { tokenWidthEm(it).toDouble() }.toFloat() + gapCount * gapEm
}

private fun renumbered(row: PageRow, slot: Int): PageRow =
    when (row) {
        is PageRow.HeaderRow -> row.copy(slot = slot)
        is PageRow.BasmalaRow -> row.copy(slot = slot)
        is PageRow.TextRow -> row.copy(slot = slot)
    }
