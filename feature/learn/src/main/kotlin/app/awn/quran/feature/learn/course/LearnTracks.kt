package app.awn.quran.feature.learn.course

/**
 * The four parts of Learn. Reading takes a beginner from the letters to reading a whole ayah, as a
 * Qaida does; Tajweed teaches the recitation rules in nine units; Grammar teaches how words are
 * built; Words is the frequency course of ten-word sets.
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
    /** Null when the track can be started now; otherwise the Reading unit after which it suits. */
    val opensAfterReadingUnit: Int?,
)

/** What the tracks need to know about the content and the user. */
data class TrackInput(
    val content: CourseContent,
    val languageCode: String,
    val doneLessonIds: Set<String>,
    /** True when the user chose to start with the letters, so Tajweed waits for Reading. */
    val includeLetters: Boolean,
)

object LearnTracks {

    /**
     * Tajweed rules need the sukoon and the shaddah, which Reading teaches in units 8 and 9; a user
     * who starts with the letters meets Tajweed after those.
     */
    const val TAJWEED_OPENS_AFTER_READING_UNIT = 9

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
            LessonCourse.Tajweed -> LearnTrack.Tajweed
            LessonCourse.Basics,
            LessonCourse.Grammar -> LearnTrack.Grammar
            null -> null
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
            LearnTrack.Tajweed -> courseTitles(input, LessonCourse.Tajweed)
            LearnTrack.Words -> emptyList()
        }

    private fun courseTitles(input: TrackInput, course: LessonCourse): List<Pair<String, String>> =
        input.content.inCourse(course).map { it.id to it.text(input.languageCode).title }
}
