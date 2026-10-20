package app.awn.quran.player

/**
 * A reciter the app can play. [wordByWord] is true only when verified word timings exist, so
 * word-level seeking is offered for exactly those reciters.
 */
data class Reciter(val id: String, val displayName: String, val wordByWord: Boolean) {
    init {
        require(id.isNotBlank()) { "A reciter needs an id." }
        require(displayName.isNotBlank()) { "Reciter '$id' needs a display name." }
    }
}
