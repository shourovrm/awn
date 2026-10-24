package app.awn.quran.feature.duas.ui

private val REPEAT_STEPS = listOf(1, 3, 5)
private val SPEED_STEPS = listOf(1f, 1.25f, 1.5f, 0.75f)

/** The play count after [current] in the cycle 1, 3, 5; any other count goes back to 1. */
internal fun nextRepeatCount(current: Int): Int {
    val position = REPEAT_STEPS.indexOf(current)
    return if (position < 0) REPEAT_STEPS.first() else REPEAT_STEPS[(position + 1) % REPEAT_STEPS.size]
}

/** The speed after [current] in the cycle 1, 1.25, 1.5, 0.75; any other speed goes back to 1. */
internal fun nextSpeed(current: Float): Float {
    val position = SPEED_STEPS.indexOf(current)
    return if (position < 0) SPEED_STEPS.first() else SPEED_STEPS[(position + 1) % SPEED_STEPS.size]
}

/** "1", "1.25" or "0.75": a speed without trailing zeros, for the label next to the multiplier. */
internal fun speedText(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
