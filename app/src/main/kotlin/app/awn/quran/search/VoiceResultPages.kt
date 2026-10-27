package app.awn.quran.search

/** Rows the voice results show per page; the next page loads as the user scrolls to the end. */
const val VOICE_RESULT_PAGE_SIZE = 15

/** The rows of the first [pageCount] pages; a list shorter than that is returned whole. */
fun <T> visibleVoiceRows(rows: List<T>, pageCount: Int): List<T> =
    rows.take(pageCount * VOICE_RESULT_PAGE_SIZE)
