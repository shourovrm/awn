package app.awn.quran.tracker

/**
 * The state of every loaded word and the rules for changing it. A green word stays green, except
 * when its audio is found to belong to the same word of the next ayah ([markLeftOutAfterAll]). A
 * red word turns green only when it is said right later, and then [TrackerEvent.Corrected] tells
 * why. Tentative and Unread words carry no mark and may take any verdict.
 */
internal class WordMarks {

    var states: Array<WordState> = emptyArray()
        private set

    fun reset(size: Int) {
        states = Array(size) { WordState.Neutral }
    }

    /** Makes room for appended words; the new ones are Neutral. */
    fun grow(size: Int) {
        val old = states
        states = Array(size) { old.getOrElse(it) { WordState.Neutral } }
    }

    /** Keeps the words before [index] and clears the rest. */
    fun keepBefore(index: Int) {
        val old = states
        states = Array(old.size) { if (it < index) old[it] else WordState.Neutral }
    }

    fun confirm(word: Int, events: MutableList<TrackerEvent>) {
        val old = states[word]
        if (old == WordState.Confirmed) return
        change(word, WordState.Confirmed, events)
        if (old == WordState.Wrong) events += TrackerEvent.Corrected(word)
    }

    /**
     * Marks a word the user went past without saying it right; [leftOut] when nothing was heard.
     */
    fun markWrong(word: Int, leftOut: Boolean, events: MutableList<TrackerEvent>) {
        if (states[word] == WordState.Confirmed || states[word] == WordState.Wrong) return
        change(word, WordState.Wrong, events)
        if (leftOut) events += TrackerEvent.Skipped(word)
    }

    /**
     * Marks a word left out even though it was green: its audio belonged to the same word of the
     * next ayah (see SkipRules).
     */
    fun markLeftOutAfterAll(word: Int, events: MutableList<TrackerEvent>) {
        if (states[word] != WordState.Wrong) change(word, WordState.Wrong, events)
        events += TrackerEvent.Skipped(word)
    }

    fun markUnread(word: Int, events: MutableList<TrackerEvent>) {
        if (states[word] == WordState.Neutral || states[word] == WordState.Tentative) {
            change(word, WordState.Unread, events)
        }
    }

    fun markTentative(word: Int, events: MutableList<TrackerEvent>) {
        if (states[word] == WordState.Neutral || states[word] == WordState.Unread) {
            change(word, WordState.Tentative, events)
        }
    }

    private fun change(word: Int, state: WordState, events: MutableList<TrackerEvent>) {
        states[word] = state
        events += TrackerEvent.WordChanged(word, state)
    }
}
