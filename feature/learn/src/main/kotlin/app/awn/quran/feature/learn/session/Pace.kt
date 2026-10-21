package app.awn.quran.feature.learn.session

/** What one step of a daily session is, for timing. The text values are stored, so never change. */
enum class StepKind(val stored: String) {
    Review("review"),
    NewWord("new_word"),
    Lesson("lesson");

    companion object {
        fun fromStored(stored: String): StepKind = entries.first { it.stored == stored }
    }
}

/** How long one finished step took, in milliseconds. */
data class StepTiming(val kind: StepKind, val millis: Long)

/**
 * Seconds a user needs for one step of each kind. A new word includes the question that checks it
 * at the end of the set, so a set of ten new words costs ten times [secondsPerNewWord].
 */
data class Pace(
    val secondsPerReview: Double,
    val secondsPerNewWord: Double,
    val secondsPerLesson: Double,
) {
    companion object {
        val Default =
            Pace(
                secondsPerReview = DEFAULT_SECONDS_PER_REVIEW,
                secondsPerNewWord = DEFAULT_SECONDS_PER_NEW_WORD,
                secondsPerLesson = DEFAULT_SECONDS_PER_LESSON,
            )

        // The starting values the user asked for: about 10 s a review, 30 s a new word and two to
        // three minutes for a lesson.
        const val DEFAULT_SECONDS_PER_REVIEW = 10.0
        const val DEFAULT_SECONDS_PER_NEW_WORD = 30.0
        const val DEFAULT_SECONDS_PER_LESSON = 150.0
    }
}

/**
 * Measures the pace of one user from the timings of their latest steps. A kind with fewer than
 * [MINIMUM_SAMPLES] timings keeps its default, because a few quick taps say little. The median of
 * the latest [WINDOW] timings is used, so one question left open while the phone was put down does
 * not slow every later plan. A measured value stays within a factor of three of the default, so a
 * broken timing cannot plan a session of two questions or of two hundred.
 */
object PaceMeasurement {
    const val MINIMUM_SAMPLES = 5
    const val WINDOW = 30
    private const val LIMIT_FACTOR = 3.0
    private const val MILLIS_PER_SECOND = 1000.0

    fun estimate(timings: List<StepTiming>, defaults: Pace = Pace.Default): Pace =
        Pace(
            secondsPerReview = measured(timings, StepKind.Review, defaults.secondsPerReview),
            secondsPerNewWord = measured(timings, StepKind.NewWord, defaults.secondsPerNewWord),
            secondsPerLesson = measured(timings, StepKind.Lesson, defaults.secondsPerLesson),
        )

    private fun measured(timings: List<StepTiming>, kind: StepKind, default: Double): Double {
        val latest = timings.filter { it.kind == kind }.takeLast(WINDOW)
        if (latest.size < MINIMUM_SAMPLES) return default
        val sorted = latest.map { it.millis }.sorted()
        val middle = sorted.size / 2
        val medianMillis =
            if (sorted.size % 2 == 1) {
                sorted[middle].toDouble()
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2.0
            }
        return (medianMillis / MILLIS_PER_SECOND).coerceIn(default / LIMIT_FACTOR, default * LIMIT_FACTOR)
    }
}
