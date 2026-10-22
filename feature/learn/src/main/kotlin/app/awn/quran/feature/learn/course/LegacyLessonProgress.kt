package app.awn.quran.feature.learn.course

/**
 * Carries the progress made in lessons that were replaced over to the units that cover them, so
 * nobody who finished a lesson sees its subject as new. Two sets of lessons were replaced: the
 * twelve older tajweed lessons by tajweed units 11 to 19, and the older Reading units by the ten
 * units of the Qaida order.
 *
 * A unit counts as done when every older lesson of one of its sets is done: unit 11 covers both
 * makharij and sifat, so a user who read only makharij still has unit 11 to read.
 */
object LegacyLessonProgress {

    /** A unit and the sets of older lessons of which any one set, finished, finishes the unit. */
    private class Coverage(val unit: String, vararg val olderLessonSets: Set<String>)

    private val COVERAGES =
        listOf(
            Coverage(
                "tajweed-11-where-letters-are-made",
                setOf("makharij", "sifat"),
            ),
            Coverage("tajweed-12-heavy-and-light-letters", setOf("raa", "lam-of-allah")),
            Coverage("tajweed-13-noon-saakin-and-tanween", setOf("noon-sakinah-tanween")),
            Coverage("tajweed-14-meem-saakin", setOf("meem-sakinah")),
            Coverage("tajweed-15-nasal-sound", setOf("ghunnah")),
            Coverage("tajweed-16-echo-letters", setOf("qalqalah")),
            Coverage("tajweed-17-long-vowels-held-longer", setOf("madd-natural", "madd-extended")),
            Coverage("tajweed-18-stopping-and-starting", setOf("hamzat-al-wasl")),
            Coverage("tajweed-19-stop-signs", setOf("waqf-signs")),
            Coverage("letters-03-letters-by-name", setOf("letters-08-letters-by-name")),
            Coverage("letters-04-short-vowels", setOf("letters-03-short-vowels")),
            Coverage("letters-05-tanween", setOf("letters-04-tanween")),
            Coverage("letters-07-madd-and-leen", setOf("letters-05-long-vowels")),
            Coverage("letters-08-sukoon", setOf("letters-06-sukoon")),
            Coverage("letters-09-shaddah", setOf("letters-07-shaddah")),
            // The last unit was first called letters-08 and then letters-09, so either one counts.
            Coverage(
                "letters-10-first-ayah",
                setOf("letters-08-first-ayah"),
                setOf("letters-09-first-ayah"),
            ),
        )

    /** [doneIds] with the units that the finished older lessons cover added. */
    fun withCarriedOver(doneIds: Set<String>): Set<String> {
        val coveredUnits =
            COVERAGES.filter { coverage ->
                    coverage.olderLessonSets.any { olderLessons ->
                        doneIds.containsAll(olderLessons)
                    }
                }
                .map { it.unit }
        return doneIds + coveredUnits
    }
}
