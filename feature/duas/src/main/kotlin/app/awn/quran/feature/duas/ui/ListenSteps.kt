package app.awn.quran.feature.duas.ui

private val REPEAT_STEPS = listOf(1, 3, 5)

/** The play count after [current] in the cycle 1, 3, 5; any other count goes back to 1. */
internal fun nextRepeatCount(current: Int): Int {
    val position = REPEAT_STEPS.indexOf(current)
    return if (position < 0) REPEAT_STEPS.first()
    else REPEAT_STEPS[(position + 1) % REPEAT_STEPS.size]
}
