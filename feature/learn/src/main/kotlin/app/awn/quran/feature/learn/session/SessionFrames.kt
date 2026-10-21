package app.awn.quran.feature.learn.session

import app.awn.quran.core.data.AyahRef
import app.awn.quran.feature.learn.course.CourseLesson
import app.awn.quran.feature.learn.course.LessonScreenData

/** What the line above the progress bar says. The screens turn each into text. */
sealed interface FrameLabel {
    data object None : FrameLabel

    data class NewWord(val number: Int, val total: Int) : FrameLabel

    data class Check(val number: Int, val total: Int) : FrameLabel

    data class LessonScreen(val number: Int, val total: Int) : FrameLabel
}

/** One screen of a session. */
sealed interface SessionFrame {
    val label: FrameLabel

    /** A word met for the first time, with the ayahs where it appears. */
    data class NewWord(
        val info: WordInfo,
        val ayahs: List<AyahRef>,
        override val label: FrameLabel,
    ) : SessionFrame

    /** One reading screen of a lesson. */
    data class Reading(
        val lesson: CourseLesson,
        val screen: LessonScreenData,
        override val label: FrameLabel,
    ) : SessionFrame

    /**
     * A question. [confirmWithButton] is true for the check at the end of the new words, where the
     * user picks an option and then presses Check; elsewhere an option answers at once.
     */
    data class Ask(
        val question: Question,
        val confirmWithButton: Boolean,
        override val label: FrameLabel,
    ) : SessionFrame

    /**
     * The screen after an answer. [chosenIndex] is null when the user pressed "I don't know".
     * [backInDays] is when the item returns, null in a quiz the user started, which moves no date.
     */
    data class Answer(
        val question: Question,
        val chosenIndex: Int?,
        val backInDays: Int?,
        override val label: FrameLabel,
    ) : SessionFrame {
        val isCorrect: Boolean
            get() = chosenIndex == question.correctIndex
    }

    /** The end. */
    data class Finished(val summary: SessionSummary) : SessionFrame {
        override val label: FrameLabel = FrameLabel.None
    }
}

/** What the session did, for the end screen. */
data class SessionSummary(
    val isOwnQuiz: Boolean,
    val wordsRevised: Int,
    val newWords: Int,
    val answeredCorrectly: Int,
    val wordsMetBefore: Int,
    val wordsMetAfter: Int,
    val wordsKnownBefore: Int,
    val wordsKnownAfter: Int,
    /** Share of all Quran words that the known words cover, 0.0 to 1.0. */
    val coverage: Double,
    /** The last ayah shown, with each of its words marked known or not; null when none. */
    val lastAyah: LastAyah?,
    val tomorrowReviews: Int,
    val tomorrowNewWords: Int,
)

data class LastAyah(val ayah: AyahRef, val words: List<LastAyahWord>) {
    val knownCount: Int
        get() = words.count { it.isKnown }
}

data class LastAyahWord(val text: String, val isKnown: Boolean)

/** What the screen draws now. [progress] runs from 0 to 1; [secondsLeft] is an estimate. */
data class SessionState(val frame: SessionFrame?, val progress: Float, val secondsLeft: Int)
