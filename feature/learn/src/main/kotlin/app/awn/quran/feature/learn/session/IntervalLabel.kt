package app.awn.quran.feature.learn.session

/** The unit an interval under a rating button is written in. */
enum class IntervalUnit {
    Day,
    Week,
    Month,
    Year,
}

/** A whole number of [unit]s: 1 d, 4 d, 2 wk, 3 mo, 1 yr. */
data class IntervalLabel(val count: Int, val unit: IntervalUnit) {

    companion object {
        private const val DAYS_PER_WEEK = 7
        private const val DAYS_PER_MONTH = 30
        private const val DAYS_PER_YEAR = 365

        /** The largest unit that fits [days] at least once, rounded down; under a week is days. */
        fun of(days: Int): IntervalLabel =
            when {
                days >= DAYS_PER_YEAR -> IntervalLabel(days / DAYS_PER_YEAR, IntervalUnit.Year)
                days >= DAYS_PER_MONTH -> IntervalLabel(days / DAYS_PER_MONTH, IntervalUnit.Month)
                days >= DAYS_PER_WEEK -> IntervalLabel(days / DAYS_PER_WEEK, IntervalUnit.Week)
                else -> IntervalLabel(days.coerceAtLeast(1), IntervalUnit.Day)
            }
    }
}
