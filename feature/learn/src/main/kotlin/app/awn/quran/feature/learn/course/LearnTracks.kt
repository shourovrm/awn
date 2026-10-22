package app.awn.quran.feature.learn.course

/**
 * The four parts of Learn. Reading takes a beginner from the letters to reading a whole ayah, as a
 * Qaida does; Tajweed teaches the recitation rules; Grammar teaches how words are built; Words is
 * the frequency course of ten-word sets.
 */
enum class LearnTrack {
    Reading,
    Tajweed,
    Grammar,
    Words,
}

/** One numbered unit of a track; [number] counts from 1 in the track's order. */
data class TrackUnit(val id: String, val number: Int, val title: String, val state: PathState)

/** A track as the hub shows it: where the user is and whether it is open yet. */
data class TrackSummary(
    val track: LearnTrack,
    val doneCount: Int,
    val unitCount: Int,
    val next: TrackUnit?,
    /** Null when the track is open; otherwise the Reading unit that opens it. */
    val opensAfterReadingUnit: Int?,
)

/** A tajweed lesson of the older lesson list, which the Tajweed track lists by id. */
data class TajweedLessonTitle(val id: String, val title: String)

/** What the tracks need to know about the content and the user. */
data class TrackInput(
    val content: CourseContent,
    val languageCode: String,
    val tajweedLessons: List<TajweedLessonTitle>,
    val doneLessonIds: Set<String>,
    /** True when the user chose to start with the letters, so Tajweed waits for Reading. */
    val includeLetters: Boolean,
)

object LearnTracks {

    /**
     * Tajweed rules need the sukoon and the shaddah, which Reading teaches in units 6 and 7; a user
     * who starts with the letters meets Tajweed after those.
     */
    const val TAJWEED_OPENS_AFTER_READING_UNIT = 7

    /**
     * The order of the tajweed lessons: where letters are said and how they sound first, then the
     * rules of noon and meem sakinah, ghunnah and qalqalah, then the long vowels, and the rules of
     * single letters and of stopping last.
     */
    val TAJWEED_ORDER =
        listOf(
            "makharij",
            "sifat",
            "noon-sakinah-tanween",
            "meem-sakinah",
            "ghunnah",
            "qalqalah",
            "madd-natural",
            "madd-extended",
            "raa",
            "lam-of-allah",
            "hamzat-al-wasl",
            "waqf-signs",
        )

    /** The units of [track] in order. Words has no units: its sets are the course path. */
    fun units(track: LearnTrack, input: TrackInput): List<TrackUnit> {
        val ordered = orderedTitles(track, input)
        val firstNotDone = ordered.indexOfFirst { (id, _) -> id !in input.doneLessonIds }
        return ordered.mapIndexed { index, (id, title) ->
            val state =
                when {
                    id in input.doneLessonIds -> PathState.Done
                    index == firstNotDone -> PathState.Next
                    else -> PathState.Upcoming
                }
            TrackUnit(id = id, number = index + 1, title = title, state = state)
        }
    }

    /** The track's unit that follows [lessonId], or null when it is the last one. */
    fun unitAfter(track: LearnTrack, lessonId: String, input: TrackInput): TrackUnit? {
        val units = units(track, input)
        val position = units.indexOfFirst { it.id == lessonId }
        return if (position == -1) null else units.getOrNull(position + 1)
    }

    /** The track a course lesson belongs to, or null for a lesson outside the tracks. */
    fun trackOf(lessonId: String, content: CourseContent): LearnTrack? =
        when (content.lesson(lessonId)?.course) {
            LessonCourse.Letters -> LearnTrack.Reading
            LessonCourse.Basics,
            LessonCourse.Grammar -> LearnTrack.Grammar
            null -> if (lessonId in TAJWEED_ORDER) LearnTrack.Tajweed else null
        }

    fun summary(track: LearnTrack, input: TrackInput): TrackSummary {
        val units = units(track, input)
        return TrackSummary(
            track = track,
            doneCount = units.count { it.state == PathState.Done },
            unitCount = units.size,
            next = units.firstOrNull { it.state == PathState.Next },
            opensAfterReadingUnit = if (isOpen(track, input)) null else readingUnitThatOpens(input),
        )
    }

    fun isOpen(track: LearnTrack, input: TrackInput): Boolean {
        if (track != LearnTrack.Tajweed || !input.includeLetters) return true
        val needed = units(LearnTrack.Reading, input).take(readingUnitThatOpens(input))
        return needed.all { it.state == PathState.Done }
    }

    // While Reading has fewer units than the rule asks for, finishing them all opens Tajweed.
    private fun readingUnitThatOpens(input: TrackInput): Int =
        minOf(TAJWEED_OPENS_AFTER_READING_UNIT, input.content.inCourse(LessonCourse.Letters).size)

    private fun orderedTitles(track: LearnTrack, input: TrackInput): List<Pair<String, String>> =
        when (track) {
            LearnTrack.Reading -> courseTitles(input, LessonCourse.Letters)
            LearnTrack.Grammar ->
                courseTitles(input, LessonCourse.Basics) + courseTitles(input, LessonCourse.Grammar)
            LearnTrack.Tajweed -> {
                val byId = input.tajweedLessons.associateBy { it.id }
                TAJWEED_ORDER.mapNotNull { id -> byId[id]?.let { id to it.title } } +
                    input.tajweedLessons
                        .filter { it.id !in TAJWEED_ORDER }
                        .map { it.id to it.title }
            }
            LearnTrack.Words -> emptyList()
        }

    private fun courseTitles(input: TrackInput, course: LessonCourse): List<Pair<String, String>> =
        input.content.inCourse(course).map { it.id to it.text(input.languageCode).title }
}
