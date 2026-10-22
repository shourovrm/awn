package app.awn.quran.feature.learn.course

/**
 * Carries the progress of the twelve older tajweed lessons over to the nine tajweed units that
 * replaced them, so nobody who finished a lesson sees its subject as new.
 *
 * A unit counts as done when every older lesson that it covers is done: unit 11 covers both
 * makharij and sifat, so a user who read only makharij still has unit 11 to read.
 */
object LegacyTajweedProgress {

    private val UNIT_OF_LEGACY_LESSON =
        mapOf(
            "makharij" to "tajweed-11-where-letters-are-made",
            "sifat" to "tajweed-11-where-letters-are-made",
            "raa" to "tajweed-12-heavy-and-light-letters",
            "lam-of-allah" to "tajweed-12-heavy-and-light-letters",
            "noon-sakinah-tanween" to "tajweed-13-noon-saakin-and-tanween",
            "meem-sakinah" to "tajweed-14-meem-saakin",
            "ghunnah" to "tajweed-15-nasal-sound",
            "qalqalah" to "tajweed-16-echo-letters",
            "madd-natural" to "tajweed-17-long-vowels-held-longer",
            "madd-extended" to "tajweed-17-long-vowels-held-longer",
            "hamzat-al-wasl" to "tajweed-18-stopping-and-starting",
            "waqf-signs" to "tajweed-19-stop-signs",
        )

    /** [doneIds] with the units that the finished older lessons cover added. */
    fun withCarriedOver(doneIds: Set<String>): Set<String> {
        val legacyLessonsByUnit = UNIT_OF_LEGACY_LESSON.entries.groupBy({ it.value }, { it.key })
        val coveredUnits =
            legacyLessonsByUnit
                .filterValues { legacyLessons -> doneIds.containsAll(legacyLessons) }
                .keys
        return doneIds + coveredUnits
    }
}
