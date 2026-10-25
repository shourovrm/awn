package app.awn.quran.tracker

/**
 * Follows a recitation word by word with a cursor: the word the user is expected to say next.
 *
 * Each time phonemes arrive, the heard phonemes that no word has taken yet are fitted against a few
 * candidates only: the cursor word, the two words after it, the cursor word joined with the next,
 * and the words just passed ([CursorDecisions]). A word is green ([WordState.Confirmed]) when it
 * passes on cost, coverage and closure ([WordJudge]); its audio is then gone. A word the cursor
 * jumps over is red ([WordState.Wrong]) only once a later word proves the jump ([SkipRules]), and
 * at most two words go red in one step, except a whole ayah left out. A word being said stays
 * [WordState.Tentative] (no fill), and a mismatch alone never colours anything. Only after a long
 * stretch of audio fits nowhere near the cursor does [LostSearch] look through all loaded words,
 * and it moves the cursor only to a place that is clearly the only match; the words a forward jump
 * passes are [WordState.Unread].
 *
 * Whether a green word was also pronounced well is not decided here: substitutions and held vowels
 * are reported as events, and the recite session shows the word as mispronounced.
 */
class AlignmentTracker(config: TrackerConfig = TrackerConfig()) : Tracker {

    private val tables = ReferenceTables()
    private val machine = CursorMachine(tables)
    private var startIndex = 0
    private var hasHeardRecitation = false

    init {
        setConfig(config)
    }

    override fun load(reference: List<ReferenceWord>, startIndex: Int) {
        this.startIndex = startIndex
        tables.clearSymbols()
        tables.rebuild(reference)
        reset()
    }

    // The symbol numbers stay as they are, because the heard phonemes are stored as those numbers.
    override fun append(words: List<ReferenceWord>) {
        if (words.isEmpty()) return
        tables.rebuild(tables.words + words)
        machine.marks.grow(tables.size)
    }

    override fun reset() {
        machine.marks.reset(tables.size)
        machine.restartAt(startIndex)
        hasHeardRecitation = false
    }

    override fun continueFrom(index: Int) {
        startIndex = index.coerceIn(0, tables.size)
        machine.marks.keepBefore(startIndex)
        machine.restartAt(startIndex)
        hasHeardRecitation = false
    }

    override fun setConfig(config: TrackerConfig) {
        machine.thresholds = AlignmentThresholds.forStrictness(config.strictness)
    }

    override fun stateOfWord(index: Int): WordState =
        machine.marks.states.getOrElse(index) { WordState.Neutral }

    override fun currentIndex(): Int = machine.cursor

    override fun onPhonemes(events: List<PhonemeEvent>): List<TrackerEvent> {
        val reported = mutableListOf<TrackerEvent>()
        val tokens = events.filter { it.symbol != BLANK_SYMBOL }
        if (tables.size == 0 || tokens.isEmpty()) return reported
        val lastOnset = machine.heard.lastOnset()
        // A pause longer than a breath closes a word waiting for its last consonant.
        if (lastOnset != null && tokens.first().onsetSeconds - lastOnset > BREATH_SECONDS) {
            machine.closeHeldWord(reported)
        }
        var addedCount = 0
        for (token in tokens) {
            machine.durations.observe(token)
            for (character in token.symbol) {
                if (machine.heard.add(tables.idOf(character.toString()), token.onsetSeconds)) {
                    addedCount++
                }
            }
        }
        val isJudging = addedCount > 0 && (hasHeardRecitation || recitationHasStarted())
        if (isJudging) machine.advance(reported)
        return reported
    }

    override fun closePending(): List<TrackerEvent> {
        val reported = mutableListOf<TrackerEvent>()
        machine.closeHeldWord(reported)
        return reported
    }

    // A few stray phonemes from room sound align to the first words by chance and would colour
    // them. Nothing is judged until the heard sequence matches the start of the text near the
    // cursor well enough to be recitation; until then only the latest phonemes are kept.
    private fun recitationHasStarted(): Boolean {
        val cursor = machine.cursor
        if (cursor >= tables.size) return false
        val heardIds = machine.heard.toIntArray()
        val expected = tables.idsOfWords(cursor, entryWindowEnd(tables, cursor) - 1)
        val operations = PhonemeAligner.align(heardIds, expected)
        val matches = operations.count { it.step == AlignmentStep.Match }
        // Chance matches need gaps in the expected text to line up; a user who starts at the
        // first word leaves almost none.
        val lastMatchedOffset =
            operations.lastOrNull { it.step == AlignmentStep.Match }?.expectedIndex ?: -1
        val leftOutBeforeLastMatch = operations.count {
            it.step == AlignmentStep.Deletion && it.expectedIndex < lastMatchedOffset
        }
        val isRecitation =
            matches >= machine.thresholds.entryMatches &&
                matches >= ENTRY_MINIMUM_MATCH_SHARE * heardIds.size &&
                leftOutBeforeLastMatch <= ENTRY_MAXIMUM_LEFT_OUT
        if (isRecitation) {
            hasHeardRecitation = true
        } else if (heardIds.size > ENTRY_BUFFER_PHONEMES) {
            machine.heard.drop(heardIds.size - ENTRY_BUFFER_PHONEMES)
        }
        return isRecitation
    }

    private companion object {
        const val BLANK_SYMBOL = "<blank>"
        const val BREATH_SECONDS = 0.6f

        // Before the first judgement, at least this share of the heard phonemes must match ...
        const val ENTRY_MINIMUM_MATCH_SHARE = 0.7f

        // ... with at most this many expected phonemes left out among them ...
        const val ENTRY_MAXIMUM_LEFT_OUT = 2

        // ... and only this many of the latest heard phonemes are kept while waiting.
        const val ENTRY_BUFFER_PHONEMES = 16
    }
}

private const val AYAHS_AHEAD_IN_ENTRY_WINDOW = 2

// The entry check looks at the ayah of [cursor] and the two after it; returns one past its last
// word.
private fun entryWindowEnd(tables: ReferenceTables, cursor: Int): Int {
    val lastAyah = tables.ayahSequenceOf(cursor) + AYAHS_AHEAD_IN_ENTRY_WINDOW
    var end = cursor
    while (end < tables.size && tables.ayahSequenceOf(end) <= lastAyah) end++
    return end
}
